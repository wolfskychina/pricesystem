package com.bank.trading.position.service;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.event.HedgeFillEvent;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.core.idgen.IdGenerator;
import com.bank.trading.common.persistence.idempotent.IdempotentConsumer;
import com.bank.trading.position.entity.HedgePosition;
import com.bank.trading.position.entity.NetExposure;
import com.bank.trading.position.entity.Position;
import com.bank.trading.position.mapper.HedgePositionMapper;
import com.bank.trading.position.mapper.PositionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 持仓管理服务，是 position-service 的核心业务类。
 * <p>
 * 承担三大职责：
 * <ol>
 *   <li><b>客户持仓更新</b>：消费 {@link TradeEvent}，按 (customerId, symbol) 维度
 *       累加客户头寸，计算加权平均成本与已实现盈亏。</li>
 *   <li><b>对冲持仓更新</b>：消费 {@link HedgeFillEvent}，按 symbol 维度累加做市商
 *       对冲头寸。</li>
 *   <li><b>净敞口计算</b>：实时聚合客户总头寸与对冲头寸，输出每个合约的净敞口
 *       （= 客户头寸 − 对冲头寸）。</li>
 * </ol>
 * <p>
 * <b>幂等消费</b>：通过 {@link IdempotentConsumer} + processed_events 表保证同一事件
 * 不被重复处理。事件 ID 取自 {@link com.bank.trading.common.core.event.BaseEvent#getEventId()}，
 * 若事件无 eventId 则回退到业务唯一键（tradeId / hedgeTradeId）。
 * <p>
 * <b>方向语义</b>：
 * <ul>
 *   <li>客户 BUY → position.qty += qty（增加多头）</li>
 *   <li>客户 SELL → position.qty −= qty（减少多头 / 增加空头）</li>
 *   <li>对冲 BUY → hedgePosition.qty += qty（增加对冲多头）</li>
 *   <li>对冲 SELL → hedgePosition.qty −= qty（增加对冲空头）</li>
 * </ul>
 * 注意：对冲方向 = 客户方向（客户 BUY → 做市商建立空头 → 对冲 BUY 平掉空头），
 * 因此 customerPosition.qty 与 hedgePosition.qty 同向变化，相减即得净敞口。
 */
@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    /** 数量与金额的统一小数位 */
    private static final int QTY_SCALE = 4;
    private static final int COST_SCALE = 8;

    private final PositionMapper positionMapper;
    private final HedgePositionMapper hedgePositionMapper;
    private final IdempotentConsumer idempotentConsumer;
    private final IdGenerator idGenerator;

    public PositionService(PositionMapper positionMapper,
                           HedgePositionMapper hedgePositionMapper,
                           IdempotentConsumer idempotentConsumer,
                           IdGenerator idGenerator) {
        this.positionMapper = positionMapper;
        this.hedgePositionMapper = hedgePositionMapper;
        this.idempotentConsumer = idempotentConsumer;
        this.idGenerator = idGenerator;
    }

    // ==================== 客户持仓更新 ====================

    /**
     * 处理客户成交事件，更新客户持仓。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>幂等校验：processed_events 表去重</li>
     *   <li>查询 (customerId, symbol) 现有持仓</li>
     *   <li>按 BUY/SELL 方向更新持仓数量、平均成本、已实现盈亏</li>
     *   <li>持久化（insert 或 update）</li>
     * </ol>
     *
     * @param event 客户成交事件
     */
    @Transactional
    public void onTradeEvent(TradeEvent event) {
        String eventId = resolveEventId(event.getEventId(), event.getTradeId());
        idempotentConsumer.consume(eventId, () -> {
            log.info("Updating customer position: tradeId={}, customerId={}, symbol={}, side={}, qty={}, price={}",
                    event.getTradeId(), event.getCustomerId(), event.getSymbol(),
                    event.getSide(), event.getQty(), event.getPrice());
            applyTradeEvent(event);
            return null;
        });
    }

    /**
     * 应用成交事件到持仓（无幂等校验，由调用方保证）。
     */
    private void applyTradeEvent(TradeEvent event) {
        Position position = positionMapper.findByCustomerAndSymbol(
                event.getCustomerId(), event.getSymbol());
        boolean isNew = (position == null);

        if (isNew) {
            position = new Position();
            position.setId(idGenerator.nextLongId());
            position.setCustomerId(event.getCustomerId());
            position.setSymbol(event.getSymbol());
            position.setQty(BigDecimal.ZERO);
            position.setAvgCost(BigDecimal.ZERO);
            position.setRealizedPnl(BigDecimal.ZERO);
            position.setVersion(0);
            long now = System.currentTimeMillis();
            position.setCreatedAt(now);
            position.setUpdatedAt(now);
        }

        OrderSide side = OrderSide.of(event.getSide());
        BigDecimal signedQty = side == OrderSide.BUY
                ? event.getQty() : event.getQty().negate();
        applyPositionUpdate(position, signedQty, event.getPrice());

        if (isNew) {
            positionMapper.insert(position);
        } else {
            positionMapper.update(position);
        }
        log.info("Customer position updated: customerId={}, symbol={}, newQty={}, avgCost={}, realizedPnl={}",
                position.getCustomerId(), position.getSymbol(),
                position.getQty(), position.getAvgCost(), position.getRealizedPnl());
    }

    // ==================== 对冲持仓更新 ====================

    /**
     * 处理对冲成交事件，更新做市商对冲头寸。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>幂等校验：processed_events 表去重</li>
     *   <li>查询 symbol 现有对冲持仓</li>
     *   <li>按 BUY/SELL 方向累加对冲头寸</li>
     *   <li>持久化（insert 或 update）</li>
     * </ol>
     *
     * @param event 对冲成交事件
     */
    @Transactional
    public void onHedgeFillEvent(HedgeFillEvent event) {
        String eventId = resolveEventId(event.getEventId(), event.getHedgeTradeId());
        idempotentConsumer.consume(eventId, () -> {
            log.info("Updating hedge position: hedgeTradeId={}, symbol={}, side={}, qty={}, price={}",
                    event.getHedgeTradeId(), event.getSymbol(),
                    event.getSide(), event.getQty(), event.getPrice());
            applyHedgeFillEvent(event);
            return null;
        });
    }

    /**
     * 应用对冲成交事件到对冲持仓（无幂等校验，由调用方保证）。
     */
    private void applyHedgeFillEvent(HedgeFillEvent event) {
        HedgePosition hedgePosition = hedgePositionMapper.findBySymbol(event.getSymbol());
        boolean isNew = (hedgePosition == null);

        if (isNew) {
            hedgePosition = new HedgePosition();
            hedgePosition.setId(idGenerator.nextLongId());
            hedgePosition.setSymbol(event.getSymbol());
            hedgePosition.setQty(BigDecimal.ZERO);
            hedgePosition.setAvgCost(BigDecimal.ZERO);
            hedgePosition.setVersion(0);
            hedgePosition.setUpdatedAt(System.currentTimeMillis());
        }

        OrderSide side = OrderSide.of(event.getSide());
        BigDecimal signedQty = side == OrderSide.BUY
                ? event.getQty() : event.getQty().negate();
        applyHedgePositionUpdate(hedgePosition, signedQty, event.getPrice());

        if (isNew) {
            hedgePositionMapper.insert(hedgePosition);
        } else {
            hedgePositionMapper.update(hedgePosition);
        }
        log.info("Hedge position updated: symbol={}, newQty={}, avgCost={}",
                hedgePosition.getSymbol(), hedgePosition.getQty(), hedgePosition.getAvgCost());
    }

    // ==================== 持仓会计算法 ====================

    /**
     * 应用一笔带符号的数量变化到客户持仓，更新 qty、avg_cost、realized_pnl。
     * <p>
     * <b>会计规则</b>（signedQty > 0 表示开多/加多，< 0 表示开空/加空）：
     * <ul>
     *   <li>同向加仓（持仓方向与变化方向相同）：加权平均更新 avg_cost</li>
     *   <li>反向减仓（部分平仓）：avg_cost 不变，realized_pnl += 平仓数量 × 价差</li>
     *   <li>反手（平仓并反向开仓）：先平掉旧头寸结算盈亏，剩余部分以新价格开仓</li>
     *   <li>从零开仓：avg_cost = price</li>
     * </ul>
     *
     * @param position   持仓实体（会被修改）
     * @param signedQty  带符号的数量变化（正=买入，负=卖出）
     * @param price      成交价格
     */
    private void applyPositionUpdate(Position position, BigDecimal signedQty, BigDecimal price) {
        BigDecimal oldQty = position.getQty();
        BigDecimal newQty = oldQty.add(signedQty);
        BigDecimal oldAvgCost = position.getAvgCost();
        BigDecimal realizedPnl = position.getRealizedPnl();

        // 判断是否同向（持仓为 0 时按变化方向视为同向，直接开仓）
        boolean sameDirection = (oldQty.signum() == 0)
                || (oldQty.signum() > 0 && signedQty.signum() > 0)
                || (oldQty.signum() < 0 && signedQty.signum() < 0);

        if (sameDirection) {
            // 同向加仓：加权平均成本
            if (oldQty.signum() == 0) {
                // 从零开仓
                position.setAvgCost(price);
            } else {
                BigDecimal oldNotional = oldQty.abs().multiply(oldAvgCost);
                BigDecimal addNotional = signedQty.abs().multiply(price);
                BigDecimal totalNotional = oldNotional.add(addNotional);
                BigDecimal totalQty = oldQty.abs().add(signedQty.abs());
                position.setAvgCost(totalNotional.divide(totalQty, COST_SCALE, RoundingMode.HALF_UP));
            }
            position.setRealizedPnl(realizedPnl); // 不变
        } else {
            // 反向变化：先平仓，可能反手
            BigDecimal closingQty = signedQty.abs().min(oldQty.abs());
            // 多头平仓：(price - avg_cost) × closingQty；空头平仓：(avg_cost - price) × closingQty
            BigDecimal pnlPerUnit = oldQty.signum() > 0
                    ? price.subtract(oldAvgCost)
                    : oldAvgCost.subtract(price);
            BigDecimal closedPnl = pnlPerUnit.multiply(closingQty);
            position.setRealizedPnl(realizedPnl.add(closedPnl));

            BigDecimal remaining = signedQty.abs().subtract(oldQty.abs());
            if (remaining.signum() > 0) {
                // 反手：剩余部分以新价格开仓
                position.setAvgCost(price);
            } else {
                // 仅减仓，avg_cost 不变
                position.setAvgCost(oldAvgCost);
            }
        }

        position.setQty(newQty.setScale(QTY_SCALE, RoundingMode.HALF_UP));
        position.setUpdatedAt(System.currentTimeMillis());
    }

    /**
     * 应用一笔带符号的数量变化到对冲持仓，更新 qty 与 avg_cost。
     * <p>
     * 对冲持仓不计算 realized_pnl（对冲的目的是管理敞口而非盈利），
     * 会计规则与客户持仓相同，仅省略盈亏结算。
     *
     * @param hedgePosition 对冲持仓实体（会被修改）
     * @param signedQty     带符号的数量变化
     * @param price         成交价格
     */
    private void applyHedgePositionUpdate(HedgePosition hedgePosition,
                                          BigDecimal signedQty, BigDecimal price) {
        BigDecimal oldQty = hedgePosition.getQty();
        BigDecimal newQty = oldQty.add(signedQty);
        BigDecimal oldAvgCost = hedgePosition.getAvgCost();

        boolean sameDirection = (oldQty.signum() == 0)
                || (oldQty.signum() > 0 && signedQty.signum() > 0)
                || (oldQty.signum() < 0 && signedQty.signum() < 0);

        if (sameDirection) {
            if (oldQty.signum() == 0) {
                hedgePosition.setAvgCost(price);
            } else {
                BigDecimal oldNotional = oldQty.abs().multiply(oldAvgCost);
                BigDecimal addNotional = signedQty.abs().multiply(price);
                BigDecimal totalNotional = oldNotional.add(addNotional);
                BigDecimal totalQty = oldQty.abs().add(signedQty.abs());
                hedgePosition.setAvgCost(totalNotional.divide(totalQty, COST_SCALE, RoundingMode.HALF_UP));
            }
        } else {
            BigDecimal remaining = signedQty.abs().subtract(oldQty.abs());
            if (remaining.signum() > 0) {
                // 反手：以新价格开仓
                hedgePosition.setAvgCost(price);
            }
            // 仅减仓：avg_cost 不变
        }

        hedgePosition.setQty(newQty.setScale(QTY_SCALE, RoundingMode.HALF_UP));
        hedgePosition.setUpdatedAt(System.currentTimeMillis());
    }

    // ==================== 查询与敞口计算 ====================

    /**
     * 查询某客户的全部持仓。
     *
     * @param customerId 客户 ID
     * @return 持仓列表
     */
    public List<Position> findByCustomer(String customerId) {
        return positionMapper.findByCustomer(customerId);
    }

    /**
     * 查询某客户某合约的持仓。
     *
     * @param customerId 客户 ID
     * @param symbol     合约代码
     * @return 持仓实体；不存在返回 null
     */
    public Position findByCustomerAndSymbol(String customerId, String symbol) {
        return positionMapper.findByCustomerAndSymbol(customerId, symbol);
    }

    /**
     * 计算所有合约的净敞口。
     * <p>
     * 净敞口 = 客户总头寸 − 对冲头寸。完全对冲时为 0；非零值表示未对冲的敞口。
     * <p>
     * 实现方式：分别按 symbol 聚合客户持仓与对冲持仓，做差集合并。
     *
     * @return 净敞口列表（每个合约一项）
     */
    public List<NetExposure> calculateNetExposure() {
        // 按 symbol 聚合客户头寸
        Map<String, BigDecimal> customerBySymbol = new HashMap<>();
        for (Position p : positionMapper.sumQtyBySymbol()) {
            customerBySymbol.put(p.getSymbol(), p.getQty());
        }

        // 按合约读取对冲头寸
        Map<String, BigDecimal> hedgeBySymbol = new HashMap<>();
        for (HedgePosition hp : hedgePositionMapper.findAll()) {
            hedgeBySymbol.put(hp.getSymbol(), hp.getQty());
        }

        // 合并所有 symbol，计算净敞口
        List<NetExposure> result = new ArrayList<>();
        for (String symbol : unionSymbols(customerBySymbol, hedgeBySymbol)) {
            BigDecimal customer = customerBySymbol.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal hedge = hedgeBySymbol.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal net = customer.subtract(hedge);
            result.add(new NetExposure(symbol, customer, hedge, net));
        }
        return result;
    }

    /**
     * 计算指定合约的净敞口。
     *
     * @param symbol 合约代码
     * @return 净敞口视图；客户与对冲均不存在时返回 null
     */
    public NetExposure calculateNetExposure(String symbol) {
        List<Position> customerPositions = positionMapper.findAll();
        BigDecimal customerQty = customerPositions.stream()
                .filter(p -> symbol.equals(p.getSymbol()))
                .map(Position::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        HedgePosition hedge = hedgePositionMapper.findBySymbol(symbol);
        BigDecimal hedgeQty = (hedge != null) ? hedge.getQty() : BigDecimal.ZERO;

        if (customerQty.signum() == 0 && hedgeQty.signum() == 0) {
            return null;
        }
        return new NetExposure(symbol, customerQty, hedgeQty, customerQty.subtract(hedgeQty));
    }

    private java.util.Set<String> unionSymbols(Map<String, BigDecimal> a, Map<String, BigDecimal> b) {
        java.util.Set<String> symbols = new java.util.TreeSet<>();
        symbols.addAll(a.keySet());
        symbols.addAll(b.keySet());
        return symbols;
    }

    /**
     * 解析事件 ID：优先使用 eventId，否则回退到业务唯一键。
     *
     * @param eventId    事件 ID
     * @param fallbackId 业务唯一键（tradeId 或 hedgeTradeId）
     * @return 用于幂等去重的 ID
     */
    private String resolveEventId(String eventId, String fallbackId) {
        if (eventId != null && !eventId.isEmpty()) {
            return eventId;
        }
        return fallbackId;
    }
}
