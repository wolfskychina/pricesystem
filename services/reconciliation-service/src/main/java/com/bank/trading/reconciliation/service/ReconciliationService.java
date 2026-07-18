package com.bank.trading.reconciliation.service;

import com.bank.trading.reconciliation.client.DownstreamClient;
import com.bank.trading.reconciliation.dto.ReconciliationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 对账核心服务。
 * <p>
 * <b>对账维度</b>：
 * <ol>
 *   <li><b>敞口对账</b>：遍历每个 symbol 的净敞口，若 |netExposure| &gt; threshold 则记录 discrepancy</li>
 *   <li><b>额度对账</b>：sum(usedCredit) vs sum(|customerPosition|)，差异超过阈值则告警</li>
 *   <li><b>对冲挂单对账</b>：检查 status=NEW 的对冲订单数（说明对冲延迟，需关注）</li>
 * </ol>
 * <p>
 * 对账不修改任何业务状态，仅生成只读报告。补偿动作由人工或专门脚本触发。
 */
@Slf4j
@Service
public class ReconciliationService {

    private final DownstreamClient downstream;

    @Value("${reconciliation.exposure-alert-threshold:5}")
    private BigDecimal exposureAlertThreshold;

    @Value("${reconciliation.credit-alert-threshold:100}")
    private BigDecimal creditAlertThreshold;

    @Value("${reconciliation.hedge-order-check-limit:100}")
    private int hedgeOrderCheckLimit;

    public ReconciliationService(DownstreamClient downstream) {
        this.downstream = downstream;
    }

    /**
     * 执行一次完整对账。
     *
     * @return 对账结果
     */
    public ReconciliationResult reconcile() {
        ReconciliationResult result = new ReconciliationResult();
        result.setBatchId("REC-" + UUID.randomUUID().toString().substring(0, 8));
        result.setStartedAt(System.currentTimeMillis());
        result.setConsistent(true);   // 默认一致，发现 discrepancy 后翻为 false
        result.setExposureAlertThreshold(exposureAlertThreshold);
        result.setCreditAlertThreshold(creditAlertThreshold);

        BigDecimal totalCustomerPositionAbs = BigDecimal.ZERO;
        BigDecimal totalNetExposureAbs = BigDecimal.ZERO;

        // 1. 敞口对账
        List<DownstreamClient.ExposureSnapshot> exposures = downstream.fetchNetExposure();
        if (exposures.isEmpty()) {
            result.addDiscrepancy("data-missing", "exposure",
                    "position-service 未返回敞口数据或服务不可达");
        } else {
            for (DownstreamClient.ExposureSnapshot e : exposures) {
                if (e.netExposure == null) {
                    continue;
                }
                BigDecimal absNet = e.netExposure.abs();
                totalNetExposureAbs = totalNetExposureAbs.add(absNet);
                if (e.customerPosition != null) {
                    totalCustomerPositionAbs = totalCustomerPositionAbs.add(e.customerPosition.abs());
                }
                if (absNet.compareTo(exposureAlertThreshold) > 0) {
                    result.addDiscrepancy("exposure", e.symbol,
                            String.format("净敞口 %s 超过阈值 %s（customer=%s, hedge=%s）",
                                    e.netExposure, exposureAlertThreshold,
                                    e.customerPosition, e.hedgePosition));
                }
            }
        }

        // 2. 额度对账
        List<DownstreamClient.AccountSnapshot> accounts = downstream.fetchAccounts();
        BigDecimal totalUsedCredit = BigDecimal.ZERO;
        if (accounts.isEmpty()) {
            result.addDiscrepancy("data-missing", "accounts",
                    "account-service 未返回账户数据或服务不可达");
        } else {
            for (DownstreamClient.AccountSnapshot a : accounts) {
                if (a.usedCredit != null) {
                    totalUsedCredit = totalUsedCredit.add(a.usedCredit);
                }
            }
        }
        BigDecimal creditDelta = totalCustomerPositionAbs.subtract(totalUsedCredit);
        result.setTotalCustomerPositionValue(totalCustomerPositionAbs);
        result.setTotalUsedCredit(totalUsedCredit);
        result.setTotalNetExposureAbs(totalNetExposureAbs);
        result.setCreditDelta(creditDelta);
        if (creditDelta.abs().compareTo(creditAlertThreshold) > 0) {
            result.addDiscrepancy("credit", "AGGREGATE",
                    String.format("额度差异 %s 超过阈值 %s（持仓占用=%s, 账户已用=%s）",
                            creditDelta, creditAlertThreshold,
                            totalCustomerPositionAbs, totalUsedCredit));
        }

        // 3. 对冲挂单对账（NEW 状态的对冲单表示延迟未成交）
        List<DownstreamClient.HedgeOrderSnapshot> hedgeOrders =
                downstream.fetchRecentHedgeOrders(hedgeOrderCheckLimit);
        long pendingHedgeCount = hedgeOrders.stream()
                .filter(o -> "NEW".equalsIgnoreCase(o.status))
                .count();
        if (pendingHedgeCount > 0) {
            result.addDiscrepancy("hedge-pending", "AGGREGATE",
                    String.format("有 %d 笔对冲订单处于 NEW 状态（可能延迟未成交）", pendingHedgeCount));
        }

        result.setFinishedAt(System.currentTimeMillis());
        if (result.isConsistent()) {
            result.setRemark("所有对账项一致");
            log.info("Reconciliation {} PASS: exposures={}, accounts={}, delta={}",
                    result.getBatchId(), exposures.size(), accounts.size(), creditDelta);
        } else {
            result.setRemark(String.format("发现 %d 条不一致项", result.getDiscrepancies().size()));
            log.warn("Reconciliation {} FAIL: discrepancies={}",
                    result.getBatchId(), result.getDiscrepancies().size());
        }
        return result;
    }
}
