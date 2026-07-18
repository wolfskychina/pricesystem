-- 客户持仓表：记录每个客户在每个合约上的净持仓与成本
-- qty 正=多头，负=空头；按 (customer_id, symbol) 唯一
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

CREATE TABLE IF NOT EXISTS position (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    qty DECIMAL(20,4) NOT NULL DEFAULT 0,
    avg_cost DECIMAL(20,8) DEFAULT 0,
    realized_pnl DECIMAL(20,8) DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_pos_cust_sym ON position(customer_id, symbol);
CREATE INDEX IF NOT EXISTS idx_pos_customer ON position(customer_id);

COMMENT ON TABLE position IS '客户持仓表：记录每个客户在每个合约上的净持仓与成本（qty 正=多头，负=空头）';
COMMENT ON COLUMN position.id IS '自增主键';
COMMENT ON COLUMN position.customer_id IS '客户 ID';
COMMENT ON COLUMN position.symbol IS '合约代码';
COMMENT ON COLUMN position.qty IS '净持仓数量（正=多头，负=空头）';
COMMENT ON COLUMN position.avg_cost IS '持仓均价';
COMMENT ON COLUMN position.realized_pnl IS '已实现盈亏';
COMMENT ON COLUMN position.version IS '乐观锁版本号';
COMMENT ON COLUMN position.created_at IS '创建时间（epoch 毫秒）';
COMMENT ON COLUMN position.updated_at IS '更新时间（epoch 毫秒）';

-- 对冲持仓表：记录做市商对冲头寸，按合约维度汇总（不区分客户）
-- qty 正=多头对冲，负=空头对冲；按 symbol 唯一
CREATE TABLE IF NOT EXISTS hedge_position (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL UNIQUE,
    qty DECIMAL(20,4) NOT NULL DEFAULT 0,
    avg_cost DECIMAL(20,8) DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    updated_at BIGINT
);

COMMENT ON TABLE hedge_position IS '对冲持仓表：记录做市商对冲头寸，按合约维度汇总（不区分客户）';
COMMENT ON COLUMN hedge_position.id IS '自增主键';
COMMENT ON COLUMN hedge_position.symbol IS '合约代码';
COMMENT ON COLUMN hedge_position.qty IS '净对冲持仓（正=多头对冲，负=空头对冲）';
COMMENT ON COLUMN hedge_position.avg_cost IS '对冲持仓均价';
COMMENT ON COLUMN hedge_position.version IS '乐观锁版本号';
COMMENT ON COLUMN hedge_position.updated_at IS '更新时间（epoch 毫秒）';

-- 已处理事件表：用于幂等去重（Kafka 至少一次投递下保证业务幂等）
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP
);

COMMENT ON TABLE processed_events IS '已处理事件记录（幂等消费去重表）';
COMMENT ON COLUMN processed_events.event_id IS '事件 ID（与 outbox.event_id 对应）';
COMMENT ON COLUMN processed_events.processed_at IS '处理完成时间';
