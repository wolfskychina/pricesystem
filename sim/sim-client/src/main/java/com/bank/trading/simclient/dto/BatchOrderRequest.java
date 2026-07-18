package com.bank.trading.simclient.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量下单请求。
 * <p>
 * 当不指定具体订单列表时，sim-client 将根据参数自动生成 N 笔随机订单。
 */
@Data
public class BatchOrderRequest {

    /** 目标 OMS/Gateway 的下单接口 URL，为空时使用默认配置 sim-client.target-orders-url */
    private String targetOrdersUrl;

    /** 模式：RANDOM（随机生成）或 FIXED（使用 orders 列表） */
    private String mode = "RANDOM";

    /** 随机模式下生成的订单数量 */
    private int count = 10;

    /** 随机模式下可选的客户 ID 列表（为空时使用 SIM-CUST-001 ~ 005） */
    private List<String> customerIds;

    /** 随机模式下可选的合约代码列表（为空时使用 AU2406/AG2406/CU2406/RB2406） */
    private List<String> symbols;

    /** 随机模式下订单方向权重：BUY/SELL 各占 50% */
    private boolean randomSide = true;

    /** 随机模式下每笔订单数量上限（实际在 [1, maxQty] 区间随机） */
    private int maxQty = 10;

    /** FIXED 模式下要提交的订单列表 */
    private List<com.bank.trading.common.core.dto.OrderCreateDTO> orders;

    /** 每笔提交间隔（毫秒），用于压测时控制速率 */
    private long intervalMs = 0;
}
