package com.bank.trading.oms.service;

import com.bank.trading.common.core.dto.*;
import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.enums.OrderStatus;
import com.bank.trading.common.core.enums.OrderType;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.core.idgen.IdGenerator;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.outbox.OutboxService;
import com.bank.trading.oms.entity.Order;
import com.bank.trading.oms.entity.Trade;
import com.bank.trading.oms.mapper.OrderMapper;
import com.bank.trading.oms.mapper.TradeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final EventStoreService eventStoreService;
    private final OutboxService outboxService;
    private final RiskChecker riskChecker;
    private final PriceProvider priceProvider;
    private final IdGenerator idGenerator;

    @Value("${oms.trade-topic:trade-event}")
    private String tradeTopic;

    private final AtomicLong tradeIdGenerator = new AtomicLong(0);

    public OrderService(OrderMapper orderMapper,
                        TradeMapper tradeMapper,
                        EventStoreService eventStoreService,
                        OutboxService outboxService,
                        RiskChecker riskChecker,
                        PriceProvider priceProvider,
                        IdGenerator idGenerator) {
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.eventStoreService = eventStoreService;
        this.outboxService = outboxService;
        this.riskChecker = riskChecker;
        this.priceProvider = priceProvider;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public OrderDTO createOrder(OrderCreateDTO createDTO) {
        validateCreateRequest(createDTO);

        Order existing = orderMapper.findByClientOrderId(createDTO.getClientOrderId(), createDTO.getCustomerId());
        if (existing != null) {
            return toDTO(existing);
        }

        Order order = new Order();
        order.setId(idGenerator.nextLongId());
        order.setOrderId(generateOrderId());
        order.setClientOrderId(createDTO.getClientOrderId());
        order.setCustomerId(createDTO.getCustomerId());
        order.setSymbol(createDTO.getSymbol());
        order.setSide(createDTO.getSide().toUpperCase());
        order.setType(createDTO.getType().toUpperCase());
        order.setQty(createDTO.getQty());
        order.setFilledQty(BigDecimal.ZERO);
        order.setPrice(createDTO.getPrice());
        order.setAvgPrice(BigDecimal.ZERO);
        order.setStatus(OrderStatus.NEW.getCode());
        order.setTraceId(createDTO.getTraceId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderMapper.insert(order);
        log.info("Order created: orderId={}, clientOrderId={}, customer={}, symbol={}, side={}, qty={}",
                order.getOrderId(), order.getClientOrderId(), order.getCustomerId(),
                order.getSymbol(), order.getSide(), order.getQty());

        return processOrder(order);
    }

    private OrderDTO processOrder(Order order) {
        order.setStatus(OrderStatus.PENDING_RISK.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateByOrderId(order);

        RiskCheckRequest riskRequest = buildRiskRequest(order);
        RiskCheckResult riskResult = riskChecker.check(riskRequest);

        if (!riskResult.isPassed()) {
            order.setStatus(OrderStatus.REJECTED.getCode());
            order.setRejectReason(riskResult.getRejectReason());
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateByOrderId(order);
            log.info("Order rejected: orderId={}, reason={}", order.getOrderId(), riskResult.getRejectReason());
            return toDTO(order);
        }

        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateByOrderId(order);

        return matchOrder(order);
    }

    private OrderDTO matchOrder(Order order) {
        BigDecimal executionPrice;

        if (OrderType.MARKET.getCode().equals(order.getType())) {
            QuoteDTO quote = priceProvider.getQuote(order.getSymbol());
            if (quote == null) {
                order.setStatus(OrderStatus.REJECTED.getCode());
                order.setRejectReason("No market data available for symbol: " + order.getSymbol());
                order.setUpdatedAt(LocalDateTime.now());
                orderMapper.updateByOrderId(order);
                log.info("Order rejected (no market data): orderId={}", order.getOrderId());
                return toDTO(order);
            }

            if (quote.getValidUntil() != null && quote.getValidUntil() < System.currentTimeMillis()) {
                order.setStatus(OrderStatus.REJECTED.getCode());
                order.setRejectReason("Quote expired for symbol: " + order.getSymbol()
                        + ", please request a new quote (RFQ)");
                order.setUpdatedAt(LocalDateTime.now());
                orderMapper.updateByOrderId(order);
                log.info("Order rejected (quote expired): orderId={}, symbol={}, validUntil={}",
                        order.getOrderId(), order.getSymbol(), quote.getValidUntil());
                return toDTO(order);
            }

            if ("BUY".equalsIgnoreCase(order.getSide())) {
                executionPrice = quote.getCustomerAskPrice();
            } else {
                executionPrice = quote.getCustomerBidPrice();
            }
        } else {
            executionPrice = order.getPrice();
        }

        BigDecimal filledQty = order.getQty();
        BigDecimal filledAmount = filledQty.multiply(executionPrice).setScale(4, RoundingMode.HALF_UP);

        Trade trade = new Trade();
        trade.setId(idGenerator.nextLongId());
        trade.setTradeId(generateTradeId());
        trade.setOrderId(order.getOrderId());
        trade.setClientOrderId(order.getClientOrderId());
        trade.setCustomerId(order.getCustomerId());
        trade.setSymbol(order.getSymbol());
        trade.setSide(order.getSide());
        trade.setQty(filledQty);
        trade.setPrice(executionPrice);
        trade.setAmount(filledAmount);
        trade.setTradeType("CUSTOMER");
        trade.setTradeTime(LocalDateTime.now());
        tradeMapper.insert(trade);

        order.setFilledQty(filledQty);
        order.setAvgPrice(executionPrice);
        order.setStatus(OrderStatus.FILLED.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateByOrderId(order);

        publishTradeEvent(order, trade);
        log.info("Order filled: orderId={}, tradeId={}, price={}, qty={}",
                order.getOrderId(), trade.getTradeId(), executionPrice, filledQty);

        return toDTO(order);
    }

    private void publishTradeEvent(Order order, Trade trade) {
        try {
            TradeEvent event = new TradeEvent(order.getCustomerId());
            event.setOrderId(order.getOrderId());
            event.setTradeId(trade.getTradeId());
            event.setClientOrderId(order.getClientOrderId());
            event.setCustomerId(order.getCustomerId());
            event.setSymbol(order.getSymbol());
            event.setSide(order.getSide());
            event.setQty(trade.getQty());
            event.setPrice(trade.getPrice());
            event.setAmount(trade.getAmount());
            event.setTradeTime(trade.getTradeTime() != null
                    ? trade.getTradeTime().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null);

            int shardId = 0;
            eventStoreService.appendEvent(event, "TRADE", trade.getTradeId(), shardId);
            outboxService.saveEvent(tradeTopic, event, shardId);
        } catch (Exception e) {
            log.warn("Failed to publish trade event: orderId={}, error={}",
                    order.getOrderId(), e.getMessage());
        }
    }

    @Transactional
    public OrderDTO cancelOrder(String orderId) {
        Order order = orderMapper.findByOrderId(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        OrderStatus currentStatus = OrderStatus.of(order.getStatus());
        if (currentStatus.isFinal()) {
            throw new IllegalStateException("Cannot cancel order in final state: " + currentStatus);
        }
        if (!currentStatus.canCancel()) {
            throw new IllegalStateException("Cannot cancel order in state: " + currentStatus);
        }

        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateByOrderId(order);
        log.info("Order cancelled: orderId={}", order.getOrderId());

        return toDTO(order);
    }

    public OrderDTO getOrderByOrderId(String orderId) {
        Order order = orderMapper.findByOrderId(orderId);
        return order != null ? toDTO(order) : null;
    }

    public OrderDTO getOrderByClientOrderId(String clientOrderId, String customerId) {
        Order order = orderMapper.findByClientOrderId(clientOrderId, customerId);
        return order != null ? toDTO(order) : null;
    }

    public List<OrderDTO> getOrdersByCustomer(String customerId) {
        List<Order> orders = orderMapper.findByCustomerId(customerId);
        return orders.stream().map(this::toDTO).toList();
    }

    public List<TradeDTO> getTradesByOrderId(String orderId) {
        List<Trade> trades = tradeMapper.findByOrderId(orderId);
        return trades.stream().map(this::toTradeDTO).toList();
    }

    public List<TradeDTO> getTradesByCustomer(String customerId) {
        List<Trade> trades = tradeMapper.findByCustomerId(customerId);
        return trades.stream().map(this::toTradeDTO).toList();
    }

    private void validateCreateRequest(OrderCreateDTO dto) {
        if (dto.getClientOrderId() == null || dto.getClientOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("clientOrderId is required");
        }
        if (dto.getCustomerId() == null || dto.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (dto.getSymbol() == null || dto.getSymbol().trim().isEmpty()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (dto.getSide() == null || (!dto.getSide().equalsIgnoreCase(OrderSide.BUY.getCode())
                && !dto.getSide().equalsIgnoreCase(OrderSide.SELL.getCode()))) {
            throw new IllegalArgumentException("Invalid side: " + dto.getSide());
        }
        if (dto.getType() == null || (!dto.getType().equalsIgnoreCase(OrderType.MARKET.getCode())
                && !dto.getType().equalsIgnoreCase(OrderType.LIMIT.getCode()))) {
            throw new IllegalArgumentException("Invalid type: " + dto.getType());
        }
        if (dto.getQty() == null || dto.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (OrderType.LIMIT.getCode().equalsIgnoreCase(dto.getType())
                && (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("price is required for limit order");
        }
    }

    private RiskCheckRequest buildRiskRequest(Order order) {
        RiskCheckRequest request = new RiskCheckRequest();
        request.setCustomerId(order.getCustomerId());
        request.setSymbol(order.getSymbol());
        request.setSide(order.getSide());
        request.setOrderType(order.getType());
        request.setQty(order.getQty());
        request.setPrice(order.getPrice());
        request.setClientOrderId(order.getClientOrderId());
        request.setTraceId(order.getTraceId());
        return request;
    }

    private String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String generateTradeId() {
        return "TRD-" + System.currentTimeMillis() + "-" + tradeIdGenerator.incrementAndGet();
    }

    private OrderDTO toDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setOrderId(order.getOrderId());
        dto.setClientOrderId(order.getClientOrderId());
        dto.setCustomerId(order.getCustomerId());
        dto.setSymbol(order.getSymbol());
        dto.setSide(order.getSide());
        dto.setType(order.getType());
        dto.setQty(order.getQty());
        dto.setFilledQty(order.getFilledQty());
        dto.setPrice(order.getPrice());
        dto.setAvgPrice(order.getAvgPrice());
        dto.setStatus(order.getStatus());
        dto.setRejectReason(order.getRejectReason());
        dto.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null);
        dto.setUpdatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null);
        return dto;
    }

    private TradeDTO toTradeDTO(Trade trade) {
        TradeDTO dto = new TradeDTO();
        dto.setTradeId(trade.getTradeId());
        dto.setOrderId(trade.getOrderId());
        dto.setCustomerId(trade.getCustomerId());
        dto.setSymbol(trade.getSymbol());
        dto.setSide(trade.getSide());
        dto.setQty(trade.getQty());
        dto.setPrice(trade.getPrice());
        dto.setAmount(trade.getAmount());
        dto.setTradeTime(trade.getTradeTime() != null
                ? trade.getTradeTime().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null);
        return dto;
    }
}
