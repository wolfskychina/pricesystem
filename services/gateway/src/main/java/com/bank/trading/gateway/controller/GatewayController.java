package com.bank.trading.gateway.controller;

import com.bank.trading.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关健康与路由信息接口。
 * <p>
 * 提供简单的存活探针与路由清单摘要，便于运维与前端快速定位网关可达性。
 */
@RestController
public class GatewayController {

    /**
     * 网关存活探针。
     */
    @GetMapping("/actuator/health/gateway-custom")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("component", "gateway");
        return Result.success(data);
    }

    /**
     * 网关路由摘要（人工查阅用，不含服务实例详情）。
     */
    @GetMapping("/gateway/routes-summary")
    public Result<Map<String, String>> routesSummary() {
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("/api/orders/**", "oms-service");
        routes.put("/api/refdata/**", "refdata-service");
        routes.put("/api/quotes/**", "pricing-service");
        routes.put("/api/risk/**", "risk-service");
        routes.put("/api/positions/**", "position-service");
        routes.put("/api/accounts/**", "account-service");
        routes.put("/api/execution/**", "execution-service");
        routes.put("/api/marketdata/**", "market-data-service");
        return Result.success(routes);
    }
}
