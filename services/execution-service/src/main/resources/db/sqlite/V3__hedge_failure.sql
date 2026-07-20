-- 对冲失败应对方案：新增表与字段

-- 1. 修改 hedge_orders 表：新增重试相关字段
ALTER TABLE hedge_orders ADD COLUMN retry_count INTEGER DEFAULT 0;
ALTER TABLE hedge_orders ADD COLUMN next_retry_at BIGINT;
ALTER TABLE hedge_orders ADD COLUMN hedge_channel VARCHAR(32) DEFAULT 'PRIMARY';
ALTER TABLE hedge_orders ADD COLUMN failure_reason TEXT;

-- 2. 修改 hedge_batch_items 表：新增重试相关字段
ALTER TABLE hedge_batch_items ADD COLUMN retry_count INTEGER DEFAULT 0;
ALTER TABLE hedge_batch_items ADD COLUMN failure_reason TEXT;

-- 3. 新增失败敞口跟踪表：记录等待对冲的失败敞口
CREATE TABLE IF NOT EXISTS hedge_failure_exposure (
    id BIGINT PRIMARY KEY,                            -- 分布式 ID 主键（应用层 Snowflake 发号器生成）
    customer_id VARCHAR(32) NOT NULL,                 -- 客户 ID
    symbol VARCHAR(32) NOT NULL,                      -- 合约代码
    side VARCHAR(8) NOT NULL,                         -- 对冲方向（与客户成交同向）
    pending_qty DECIMAL(20,4) NOT NULL,              -- 等待对冲的数量
    exposure_amount DECIMAL(22,2) NOT NULL,          -- 敞口金额（qty × 当前价格）
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',   -- PENDING/HEDGED/EMERGENCY_CLOSED/EXPIRED
    retry_count INTEGER NOT NULL DEFAULT 0,           -- 已重试次数
    last_retry_at BIGINT,                             -- 上次重试时间
    original_trade_id VARCHAR(64) NOT NULL,          -- 关联客户成交 trade_id
    hedge_order_id VARCHAR(64),                       -- 关联对冲订单
    created_at BIGINT NOT NULL,                       -- 创建时间
    resolved_at BIGINT                                -- 解决时间
);

CREATE INDEX IF NOT EXISTS idx_hfe_status ON hedge_failure_exposure(status, created_at);
CREATE INDEX IF NOT EXISTS idx_hfe_symbol ON hedge_failure_exposure(symbol, status);
CREATE INDEX IF NOT EXISTS idx_hfe_trade ON hedge_failure_exposure(original_trade_id);

-- 4. 新增死信队列表：重试耗尽后的终极兜底
CREATE TABLE IF NOT EXISTS hedge_dlq (
    id BIGINT PRIMARY KEY,                            -- 分布式 ID 主键（应用层 Snowflake 发号器生成）
    hedge_order_id VARCHAR(64) NOT NULL,              -- 关联对冲订单
    original_trade_id VARCHAR(64) NOT NULL,           -- 关联客户成交
    customer_id VARCHAR(32),                          -- 客户 ID
    symbol VARCHAR(32),                               -- 合约代码
    side VARCHAR(8),                                  -- 对冲方向
    qty DECIMAL(20,4),                                -- 委托数量
    reason TEXT,                                      -- 失败原因
    retry_count INTEGER NOT NULL DEFAULT 0,           -- 已重试次数
    max_retry_count INTEGER NOT NULL DEFAULT 3,       -- 最大补偿重试次数
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',   -- PENDING/RECOVERED/FAILED
    created_at BIGINT NOT NULL,                       -- 创建时间
    recovered_at BIGINT                               -- 恢复时间
);

CREATE INDEX IF NOT EXISTS idx_dlq_status ON hedge_dlq(status, created_at);
CREATE INDEX IF NOT EXISTS idx_dlq_order ON hedge_dlq(hedge_order_id);
