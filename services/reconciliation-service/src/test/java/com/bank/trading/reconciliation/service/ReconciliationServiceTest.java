package com.bank.trading.reconciliation.service;

import com.bank.trading.reconciliation.client.DownstreamClient;
import com.bank.trading.reconciliation.dto.ReconciliationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReconciliationService 单元测试。
 * <p>
 * 使用手写 StubDownstreamClient（继承真实类并重写 fetch 方法），不依赖 Mockito
 * （Java 25 兼容性问题）。@Value 注入的字段通过 ReflectionTestUtils 设置。
 */
class ReconciliationServiceTest {

    private StubDownstreamClient stub;
    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        stub = new StubDownstreamClient();
        service = new ReconciliationService(stub);
        // 注入 @Value 字段（与 application.yml 默认值一致）
        ReflectionTestUtils.setField(service, "exposureAlertThreshold", new BigDecimal("5"));
        ReflectionTestUtils.setField(service, "creditAlertThreshold", new BigDecimal("100"));
        ReflectionTestUtils.setField(service, "hedgeOrderCheckLimit", 100);
    }

    @Test
    @DisplayName("全部一致：敞口/额度/对冲挂单均正常，consistent=true")
    void reconcile_allConsistent() {
        stub.exposures = Arrays.asList(
                snap("AU2406", "10", "-10", "0"),     // 净敞口 0
                snap("AG2406", "20", "-20", "0")      // 净敞口 0
        );
        stub.accounts = Arrays.asList(
                acct("C001", "1000", "30"),            // usedCredit=30
                acct("C002", "1000", "0")
        );
        stub.hedgeOrders = Collections.emptyList();   // 无 NEW 状态挂单

        ReconciliationResult r = service.reconcile();

        assertTrue(r.isConsistent(), "全一致场景应为 consistent");
        assertTrue(r.getDiscrepancies().isEmpty(), "discrepancies 应为空");
        assertEquals(new BigDecimal("30"), r.getTotalCustomerPositionValue(), "客户持仓绝对值合计=10+20=30");
        assertEquals(new BigDecimal("30"), r.getTotalUsedCredit(), "已用额度合计=30+0=30");
        assertEquals(BigDecimal.ZERO, r.getCreditDelta(), "额度差异=30-30=0");
        assertNotNull(r.getBatchId());
        assertNotNull(r.getStartedAt());
        assertNotNull(r.getFinishedAt());
    }

    @Test
    @DisplayName("敞口超阈值：netExposure=8 > 5，记录 exposure 类型不一致")
    void reconcile_exposureExceedsThreshold() {
        stub.exposures = Collections.singletonList(
                snap("AU2406", "10", "2", "8")   // 净敞口 8 > 5
        );
        stub.accounts = Collections.emptyList();
        stub.hedgeOrders = Collections.emptyList();

        ReconciliationResult r = service.reconcile();

        assertFalse(r.isConsistent());
        // exposures 非空但 accounts 为空 → 应同时有 exposure + accounts data-missing 两条
        boolean hasExposure = r.getDiscrepancies().stream()
                .anyMatch(d -> "exposure".equals(d.getType()) && "AU2406".equals(d.getKey()));
        assertTrue(hasExposure, "应包含 AU2406 的 exposure 不一致");
        assertEquals(new BigDecimal("8"), r.getTotalNetExposureAbs());
    }

    @Test
    @DisplayName("额度差异超阈值：持仓占用=200，已用额度=50，delta=150 > 100")
    void reconcile_creditDeltaExceedsThreshold() {
        stub.exposures = Collections.singletonList(
                snap("AU2406", "200", "-200", "0")   // 持仓绝对值=200
        );
        stub.accounts = Collections.singletonList(
                acct("C001", "1000", "50")           // 已用=50
        );
        stub.hedgeOrders = Collections.emptyList();

        ReconciliationResult r = service.reconcile();

        assertFalse(r.isConsistent());
        boolean hasCredit = r.getDiscrepancies().stream()
                .anyMatch(d -> "credit".equals(d.getType()) && "AGGREGATE".equals(d.getKey()));
        assertTrue(hasCredit, "应包含 AGGREGATE 的 credit 不一致");
        assertEquals(new BigDecimal("150"), r.getCreditDelta());
    }

    @Test
    @DisplayName("下游服务不可达：返回空列表，记录 data-missing")
    void reconcile_dataMissing() {
        stub.exposures = Collections.emptyList();
        stub.accounts = Collections.emptyList();
        stub.hedgeOrders = Collections.emptyList();

        ReconciliationResult r = service.reconcile();

        assertFalse(r.isConsistent());
        boolean hasExposureMissing = r.getDiscrepancies().stream()
                .anyMatch(d -> "data-missing".equals(d.getType()) && "exposure".equals(d.getKey()));
        boolean hasAccountsMissing = r.getDiscrepancies().stream()
                .anyMatch(d -> "data-missing".equals(d.getType()) && "accounts".equals(d.getKey()));
        assertTrue(hasExposureMissing, "应记录 exposure 数据缺失");
        assertTrue(hasAccountsMissing, "应记录 accounts 数据缺失");
    }

    @Test
    @DisplayName("对冲挂单存在 NEW 状态：记录 hedge-pending 不一致")
    void reconcile_pendingHedgeOrders() {
        stub.exposures = Collections.singletonList(
                snap("AU2406", "10", "-10", "0")
        );
        stub.accounts = Collections.singletonList(
                acct("C001", "1000", "10")
        );
        stub.hedgeOrders = Arrays.asList(
                hedge("H001", "AU2406", "BUY", "5", "0", "NEW"),
                hedge("H002", "AU2406", "BUY", "3", "0", "FILLED"),
                hedge("H003", "AU2406", "BUY", "2", "0", "NEW")
        );

        ReconciliationResult r = service.reconcile();

        // 持仓占用=10，已用=10，delta=0；敞口=0；但有 NEW 状态对冲单 → 不一致
        assertFalse(r.isConsistent());
        boolean hasPending = r.getDiscrepancies().stream()
                .anyMatch(d -> "hedge-pending".equals(d.getType()));
        assertTrue(hasPending, "应记录 hedge-pending 不一致");
    }

    @Test
    @DisplayName("netExposure 为 null 时跳过该项，不影响总额计算")
    void reconcile_nullNetExposureSkipped() {
        stub.exposures = Arrays.asList(
                snap("AU2406", "10", "-10", "0"),
                snap("AG2406", "20", null, null)   // netExposure 为 null，跳过
        );
        stub.accounts = Collections.emptyList();
        stub.hedgeOrders = Collections.emptyList();

        ReconciliationResult r = service.reconcile();

        // 只计算 AU2406 的 customerPosition=10
        assertEquals(new BigDecimal("10"), r.getTotalCustomerPositionValue());
        assertEquals(new BigDecimal("0"), r.getTotalNetExposureAbs());
    }

    // ---------- 工具方法 ----------

    private DownstreamClient.ExposureSnapshot snap(String symbol, String cust, String hedge, String net) {
        DownstreamClient.ExposureSnapshot s = new DownstreamClient.ExposureSnapshot();
        s.symbol = symbol;
        s.customerPosition = cust == null ? null : new BigDecimal(cust);
        s.hedgePosition = hedge == null ? null : new BigDecimal(hedge);
        s.netExposure = net == null ? null : new BigDecimal(net);
        return s;
    }

    private DownstreamClient.AccountSnapshot acct(String customerId, String limit, String used) {
        DownstreamClient.AccountSnapshot s = new DownstreamClient.AccountSnapshot();
        s.customerId = customerId;
        s.creditLimit = limit == null ? null : new BigDecimal(limit);
        s.usedCredit = used == null ? null : new BigDecimal(used);
        return s;
    }

    private DownstreamClient.HedgeOrderSnapshot hedge(String id, String symbol, String side,
                                                      String qty, String filled, String status) {
        DownstreamClient.HedgeOrderSnapshot s = new DownstreamClient.HedgeOrderSnapshot();
        s.hedgeOrderId = id;
        s.symbol = symbol;
        s.side = side;
        s.qty = new BigDecimal(qty);
        s.filledQty = new BigDecimal(filled);
        s.status = status;
        return s;
    }

    /**
     * 桩 DownstreamClient：绕过 RestTemplate，直接返回测试预设数据。
     */
    static class StubDownstreamClient extends DownstreamClient {
        List<ExposureSnapshot> exposures = new ArrayList<>();
        List<AccountSnapshot> accounts = new ArrayList<>();
        List<HedgeOrderSnapshot> hedgeOrders = new ArrayList<>();

        StubDownstreamClient() {
            super(new RestTemplate());
        }

        @Override
        public List<ExposureSnapshot> fetchNetExposure() {
            return exposures;
        }

        @Override
        public List<AccountSnapshot> fetchAccounts() {
            return accounts;
        }

        @Override
        public List<HedgeOrderSnapshot> fetchRecentHedgeOrders(int limit) {
            return hedgeOrders;
        }
    }
}
