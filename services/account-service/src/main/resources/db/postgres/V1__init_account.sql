-- 客户账户表：客户主数据 + 信用额度 + 已用额度
-- 可用额度 = credit_limit - used_credit
-- 由 account-service 维护，risk-service 事前风控时查询，trade-event 消费时扣减/释放
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

CREATE TABLE IF NOT EXISTS customer (
    id BIGINT PRIMARY KEY,
    customer_id VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128),
    level VARCHAR(16) DEFAULT 'NORMAL',
    status VARCHAR(16) DEFAULT 'ACTIVE',
    credit_limit DECIMAL(20,2) NOT NULL DEFAULT 0,
    used_credit DECIMAL(20,2) NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_customer_status ON customer(status);
CREATE INDEX IF NOT EXISTS idx_customer_level ON customer(level);

COMMENT ON TABLE customer IS '客户账户表：客户主数据 + 信用额度 + 已用额度（可用额度 = credit_limit - used_credit）';
COMMENT ON COLUMN customer.id IS '分布式 ID 主键（应用层 Snowflake 发号器生成）';
COMMENT ON COLUMN customer.customer_id IS '客户唯一标识（业务主键）';
COMMENT ON COLUMN customer.name IS '客户名称';
COMMENT ON COLUMN customer.level IS '客户等级（NORMAL/VIP）';
COMMENT ON COLUMN customer.status IS '客户状态（ACTIVE/FROZEN/CLOSED）';
COMMENT ON COLUMN customer.credit_limit IS '信用额度上限';
COMMENT ON COLUMN customer.used_credit IS '已用额度（成交时扣减，平仓时释放）';
COMMENT ON COLUMN customer.version IS '乐观锁版本号';
COMMENT ON COLUMN customer.created_at IS '创建时间（epoch 毫秒）';
COMMENT ON COLUMN customer.updated_at IS '更新时间（epoch 毫秒）';

-- 已处理事件表：用于幂等去重（Kafka 至少一次投递下保证业务幂等）
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP
);

COMMENT ON TABLE processed_events IS '已处理事件记录（幂等消费去重表）';
COMMENT ON COLUMN processed_events.event_id IS '事件 ID（与 outbox.event_id 对应）';
COMMENT ON COLUMN processed_events.processed_at IS '处理完成时间';
