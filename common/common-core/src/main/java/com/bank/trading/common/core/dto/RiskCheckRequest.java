package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 风控检查请求 DTO，用于交易服务向风控服务发起事前风控同步调用。
 *
 * <p>在客户下单后、订单进入撮合前，交易服务会组装本请求同步调用风控服务，
 * 校验该笔订单是否满足风控规则（如持仓限额、单笔限额、客户状态等）。
 * 风控通过后订单才会进入 ACCEPTED 状态参与撮合。</p>
 *
 * <p>风控校验是做市商风控体系的第一道关口，属于<b>事前风控</b>，
 * 与事中风控（实时持仓监控）和事后风控（清算审计）共同构成完整风控链路。</p>
 */
@Data
public class RiskCheckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户 ID，用于查询客户风控额度与持仓 */
    private String customerId;
    /** 合约符号，用于查询合约风控参数 */
    private String symbol;
    /** 订单方向（BUY/SELL），影响持仓方向与敞口计算 */
    private String side;
    /** 订单类型（MARKET/LIMIT），市价单需按当前价估算风控 */
    private String orderType;
    /** 委托数量，用于校验单笔限额与持仓限额 */
    private BigDecimal qty;
    /** 委托价格（限价单有效），用于估算占用保证金 */
    private BigDecimal price;
    /** 市场中间价，用于价格偏离度检查和市价单金额估算 */
    private BigDecimal marketMidPrice;
    /** 已用信用额度 */
    private BigDecimal usedCredit;
    /** 当日已用金额 */
    private BigDecimal dailyUsedAmount;
    /** 当前持仓数量 */
    private BigDecimal currentPosition;
    /** 客户端订单 ID，用于风控日志关联与幂等 */
    private String clientOrderId;
    /** 分布式链路追踪 ID，串联风控调用链路 */
    private String traceId;
}
