package com.bank.trading.simexchange.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.simexchange.engine.MatchingEngine;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.TradeFill;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单 REST 接口。
 * <p>
 * 提供下单、查询订单状态、查询成交记录三类接口，是外部交易策略与模拟交易所交互的入口。
 * <p>
 * <b>异步两阶段模型</b>（对齐真实期货交易所 CTP/Globex 语义）：
 * <ul>
 *   <li>{@link #submitOrder} 同步仅做参数校验并返回状态为 NEW 的订单对象（订单已受理，尚未撮合）。</li>
 *   <li>撮合在异步线程中执行，完成后通过 Webhook 回调推送订单状态回报与成交通知
 *       （模拟 CTP {@code OnRtnOrder} / {@code OnRtnTrade}）。</li>
 *   <li>调用方可通过 {@link #getOrder} 轮询订单状态，或注册 Webhook 接收异步回调。</li>
 * </ul>
 */
@RestController
@RequestMapping("/exchange/orders")
public class OrderController {

    /** 撮合引擎，负责接收订单、撮合成交并维护订单与成交流水 */
    private final MatchingEngine matchingEngine;

    /**
     * 构造函数，通过依赖注入获取撮合引擎。
     *
     * @param matchingEngine 撮合引擎实例
     */
    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    /**
     * 提交订单接口（同步受理阶段）。
     * <p>
     * 业务逻辑：将请求体中的订单字段透传给撮合引擎，由撮合引擎生成订单 ID、
     * 入表（状态=NEW）并提交异步撮合任务。<b>同步返回的订单对象状态为 NEW，不包含成交结果</b>。
     * <p>
     * 成交结果通过以下两种方式获取：
     * <ol>
     *   <li>异步 Webhook 回调（需先调用 {@code POST /exchange/callbacks/register} 注册）</li>
     *   <li>轮询 {@code GET /exchange/orders/{orderId}} 查询订单状态</li>
     * </ol>
     *
     * @param request 订单请求体，包含客户单号、合约、方向、类型、数量、价格等
     * @return 包装了订单对象（状态=NEW）的统一响应
     * @throws IllegalArgumentException 参数校验失败（模拟 CTP 前置同步校验拒绝）
     */
    @PostMapping
    public Result<ExchangeOrder> submitOrder(@RequestBody OrderRequest request) {
        ExchangeOrder order = matchingEngine.submitOrder(
                request.getClientOrderId(),
                request.getSymbol(),
                request.getSide(),
                request.getType(),
                request.getQty(),
                request.getPrice()
        );
        return Result.success(order);
    }

    /**
     * 根据订单 ID 查询订单详情。
     * <p>
     * 业务逻辑：从撮合引擎的订单表中查找。若订单 ID 不存在，返回 404 错误。
     *
     * @param orderId 模拟交易所生成的订单 ID（UUID 去横线格式）
     * @return 包装了订单对象的统一响应；不存在时返回 404 失败响应
     */
    @GetMapping("/{orderId}")
    public Result<ExchangeOrder> getOrder(@PathVariable String orderId) {
        ExchangeOrder order = matchingEngine.getOrder(orderId);
        if (order == null) {
            // 订单不存在，按 404 返回
            return Result.fail(404, "Order not found: " + orderId);
        }
        return Result.success(order);
    }

    /**
     * 查询全部成交流水。
     * <p>
     * 业务逻辑：返回撮合引擎中累计的全部成交记录，按成交时间顺序排列。
     * 用于成交回放、对账与策略效果分析。
     *
     * @return 包装了成交流水列表的统一响应
     */
    @GetMapping("/trades")
    public Result<List<TradeFill>> getTrades() {
        return Result.success(matchingEngine.getTradeFills());
    }

    /**
     * 下单请求体。
     * <p>
     * 作为 {@link #submitOrder} 的请求载荷，承载客户端提交的订单字段。
     * 字段含义与 {@link ExchangeOrder} 对应业务字段一致，但不包含服务端生成的字段
     * （如订单 ID、成交数量、状态等）。
     */
    public static class OrderRequest {
        /** 客户端自定义订单号，用于幂等去重与客户端对账 */
        private String clientOrderId;
        /** 合约代码，例如 {@code EURUSD} */
        private String symbol;
        /** 订单方向，取值见 {@link com.bank.trading.common.core.enums.OrderSide}，如 BUY/SELL */
        private String side;
        /** 订单类型，取值见 {@link com.bank.trading.common.core.enums.OrderType}，如 MARKET/LIMIT */
        private String type;
        /** 委托数量 */
        private BigDecimal qty;
        /** 委托价格；市价单可留空，限价单必填 */
        private BigDecimal price;

        /** 获取客户端订单号 */
        public String getClientOrderId() {
            return clientOrderId;
        }

        /** 设置客户端订单号 */
        public void setClientOrderId(String clientOrderId) {
            this.clientOrderId = clientOrderId;
        }

        /** 获取合约代码 */
        public String getSymbol() {
            return symbol;
        }

        /** 设置合约代码 */
        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        /** 获取订单方向 */
        public String getSide() {
            return side;
        }

        /** 设置订单方向 */
        public void setSide(String side) {
            this.side = side;
        }

        /** 获取订单类型 */
        public String getType() {
            return type;
        }

        /** 设置订单类型 */
        public void setType(String type) {
            this.type = type;
        }

        /** 获取委托数量 */
        public BigDecimal getQty() {
            return qty;
        }

        /** 设置委托数量 */
        public void setQty(BigDecimal qty) {
            this.qty = qty;
        }

        /** 获取委托价格 */
        public BigDecimal getPrice() {
            return price;
        }

        /** 设置委托价格 */
        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}
