package com.bank.trading.position.monitor;

import com.bank.trading.position.entity.HedgePosition;
import com.bank.trading.position.mapper.HedgePositionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存监控器，定时检查对冲持仓是否超过阈值，超阈值时触发告警。
 * <p>
 * 对冲持仓（库存）是系统在交易所累积的对冲单净头寸。由于当前采用增量对冲策略，
 * 每次对冲只对当前批次净敞口下单，不考虑历史对冲持仓，导致对冲持仓随时间累积。
 * 当对冲持仓超过设定的库存上限时，本监控器会记录告警日志，供运维人员关注。
 * <p>
 * <b>告警触发条件</b>：任一合约的 |对冲持仓| > {@code position.hedge-inventory-cap}
 * <p>
 * <b>运行时行为</b>：
 * <ul>
 *   <li>定时扫描所有合约的对冲持仓</li>
 *   <li>超阈值时记录 WARN 日志（含 symbol、当前持仓、阈值）</li>
 *   <li>不自动减仓，由运维人员根据告警决定是否人工介入</li>
 * </ul>
 */
@Component
public class InventoryMonitor {

    private static final Logger log = LoggerFactory.getLogger(InventoryMonitor.class);

    private final HedgePositionMapper hedgePositionMapper;

    /**
     * 对冲持仓库存上限（手），绝对值超过此阈值时触发告警。
     * 默认 100 手，可通过 position.hedge-inventory-cap 配置。
     */
    @Value("${position.hedge-inventory-cap:100}")
    private BigDecimal hedgeInventoryCap;

    public InventoryMonitor(HedgePositionMapper hedgePositionMapper) {
        this.hedgePositionMapper = hedgePositionMapper;
    }

    /**
     * 定时检查所有合约的对冲持仓是否超过库存上限。
     * <p>
     * 默认每 60 秒执行一次，可通过 position.inventory-check-interval-ms 配置。
     */
    @Scheduled(fixedRateString = "${position.inventory-check-interval-ms:60000}")
    public void checkInventory() {
        List<HedgePosition> positions = hedgePositionMapper.findAll();
        if (positions.isEmpty()) {
            return;
        }

        int exceededCount = 0;
        for (HedgePosition pos : positions) {
            BigDecimal absQty = pos.getQty().abs();
            if (absQty.compareTo(hedgeInventoryCap) > 0) {
                exceededCount++;
                log.warn("Hedge inventory exceeds cap: symbol={}, currentQty={}, cap={}, " +
                                "exceedBy={}",
                        pos.getSymbol(), pos.getQty(), hedgeInventoryCap,
                        absQty.subtract(hedgeInventoryCap));
            }
        }

        if (exceededCount > 0) {
            log.warn("Inventory check completed: {} of {} symbols exceed hedge inventory cap ({})",
                    exceededCount, positions.size(), hedgeInventoryCap);
        } else {
            log.debug("Inventory check completed: all {} symbols within hedge inventory cap ({})",
                    positions.size(), hedgeInventoryCap);
        }
    }
}