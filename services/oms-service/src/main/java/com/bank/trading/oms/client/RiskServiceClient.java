package com.bank.trading.oms.client;

import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class RiskServiceClient {

    private final RestTemplate restTemplate;

    @Value("${oms.risk-service-url:http://risk-service}")
    private String riskServiceUrl;

    public RiskServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public RiskCheckResult checkPreTrade(RiskCheckRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RiskCheckRequest> entity = new HttpEntity<>(request, headers);

            String url = riskServiceUrl + "/risk/pre-trade";
            Result result = restTemplate.postForObject(url, entity, Result.class);

            if (result != null && result.isSuccess() && result.getData() != null) {
                return convertResult(result.getData());
            }
            return RiskCheckResult.reject("RISK_SERVICE_ERROR", "Risk service returned failure");
        } catch (Exception e) {
            log.warn("Failed to call risk service: {}", e.getMessage());
            return RiskCheckResult.reject("RISK_SERVICE_UNAVAILABLE", "Risk service unavailable: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private RiskCheckResult convertResult(Object data) {
        if (data instanceof java.util.Map) {
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) data;
            RiskCheckResult result = new RiskCheckResult();
            result.setPassed(Boolean.TRUE.equals(map.get("passed")));
            result.setRejectCode((String) map.get("rejectCode"));
            result.setRejectReason((String) map.get("rejectReason"));
            result.setRuleName((String) map.get("ruleName"));
            return result;
        }
        return RiskCheckResult.reject("RISK_PARSE_ERROR", "Failed to parse risk result");
    }
}
