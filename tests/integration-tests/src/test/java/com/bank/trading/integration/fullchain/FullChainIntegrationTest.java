package com.bank.trading.integration.fullchain;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.account.dto.CreditInfo;
import com.bank.trading.account.entity.Customer;
import com.bank.trading.account.mapper.CustomerMapper;
import com.bank.trading.account.service.AccountService;
import com.bank.trading.common.core.dto.OrderCreateDTO;
import com.bank.trading.common.core.dto.OrderDTO;
import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.common.core.event.HedgeFillEvent;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.persistence.eventstore.EventStoreRecord;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.idempotent.IdempotentConsumer;
import com.bank.trading.common.persistence.idempotent.ProcessedEventMapper;
import com.bank.trading.common.persistence.outbox.OutboxMessage;
import com.bank.trading.common.persistence.outbox.OutboxService;
import com.bank.trading.execution.client.ExchangeSessionClient;
import com.bank.trading.execution.dto.ExchangeOrderRequest;
import com.bank.trading.execution.dto.ExchangeOrderResponse;
import com.bank.trading.execution.dto.ExchangeTradeNotification;
import com.bank.trading.execution.entity.HedgeBatchItem;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.entity.HedgeTrade;
import com.bank.trading.execution.mapper.HedgeBatchItemMapper;
import com.bank.trading.execution.mapper.HedgeOrderMapper;
import com.bank.trading.execution.mapper.HedgeTradeMapper;
import com.bank.trading.execution.service.ExecutionService;
import com.bank.trading.execution.service.HedgeBatcher;
import com.bank.trading.oms.entity.Order;
import com.bank.trading.oms.entity.Trade;
import com.bank.trading.oms.mapper.OrderMapper;
import com.bank.trading.oms.mapper.TradeMapper;
import com.bank.trading.oms.service.OrderService;
import com.bank.trading.oms.service.PriceProvider;
import com.bank.trading.oms.service.RiskChecker;
import com.bank.trading.position.entity.HedgePosition;
import com.bank.trading.position.entity.NetExposure;
import com.bank.trading.position.entity.Position;
import com.bank.trading.position.mapper.HedgePositionMapper;
import com.bank.trading.position.mapper.PositionMapper;
import com.bank.trading.position.service.PositionService;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.DefaultRiskRuleEngine;
import com.bank.trading.risk.rules.CreditLimitRule;
import com.bank.trading.risk.rules.DailyAmountLimitRule;
import com.bank.trading.risk.rules.PositionLimitRule;
import com.bank.trading.risk.rules.PriceDeviationRule;
import com.bank.trading.risk.rules.SingleOrderLimitRule;
import com.bank.trading.risk.service.RiskService;
import com.bank.trading.simexchange.callback.CallbackRegistry;
import com.bank.trading.simexchange.engine.MarketDataEngine;
import com.bank.trading.simexchange.engine.MatchingEngine;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.SymbolConfig;
import com.bank.trading.simexchange.model.TradeFill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全链路端到端集成测试：验证从客户下单到持仓/额度/对冲的完整业务闭环。
 * <p>
 * 在单 JVM 内组装所有真实业务组件，通过内存 Mapper 和桥接类模拟外部依赖
 * （Kafka、HTTP、WebSocket），验证以下完整链路：
 * <pre>
 * 客户下单 → OrderService (OMS)
 *   → 风控检查 (RiskService, 真实规则引擎)
 *   → 成交 → Outbox 写入 → CapturingKafka 模拟 Kafka 发送
 *     → PositionService.onTradeEvent (更新客户持仓)
 *     → AccountService.onTradeEvent (扣减信用额度)
 *     → ExecutionService.onTradeEvent + HedgeBatcher (对冲下单)
 *       → MatchingEngine (sim-exchange 真实撮合引擎)
 *       → Webhook 回调 → ExecutionService.onTradeNotification
 *       → hedge-fill-event → CapturingKafka
 *         → PositionService.onHedgeFillEvent (更新对冲持仓 + 净敞口)
 * </pre>
 */
class FullChainIntegrationTest {

    private static final String SYMBOL = "AU2406";
    private static final String CUSTOMER_ID = "CUST-E2E-001";
    private static final BigDecimal INITIAL_CREDIT = new BigDecimal("1000000.00");
    private static final BigDecimal INITIAL_PRICE = new BigDecimal("520.00");

    // ==== OMS ====
    private OrderService orderService;
    private InMemoryOmsOrderMapper omsOrderMapper;
    private InMemoryOmsTradeMapper omsTradeMapper;

    // ==== Risk ====
    private RiskService riskService;

    // ==== Position ====
    private PositionService positionService;
    private InMemoryPositionMapper positionMapper;
    private InMemoryHedgePositionMapper hedgePositionMapper;

    // ==== Account ====
    private AccountService accountService;
    private InMemoryAccountCustomerMapper accountCustomerMapper;

    // ==== Execution ====
    private ExecutionService executionService;
    private HedgeBatcher hedgeBatcher;
    private InMemoryHedgeOrderMapper hedgeOrderMapper;
    private InMemoryHedgeTradeMapper hedgeTradeMapper;
    private InMemoryBatchItemMapper batchItemMapper;

    // ==== Sim Exchange ====
    private MarketDataEngine marketDataEngine;
    private MatchingEngine matchingEngine;

    // ==== Kafka 捕获 ====
    private CapturingKafkaTemplate kafkaTemplate;

    @BeforeEach
    void setUp() {
        // 1. 初始化行情引擎 & 撮合引擎
        marketDataEngine = new MarketDataEngine();
        SymbolConfig config = new SymbolConfig();
        config.setCode(SYMBOL);
        config.setName("黄金期货");
        config.setInitialPrice(INITIAL_PRICE);
        config.setTickSize(new BigDecimal("0.01"));
        config.setMinQty(new BigDecimal("1"));
        config.setVolatility(0.1);
        config.setDrift(0.0);
        config.setMultiplier(100);
        marketDataEngine.init(List.of(config), 1.0);

        // 2. 初始化 Kafka 捕获器
        kafkaTemplate = new CapturingKafkaTemplate();

        // 3. 初始化 Execution Service + 桥接
        hedgeOrderMapper = new InMemoryHedgeOrderMapper();
        hedgeTradeMapper = new InMemoryHedgeTradeMapper();
        batchItemMapper = new InMemoryBatchItemMapper();

        ExecutionServiceHolder execHolder = new ExecutionServiceHolder();
        BridgeCallbackRegistry callbackRegistry = new BridgeCallbackRegistry(execHolder, 30L);
        matchingEngine = new MatchingEngine(marketDataEngine, callbackRegistry);
        matchingEngine.clearForTest();

        BridgeExchangeSessionClient exchangeClient = new BridgeExchangeSessionClient(matchingEngine);

        executionService = new ExecutionService(
                hedgeOrderMapper, hedgeTradeMapper, batchItemMapper, exchangeClient, kafkaTemplate);
        setField(executionService, "tradeTopic", "trade-event");
        setField(executionService, "hedgeFillTopic", "hedge-fill-event");
        setField(executionService, "hedgeOrderType", "MARKET");
        setField(executionService, "hedgeRatio", new BigDecimal("1.0"));
        execHolder.service = executionService;

        hedgeBatcher = new HedgeBatcher(batchItemMapper, executionService);
        hedgeBatcher.setBatchingEnabled(false);
        hedgeBatcher.setBatchingWindowMs(999_999);
        hedgeBatcher.setSizeThreshold(new BigDecimal("999"));
        hedgeBatcher.setHedgeRatio(new BigDecimal("1.0"));

        // 4. 初始化 Position Service
        positionMapper = new InMemoryPositionMapper();
        hedgePositionMapper = new InMemoryHedgePositionMapper();
        InMemoryProcessedEventMapper positionProcessed = new InMemoryProcessedEventMapper();
        IdempotentConsumer positionIdempotent = new IdempotentConsumer(positionProcessed);
        positionService = new PositionService(positionMapper, hedgePositionMapper, positionIdempotent);

        // 5. 初始化 Account Service
        accountCustomerMapper = new InMemoryAccountCustomerMapper();
        InMemoryProcessedEventMapper accountProcessed = new InMemoryProcessedEventMapper();
        IdempotentConsumer accountIdempotent = new IdempotentConsumer(accountProcessed);
        accountService = new AccountService(accountCustomerMapper, accountIdempotent);

        // 创建测试客户
        Customer customer = new Customer();
        customer.setCustomerId(CUSTOMER_ID);
        customer.setName("端到端测试客户");
        customer.setCreditLimit(INITIAL_CREDIT);
        accountService.createCustomer(customer);

        // 6. 初始化 Risk Service
        RiskProperties riskProps = buildRiskProperties();
        DefaultRiskRuleEngine ruleEngine = new DefaultRiskRuleEngine(
                new CreditLimitRule(riskProps),
                new SingleOrderLimitRule(riskProps),
                new DailyAmountLimitRule(riskProps),
                new PositionLimitRule(riskProps),
                new PriceDeviationRule(riskProps)
        );
        riskService = new RiskService(ruleEngine);

        // 7. 初始化 OMS Service
        omsOrderMapper = new InMemoryOmsOrderMapper();
        omsTradeMapper = new InMemoryOmsTradeMapper();
        MockEventStoreService eventStoreService = new MockEventStoreService();
        MockOutboxService outboxService = new MockOutboxService(kafkaTemplate);

        RiskChecker riskChecker = new DirectRiskChecker(riskService, accountService, positionService, marketDataEngine);
        PriceProvider priceProvider = new MarketDataPriceProvider(marketDataEngine);

        orderService = new OrderService(
                omsOrderMapper, omsTradeMapper, eventStoreService, outboxService,
                riskChecker, priceProvider);
        setField(orderService, "tradeTopic", "trade-event");
    }

    // ==================== 测试场景 ====================

    @Test
    @DisplayName("FULL-1: 客户市价买入 10 手 → 风控通过 → 成交 → 持仓更新 → 额度扣减 → 对冲下单 → 对冲成交 → 净敞口为 0")
    void fullChain_marketBuy_10lots_fullHedge() throws Exception {
        // ========== 阶段 1：客户下单 ==========
        OrderCreateDTO createDTO = new OrderCreateDTO();
        createDTO.setClientOrderId("CLI-E2E-001");
        createDTO.setCustomerId(CUSTOMER_ID);
        createDTO.setSymbol(SYMBOL);
        createDTO.setSide("BUY");
        createDTO.setType("MARKET");
        createDTO.setQty(new BigDecimal("10"));

        OrderDTO order = orderService.createOrder(createDTO);

        assertNotNull(order);
        assertEquals("FILLED", order.getStatus(), "市价单应立即成交");
        assertDecimalEquals(new BigDecimal("10"), order.getFilledQty());
        assertTrue(order.getAvgPrice().compareTo(BigDecimal.ZERO) > 0);

        List<Trade> trades = omsTradeMapper.trades;
        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(CUSTOMER_ID, trade.getCustomerId());
        assertEquals("BUY", trade.getSide());

        assertTrue(kafkaTemplate.sentMessages.stream()
                        .anyMatch(m -> "trade-event".equals(m.topic)),
                "应有 trade-event 发送到 Kafka");

        // ========== 阶段 2：trade-event 分发给 position & account ==========
        List<CapturingKafkaTemplate.SentMessage> tradeEvents = kafkaTemplate.getByTopic("trade-event");
        assertEquals(1, tradeEvents.size());
        TradeEvent tradeEvent = JSON.parseObject(tradeEvents.get(0).value, TradeEvent.class);

        // 2a: position-service 消费
        positionService.onTradeEvent(tradeEvent);
        Position custPos = positionMapper.findByCustomerAndSymbol(CUSTOMER_ID, SYMBOL);
        assertNotNull(custPos);
        assertDecimalEquals(new BigDecimal("10.0000"), custPos.getQty());
        assertTrue(custPos.getAvgCost().compareTo(BigDecimal.ZERO) > 0);

        // 2b: account-service 消费
        accountService.onTradeEvent(tradeEvent);
        Customer cust = accountService.findByCustomerId(CUSTOMER_ID);
        assertTrue(cust.getUsedCredit().compareTo(BigDecimal.ZERO) > 0);
        BigDecimal expectedUsed = tradeEvent.getAmount().setScale(2, RoundingMode.HALF_UP);
        assertDecimalEquals(expectedUsed, cust.getUsedCredit());

        CreditInfo creditInfo = accountService.getCreditInfo(CUSTOMER_ID);
        assertDecimalEquals(INITIAL_CREDIT, creditInfo.getCreditLimit());
        assertDecimalEquals(expectedUsed, creditInfo.getUsedCredit());
        assertDecimalEquals(INITIAL_CREDIT.subtract(expectedUsed), creditInfo.getAvailableCredit());

        // ========== 阶段 3：trade-event 分发给 execution-service ==========
        hedgeBatcher.enqueue(tradeEvent);

        assertEquals(1, hedgeOrderMapper.orders.size());
        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("BUY", hedgeOrder.getSide(), "客户 BUY → 对冲 BUY");
        assertDecimalEquals(new BigDecimal("10.0000"), hedgeOrder.getQty());
        assertEquals("NEW", hedgeOrder.getStatus());
        assertNotNull(hedgeOrder.getExchangeOrderId());

        ExchangeOrder simOrder = matchingEngine.getAllOrders().stream()
                .filter(o -> hedgeOrder.getExchangeOrderId().equals(o.getOrderId()))
                .findFirst().orElse(null);
        assertNotNull(simOrder);
        assertEquals("BUY", simOrder.getSide());

        // ========== 阶段 4：等待异步撮合 + Webhook 回调 ==========
        awaitHedgeOrderFilled(hedgeOrder.getHedgeOrderId(), 2000);

        HedgeOrder filledHedge = hedgeOrderMapper.findByHedgeOrderId(hedgeOrder.getHedgeOrderId());
        assertEquals("FILLED", filledHedge.getStatus());
        assertDecimalEquals(new BigDecimal("10.0000"), filledHedge.getFilledQty());
        assertTrue(filledHedge.getAvgPrice().compareTo(BigDecimal.ZERO) > 0);

        List<CapturingKafkaTemplate.SentMessage> hedgeFillEvents = kafkaTemplate.getByTopic("hedge-fill-event");
        assertTrue(hedgeFillEvents.size() >= 1);
        HedgeFillEvent hedgeFillEvent = JSON.parseObject(
                hedgeFillEvents.get(hedgeFillEvents.size() - 1).value, HedgeFillEvent.class);
        assertEquals("BUY", hedgeFillEvent.getSide());
        assertEquals(SYMBOL, hedgeFillEvent.getSymbol());

        // ========== 阶段 5：hedge-fill-event 分发给 position-service ==========
        positionService.onHedgeFillEvent(hedgeFillEvent);

        HedgePosition hedgePos = hedgePositionMapper.findBySymbol(SYMBOL);
        assertNotNull(hedgePos);
        assertTrue(hedgePos.getQty().compareTo(BigDecimal.ZERO) > 0,
                "对冲持仓数量应与客户同向（BUY 为正）");

        List<NetExposure> exposures = positionService.calculateNetExposure();
        NetExposure exposure = exposures.stream()
                .filter(e -> SYMBOL.equals(e.getSymbol()))
                .findFirst().orElse(null);
        assertNotNull(exposure);

        BigDecimal netExp = exposure.getNetExposure();
        assertTrue(netExp.abs().compareTo(new BigDecimal("0.001")) <= 0,
                "全额对冲后净敞口应接近 0，实际: " + netExp);

        System.out.println("=== 全链路验证通过 ===");
        System.out.println("客户持仓: " + custPos.getQty() + " @ " + custPos.getAvgCost());
        System.out.println("已用额度: " + cust.getUsedCredit());
        System.out.println("可用额度: " + creditInfo.getAvailableCredit());
        System.out.println("对冲成交: " + filledHedge.getFilledQty() + " @ " + filledHedge.getAvgPrice());
        System.out.println("净敞口: " + netExp);
    }

    @Test
    @DisplayName("FULL-2: 信用额度不足 → 风控拦截 → 订单被拒绝 → 不成交不扣额度")
    void fullChain_creditExceeded_rejectedByRisk() {
        String smallCustomer = "CUST-SMALL-001";
        Customer small = new Customer();
        small.setCustomerId(smallCustomer);
        small.setName("小额度客户");
        small.setCreditLimit(new BigDecimal("1000.00"));
        accountService.createCustomer(small);

        OrderCreateDTO createDTO = new OrderCreateDTO();
        createDTO.setClientOrderId("CLI-REJ-001");
        createDTO.setCustomerId(smallCustomer);
        createDTO.setSymbol(SYMBOL);
        createDTO.setSide("BUY");
        createDTO.setType("MARKET");
        createDTO.setQty(new BigDecimal("10"));

        OrderDTO order = orderService.createOrder(createDTO);

        assertNotNull(order);
        assertEquals("REJECTED", order.getStatus(), "信用不足应被拒绝");
        assertNotNull(order.getRejectReason());
        assertTrue(order.getRejectReason().toLowerCase().contains("credit") 
                        || order.getRejectReason().toLowerCase().contains("exceeds"),
                "拒绝原因应与信用额度相关，实际: " + order.getRejectReason());

        assertEquals(0, omsTradeMapper.trades.stream()
                        .filter(t -> smallCustomer.equals(t.getCustomerId()))
                        .count(),
                "被拒绝的订单不应有成交");

        Customer after = accountService.findByCustomerId(smallCustomer);
        assertDecimalEquals(BigDecimal.ZERO, after.getUsedCredit(),
                "被拒绝的订单不应扣减额度");

        Position pos = positionMapper.findByCustomerAndSymbol(smallCustomer, SYMBOL);
        assertNull(pos, "被拒绝的订单不应创建持仓");
    }

    @Test
    @DisplayName("FULL-3: 客户卖出 → 持仓减少 → 额度释放 → 对冲卖出")
    void fullChain_marketSell_reducesPosition() throws Exception {
        // 先买 10 手建立持仓
        OrderCreateDTO buyDTO = new OrderCreateDTO();
        buyDTO.setClientOrderId("CLI-SELL-BUY");
        buyDTO.setCustomerId(CUSTOMER_ID);
        buyDTO.setSymbol(SYMBOL);
        buyDTO.setSide("BUY");
        buyDTO.setType("MARKET");
        buyDTO.setQty(new BigDecimal("10"));
        OrderDTO buyOrder = orderService.createOrder(buyDTO);
        assertEquals("FILLED", buyOrder.getStatus());

        List<CapturingKafkaTemplate.SentMessage> tradeEvents = kafkaTemplate.getByTopic("trade-event");
        TradeEvent buyEvent = JSON.parseObject(tradeEvents.get(tradeEvents.size() - 1).value, TradeEvent.class);
        positionService.onTradeEvent(buyEvent);
        accountService.onTradeEvent(buyEvent);
        hedgeBatcher.enqueue(buyEvent);
        awaitHedgeOrderFilled(hedgeOrderMapper.orders.get(hedgeOrderMapper.orders.size() - 1).getHedgeOrderId(), 2000);
        List<CapturingKafkaTemplate.SentMessage> hedgeEvents = kafkaTemplate.getByTopic("hedge-fill-event");
        HedgeFillEvent buyHedgeEvent = JSON.parseObject(
                hedgeEvents.get(hedgeEvents.size() - 1).value, HedgeFillEvent.class);
        positionService.onHedgeFillEvent(buyHedgeEvent);

        BigDecimal usedBefore = accountService.getCreditInfo(CUSTOMER_ID).getUsedCredit();
        Position posBefore = positionMapper.findByCustomerAndSymbol(CUSTOMER_ID, SYMBOL);
        assertDecimalEquals(new BigDecimal("10.0000"), posBefore.getQty());

        // 卖出 4 手
        OrderCreateDTO sellDTO = new OrderCreateDTO();
        sellDTO.setClientOrderId("CLI-SELL-001");
        sellDTO.setCustomerId(CUSTOMER_ID);
        sellDTO.setSymbol(SYMBOL);
        sellDTO.setSide("SELL");
        sellDTO.setType("MARKET");
        sellDTO.setQty(new BigDecimal("4"));

        OrderDTO sellOrder = orderService.createOrder(sellDTO);
        assertEquals("FILLED", sellOrder.getStatus());

        TradeEvent sellEvent = JSON.parseObject(
                kafkaTemplate.getByTopic("trade-event").get(
                        kafkaTemplate.getByTopic("trade-event").size() - 1).value,
                TradeEvent.class);

        positionService.onTradeEvent(sellEvent);
        accountService.onTradeEvent(sellEvent);

        Position posAfter = positionMapper.findByCustomerAndSymbol(CUSTOMER_ID, SYMBOL);
        assertDecimalEquals(new BigDecimal("6.0000"), posAfter.getQty(), "10 - 4 = 6 手");
        assertNotNull(posAfter.getRealizedPnl());

        BigDecimal usedAfter = accountService.getCreditInfo(CUSTOMER_ID).getUsedCredit();
        assertTrue(usedAfter.compareTo(usedBefore) < 0, "卖出后已用额度应减少");

        // 对冲方向：客户 SELL → 对冲 SELL
        int orderCountBefore = hedgeOrderMapper.orders.size();
        hedgeBatcher.enqueue(sellEvent);
        assertEquals(orderCountBefore + 1, hedgeOrderMapper.orders.size());
        HedgeOrder lastHedge = hedgeOrderMapper.orders.get(hedgeOrderMapper.orders.size() - 1);
        assertEquals("SELL", lastHedge.getSide(), "客户 SELL → 对冲 SELL");
    }

    @Test
    @DisplayName("FULL-4: 多客户独立 → 各自持仓/额度互不影响")
    void fullChain_multipleCustomers_independent() throws Exception {
        String cust2 = "CUST-E2E-002";
        Customer c2 = new Customer();
        c2.setCustomerId(cust2);
        c2.setName("第二个客户");
        c2.setCreditLimit(new BigDecimal("500000.00"));
        accountService.createCustomer(c2);

        OrderCreateDTO dto1 = new OrderCreateDTO();
        dto1.setClientOrderId("CLI-MULTI-1");
        dto1.setCustomerId(CUSTOMER_ID);
        dto1.setSymbol(SYMBOL);
        dto1.setSide("BUY");
        dto1.setType("MARKET");
        dto1.setQty(new BigDecimal("10"));
        orderService.createOrder(dto1);

        OrderCreateDTO dto2 = new OrderCreateDTO();
        dto2.setClientOrderId("CLI-MULTI-2");
        dto2.setCustomerId(cust2);
        dto2.setSymbol(SYMBOL);
        dto2.setSide("BUY");
        dto2.setType("MARKET");
        dto2.setQty(new BigDecimal("5"));
        orderService.createOrder(dto2);

        List<CapturingKafkaTemplate.SentMessage> allTrades = kafkaTemplate.getByTopic("trade-event");
        assertEquals(2, allTrades.size());

        for (CapturingKafkaTemplate.SentMessage msg : allTrades) {
            TradeEvent evt = JSON.parseObject(msg.value, TradeEvent.class);
            positionService.onTradeEvent(evt);
            accountService.onTradeEvent(evt);
            hedgeBatcher.enqueue(evt);
        }

        Position p1 = positionMapper.findByCustomerAndSymbol(CUSTOMER_ID, SYMBOL);
        assertDecimalEquals(new BigDecimal("10.0000"), p1.getQty());

        Position p2 = positionMapper.findByCustomerAndSymbol(cust2, SYMBOL);
        assertDecimalEquals(new BigDecimal("5.0000"), p2.getQty());

        BigDecimal credit1 = accountService.getCreditInfo(CUSTOMER_ID).getUsedCredit();
        BigDecimal credit2 = accountService.getCreditInfo(cust2).getUsedCredit();
        assertTrue(credit1.compareTo(credit2) > 0, "客户 1 成交金额更大，已用额度更多");

        // 等待所有对冲成交
        for (HedgeOrder ho : hedgeOrderMapper.orders) {
            awaitHedgeOrderFilled(ho.getHedgeOrderId(), 2000);
        }
        for (CapturingKafkaTemplate.SentMessage msg : kafkaTemplate.getByTopic("hedge-fill-event")) {
            HedgeFillEvent hfe = JSON.parseObject(msg.value, HedgeFillEvent.class);
            positionService.onHedgeFillEvent(hfe);
        }

        List<NetExposure> exposures = positionService.calculateNetExposure();
        NetExposure exp = exposures.stream()
                .filter(e -> SYMBOL.equals(e.getSymbol()))
                .findFirst().orElse(null);
        assertNotNull(exp);
        assertTrue(exp.getNetExposure().abs().compareTo(new BigDecimal("0.01")) <= 0,
                "多客户聚合全额对冲后净敞口应接近 0，实际: " + exp.getNetExposure());
    }

    // ==================== 辅助方法 ====================

    private RiskProperties buildRiskProperties() {
        RiskProperties props = new RiskProperties();
        props.setDefaultCreditLimit(new BigDecimal("999999999.00"));
        props.setDefaultSingleOrderQtyLimit(10000);
        props.setDefaultDailyTradeAmountLimit(new BigDecimal("999999999.00"));
        props.setDefaultPositionLimit(10000);
        props.setDefaultPriceDeviationBps(500);

        RiskProperties.CustomerLimit smallLimit = new RiskProperties.CustomerLimit();
        smallLimit.setCustomerId("CUST-SMALL-001");
        smallLimit.setCreditLimit(new BigDecimal("1000.00"));
        props.getCustomerLimits().add(smallLimit);

        return props;
    }

    private void awaitHedgeOrderFilled(String hedgeOrderId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        // 第一阶段：等待对冲订单状态变为 FILLED
        while (System.currentTimeMillis() < deadline) {
            HedgeOrder order = hedgeOrderMapper.findByHedgeOrderId(hedgeOrderId);
            if (order != null && "FILLED".equals(order.getStatus())) {
                break;
            }
            Thread.sleep(20);
        }
        // 第二阶段：等待 onTradeNotification 完成（hedgeTrade 被创建 + hedge-fill-event 被发布）
        // MatchingEngine 异步流程：先 notifyOrderUpdate(FILLED)，后 notifyTrade
        // onTradeNotification 中先 insert hedgeTrade，后 publishHedgeFillEvent
        while (System.currentTimeMillis() < deadline) {
            boolean hasTrade = hedgeTradeMapper.trades.stream()
                    .anyMatch(t -> hedgeOrderId.equals(t.getHedgeOrderId()));
            if (hasTrade) {
                break;
            }
            Thread.sleep(20);
        }
        // 第三阶段：短暂等待，确保 publishHedgeFillEvent 执行完成（hedgeTrade 已创建但事件可能还没发送）
        Thread.sleep(50);
        HedgeOrder order = hedgeOrderMapper.findByHedgeOrderId(hedgeOrderId);
        if (!"FILLED".equals(order.getStatus())) {
            fail("对冲订单超时未成交: " + hedgeOrderId + ", 当前状态: "
                    + (order != null ? order.getStatus() : "null"));
        }
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                "expected: <" + expected + "> but was: <" + actual + ">");
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String msg) {
        assertTrue(expected.compareTo(actual) == 0, msg
                + " expected: <" + expected + "> but was: <" + actual + ">");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 桥接与 Mock 类 ====================

    static class DirectRiskChecker implements RiskChecker {
        private final RiskService riskService;
        private final AccountService accountService;
        private final PositionService positionService;
        private final MarketDataEngine marketDataEngine;

        DirectRiskChecker(RiskService riskService, AccountService accountService,
                          PositionService positionService, MarketDataEngine marketDataEngine) {
            this.riskService = riskService;
            this.accountService = accountService;
            this.positionService = positionService;
            this.marketDataEngine = marketDataEngine;
        }

        @Override
        public RiskCheckResult check(RiskCheckRequest request) {
            CreditInfo credit = accountService.getCreditInfo(request.getCustomerId());
            if (credit != null) {
                request.setUsedCredit(credit.getUsedCredit());
            }
            Position pos = positionService.findByCustomerAndSymbol(
                    request.getCustomerId(), request.getSymbol());
            if (pos != null) {
                request.setCurrentPosition(pos.getQty().abs());
            }
            if ("MARKET".equalsIgnoreCase(request.getOrderType())) {
                var md = marketDataEngine.getLatest(request.getSymbol());
                if (md != null && md.getLastPrice() != null) {
                    request.setMarketMidPrice(md.getLastPrice());
                }
            }
            return riskService.checkPreTrade(request);
        }
    }

    static class MarketDataPriceProvider implements PriceProvider {
        private final MarketDataEngine marketDataEngine;

        MarketDataPriceProvider(MarketDataEngine marketDataEngine) {
            this.marketDataEngine = marketDataEngine;
        }

        @Override
        public BigDecimal getExecutionPrice(String symbol, String side) {
            var md = marketDataEngine.getLatest(symbol);
            if (md == null) return null;
            if ("BUY".equalsIgnoreCase(side)) {
                return md.getAskPrice() != null ? md.getAskPrice() : md.getLastPrice();
            } else {
                return md.getBidPrice() != null ? md.getBidPrice() : md.getLastPrice();
            }
        }
    }

    // ---- OMS Mapper 内存实现 ----

    static class InMemoryOmsOrderMapper implements OrderMapper {
        final List<Order> orders = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(Order order) {
            order.setId(++idSeq);
            orders.add(order);
            return 1;
        }
        @Override public int updateByOrderId(Order order) {
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i).getOrderId().equals(order.getOrderId())) {
                    orders.set(i, order);
                    return 1;
                }
            }
            return 0;
        }
        @Override public Order findByOrderId(String orderId) {
            return orders.stream().filter(o -> orderId.equals(o.getOrderId()))
                    .findFirst().orElse(null);
        }
        @Override public Order findByClientOrderId(String clientOrderId, String customerId) {
            return orders.stream()
                    .filter(o -> clientOrderId.equals(o.getClientOrderId())
                            && customerId.equals(o.getCustomerId()))
                    .findFirst().orElse(null);
        }
        @Override public List<Order> findByCustomerId(String customerId) {
            return orders.stream().filter(o -> customerId.equals(o.getCustomerId())).toList();
        }
        @Override public List<Order> findRecent(int limit) {
            return orders.stream().limit(limit).toList();
        }
        @Override public int countByStatus(String status) {
            return (int) orders.stream().filter(o -> status.equals(o.getStatus())).count();
        }
    }

    static class InMemoryOmsTradeMapper implements TradeMapper {
        final List<Trade> trades = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(Trade trade) {
            trade.setId(++idSeq);
            trades.add(trade);
            return 1;
        }
        @Override public List<Trade> findByOrderId(String orderId) {
            return trades.stream().filter(t -> orderId.equals(t.getOrderId())).toList();
        }
        @Override public List<Trade> findByCustomerId(String customerId) {
            return trades.stream().filter(t -> customerId.equals(t.getCustomerId())).toList();
        }
        @Override public List<Trade> findRecent(int limit) {
            return trades.stream().limit(limit).toList();
        }
    }

    // ---- Position Mapper 内存实现 ----

    static class InMemoryPositionMapper implements PositionMapper {
        final List<Position> positions = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(Position p) { p.setId(++idSeq); positions.add(p); return 1; }
        @Override public Position findByCustomerAndSymbol(String c, String s) {
            return positions.stream().filter(p -> c.equals(p.getCustomerId()) && s.equals(p.getSymbol()))
                    .findFirst().orElse(null);
        }
        @Override public List<Position> findByCustomer(String c) {
            return positions.stream().filter(p -> c.equals(p.getCustomerId())).toList();
        }
        @Override public List<Position> findAll() { return new ArrayList<>(positions); }
        @Override public List<Position> sumQtyBySymbol() {
            Map<String, BigDecimal> sums = new HashMap<>();
            for (Position p : positions) {
                sums.merge(p.getSymbol(), p.getQty(), BigDecimal::add);
            }
            List<Position> result = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> e : sums.entrySet()) {
                Position agg = new Position();
                agg.setSymbol(e.getKey());
                agg.setQty(e.getValue());
                result.add(agg);
            }
            return result;
        }
        @Override public int update(Position p) {
            for (int i = 0; i < positions.size(); i++) {
                if (p.getId() != null && p.getId().equals(positions.get(i).getId())) {
                    positions.set(i, p);
                    return 1;
                }
            }
            return 0;
        }
    }

    static class InMemoryHedgePositionMapper implements HedgePositionMapper {
        final List<HedgePosition> positions = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgePosition p) {
            p.setId(++idSeq); positions.add(p); return 1;
        }
        @Override public HedgePosition findBySymbol(String s) {
            return positions.stream().filter(p -> s.equals(p.getSymbol()))
                    .findFirst().orElse(null);
        }
        @Override public List<HedgePosition> findAll() {
            return new ArrayList<>(positions);
        }
        @Override public int update(HedgePosition p) {
            for (int i = 0; i < positions.size(); i++) {
                if (p.getId() != null && p.getId().equals(positions.get(i).getId())) {
                    positions.set(i, p);
                    return 1;
                }
            }
            return 0;
        }
    }

    // ---- Account Mapper 内存实现 ----

    static class InMemoryAccountCustomerMapper implements CustomerMapper {
        final List<Customer> customers = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(Customer c) { c.setId(++idSeq); customers.add(c); return 1; }
        @Override public Customer findByCustomerId(String id) {
            return customers.stream().filter(c -> id.equals(c.getCustomerId()))
                    .findFirst().orElse(null);
        }
        @Override public List<Customer> findAll() { return new ArrayList<>(customers); }
        @Override public int update(Customer customer) {
            for (int i = 0; i < customers.size(); i++) {
                if (customer.getCustomerId().equals(customers.get(i).getCustomerId())) {
                    Customer existing = customers.get(i);
                    customer.setUsedCredit(existing.getUsedCredit());
                    Integer newVersion = existing.getVersion() == null ? 1 : existing.getVersion() + 1;
                    customer.setVersion(newVersion);
                    customers.set(i, customer);
                    return 1;
                }
            }
            return 0;
        }
        @Override public int updateUsedCredit(Customer customer) {
            for (int i = 0; i < customers.size(); i++) {
                if (customer.getCustomerId().equals(customers.get(i).getCustomerId())) {
                    Customer existing = customers.get(i);
                    Customer updated = new Customer();
                    updated.setId(existing.getId());
                    updated.setCustomerId(existing.getCustomerId());
                    updated.setName(existing.getName());
                    updated.setLevel(existing.getLevel());
                    updated.setStatus(existing.getStatus());
                    updated.setCreditLimit(existing.getCreditLimit());
                    updated.setUsedCredit(customer.getUsedCredit());
                    Integer newVersion = existing.getVersion() == null ? 1 : existing.getVersion() + 1;
                    updated.setVersion(newVersion);
                    updated.setCreatedAt(existing.getCreatedAt());
                    updated.setUpdatedAt(customer.getUpdatedAt());
                    customers.set(i, updated);
                    return 1;
                }
            }
            return 0;
        }
        @Override public int deleteByCustomerId(String id) {
            return customers.removeIf(c -> id.equals(c.getCustomerId())) ? 1 : 0;
        }
    }

    // ---- Execution Mapper 内存实现 ----

    static class InMemoryHedgeOrderMapper implements HedgeOrderMapper {
        final List<HedgeOrder> orders = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgeOrder o) { o.setId(++idSeq); orders.add(o); return 1; }
        @Override public HedgeOrder findByHedgeOrderId(String id) {
            return orders.stream().filter(o -> id.equals(o.getHedgeOrderId()))
                    .findFirst().orElse(null);
        }
        @Override public HedgeOrder findByExchangeOrderId(String id) {
            return orders.stream().filter(o -> id.equals(o.getExchangeOrderId()))
                    .findFirst().orElse(null);
        }
        @Override public int updateByExchangeOrderId(HedgeOrder o) {
            for (int i = 0; i < orders.size(); i++) {
                if (o.getExchangeOrderId() != null
                        && o.getExchangeOrderId().equals(orders.get(i).getExchangeOrderId())) {
                    HedgeOrder existing = orders.get(i);
                    existing.setStatus(o.getStatus());
                    existing.setFilledQty(o.getFilledQty());
                    existing.setAvgPrice(o.getAvgPrice());
                    existing.setUpdatedAt(o.getUpdatedAt());
                    return 1;
                }
            }
            return 0;
        }
        @Override public List<HedgeOrder> findRecent(int limit) {
            return orders.stream().limit(limit).toList();
        }
        @Override public int countByStatus(String status) {
            return (int) orders.stream().filter(o -> status.equals(o.getStatus())).count();
        }
    }

    static class InMemoryHedgeTradeMapper implements HedgeTradeMapper {
        final List<HedgeTrade> trades = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgeTrade t) {
            t.setId(++idSeq); trades.add(t); return 1;
        }
        @Override public List<HedgeTrade> findByHedgeOrderId(String id) {
            return trades.stream().filter(t -> id.equals(t.getHedgeOrderId())).toList();
        }
        @Override public HedgeTrade findByExchangeTradeId(String id) {
            return trades.stream().filter(t -> id.equals(t.getExchangeTradeId()))
                    .findFirst().orElse(null);
        }
        @Override public List<HedgeTrade> findRecent(int limit) {
            return trades.stream().limit(limit).toList();
        }
    }

    static class InMemoryBatchItemMapper implements HedgeBatchItemMapper {
        final List<HedgeBatchItem> items = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgeBatchItem item) { item.setId(++idSeq); items.add(item); return 1; }
        @Override public List<HedgeBatchItem> findByHedgeOrderId(String hedgeOrderId) {
            return items.stream().filter(i -> hedgeOrderId.equals(i.getHedgeOrderId())).toList();
        }
        @Override public HedgeBatchItem findByOriginalTradeId(String originalTradeId) {
            return items.stream().filter(i -> originalTradeId.equals(i.getOriginalTradeId()))
                    .findFirst().orElse(null);
        }
        @Override public int update(HedgeBatchItem item) {
            for (int i = 0; i < items.size(); i++) {
                if (item.getId() != null && item.getId().equals(items.get(i).getId())) {
                    items.set(i, item);
                    return 1;
                }
            }
            return 0;
        }
        @Override public int countByStatus(String status) {
            return (int) items.stream().filter(i -> status.equals(i.getStatus())).count();
        }
    }

    // ---- 幂等表内存实现 ----

    static class InMemoryProcessedEventMapper implements ProcessedEventMapper {
        final Set<String> processed = new HashSet<>();

        @Override public int insert(String eventId, LocalDateTime processedAt) {
            return processed.add(eventId) ? 1 : 0;
        }
        @Override public int exists(String eventId) {
            return processed.contains(eventId) ? 1 : 0;
        }
    }

    // ---- Outbox 内存实现（带 relay 立即发送到 CapturingKafka） ----

    static class MockOutboxService implements OutboxService {
        private final CapturingKafkaTemplate kafkaTemplate;
        final List<OutboxMessage> messages = new ArrayList<>();
        long idSeq = 0;

        MockOutboxService(CapturingKafkaTemplate kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        @Override
        public void saveEvent(String topic, com.bank.trading.common.core.event.BaseEvent event, int shardId) {
            OutboxMessage msg = new OutboxMessage();
            msg.setId(++idSeq);
            msg.setEventId(event.getEventId());
            msg.setTopic(topic);
            msg.setPartitionKey(event.getPartitionKey());
            msg.setPayload(JSON.toJSONString(event));
            msg.setStatus("PENDING");
            msg.setShardId(shardId);
            messages.add(msg);

            // 立即 relay 到 Kafka
            try {
                kafkaTemplate.send(topic, msg.getPartitionKey(), msg.getPayload()).get();
                msg.setStatus("SENT");
            } catch (Exception e) {
                msg.setStatus("FAILED");
            }
        }

        @Override
        public boolean isSent(String eventId) {
            return messages.stream().anyMatch(m -> eventId.equals(m.getEventId()) && "SENT".equals(m.getStatus()));
        }
    }

    // ---- EventStore 内存实现 ----

    static class MockEventStoreService implements EventStoreService {
        final List<Object> events = new ArrayList<>();

        @Override
        public void appendEvent(com.bank.trading.common.core.event.BaseEvent event,
                                String aggregateType, String aggregateId, int shardId) {
            events.add(event);
        }

        @Override
        public List<EventStoreRecord> findByCustomer(String customerId, int shardId) {
            return List.of();
        }

        @Override
        public List<EventStoreRecord> findByAggregate(String aggregateType, String aggregateId, int shardId) {
            return List.of();
        }
    }

    // ---- Capturing Kafka Template ----

    static class CapturingKafkaTemplate extends KafkaTemplate<String, String> {
        final List<SentMessage> sentMessages = new CopyOnWriteArrayList<>();

        CapturingKafkaTemplate() {
            super(new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(
                    java.util.Collections.emptyMap()));
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
            sentMessages.add(new SentMessage(topic, key, data));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String data) {
            sentMessages.add(new SentMessage(topic, null, data));
            return CompletableFuture.completedFuture(null);
        }

        List<SentMessage> getByTopic(String topic) {
            return sentMessages.stream().filter(m -> topic.equals(m.topic)).toList();
        }

        static class SentMessage {
            final String topic;
            final String key;
            final String value;

            SentMessage(String topic, String key, String value) {
                this.topic = topic;
                this.key = key;
                this.value = value;
            }
        }
    }

    // ---- ExecutionService Holder（打破循环依赖） ----

    static class ExecutionServiceHolder {
        ExecutionService service;
    }

    // ---- 桥接：ExchangeSessionClient → MatchingEngine ----

    static class BridgeExchangeSessionClient extends ExchangeSessionClient {
        private final MatchingEngine matchingEngine;

        BridgeExchangeSessionClient(MatchingEngine matchingEngine) {
            super(null, null, null);
            this.matchingEngine = matchingEngine;
        }

        @Override
        public ExchangeOrderResponse submitOrder(ExchangeOrderRequest request) {
            ExchangeOrder order = matchingEngine.submitOrder(
                    request.getClientOrderId(),
                    request.getSymbol(),
                    request.getSide(),
                    request.getType(),
                    request.getQty(),
                    request.getPrice());
            return convertToResponse(order);
        }

        @Override
        public ExchangeOrderResponse queryOrder(String exchangeOrderId) {
            ExchangeOrder order = matchingEngine.getOrder(exchangeOrderId);
            return order != null ? convertToResponse(order) : null;
        }

        private ExchangeOrderResponse convertToResponse(ExchangeOrder order) {
            ExchangeOrderResponse response = new ExchangeOrderResponse();
            response.setOrderId(order.getOrderId());
            response.setClientOrderId(order.getClientOrderId());
            response.setSymbol(order.getSymbol());
            response.setSide(order.getSide());
            response.setType(order.getType());
            response.setQty(order.getQty());
            response.setPrice(order.getPrice());
            response.setFilledQty(order.getFilledQty());
            response.setAvgPrice(order.getAvgPrice());
            response.setStatus(order.getStatus());
            response.setCreatedAt(order.getCreatedAt());
            response.setUpdatedAt(order.getUpdatedAt());
            return response;
        }
    }

    // ---- 桥接：sim-exchange Callback → ExecutionService ----

    static class BridgeCallbackRegistry extends CallbackRegistry {
        private final ExecutionServiceHolder holder;
        private final long matchDelayMs;

        BridgeCallbackRegistry(ExecutionServiceHolder holder, long matchDelayMs) {
            super(null, matchDelayMs);
            this.holder = holder;
            this.matchDelayMs = matchDelayMs;
        }

        @Override
        public long getMatchDelayMs() {
            return matchDelayMs;
        }

        @Override
        public void notifyOrderUpdate(Object payload) {
            if (payload instanceof ExchangeOrder) {
                ExchangeOrderResponse response = convertToResponse((ExchangeOrder) payload);
                holder.service.onOrderNotification(response);
            }
        }

        @Override
        public void notifyTrade(Object payload) {
            if (payload instanceof TradeFill) {
                ExchangeTradeNotification notification = convertToNotification((TradeFill) payload);
                holder.service.onTradeNotification(notification);
            }
        }

        private ExchangeOrderResponse convertToResponse(ExchangeOrder order) {
            ExchangeOrderResponse response = new ExchangeOrderResponse();
            response.setOrderId(order.getOrderId());
            response.setClientOrderId(order.getClientOrderId());
            response.setSymbol(order.getSymbol());
            response.setSide(order.getSide());
            response.setType(order.getType());
            response.setQty(order.getQty());
            response.setPrice(order.getPrice());
            response.setFilledQty(order.getFilledQty());
            response.setAvgPrice(order.getAvgPrice());
            response.setStatus(order.getStatus());
            response.setCreatedAt(order.getCreatedAt());
            response.setUpdatedAt(order.getUpdatedAt());
            return response;
        }

        private ExchangeTradeNotification convertToNotification(TradeFill fill) {
            ExchangeTradeNotification notification = new ExchangeTradeNotification();
            notification.setTradeId(fill.getTradeId());
            notification.setOrderId(fill.getOrderId());
            notification.setSymbol(fill.getSymbol());
            notification.setSide(fill.getSide());
            notification.setQty(fill.getQty());
            notification.setPrice(fill.getPrice());
            notification.setAmount(fill.getAmount());
            notification.setTradeTime(fill.getTradeTime());
            return notification;
        }
    }
}
