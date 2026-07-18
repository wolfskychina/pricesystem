package com.bank.trading.reconciliation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 对账结果。
 * <p>
 * 包含本次对账的时间范围、各维度检查结果、不一致条目列表、整体结论。
 */
@Data
public class ReconciliationResult {

    /** 对账批次 ID */
    private String batchId;

    /** 对账开始时间（毫秒） */
    private Long startedAt;

    /** 对账结束时间（毫秒） */
    private Long finishedAt;

    /** 客户持仓总额（绝对值，用于额度对比） */
    private BigDecimal totalCustomerPositionValue;

    /** 账户已用额度合计 */
    private BigDecimal totalUsedCredit;

    /** 净敞口绝对值合计 */
    private BigDecimal totalNetExposureAbs;

    /** 额度差异（totalCustomerPositionValue - totalUsedCredit），超过阈值则告警 */
    private BigDecimal creditDelta;

    /** 敞口告警阈值（绝对值） */
    private BigDecimal exposureAlertThreshold;

    /** 额度告警阈值（绝对值） */
    private BigDecimal creditAlertThreshold;

    /** 不一致条目列表 */
    private List<Discrepancy> discrepancies = new ArrayList<>();

    /** 是否整体一致 */
    private boolean consistent;

    /** 备注（人工查阅用） */
    private String remark;

    /**
     * 追加一条不一致记录，并将 consistent 置为 false。
     */
    public void addDiscrepancy(String type, String key, String detail) {
        discrepancies.add(new Discrepancy(type, key, detail));
        consistent = false;
    }

    /**
     * 单条不一致记录。
     */
    @Data
    public static class Discrepancy {
        /** 不一致类型：exposure / credit / data-missing */
        private String type;
        /** 关联键（symbol 或 customerId） */
        private String key;
        /** 详情描述 */
        private String detail;

        public Discrepancy() {
        }

        public Discrepancy(String type, String key, String detail) {
            this.type = type;
            this.key = key;
            this.detail = detail;
        }
    }
}
