package com.bank.trading.simexchange.engine;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.enums.OrderStatus;
import com.bank.trading.common.core.enums.OrderType;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.TradeFill;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MatchingEngine {

    private final MarketDataEngine marketDataEngine;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchingEngine.class);

    public MatchingEngine(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    public MatchingEngine(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    public MatchingEngine(MarketDataEngine marketDataEngine, Map<String, ExchangeOrder> orderMap, List<TradeFill> tradeFills, AtomicLong tradeIdGenerator) {
        this.marketDataEngine = marketDataEngine;
        this.orderMap = orderMap;
        this.tradeFills = tradeFills;
        this.tradeIdGenerator = tradeIdGenerator;
    }

    private final Map<String, ExchangeOrder> orderMap = new ConcurrentHashMap<>();
    private final List<TradeFill> tradeFills = new ArrayList<>();
    private final AtomicLong tradeIdGenerator = new AtomicLong(0);

    public ExchangeOrder submitOrder(String clientOrderId, String symbol, String side,
                                     String type, BigDecimal qty, BigDecimal price) {
        ExchangeOrder order = new ExchangeOrder();
        order.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        order.setClientOrderId(clientOrderId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setType(type);
        order.setQty(qty);
        order.setPrice(price);
        order.setFilledQty(BigDecimal.ZERO);
        order.setStatus(OrderStatus.NEW.getCode());
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());

        orderMap.put(order.getOrderId(), order);

        matchOrder(order);

        return order;
    }

    private void matchOrder(ExchangeOrder order) {
        String symbol = order.getSymbol();
        var marketData = marketDataEngine.getLatest(symbol);
        if (marketData == null) {
            order.setStatus(OrderStatus.REJECTED.getCode());
            order.setUpdatedAt(System.currentTimeMillis());
            log.warn("Order rejected: no market data for symbol={}", symbol);
            return;
        }

        OrderSide side = OrderSide.of(order.getSide());
        OrderType type = OrderType.of(order.getType());

        BigDecimal execPrice;
        if (type == OrderType.MARKET) {
            execPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
        } else {
            execPrice = order.getPrice();
            BigDecimal marketPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
            boolean canMatch = side == OrderSide.BUY
                    ? execPrice.compareTo(marketPrice) >= 0
                    : execPrice.compareTo(marketPrice) <= 0;
            if (!canMatch) {
                order.setStatus(OrderStatus.REJECTED.getCode());
                order.setUpdatedAt(System.currentTimeMillis());
                log.warn("Order rejected: limit price cannot match market, orderId={}", order.getOrderId());
                return;
            }
            execPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
        }

        fillOrder(order, order.getQty(), execPrice);
    }

    private void fillOrder(ExchangeOrder order, BigDecimal fillQty, BigDecimal fillPrice) {
        BigDecimal amount = fillQty.multiply(fillPrice);

        TradeFill fill = new TradeFill();
        fill.setTradeId("T" + tradeIdGenerator.incrementAndGet());
        fill.setOrderId(order.getOrderId());
        fill.setSymbol(order.getSymbol());
        fill.setSide(order.getSide());
        fill.setQty(fillQty);
        fill.setPrice(fillPrice);
        fill.setAmount(amount);
        fill.setTradeTime(System.currentTimeMillis());

        tradeFills.add(fill);

        order.setFilledQty(order.getFilledQty().add(fillQty));
        order.setAvgPrice(amount.divide(fillQty, 8, java.math.RoundingMode.HALF_UP));
        order.setStatus(OrderStatus.FILLED.getCode());
        order.setUpdatedAt(System.currentTimeMillis());

        log.info("Order filled: orderId={}, symbol={}, side={}, qty={}, price={}",
                order.getOrderId(), order.getSymbol(), order.getSide(), fillQty, fillPrice);
    }

    public ExchangeOrder getOrder(String orderId) {
        return orderMap.get(orderId);
    }

    public List<TradeFill> getTradeFills() {
        return new ArrayList<>(tradeFills);
    }
}
