-- 银行自身持仓表：记录银行作为做市商在单个合约上的实时头寸汇总
-- 银行自身头寸与客户成交同步产生（银行是每一笔客户交易的对手方）
-- 银行头寸 = −Σ(客户头寸)，由 trade-event 驱动更新，无需等待对冲成交回报
-- qty 正=银行多头（客户净卖出），负=银行空头（客户净买入）
SET client_encoding TO 'UTF8';

CREATE TABLE IF NOT EXISTS bank_position (
    id BIGINT PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL UNIQUE,
    qty DECIMAL(20,4) NOT NULL DEFAULT 0,
    avg_cost DECIMAL(20,8) DEFAULT 0,
    realized_pnl DECIMAL(20,8) DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

COMMENT ON TABLE bank_position IS '银行自身持仓表：记录银行作为做市商在单个合约上的实时头寸汇总（qty 正=多头，负=空头）';
COMMENT ON COLUMN bank_position.id IS '分布式 ID 主键（应用层 Snowflake 发号器生成）';
COMMENT ON COLUMN bank_position.symbol IS '合约代码';
COMMENT ON COLUMN bank_position.qty IS '银行净持仓（正=多头/客户净卖出，负=空头/客户净买入）';
COMMENT ON COLUMN bank_position.avg_cost IS '持仓均价';
COMMENT ON COLUMN bank_position.realized_pnl IS '已实现盈亏';
COMMENT ON COLUMN bank_position.version IS '乐观锁版本号';
COMMENT ON COLUMN bank_position.created_at IS '创建时间（epoch 毫秒）';
COMMENT ON COLUMN bank_position.updated_at IS '更新时间（epoch 毫秒）';