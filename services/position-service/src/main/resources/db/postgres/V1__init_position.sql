-- 客户持仓表：记录每个客户在每个合约上的净持仓与成本
-- qty 正=多头，负=空头；按 (customer_id, symbol) 唯一
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

-- 已处理事件表：用于幂等去重（Kafka 至少一次投递下保证业务幂等）
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP
);
