package com.bank.trading.reconciliation.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 下游服务客户端：聚合 position / account / execution 三个服务的查询能力。
 * <p>
 * 为简化模块，统一放在一个 client 类中。每个查询方法独立容错：
 * 任一服务不可达时返回空列表 / null，对账逻辑据此记录 data-missing 不一致。
 */
@Slf4j
@Component
public class DownstreamClient {

    private final RestTemplate restTemplate;

    @Value("${reconciliation.position-service-url:http://position-service}")
    private String positionServiceUrl;

    @Value("${reconciliation.account-service-url:http://account-service}")
    private String accountServiceUrl;

    @Value("${reconciliation.execution-service-url:http://execution-service}")
    private String executionServiceUrl;

    public DownstreamClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 拉取所有合约的净敞口。
     * <p>
     * 调用 GET /positions/exposure，返回 [{symbol, customerPosition, hedgePosition, netExposure}, ...]
     */
    public List<ExposureSnapshot> fetchNetExposure() {
        try {
            String resp = restTemplate.getForObject(
                    positionServiceUrl + "/positions/exposure", String.class);
            return parseExposure(resp);
        } catch (Exception e) {
            log.warn("Fetch net exposure failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 拉取所有客户列表。
     * <p>
     * 调用 GET /accounts，返回 [{customerId, creditLimit, usedCredit, ...}, ...]
     */
    public List<AccountSnapshot> fetchAccounts() {
        try {
            String resp = restTemplate.getForObject(
                    accountServiceUrl + "/accounts", String.class);
            return parseAccounts(resp);
        } catch (Exception e) {
            log.warn("Fetch accounts failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 拉取最近的对冲订单列表。
     * <p>
     * 调用 GET /execution/orders?limit=N，返回对冲订单快照。
     */
    public List<HedgeOrderSnapshot> fetchRecentHedgeOrders(int limit) {
        try {
            String resp = restTemplate.getForObject(
                    executionServiceUrl + "/execution/orders?limit=" + limit, String.class);
            return parseHedgeOrders(resp);
        } catch (Exception e) {
            log.warn("Fetch hedge orders failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<ExposureSnapshot> parseExposure(String resp) {
        List<ExposureSnapshot> list = new ArrayList<>();
        if (resp == null) {
            return list;
        }
        JSONObject json = JSON.parseObject(resp);
        JSONArray arr = json.getJSONArray("data");
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            ExposureSnapshot s = new ExposureSnapshot();
            s.symbol = o.getString("symbol");
            s.customerPosition = o.getBigDecimal("customerPosition");
            s.hedgePosition = o.getBigDecimal("hedgePosition");
            s.netExposure = o.getBigDecimal("netExposure");
            list.add(s);
        }
        return list;
    }

    private List<AccountSnapshot> parseAccounts(String resp) {
        List<AccountSnapshot> list = new ArrayList<>();
        if (resp == null) {
            return list;
        }
        JSONObject json = JSON.parseObject(resp);
        JSONArray arr = json.getJSONArray("data");
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            AccountSnapshot s = new AccountSnapshot();
            s.customerId = o.getString("customerId");
            s.creditLimit = o.getBigDecimal("creditLimit");
            s.usedCredit = o.getBigDecimal("usedCredit");
            list.add(s);
        }
        return list;
    }

    private List<HedgeOrderSnapshot> parseHedgeOrders(String resp) {
        List<HedgeOrderSnapshot> list = new ArrayList<>();
        if (resp == null) {
            return list;
        }
        JSONObject json = JSON.parseObject(resp);
        JSONArray arr = json.getJSONArray("data");
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            HedgeOrderSnapshot s = new HedgeOrderSnapshot();
            s.hedgeOrderId = o.getString("hedgeOrderId");
            s.symbol = o.getString("symbol");
            s.side = o.getString("side");
            s.qty = o.getBigDecimal("qty");
            s.filledQty = o.getBigDecimal("filledQty");
            s.status = o.getString("status");
            list.add(s);
        }
        return list;
    }

    /** 净敞口快照 */
    public static class ExposureSnapshot {
        public String symbol;
        public BigDecimal customerPosition;
        public BigDecimal hedgePosition;
        public BigDecimal netExposure;
    }

    /** 账户快照 */
    public static class AccountSnapshot {
        public String customerId;
        public BigDecimal creditLimit;
        public BigDecimal usedCredit;
    }

    /** 对冲订单快照 */
    public static class HedgeOrderSnapshot {
        public String hedgeOrderId;
        public String symbol;
        public String side;
        public BigDecimal qty;
        public BigDecimal filledQty;
        public String status;
    }
}
