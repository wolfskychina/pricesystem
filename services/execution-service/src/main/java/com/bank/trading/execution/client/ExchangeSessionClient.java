package com.bank.trading.execution.client;

import com.bank.trading.execution.dto.ExchangeOrderRequest;
import com.bank.trading.execution.dto.ExchangeOrderResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bank.trading.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 交易所会话客户端，封装与 sim-exchange 的所有交互。
 * <p>
 * 对应真实做市商系统中的 CTP SDK 封装层或 FIX 引擎客户端。在真实系统中，
 * 做市商通过 {@code CThostFtdcTraderApi}（CTP）或 FIX 会话与交易所通信；
 * 本类通过 HTTP 调用 sim-exchange 的 REST 接口，但<b>语义对齐</b>：
 * <ul>
 *   <li>{@link #submitOrder}：提交对冲单，对应 CTP {@code ReqOrderInsert}，
 *       同步返回订单受理（NEW），成交通过 Webhook 异步推送</li>
 *   <li>{@link #registerCallback}：注册 Webhook 回调地址，对应 CTP 中
 *       {@code RegisterSpi} 注册回调处理器</li>
 *   <li>{@link #queryOrder}：查询订单状态，用于回调丢失时的主动对账</li>
 * </ul>
 * <p>
 * 错误处理：网络异常或交易所返回非 200 业务码时抛出 {@link ExchangeException}，
 * 由上层 ExecutionService 决定重试或告警。
 */
@Component
public class ExchangeSessionClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeSessionClient.class);

    /** HTTP 客户端 */
    private final RestTemplate restTemplate;

    /** sim-exchange 基础地址，例如 http://localhost:8081 */
    private final String exchangeBaseUrl;

    /** 本服务回调接收基础地址，例如 http://localhost:8086/execution/callback */
    private final String callbackBaseUrl;

    /**
     * 构造函数，通过依赖注入获取 RestTemplate 与配置。
     *
     * @param restTemplate    HTTP 客户端
     * @param exchangeBaseUrl 模拟交易所基础地址（execution.exchange-base-url）
     * @param callbackBaseUrl 本服务回调接收地址（execution.callback-base-url）
     */
    public ExchangeSessionClient(RestTemplate restTemplate,
                                 @Value("${execution.exchange-base-url}") String exchangeBaseUrl,
                                 @Value("${execution.callback-base-url}") String callbackBaseUrl) {
        this.restTemplate = restTemplate;
        this.exchangeBaseUrl = exchangeBaseUrl;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    /**
     * 注册 Webhook 回调地址到交易所。
     * <p>
     * 对应真实做市商启动时调用 {@code RegisterSpi} 注册回调处理器。应在服务启动时
     * 调用一次，之后交易所撮合完成会主动向 {@code {callbackBaseUrl}/order} 和
     * {@code {callbackBaseUrl}/trade} 推送回报。
     * <p>
     * 错误处理：注册失败仅记录日志不抛异常，不影响服务启动（可后续重试）。
     */
    public void registerCallback() {
        String url = exchangeBaseUrl + "/exchange/callbacks/register";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JSONObject body = new JSONObject();
            body.put("url", callbackBaseUrl);
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);

            restTemplate.postForEntity(url, entity, String.class);
            log.info("Webhook callback registered to exchange: {}", callbackBaseUrl);
        } catch (Exception e) {
            log.warn("Failed to register webhook callback to exchange: {}", e.getMessage());
        }
    }

    /**
     * 向交易所提交对冲订单（同步受理阶段）。
     * <p>
     * 对应 CTP {@code ReqOrderInsert}。同步返回的订单状态为 NEW（已受理，尚未撮合），
     * 成交结果将通过 Webhook 异步推送。
     *
     * @param request 对冲订单请求
     * @return 交易所返回的订单对象（状态=NEW）
     * @throws ExchangeException 网络错误或交易所返回业务错误时抛出
     */
    public ExchangeOrderResponse submitOrder(ExchangeOrderRequest request) {
        String url = exchangeBaseUrl + "/exchange/orders";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ExchangeOrderRequest> entity = new HttpEntity<>(request, headers);

            String responseJson = restTemplate.postForObject(url, entity, String.class);
            return parseOrderResponse(responseJson);
        } catch (Exception e) {
            throw new ExchangeException("Failed to submit order to exchange: " + e.getMessage(), e);
        }
    }

    /**
     * 查询订单状态（主动对账用）。
     * <p>
     * 对应 CTP {@code ReqQryOrder}。当 Webhook 回调丢失或需要确认订单最终状态时调用。
     *
     * @param exchangeOrderId 交易所订单 ID
     * @return 订单对象；不存在时返回 null
     * @throws ExchangeException 网络错误时抛出
     */
    public ExchangeOrderResponse queryOrder(String exchangeOrderId) {
        String url = exchangeBaseUrl + "/exchange/orders/" + exchangeOrderId;
        try {
            String responseJson = restTemplate.getForObject(url, String.class);
            return parseOrderResponse(responseJson);
        } catch (Exception e) {
            throw new ExchangeException("Failed to query order from exchange: " + e.getMessage(), e);
        }
    }

    /**
     * 解析交易所返回的 {@code Result<ExchangeOrder>} JSON。
     * <p>
     * sim-exchange 所有接口返回 {@code Result} 包装结构，需先取 data 字段
     * 再反序列化为 {@link ExchangeOrderResponse}。
     *
     * @param json 原始 JSON 字符串
     * @return 订单对象；业务错误（code != 200）或无数据时返回 null
     */
    private ExchangeOrderResponse parseOrderResponse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        JSONObject result = JSON.parseObject(json);
        Integer code = result.getInteger("code");
        if (code == null || code != 200) {
            String message = result.getString("message");
            log.warn("Exchange returned business error: code={}, message={}", code, message);
            return null;
        }
        JSONObject data = result.getJSONObject("data");
        if (data == null) {
            return null;
        }
        return data.toJavaObject(ExchangeOrderResponse.class);
    }

    /**
     * 交易所通信异常。
     */
    public static class ExchangeException extends RuntimeException {
        public ExchangeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
