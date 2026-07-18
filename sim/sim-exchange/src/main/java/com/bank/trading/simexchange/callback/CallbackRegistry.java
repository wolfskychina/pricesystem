package com.bank.trading.simexchange.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Webhook 回调注册表与推送器。
 * <p>
 * 模拟真实交易所（CTP/Globex）通过 SDK/FIX 会话回调推送订单状态回报与成交通知的机制。
 * 做市商 execution-service 启动时调用 {@code POST /exchange/callbacks/register} 注册回调地址，
 * 撮合完成后由本组件异步推送两类回调：
 * <ul>
 *   <li>订单状态回报（模拟 CTP {@code OnRtnOrder}）：订单状态变更时推送</li>
 *   <li>成交通知（模拟 CTP {@code OnRtnTrade}）：发生成交时推送</li>
 * </ul>
 * <p>
 * 回调失败仅记录日志，不阻塞撮合流程（模拟真实交易所的"best effort"推送语义）。
 */
@Component
public class CallbackRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallbackRegistry.class);

    /** 注册的回调地址集合；支持多个 EMS 实例同时订阅 */
    private final Set<String> callbackUrls = new CopyOnWriteArraySet<>();

    /** HTTP 客户端，用于推送回调 */
    private final RestTemplate restTemplate;

    /** 撮合延迟（毫秒），模拟交易所从受理到撮合的延迟 */
    private final long matchDelayMs;

    /**
     * 构造函数。
     *
     * @param restTemplate HTTP 客户端
     * @param matchDelayMs 撮合延迟（毫秒），从配置 sim-exchange.match-delay-ms 读取
     */
    @Autowired
    public CallbackRegistry(RestTemplate restTemplate,
                            @Value("${sim-exchange.match-delay-ms:0}") long matchDelayMs) {
        this.restTemplate = restTemplate;
        this.matchDelayMs = matchDelayMs;
    }

    /**
     * 注册回调地址。
     * <p>
     * 做市商 EMS 启动时调用，将自身的回调接收地址加入订阅列表。
     * 重复注册同一地址会被忽略（幂等）。
     *
     * @param url 回调地址，例如 {@code http://execution-service:8086/execution/callback/order}
     */
    public void register(String url) {
        callbackUrls.add(url);
        log.info("Webhook callback registered: {}", url);
    }

    /**
     * 获取配置的撮合延迟。
     *
     * @return 撮合延迟（毫秒）
     */
    public long getMatchDelayMs() {
        return matchDelayMs;
    }

    /**
     * 推送订单状态回报（模拟 CTP OnRtnOrder）。
     * <p>
     * 并发推送到所有已注册的回调地址。单个地址推送失败仅记录日志，不影响其他地址。
     *
     * @param payload 订单状态回报载荷
     */
    public void notifyOrderUpdate(Object payload) {
        for (String url : callbackUrls) {
            // 订单回报路径约定为 {baseUrl}/execution/callback/order
            String target = buildTargetUrl(url, "/order");
            sendCallback(target, payload, "order");
        }
    }

    /**
     * 推送成交通知（模拟 CTP OnRtnTrade）。
     * <p>
     * 并发推送到所有已注册的回调地址。单个地址推送失败仅记录日志，不影响其他地址。
     *
     * @param payload 成交通知载荷
     */
    public void notifyTrade(Object payload) {
        for (String url : callbackUrls) {
            // 成交通知路径约定为 {baseUrl}/execution/callback/trade
            String target = buildTargetUrl(url, "/trade");
            sendCallback(target, payload, "trade");
        }
    }

    /**
     * 根据注册的 baseUrl 和子路径构造完整回调 URL。
     * <p>
     * 注册时传入的是 baseUrl（如 {@code http://host:8086/execution/callback}），
     * 推送时在末尾追加 {@code /order} 或 {@code /trade}。
     *
     * @param baseUrl 注册的基础 URL
     * @param subPath 子路径（{@code /order} 或 {@code /trade}）
     * @return 完整回调 URL
     */
    private String buildTargetUrl(String baseUrl, String subPath) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + subPath;
        }
        return baseUrl + subPath;
    }

    /**
     * 发送单个回调请求。
     * <p>
     * 使用 POST + JSON，失败仅记录日志不抛异常（best effort 语义）。
     *
     * @param url     目标 URL
     * @param payload 请求体
     * @param type    回调类型（order/trade），仅用于日志
     */
    private void sendCallback(String url, Object payload, String type) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, entity, String.class);
            log.debug("Callback [{}] sent to {}: {}", type, url, payload);
        } catch (Exception e) {
            log.warn("Callback [{}] failed to {}: {}", type, url, e.getMessage());
        }
    }

    /**
     * 返回当前已注册的回调地址数量（用于测试与监控）。
     *
     * @return 已注册地址数
     */
    public int size() {
        return callbackUrls.size();
    }

    /**
     * 清空所有已注册的回调地址（用于测试）。
     */
    public void clear() {
        callbackUrls.clear();
    }
}
