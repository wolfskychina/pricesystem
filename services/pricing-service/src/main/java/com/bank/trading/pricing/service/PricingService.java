package com.bank.trading.pricing.service;

import com.bank.trading.common.core.dto.QuoteDTO;
import com.bank.trading.common.core.enums.CustomerLevel;
import com.bank.trading.common.core.event.CustomerQuoteEvent;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.outbox.OutboxService;
import com.bank.trading.pricing.config.PricingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 做市报价服务核心实现类。
 *
 * <p>对标国内期货市场做市商机制，负责接收交易所行情、计算客户买卖报价、
 * 发布报价事件。核心设计包括：
 * <ul>
 *   <li><b>节流控制</b>：同一合约 500ms 内的多次行情变更只触发一次报价重算，
 *       避免震荡行情时高频推送，保护 WebSocket 客户端性能；</li>
 *   <li><b>报价有效期</b>：每条报价 3 秒内有效，超期自动失效，
 *       防止客户用过期报价在快速行情中成交造成滑点损失；</li>
 *   <li><b>点差分层</b>：根据客户等级（机构/VIP/普通）应用不同点差倍率，
 *       高价值客户享受更优报价；</li>
 *   <li><b>事件驱动</b>：行情到达即触发报价计算，通过 Kafka + Outbox 模式
 *       保证报价事件可靠投递。</li>
 * </ul>
 */
@Slf4j
@Service
public class PricingService {

    private final PricingProperties pricingProperties;
    private final EventStoreService eventStoreService;
    private final OutboxService outboxService;

    /** 市场买价缓存：symbol -> bidPrice */
    private final Map<String, BigDecimal> marketBidPrices = new ConcurrentHashMap<>();
    /** 市场卖价缓存：symbol -> askPrice */
    private final Map<String, BigDecimal> marketAskPrices = new ConcurrentHashMap<>();
    /** 最新报价缓存：symbol -> QuoteDTO，用于 REST 查询和有效期校验 */
    private final Map<String, QuoteDTO> latestQuotes = new ConcurrentHashMap<>();
    /** 报价 ID 生成器（原子递增，服务内唯一） */
    private final AtomicLong quoteIdGenerator = new AtomicLong(0);
    /**
     * 报价节流时间缓存：symbol -> 上次报价时间戳（毫秒）。
     * 用于实现 500ms 窗口节流：同一合约在节流窗口内的多次行情变更
     * 只触发一次报价重算，避免高频推送。
     */
    private final Map<String, Long> lastQuoteTime = new ConcurrentHashMap<>();

    /** 点差计算分母：1 个基点（bps）= 0.01% */
    private static final int BPS_DIVISOR = 10000;

    public PricingService(PricingProperties pricingProperties,
                          EventStoreService eventStoreService,
                          OutboxService outboxService) {
        this.pricingProperties = pricingProperties;
        this.eventStoreService = eventStoreService;
        this.outboxService = outboxService;
    }

    /**
     * 接收市场行情数据并触发报价计算。
     *
     * <p>行情数据先更新本地缓存，然后尝试生成报价。
     * 节流逻辑在 {@link #generateQuote(String)} 中实现，确保同一合约
     * 在节流窗口内只生成一次报价。
     *
     * @param symbol   合约代码
     * @param bidPrice 市场买价
     * @param askPrice 市场卖价
     */
    public void onMarketData(String symbol, BigDecimal bidPrice, BigDecimal askPrice) {
        if (symbol == null || bidPrice == null || askPrice == null) {
            return;
        }
        marketBidPrices.put(symbol, bidPrice);
        marketAskPrices.put(symbol, askPrice);
        generateQuote(symbol);
    }

    /**
     * 生成客户报价（带节流控制）。
     *
     * <p><b>节流策略</b>：同一合约在 {@link PricingProperties#getThrottleWindowMs()}
     * 时间窗口内只生成一次报价。若距上次报价时间小于节流窗口，则跳过本次报价，
     * 使用最新行情缓存，等待下一次窗口到期时再重算。
     * 这样可以避免震荡行情中高频推送，保护客户端性能。
     *
     * <p><b>报价有效期</b>：每条报价的有效期由 {@link PricingProperties#getQuoteTtlSeconds()}
     * 配置，默认 3 秒。超期报价在 {@link #getLatestQuote(String)} 中会被判定为无效。
     *
     * @param symbol 合约代码
     */
    private void generateQuote(String symbol) {
        long now = System.currentTimeMillis();

        Long lastTime = lastQuoteTime.putIfAbsent(symbol, now);
        if (lastTime != null && now - lastTime < pricingProperties.getThrottleWindowMs()) {
            log.debug("Throttling quote generation for {} ({}ms since last quote, window={}ms)",
                    symbol, now - lastTime, pricingProperties.getThrottleWindowMs());
            return;
        }
        lastQuoteTime.put(symbol, now);

        BigDecimal marketBid = marketBidPrices.get(symbol);
        BigDecimal marketAsk = marketAskPrices.get(symbol);
        if (marketBid == null || marketAsk == null) {
            return;
        }

        int bidSpreadBps = getBidSpreadBps(symbol);
        int askSpreadBps = getAskSpreadBps(symbol);

        BigDecimal midPrice = marketBid.add(marketAsk).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        BigDecimal customerBid = calculateCustomerBid(midPrice, bidSpreadBps);
        BigDecimal customerAsk = calculateCustomerAsk(midPrice, askSpreadBps);
        BigDecimal spread = customerAsk.subtract(customerBid);

        long quoteId = quoteIdGenerator.incrementAndGet();
        long validUntil = now + pricingProperties.getQuoteTtlSeconds() * 1000L;

        QuoteDTO quote = new QuoteDTO();
        quote.setSymbol(symbol);
        quote.setMarketBidPrice(marketBid);
        quote.setMarketAskPrice(marketAsk);
        quote.setCustomerBidPrice(customerBid);
        quote.setCustomerAskPrice(customerAsk);
        quote.setSpread(spread);
        quote.setQuoteId(quoteId);
        quote.setValidUntil(validUntil);
        quote.setTimestamp(now);

        latestQuotes.put(symbol, quote);

        publishQuoteEvent(quote);
        log.debug("Generated quote for {}: bid={}, ask={}, spread={}, validUntil={}",
                symbol, customerBid, customerAsk, spread, validUntil);
    }

    private BigDecimal calculateCustomerBid(BigDecimal marketAskPrice, int spreadBps) {
        BigDecimal spread = marketAskPrice.multiply(BigDecimal.valueOf(spreadBps))
                .divide(BigDecimal.valueOf(BPS_DIVISOR), 6, RoundingMode.HALF_UP);
        return marketAskPrice.subtract(spread);
    }

    private BigDecimal calculateCustomerAsk(BigDecimal marketBidPrice, int spreadBps) {
        BigDecimal spread = marketBidPrice.multiply(BigDecimal.valueOf(spreadBps))
                .divide(BigDecimal.valueOf(BPS_DIVISOR), 6, RoundingMode.HALF_UP);
        return marketBidPrice.add(spread);
    }

    private int getBidSpreadBps(String symbol) {
        for (PricingProperties.SpreadConfig config : pricingProperties.getSpreadConfigs()) {
            if (symbol.equals(config.getSymbol())) {
                return config.getBidSpreadBps();
            }
        }
        return pricingProperties.getDefaultBidSpreadBps();
    }

    private int getAskSpreadBps(String symbol) {
        for (PricingProperties.SpreadConfig config : pricingProperties.getSpreadConfigs()) {
            if (symbol.equals(config.getSymbol())) {
                return config.getAskSpreadBps();
            }
        }
        return pricingProperties.getDefaultAskSpreadBps();
    }

    /**
     * 发布报价事件到 Kafka（通过 Outbox 模式保证可靠性）。
     *
     * <p>事件同时写入事件溯源存储和发件箱表，由 Outbox 轮询任务
     * 保证至少一次投递到 Kafka。消息体包含完整的报价信息和有效期，
     * 消费方可根据 validUntil 字段判断报价是否有效。
     *
     * @param quote 报价数据
     */
    private void publishQuoteEvent(QuoteDTO quote) {
        try {
            CustomerQuoteEvent event = new CustomerQuoteEvent(quote.getSymbol(), "DEFAULT");
            event.setCustomerBidPrice(quote.getCustomerBidPrice());
            event.setCustomerAskPrice(quote.getCustomerAskPrice());
            event.setMarketBidPrice(quote.getMarketBidPrice());
            event.setMarketAskPrice(quote.getMarketAskPrice());
            event.setSpread(quote.getSpread());
            event.setQuoteId(quote.getQuoteId());
            event.setValidUntil(quote.getValidUntil());

            int shardId = 0;
            eventStoreService.appendEvent(event, "QUOTE", quote.getSymbol(), shardId);
            outboxService.saveEvent(pricingProperties.getQuoteTopic(), event, shardId);
        } catch (Exception e) {
            log.warn("Failed to publish quote event for symbol: {}, error: {}", quote.getSymbol(), e.getMessage());
        }
    }

    /**
     * 获取指定合约的最新有效报价。
     *
     * <p>若报价已超过有效期（validUntil < 当前时间），则返回 null，
     * 表示当前无有效报价，客户端应重新询价（RFQ）。
     *
     * @param symbol 合约代码
     * @return 最新有效报价，若无有效报价则返回 null
     */
    public QuoteDTO getLatestQuote(String symbol) {
        QuoteDTO quote = latestQuotes.get(symbol);
        if (quote == null) {
            return null;
        }
        if (quote.getValidUntil() != null && quote.getValidUntil() < System.currentTimeMillis()) {
            return null;
        }
        return quote;
    }

    /**
     * 生成询价（RFQ - Request For Quote）报价。
     *
     * <p>与被动行情驱动的报价不同，RFQ 是客户主动发起的询价请求，
     * 立即计算并返回针对该客户等级的专属报价。RFQ 报价不受节流限制，
     * 因为询价本身是低频操作，客户有明确成交意向。
     *
     * <p><b>点差分层</b>：根据客户等级应用不同点差倍率：
     * <ul>
     *   <li>机构客户（INSTITUTION）：50% 基础点差</li>
     *   <li>VIP 客户（VIP）：70% 基础点差</li>
     *   <li>普通客户（NORMAL）：100% 基础点差</li>
     * </ul>
     *
     * @param symbol        合约代码
     * @param customerLevel 客户等级
     * @return 专属报价，若无行情数据则返回 null
     */
    public QuoteDTO generateRfqQuote(String symbol, CustomerLevel customerLevel) {
        BigDecimal marketBid = marketBidPrices.get(symbol);
        BigDecimal marketAsk = marketAskPrices.get(symbol);
        if (marketBid == null || marketAsk == null) {
            return null;
        }

        int spreadMultiplier = getSpreadMultiplier(customerLevel);
        int baseBidSpread = getBidSpreadBps(symbol) * spreadMultiplier / 100;
        int baseAskSpread = getAskSpreadBps(symbol) * spreadMultiplier / 100;

        BigDecimal midPrice = marketBid.add(marketAsk).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        BigDecimal customerBid = calculateCustomerBid(midPrice, baseBidSpread);
        BigDecimal customerAsk = calculateCustomerAsk(midPrice, baseAskSpread);
        BigDecimal spread = customerAsk.subtract(customerBid);

        long quoteId = quoteIdGenerator.incrementAndGet();
        long now = System.currentTimeMillis();
        long validUntil = now + pricingProperties.getQuoteTtlSeconds() * 1000L;

        QuoteDTO quote = new QuoteDTO();
        quote.setSymbol(symbol);
        quote.setMarketBidPrice(marketBid);
        quote.setMarketAskPrice(marketAsk);
        quote.setCustomerBidPrice(customerBid);
        quote.setCustomerAskPrice(customerAsk);
        quote.setSpread(spread);
        quote.setQuoteId(quoteId);
        quote.setValidUntil(validUntil);
        quote.setTimestamp(now);

        return quote;
    }

    /**
     * 根据客户等级获取点差倍率（百分比）。
     *
     * <p>倍率以 100 为基准（即 100% = 基础点差），
     * 高价值客户享受更低的点差倍率：
     * <ul>
     *   <li>机构客户：50%（点差减半）</li>
     *   <li>VIP 客户：70%（点差 7 折）</li>
     *   <li>普通客户：100%（标准点差）</li>
     * </ul>
     *
     * @param level 客户等级
     * @return 点差倍率（百分比，如 50 表示 50%）
     */
    private int getSpreadMultiplier(CustomerLevel level) {
        if (level == null) {
            return 100;
        }
        switch (level) {
            case INSTITUTION:
                return 50;
            case VIP:
                return 70;
            case NORMAL:
            default:
                return 100;
        }
    }

    /**
     * 判断指定合约是否有可用的市场行情数据。
     *
     * @param symbol 合约代码
     * @return true 表示有行情数据，false 表示无数据
     */
    public boolean hasMarketData(String symbol) {
        return marketBidPrices.containsKey(symbol) && marketAskPrices.containsKey(symbol);
    }

    /**
     * 获取当前有报价的合约数量。
     *
     * @return 有报价的合约数量
     */
    public int getSymbolCount() {
        return latestQuotes.size();
    }
}
