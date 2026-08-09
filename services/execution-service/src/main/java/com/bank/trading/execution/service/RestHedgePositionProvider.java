package com.bank.trading.execution.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * 通过 REST 调用 position-service 查询对冲持仓的 {@link HedgePositionProvider} 实现。
 * <p>
 * 调用 position-service 的 {@code GET /positions/exposure/{symbol}} 接口获取净敞口，
 * 从中提取 hedgePosition 字段作为当前对冲持仓。
 * <p>
 * 当 position-service 不可达时，返回 {@link BigDecimal#ZERO}（不阻塞对冲流程，
 * 降级为不截断，依赖后续 InventoryMonitor 告警）。
 */
@Component
public class RestHedgePositionProvider implements HedgePositionProvider {

    private static final Logger log = LoggerFactory.getLogger(RestHedgePositionProvider.class);

    private final RestTemplate restTemplate;

    @Value("${execution.position-service-url:http://localhost:8087}")
    private String positionServiceUrl;

    public RestHedgePositionProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public BigDecimal getHedgePosition(String symbol) {
        String url = positionServiceUrl + "/positions/exposure/" + symbol;
        try {
            String responseJson = restTemplate.getForObject(url, String.class);
            if (responseJson == null || responseJson.isEmpty()) {
                return BigDecimal.ZERO;
            }

            JSONObject result = JSON.parseObject(responseJson);
            Integer code = result.getInteger("code");
            if (code == null || code != 200) {
                log.debug("position-service returned non-200 for symbol={}: code={}", symbol, code);
                return BigDecimal.ZERO;
            }

            JSONObject data = result.getJSONObject("data");
            if (data == null) {
                return BigDecimal.ZERO;
            }

            BigDecimal hedgeQty = data.getBigDecimal("hedgePosition");
            return hedgeQty != null ? hedgeQty : BigDecimal.ZERO;
        } catch (Exception e) {
            log.debug("Failed to query hedge position from position-service for symbol={}: {}",
                    symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}