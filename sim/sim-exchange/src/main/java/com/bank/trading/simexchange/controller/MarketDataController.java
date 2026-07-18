package com.bank.trading.simexchange.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.simexchange.engine.MarketDataEngine;
import com.bank.trading.simexchange.model.MarketData;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 行情查询 REST 接口。
 * <p>
 * 提供按合约查询当前行情快照、列出全部合约行情以及列出可交易合约清单等只读接口，
 * 供下游交易系统、策略平台或前端按需"拉取"最新行情。与 WebSocket 广播形成互补：
 * WebSocket 用于实时推送，REST 用于按需查询与初始化加载。
 * <p>
 * 所有接口统一返回 {@link Result} 包装结构，便于上游系统统一处理成功/失败。
 */
@RestController
@RequestMapping("/exchange/marketdata")
public class MarketDataController {

    /** 行情引擎，提供最新行情快照与合约清单查询能力 */
    private final MarketDataEngine marketDataEngine;

    /**
     * 构造函数，通过依赖注入获取行情引擎。
     *
     * @param marketDataEngine 行情引擎实例
     */
    public MarketDataController(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    /**
     * 查询指定合约的最新行情快照。
     * <p>
     * 业务逻辑：从行情引擎获取该合约最近一次 tick 生成的盘口数据。若合约不存在
     * 或尚未生成行情，返回 404 错误。
     *
     * @param symbol 合约代码，例如 {@code EURUSD}
     * @return 包装了 {@link MarketData} 的统一响应；不存在时返回 404 失败响应
     */
    @GetMapping("/{symbol}")
    public Result<MarketData> getMarketData(@PathVariable String symbol) {
        MarketData md = marketDataEngine.getLatest(symbol);
        if (md == null) {
            // 合约不存在或尚未生成行情，按 404 返回
            return Result.fail(404, "Symbol not found: " + symbol);
        }
        return Result.success(md);
    }

    /**
     * 列出全部合约的最新行情快照。
     * <p>
     * 业务逻辑：遍历行情引擎中所有合约代码，逐一取出最新行情，过滤掉尚未生成
     * 行情的合约（理论上极少出现，但作为防御性处理）。
     *
     * @return 包装了行情列表的统一响应
     */
    @GetMapping("/list")
    public Result<List<MarketData>> listMarketData() {
        List<MarketData> list = marketDataEngine.getSymbols().stream()
                .map(marketDataEngine::getLatest)
                .filter(md -> md != null)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 列出当前模拟交易所支持的全部合约代码。
     * <p>
     * 通常供客户端在启动时拉取合约清单，再针对感兴趣的合约订阅行情或下单。
     *
     * @return 包装了合约代码字符串列表的统一响应
     */
    @GetMapping("/symbols")
    public Result<List<String>> listSymbols() {
        return Result.success(marketDataEngine.getSymbols());
    }
}
