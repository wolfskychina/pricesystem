-- 银行自身持仓表：记录银行作为做市商在单个合约上的实时头寸汇总
-- 银行自身头寸与客户成交同步产生（银行是每一笔客户交易的对手方）
-- 银行头寸 = −Σ(客户头寸)，由 trade-event 驱动更新，无需等待对冲成交回报
-- qty 正=银行多头（客户净卖出），负=银行空头（客户净买入）
CREATE TABLE IF NOT EXISTS bank_position (
    id BIGINT PRIMARY KEY,                            -- 分布式 ID 主键（应用层 Snowflake 发号器生成）
    symbol VARCHAR(32) NOT NULL UNIQUE,               -- 合约代码
    qty DECIMAL(20,4) NOT NULL DEFAULT 0,             -- 银行净持仓（正=多头，负=空头）
    avg_cost DECIMAL(20,8) DEFAULT 0,                 -- 持仓均价
    realized_pnl DECIMAL(20,8) DEFAULT 0,             -- 已实现盈亏
    version INT NOT NULL DEFAULT 0,                   -- 乐观锁版本号
    created_at BIGINT,                                -- 创建时间（epoch 毫秒）
    updated_at BIGINT                                 -- 更新时间（epoch 毫秒）
);