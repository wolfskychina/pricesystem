-- 客户账户表：客户主数据 + 信用额度 + 已用额度
-- 可用额度 = credit_limit - used_credit
-- 由 account-service 维护，risk-service 事前风控时查询，trade-event 消费时扣减/释放
CREATE TABLE IF NOT EXISTS customer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128),
    level VARCHAR(16) DEFAULT 'NORMAL',
    status VARCHAR(16) DEFAULT 'ACTIVE',
    credit_limit DECIMAL(20,2) NOT NULL DEFAULT 0,
    used_credit DECIMAL(20,2) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_customer_status ON customer(status);
CREATE INDEX IF NOT EXISTS idx_customer_level ON customer(level);

-- 已处理事件表：用于幂等去重（Kafka 至少一次投递下保证业务幂等）
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP
);
