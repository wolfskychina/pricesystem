package com.bank.trading.oms.service;

import com.bank.trading.common.core.dto.OrderCreateDTO;
import com.bank.trading.common.core.dto.OrderDTO;
import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.common.core.enums.OrderStatus;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.outbox.OutboxService;
import com.bank.trading.oms.entity.Order;
import com.bank.trading.oms.entity.Trade;
import com.bank.trading.oms.mapper.OrderMapper;
import com.bank.trading.oms.mapper.TradeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private OrderService orderService;
    private InMemoryOrderMapper orderMapper;
    private InMemoryTradeMapper tradeMapper;
    private MockEventStoreService eventStoreService;
    private MockOutboxService outboxService;
    private MockRiskChecker riskChecker;
    private MockPriceProvider priceProvider;
    private com.bank.trading.common.core.idgen.IdGenerator idGenerator;

    static class InMemoryOrderMapper implements OrderMapper {
        final List<Order> orders = new ArrayList<>();
        long idSeq = 0;

        @Override
        public int insert(Order order) {
            order.setId(++idSeq);
            orders.add(order);
            return 1;
        }

        @Override
        public int updateByOrderId(Order order) {
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i).getOrderId().equals(order.getOrderId())) {
                    Order existing = orders.get(i);
                    existing.setFilledQty(order.getFilledQty());
                    existing.setAvgPrice(order.getAvgPrice());
                    existing.setStatus(order.getStatus());
                    existing.setRejectReason(order.getRejectReason());
                    existing.setUpdatedAt(order.getUpdatedAt());
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public Order findByOrderId(String orderId) {
            return orders.stream().filter(o -> o.getOrderId().equals(orderId)).findFirst().orElse(null);
        }

        @Override
        public Order findByClientOrderId(String clientOrderId, String customerId) {
            return orders.stream()
                    .filter(o -> o.getClientOrderId().equals(clientOrderId) && o.getCustomerId().equals(customerId))
                    .findFirst().orElse(null);
        }

        @Override
        public List<Order> findByCustomerId(String customerId) {
            return orders.stream().filter(o -> o.getCustomerId().equals(customerId)).toList();
        }

        @Override
        public List<Order> findRecent(int limit) {
            return orders.stream().limit(limit).toList();
        }

        @Override
        public int countByStatus(String status) {
            return (int) orders.stream().filter(o -> status.equals(o.getStatus())).count();
        }
    }

    static class InMemoryTradeMapper implements TradeMapper {
        final List<Trade> trades = new ArrayList<>();
        long idSeq = 0;

        @Override
        public int insert(Trade trade) {
            trade.setId(++idSeq);
            trades.add(trade);
            return 1;
        }

        @Override
        public List<Trade> findByOrderId(String orderId) {
            return trades.stream().filter(t -> t.getOrderId().equals(orderId)).toList();
        }

        @Override
        public List<Trade> findByCustomerId(String customerId) {
            return trades.stream().filter(t -> t.getCustomerId().equals(customerId)).toList();
        }

        @Override
        public List<Trade> findRecent(int limit) {
            return trades.stream().limit(limit).toList();
        }
    }

    static class MockEventStoreService implements EventStoreService {
        int appendCount = 0;

        @Override
        public void appendEvent(com.bank.trading.common.core.event.BaseEvent event,
                                String aggregateType, String aggregateId, int shardId) {
            appendCount++;
        }

        @Override
        public List<com.bank.trading.common.persistence.eventstore.EventStoreRecord> findByCustomer(String customerId, int shardId) {
            return null;
        }

        @Override
        public List<com.bank.trading.common.persistence.eventstore.EventStoreRecord> findByAggregate(String aggregateType, String aggregateId, int shardId) {
            return null;
        }
    }

    static class MockOutboxService implements OutboxService {
        int saveCount = 0;

        @Override
        public void saveEvent(String topic, com.bank.trading.common.core.event.BaseEvent event, int shardId) {
            saveCount++;
        }

        @Override
        public boolean isSent(String eventId) {
            return false;
        }
    }

    static class MockRiskChecker implements RiskChecker {
        RiskCheckResult result = RiskCheckResult.pass();

        @Override
        public RiskCheckResult check(RiskCheckRequest request) {
            return result;
        }
    }

    static class MockPriceProvider implements PriceProvider {
        BigDecimal price = new BigDecimal("520.25");
        com.bank.trading.common.core.dto.QuoteDTO quote;

        @Override
        public BigDecimal getExecutionPrice(String symbol, String side) {
            if (quote != null) {
                if ("BUY".equalsIgnoreCase(side)) {
                    return quote.getCustomerAskPrice();
                } else {
                    return quote.getCustomerBidPrice();
                }
            }
            return price;
        }

        @Override
        public com.bank.trading.common.core.dto.QuoteDTO getQuote(String symbol) {
            if (quote != null) {
                return quote;
            }
            if (price == null) {
                return null;
            }
            com.bank.trading.common.core.dto.QuoteDTO q = new com.bank.trading.common.core.dto.QuoteDTO();
            q.setSymbol(symbol);
            q.setCustomerBidPrice(price);
            q.setCustomerAskPrice(price);
            q.setValidUntil(System.currentTimeMillis() + 30000);
            return q;
        }
    }

    @BeforeEach
    void setUp() {
        orderMapper = new InMemoryOrderMapper();
        tradeMapper = new InMemoryTradeMapper();
        eventStoreService = new MockEventStoreService();
        outboxService = new MockOutboxService();
        riskChecker = new MockRiskChecker();
        priceProvider = new MockPriceProvider();
        idGenerator = new com.bank.trading.common.core.idgen.IdGenerator(1, 1);
        orderService = new OrderService(orderMapper, tradeMapper, eventStoreService,
                outboxService, riskChecker, priceProvider, idGenerator);

        try {
            java.lang.reflect.Field field = OrderService.class.getDeclaredField("tradeTopic");
            field.setAccessible(true);
            field.set(orderService, "trade-event");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OrderCreateDTO createMarketOrder(String clientOrderId, String customerId, String symbol,
                                             String side, BigDecimal qty) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setClientOrderId(clientOrderId);
        dto.setCustomerId(customerId);
        dto.setSymbol(symbol);
        dto.setSide(side);
        dto.setType("MARKET");
        dto.setQty(qty);
        return dto;
    }

    private OrderCreateDTO createLimitOrder(String clientOrderId, String customerId, String symbol,
                                            String side, BigDecimal qty, BigDecimal price) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setClientOrderId(clientOrderId);
        dto.setCustomerId(customerId);
        dto.setSymbol(symbol);
        dto.setSide(side);
        dto.setType("LIMIT");
        dto.setQty(qty);
        dto.setPrice(price);
        return dto;
    }

    @Test
    void createOrder_marketOrder_riskPassed_shouldFill() {
        OrderCreateDTO request = createMarketOrder("C001", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertNotNull(result.getOrderId());
        assertEquals("C001", result.getClientOrderId());
        assertEquals("CUST001", result.getCustomerId());
        assertEquals("AU2406", result.getSymbol());
        assertEquals("BUY", result.getSide());
        assertEquals("MARKET", result.getType());
        assertEquals(0, BigDecimal.valueOf(10).compareTo(result.getQty()));
        assertEquals(OrderStatus.FILLED.getCode(), result.getStatus());
        assertEquals(0, BigDecimal.valueOf(10).compareTo(result.getFilledQty()));
        assertEquals(0, new BigDecimal("520.25").compareTo(result.getAvgPrice()));
        assertNull(result.getRejectReason());
        assertEquals(1, tradeMapper.trades.size());
        assertEquals(1, eventStoreService.appendCount);
        assertEquals(1, outboxService.saveCount);
    }

    @Test
    void createOrder_limitOrder_riskPassed_shouldFillAtLimitPrice() {
        OrderCreateDTO request = createLimitOrder("C002", "CUST001", "AU2406", "SELL",
                BigDecimal.valueOf(5), new BigDecimal("521.00"));

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(OrderStatus.FILLED.getCode(), result.getStatus());
        assertEquals(0, new BigDecimal("521.00").compareTo(result.getAvgPrice()));
        assertEquals(0, new BigDecimal("521.00").compareTo(result.getPrice()));
    }

    @Test
    void createOrder_riskRejected_shouldReject() {
        riskChecker.result = RiskCheckResult.reject("POSITION_LIMIT", "Position limit exceeded");

        OrderCreateDTO request = createMarketOrder("C003", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(OrderStatus.REJECTED.getCode(), result.getStatus());
        assertEquals("Position limit exceeded", result.getRejectReason());
        assertEquals(0, tradeMapper.trades.size());
        assertEquals(0, eventStoreService.appendCount);
    }

    @Test
    void createOrder_marketOrder_noPriceAvailable_shouldReject() {
        priceProvider.price = null;

        OrderCreateDTO request = createMarketOrder("C004", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(OrderStatus.REJECTED.getCode(), result.getStatus());
        assertTrue(result.getRejectReason().contains("No market data"));
    }

    @Test
    void createOrder_duplicateClientOrderId_shouldReturnExisting() {
        OrderCreateDTO request = createMarketOrder("C005", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));
        OrderDTO first = orderService.createOrder(request);

        OrderCreateDTO duplicate = createMarketOrder("C005", "CUST001", "AU2406", "SELL", BigDecimal.valueOf(20));
        OrderDTO second = orderService.createOrder(duplicate);

        assertEquals(first.getOrderId(), second.getOrderId());
        assertEquals(1, orderMapper.orders.size());
    }

    @Test
    void createOrder_missingClientOrderId_shouldThrow() {
        OrderCreateDTO request = createMarketOrder(null, "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_missingCustomerId_shouldThrow() {
        OrderCreateDTO request = createMarketOrder("C006", null, "AU2406", "BUY", BigDecimal.valueOf(10));
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_invalidSide_shouldThrow() {
        OrderCreateDTO request = createMarketOrder("C007", "CUST001", "AU2406", "INVALID", BigDecimal.valueOf(10));
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_negativeQty_shouldThrow() {
        OrderCreateDTO request = createMarketOrder("C008", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(-5));
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_limitOrder_missingPrice_shouldThrow() {
        OrderCreateDTO request = createMarketOrder("C009", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));
        request.setType("LIMIT");
        request.setPrice(null);
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }

    @Test
    void cancelOrder_filledOrder_shouldThrowIllegalState() {
        OrderCreateDTO request = createMarketOrder("C010", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));
        OrderDTO created = orderService.createOrder(request);
        assertEquals(OrderStatus.FILLED.getCode(), created.getStatus());

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrder(created.getOrderId()));
    }

    @Test
    void cancelOrder_notExist_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> orderService.cancelOrder("NOT_EXIST"));
    }

    @Test
    void getOrderByOrderId_exist_shouldReturn() {
        OrderCreateDTO request = createMarketOrder("C012", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));
        OrderDTO created = orderService.createOrder(request);

        OrderDTO found = orderService.getOrderByOrderId(created.getOrderId());

        assertNotNull(found);
        assertEquals(created.getOrderId(), found.getOrderId());
    }

    @Test
    void getOrderByOrderId_notExist_shouldReturnNull() {
        assertNull(orderService.getOrderByOrderId("NOT_EXIST"));
    }

    @Test
    void getOrdersByCustomer_shouldReturnAllOrders() {
        orderService.createOrder(createMarketOrder("C013", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10)));
        orderService.createOrder(createMarketOrder("C014", "CUST001", "AG2406", "SELL", BigDecimal.valueOf(5)));
        orderService.createOrder(createMarketOrder("C015", "CUST002", "AU2406", "BUY", BigDecimal.valueOf(3)));

        List<OrderDTO> cust1Orders = orderService.getOrdersByCustomer("CUST001");
        List<OrderDTO> cust2Orders = orderService.getOrdersByCustomer("CUST002");

        assertEquals(2, cust1Orders.size());
        assertEquals(1, cust2Orders.size());
    }

    @Test
    void getTradesByOrderId_shouldReturnTrades() {
        OrderCreateDTO request = createMarketOrder("C016", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10));
        OrderDTO created = orderService.createOrder(request);

        var trades = orderService.getTradesByOrderId(created.getOrderId());

        assertEquals(1, trades.size());
        assertEquals(created.getOrderId(), trades.get(0).getOrderId());
        assertEquals(0, BigDecimal.valueOf(10).compareTo(trades.get(0).getQty()));
    }

    @Test
    void getTradesByCustomer_shouldReturnAllTrades() {
        orderService.createOrder(createMarketOrder("C017", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10)));
        orderService.createOrder(createMarketOrder("C018", "CUST001", "AG2406", "SELL", BigDecimal.valueOf(5)));

        var trades = orderService.getTradesByCustomer("CUST001");

        assertEquals(2, trades.size());
    }

    @Test
    void createOrder_shouldGenerateUniqueOrderIds() {
        OrderDTO order1 = orderService.createOrder(createMarketOrder("C019", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10)));
        OrderDTO order2 = orderService.createOrder(createMarketOrder("C020", "CUST001", "AU2406", "BUY", BigDecimal.valueOf(10)));

        assertNotEquals(order1.getOrderId(), order2.getOrderId());
        assertTrue(order1.getOrderId().startsWith("ORD-"));
    }

    @Test
    void createOrder_tradeAmount_shouldBeQtyTimesPrice() {
        OrderCreateDTO request = createLimitOrder("C021", "CUST001", "AU2406", "BUY",
                BigDecimal.valueOf(10), new BigDecimal("520.00"));

        OrderDTO result = orderService.createOrder(request);

        Trade trade = tradeMapper.trades.get(0);
        BigDecimal expectedAmount = BigDecimal.valueOf(10).multiply(new BigDecimal("520.00"));
        assertEquals(0, expectedAmount.compareTo(trade.getAmount()));
    }

    @Test
    void createOrder_marketOrder_quoteExpired_shouldReject() {
        com.bank.trading.common.core.dto.QuoteDTO expiredQuote =
                new com.bank.trading.common.core.dto.QuoteDTO();
        expiredQuote.setSymbol("AU2406");
        expiredQuote.setCustomerBidPrice(new BigDecimal("520.00"));
        expiredQuote.setCustomerAskPrice(new BigDecimal("520.50"));
        expiredQuote.setValidUntil(System.currentTimeMillis() - 1000);
        priceProvider.quote = expiredQuote;

        OrderCreateDTO request = createMarketOrder("C022", "CUST001", "AU2406", "BUY",
                BigDecimal.valueOf(10));

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(OrderStatus.REJECTED.getCode(), result.getStatus());
        assertTrue(result.getRejectReason().contains("Quote expired"),
                "Reject reason should mention quote expired");
        assertTrue(result.getRejectReason().contains("RFQ"),
                "Reject reason should suggest RFQ");
        assertEquals(0, tradeMapper.trades.size());
        assertEquals(0, eventStoreService.appendCount);
    }

    @Test
    void createOrder_marketOrder_validQuote_shouldFill() {
        com.bank.trading.common.core.dto.QuoteDTO validQuote =
                new com.bank.trading.common.core.dto.QuoteDTO();
        validQuote.setSymbol("AU2406");
        validQuote.setCustomerBidPrice(new BigDecimal("520.00"));
        validQuote.setCustomerAskPrice(new BigDecimal("520.50"));
        validQuote.setValidUntil(System.currentTimeMillis() + 30000);
        priceProvider.quote = validQuote;

        OrderCreateDTO request = createMarketOrder("C023", "CUST001", "AU2406", "BUY",
                BigDecimal.valueOf(10));

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(OrderStatus.FILLED.getCode(), result.getStatus());
        assertEquals(0, new BigDecimal("520.50").compareTo(result.getAvgPrice()),
                "BUY order should fill at customer ask price");
    }

    @Test
    void createOrder_marketOrder_sellSide_shouldUseBidPrice() {
        com.bank.trading.common.core.dto.QuoteDTO validQuote =
                new com.bank.trading.common.core.dto.QuoteDTO();
        validQuote.setSymbol("AU2406");
        validQuote.setCustomerBidPrice(new BigDecimal("520.00"));
        validQuote.setCustomerAskPrice(new BigDecimal("520.50"));
        validQuote.setValidUntil(System.currentTimeMillis() + 30000);
        priceProvider.quote = validQuote;

        OrderCreateDTO request = createMarketOrder("C024", "CUST001", "AU2406", "SELL",
                BigDecimal.valueOf(10));

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(OrderStatus.FILLED.getCode(), result.getStatus());
        assertEquals(0, new BigDecimal("520.00").compareTo(result.getAvgPrice()),
                "SELL order should fill at customer bid price");
    }
}
