-- 客户账户表：客户主数据 + 信用额度 + 已用额度
-- 可用额度 = credit_limit - used_credit
-- 由 account-service 维护，risk-service 事前风控时查询，trade-event 消费时扣减/释放
-- 注意：SQLite 数据库文件默认使用 UTF-8 编码，无需显式设置
CREATE TABLE IF NOT EXISTS customer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    customer_id VARCHAR(32) NOT NULL UNIQUE,          -- 客户唯一标识（业务主键）
    name VARCHAR(128),                                -- 客户名称
    level VARCHAR(16) DEFAULT 'NORMAL',               -- 客户等级（NORMAL/VIP）
    status VARCHAR(16) DEFAULT 'ACTIVE',              -- 客户状态（ACTIVE/FROZEN/CLOSED）
    credit_limit DECIMAL(20,2) NOT NULL DEFAULT 0,    -- 信用额度上限
    used_credit DECIMAL(20,2) NOT NULL DEFAULT 0,     -- 已用额度（成交时扣减，平仓时释放）
    version INT NOT NULL DEFAULT 0,                   -- 乐观锁版本号
    created_at BIGINT,                                -- 创建时间（epoch 毫秒）
    updated_at BIGINT                                 -- 更新时间（epoch 毫秒）
);

CREATE INDEX IF NOT EXISTS idx_customer_status ON customer(status);
CREATE INDEX IF NOT EXISTS idx_customer_level ON customer(level);

-- 已处理事件表：用于幂等去重（Kafka 至少一次投递下保证业务幂等）
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,    -- 事件 ID（与 outbox.event_id 对应）
    processed_at TIMESTAMP               -- 处理完成时间
);
