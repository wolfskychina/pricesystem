package com.bank.trading.position.service;

import com.bank.trading.common.core.event.HedgeFillEvent;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.persistence.idempotent.IdempotentConsumer;
import com.bank.trading.common.persistence.idempotent.ProcessedEventMapper;
import com.bank.trading.position.entity.NetExposure;
import com.bank.trading.position.entity.Position;
import com.bank.trading.position.mapper.HedgePositionMapper;
import com.bank.trading.position.mapper.PositionMapper;
import com.bank.trading.position.entity.HedgePosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PositionService 单元测试。
 * <p>
 * 采用手写 Mock 实现类（Java 25 + Mockito 不兼容）。
 * 覆盖：客户持仓累加、加权平均成本、平仓盈亏、反手开仓、对冲持仓累加、
 * 净敞口计算、幂等消费。
 */
class PositionServiceTest {

    private PositionService positionService;
    private InMemoryPositionMapper positionMapper;
    private InMemoryHedgePositionMapper hedgePositionMapper;
    private InMemoryProcessedEventMapper processedEventMapper;

    @BeforeEach
    void setUp() {
        positionMapper = new InMemoryPositionMapper();
        hedgePositionMapper = new InMemoryHedgePositionMapper();
        processedEventMapper = new InMemoryProcessedEventMapper();
        IdempotentConsumer idempotentConsumer = new IdempotentConsumer(processedEventMapper);
        positionService = new PositionService(positionMapper, hedgePositionMapper, idempotentConsumer);
    }

    // ==================== 客户持仓累加测试 ====================

    @Test
    @DisplayName("客户首次买入成交 → 创建多头持仓，avg_cost = 成交价")
    void onTradeEvent_firstBuy_createsLongPosition() {
        TradeEvent event = buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"));

        positionService.onTradeEvent(event);

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertNotNull(position);
        assertDecimalEquals(new BigDecimal("10.0000"), position.getQty());
        assertDecimalEquals(new BigDecimal("500.00"), position.getAvgCost());
        assertDecimalEquals(BigDecimal.ZERO, position.getRealizedPnl());
    }

    @Test
    @DisplayName("客户首次卖出成交 → 创建空头持仓（qty 为负）")
    void onTradeEvent_firstSell_createsShortPosition() {
        TradeEvent event = buildTradeEvent("T002", "CUST001", "AU2406", "SELL",
                new BigDecimal("5"), new BigDecimal("500.00"));

        positionService.onTradeEvent(event);

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertDecimalEquals(new BigDecimal("-5.0000"), position.getQty());
        assertDecimalEquals(new BigDecimal("500.00"), position.getAvgCost());
    }

    @Test
    @DisplayName("同向加仓 → 加权平均成本更新")
    void onTradeEvent_sameDirectionAdd_updatesWeightedAvgCost() {
        // 第一笔：BUY 10 @ 500
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // 第二笔：BUY 10 @ 520 → 加权 = (10*500 + 10*520)/20 = 510
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("520.00")));

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertDecimalEquals(new BigDecimal("20.0000"), position.getQty());
        assertDecimalEquals(new BigDecimal("510.00000000"), position.getAvgCost());
        assertDecimalEquals(BigDecimal.ZERO, position.getRealizedPnl());
    }

    @Test
    @DisplayName("多头部分平仓 → realized_pnl 计算，avg_cost 不变")
    void onTradeEvent_partialCloseLong_realizesPnl() {
        // 开仓：BUY 10 @ 500
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // 平仓 4 @ 525 → pnl = 4 * (525 - 500) = 100
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST001", "AU2406", "SELL",
                new BigDecimal("4"), new BigDecimal("525.00")));

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertDecimalEquals(new BigDecimal("6.0000"), position.getQty());
        assertDecimalEquals(new BigDecimal("500.00"), position.getAvgCost(), "平仓后 avg_cost 不应变化");
        assertDecimalEquals(new BigDecimal("100.00000000"), position.getRealizedPnl());
    }

    @Test
    @DisplayName("空头部分平仓 → realized_pnl 计算")
    void onTradeEvent_partialCloseShort_realizesPnl() {
        // 开空：SELL 10 @ 500
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // 平空 4 @ 480 → pnl = 4 * (500 - 480) = 80
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST001", "AU2406", "BUY",
                new BigDecimal("4"), new BigDecimal("480.00")));

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertDecimalEquals(new BigDecimal("-6.0000"), position.getQty());
        assertDecimalEquals(new BigDecimal("80.00000000"), position.getRealizedPnl());
    }

    @Test
    @DisplayName("多头反手为空头 → 先平仓结算盈亏，剩余以新价开空")
    void onTradeEvent_reverseFromLongToShort_realizesAndOpensNewSide() {
        // 开多：BUY 10 @ 500
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // 反手：SELL 15 @ 520 → 平多10（pnl=10*(520-500)=200），开空 5 @ 520
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST001", "AU2406", "SELL",
                new BigDecimal("15"), new BigDecimal("520.00")));

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertDecimalEquals(new BigDecimal("-5.0000"), position.getQty());
        assertDecimalEquals(new BigDecimal("520.00"), position.getAvgCost(), "反手开仓 avg_cost = 新价");
        assertDecimalEquals(new BigDecimal("200.00000000"), position.getRealizedPnl());
    }

    @Test
    @DisplayName("不同客户独立持仓 → 互不影响")
    void onTradeEvent_differentCustomers_independentPositions() {
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST002", "AU2406", "BUY",
                new BigDecimal("5"), new BigDecimal("510.00")));

        Position p1 = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        Position p2 = positionMapper.findByCustomerAndSymbol("CUST002", "AU2406");
        assertDecimalEquals(new BigDecimal("10.0000"), p1.getQty());
        assertDecimalEquals(new BigDecimal("5.0000"), p2.getQty());
        assertDecimalEquals(new BigDecimal("500.00"), p1.getAvgCost());
        assertDecimalEquals(new BigDecimal("510.00"), p2.getAvgCost());
    }

    @Test
    @DisplayName("同客户不同合约 → 独立持仓记录")
    void onTradeEvent_differentSymbols_separatePositions() {
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST001", "CU2406", "BUY",
                new BigDecimal("3"), new BigDecimal("70000.00")));

        List<Position> positions = positionMapper.findByCustomer("CUST001");
        assertEquals(2, positions.size());
    }

    // ==================== 对冲持仓累加测试 ====================

    @Test
    @DisplayName("对冲买入成交 → 创建对冲多头")
    void onHedgeFillEvent_buy_createsHedgeLong() {
        HedgeFillEvent event = buildHedgeFillEvent("H001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"));

        positionService.onHedgeFillEvent(event);

        HedgePosition hedge = hedgePositionMapper.findBySymbol("AU2406");
        assertNotNull(hedge);
        assertDecimalEquals(new BigDecimal("10.0000"), hedge.getQty());
        assertDecimalEquals(new BigDecimal("500.00"), hedge.getAvgCost());
    }

    @Test
    @DisplayName("对冲卖出成交 → 创建对冲空头")
    void onHedgeFillEvent_sell_createsHedgeShort() {
        HedgeFillEvent event = buildHedgeFillEvent("H002", "AU2406", "SELL",
                new BigDecimal("7"), new BigDecimal("510.00"));

        positionService.onHedgeFillEvent(event);

        HedgePosition hedge = hedgePositionMapper.findBySymbol("AU2406");
        assertDecimalEquals(new BigDecimal("-7.0000"), hedge.getQty());
    }

    @Test
    @DisplayName("对冲同向加仓 → 加权平均成本更新")
    void onHedgeFillEvent_sameDirectionAdd_updatesWeightedAvgCost() {
        positionService.onHedgeFillEvent(buildHedgeFillEvent("H001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        positionService.onHedgeFillEvent(buildHedgeFillEvent("H002", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("520.00")));

        HedgePosition hedge = hedgePositionMapper.findBySymbol("AU2406");
        assertDecimalEquals(new BigDecimal("20.0000"), hedge.getQty());
        assertDecimalEquals(new BigDecimal("510.00000000"), hedge.getAvgCost());
    }

    @Test
    @DisplayName("对冲不同合约 → 独立对冲持仓记录")
    void onHedgeFillEvent_differentSymbols_separateHedgePositions() {
        positionService.onHedgeFillEvent(buildHedgeFillEvent("H001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        positionService.onHedgeFillEvent(buildHedgeFillEvent("H002", "CU2406", "SELL",
                new BigDecimal("3"), new BigDecimal("70000.00")));

        assertEquals(2, hedgePositionMapper.findAll().size());
    }

    // ==================== 净敞口计算测试 ====================

    @Test
    @DisplayName("完全对冲 → 净敞口为 0")
    void calculateNetExposure_fullyHedged_zeroExposure() {
        // 客户买入 10
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // 对冲买入 10（与客户方向相同，做市商建立空头后对冲买入平掉）
        positionService.onHedgeFillEvent(buildHedgeFillEvent("H001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));

        NetExposure exposure = positionService.calculateNetExposure("AU2406");
        assertNotNull(exposure);
        assertDecimalEquals(new BigDecimal("10.0000"), exposure.getCustomerPosition());
        assertDecimalEquals(new BigDecimal("10.0000"), exposure.getHedgePosition());
        assertDecimalEquals(new BigDecimal("0.0000"), exposure.getNetExposure());
    }

    @Test
    @DisplayName("未对冲 → 净敞口等于客户头寸")
    void calculateNetExposure_unhedged_exposureEqualsCustomerPosition() {
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // 没有对冲

        NetExposure exposure = positionService.calculateNetExposure("AU2406");
        assertNotNull(exposure);
        assertDecimalEquals(new BigDecimal("10.0000"), exposure.getCustomerPosition());
        assertDecimalEquals(new BigDecimal("0.0000"), exposure.getHedgePosition());
        assertDecimalEquals(new BigDecimal("10.0000"), exposure.getNetExposure());
    }

    @Test
    @DisplayName("部分对冲 → 净敞口 = 客户头寸 − 对冲头寸")
    void calculateNetExposure_partiallyHedged_exposureIsDifference() {
        // 客户买入 10
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // 对冲买入 7
        positionService.onHedgeFillEvent(buildHedgeFillEvent("H001", "AU2406", "BUY",
                new BigDecimal("7"), new BigDecimal("500.00")));

        NetExposure exposure = positionService.calculateNetExposure("AU2406");
        assertDecimalEquals(new BigDecimal("3.0000"), exposure.getNetExposure());
    }

    @Test
    @DisplayName("多客户持仓聚合 → 净敞口按合约汇总")
    void calculateNetExposure_multipleCustomers_aggregatesBySymbol() {
        // CUST001 买入 AU2406 10
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        // CUST002 买入 AU2406 5
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST002", "AU2406", "BUY",
                new BigDecimal("5"), new BigDecimal("500.00")));
        // CUST001 卖出 CU2406 3
        positionService.onTradeEvent(buildTradeEvent("T003", "CUST001", "CU2406", "SELL",
                new BigDecimal("3"), new BigDecimal("70000.00")));
        // 对冲买入 AU2406 12（部分对冲）
        positionService.onHedgeFillEvent(buildHedgeFillEvent("H001", "AU2406", "BUY",
                new BigDecimal("12"), new BigDecimal("500.00")));

        List<NetExposure> exposures = positionService.calculateNetExposure();
        assertEquals(2, exposures.size());

        // AU2406: customer=15, hedge=12, net=3
        NetExposure au = exposures.stream().filter(e -> "AU2406".equals(e.getSymbol())).findFirst().orElse(null);
        assertNotNull(au);
        assertDecimalEquals(new BigDecimal("15.0000"), au.getCustomerPosition());
        assertDecimalEquals(new BigDecimal("12.0000"), au.getHedgePosition());
        assertDecimalEquals(new BigDecimal("3.0000"), au.getNetExposure());

        // CU2406: customer=-3, hedge=0, net=-3
        NetExposure cu = exposures.stream().filter(e -> "CU2406".equals(e.getSymbol())).findFirst().orElse(null);
        assertNotNull(cu);
        assertDecimalEquals(new BigDecimal("-3.0000"), cu.getNetExposure());
    }

    @Test
    @DisplayName("无持仓无对冲 → 查询指定合约敞口返回 null")
    void calculateNetExposure_noData_returnsNull() {
        NetExposure exposure = positionService.calculateNetExposure("UNKNOWN");
        assertNull(exposure);
    }

    // ==================== 幂等性测试 ====================

    @Test
    @DisplayName("同一 trade-event 重复消费 → 持仓不重复累加")
    void onTradeEvent_duplicateEvent_notAccumulated() {
        TradeEvent event = buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"));
        event.setEventId("EVT-001");

        positionService.onTradeEvent(event);
        // 模拟 Kafka 至少一次重复投递
        positionService.onTradeEvent(event);

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertDecimalEquals(new BigDecimal("10.0000"), position.getQty(), "重复消费不应累加");
    }

    @Test
    @DisplayName("同一 hedge-fill-event 重复消费 → 对冲头寸不重复累加")
    void onHedgeFillEvent_duplicateEvent_notAccumulated() {
        HedgeFillEvent event = buildHedgeFillEvent("H001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"));
        event.setEventId("EVT-H-001");

        positionService.onHedgeFillEvent(event);
        positionService.onHedgeFillEvent(event);

        HedgePosition hedge = hedgePositionMapper.findBySymbol("AU2406");
        assertDecimalEquals(new BigDecimal("10.0000"), hedge.getQty(), "重复消费不应累加");
    }

    @Test
    @DisplayName("eventId 缺失时回退到 tradeId 做幂等键")
    void onTradeEvent_eventIdMissing_fallsBackToTradeId() {
        TradeEvent event = buildTradeEvent("T-FALLBACK", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"));
        event.setEventId(null); // 缺失 eventId

        positionService.onTradeEvent(event);
        positionService.onTradeEvent(event);

        Position position = positionMapper.findByCustomerAndSymbol("CUST001", "AU2406");
        assertDecimalEquals(new BigDecimal("10.0000"), position.getQty());
        assertTrue(processedEventMapper.processed.contains("T-FALLBACK"),
                "幂等键应回退到 tradeId");
    }

    // ==================== 查询接口测试 ====================

    @Test
    @DisplayName("findByCustomer 返回客户全部持仓")
    void findByCustomer_returnsAllCustomerPositions() {
        positionService.onTradeEvent(buildTradeEvent("T001", "CUST001", "AU2406", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00")));
        positionService.onTradeEvent(buildTradeEvent("T002", "CUST001", "CU2406", "BUY",
                new BigDecimal("3"), new BigDecimal("70000.00")));
        positionService.onTradeEvent(buildTradeEvent("T003", "CUST002", "AU2406", "BUY",
                new BigDecimal("5"), new BigDecimal("500.00")));

        List<Position> cust1Positions = positionService.findByCustomer("CUST001");
        assertEquals(2, cust1Positions.size());

        List<Position> cust2Positions = positionService.findByCustomer("CUST002");
        assertEquals(1, cust2Positions.size());
    }

    // ==================== 测试辅助方法 ====================

    private TradeEvent buildTradeEvent(String tradeId, String customerId, String symbol,
                                       String side, BigDecimal qty, BigDecimal price) {
        TradeEvent event = new TradeEvent(customerId);
        event.setTradeId(tradeId);
        event.setOrderId("ORD-" + tradeId);
        event.setClientOrderId("CLORD-" + tradeId);
        event.setSymbol(symbol);
        event.setSide(side);
        event.setQty(qty);
        event.setPrice(price);
        event.setAmount(qty.multiply(price));
        event.setTradeTime(System.currentTimeMillis());
        return event;
    }

    private HedgeFillEvent buildHedgeFillEvent(String hedgeTradeId, String symbol,
                                               String side, BigDecimal qty, BigDecimal price) {
        HedgeFillEvent event = new HedgeFillEvent(symbol);
        event.setHedgeTradeId(hedgeTradeId);
        event.setSymbol(symbol);
        event.setSide(side);
        event.setQty(qty);
        event.setPrice(price);
        event.setAmount(qty.multiply(price));
        event.setTradeTime(System.currentTimeMillis());
        return event;
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                "expected: <" + expected + "> but was: <" + actual + ">");
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String msg) {
        assertTrue(expected.compareTo(actual) == 0, msg
                + " expected: <" + expected + "> but was: <" + actual + ">");
    }

    // ==================== Mock 实现类 ====================

    static class InMemoryPositionMapper implements PositionMapper {
        final List<Position> positions = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(Position position) {
            position.setId(++idSeq);
            positions.add(position);
            return 1;
        }
        @Override public Position findByCustomerAndSymbol(String customerId, String symbol) {
            return positions.stream()
                    .filter(p -> customerId.equals(p.getCustomerId()) && symbol.equals(p.getSymbol()))
                    .findFirst().orElse(null);
        }
        @Override public List<Position> findByCustomer(String customerId) {
            return positions.stream()
                    .filter(p -> customerId.equals(p.getCustomerId()))
                    .toList();
        }
        @Override public List<Position> findAll() {
            return new ArrayList<>(positions);
        }
        @Override public List<Position> sumQtyBySymbol() {
            java.util.Map<String, BigDecimal> sums = new java.util.LinkedHashMap<>();
            for (Position p : positions) {
                sums.merge(p.getSymbol(), p.getQty(), BigDecimal::add);
            }
            List<Position> result = new ArrayList<>();
            for (java.util.Map.Entry<String, BigDecimal> e : sums.entrySet()) {
                Position agg = new Position();
                agg.setSymbol(e.getKey());
                agg.setQty(e.getValue());
                result.add(agg);
            }
            return result;
        }
        @Override public int update(Position position) {
            for (int i = 0; i < positions.size(); i++) {
                if (position.getId() != null && position.getId().equals(positions.get(i).getId())) {
                    positions.set(i, position);
                    return 1;
                }
            }
            return 0;
        }
    }

    static class InMemoryHedgePositionMapper implements HedgePositionMapper {
        final List<HedgePosition> positions = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgePosition position) {
            position.setId(++idSeq);
            positions.add(position);
            return 1;
        }
        @Override public HedgePosition findBySymbol(String symbol) {
            return positions.stream()
                    .filter(p -> symbol.equals(p.getSymbol()))
                    .findFirst().orElse(null);
        }
        @Override public List<HedgePosition> findAll() {
            return new ArrayList<>(positions);
        }
        @Override public int update(HedgePosition position) {
            for (int i = 0; i < positions.size(); i++) {
                if (position.getId() != null && position.getId().equals(positions.get(i).getId())) {
                    positions.set(i, position);
                    return 1;
                }
            }
            return 0;
        }
    }

    /**
     * 手写 ProcessedEventMapper，模拟 INSERT OR IGNORE 语义：
     * 同一 eventId 第二次插入返回 0（视为重复）。
     */
    static class InMemoryProcessedEventMapper implements ProcessedEventMapper {
        final Set<String> processed = new HashSet<>();

        @Override
        public int insert(String eventId, LocalDateTime processedAt) {
            // INSERT OR IGNORE：首次返回 1，重复返回 0
            return processed.add(eventId) ? 1 : 0;
        }

        @Override
        public int exists(String eventId) {
            return processed.contains(eventId) ? 1 : 0;
        }
    }
}
