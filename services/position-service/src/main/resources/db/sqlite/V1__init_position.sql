-- 客户持仓表：记录每个客户在每个合约上的净持仓与成本
-- qty 正=多头，负=空头；按 (customer_id, symbol) 唯一
-- 注意：SQLite 数据库文件默认使用 UTF-8 编码，无需显式设置
CREATE TABLE IF NOT EXISTS position (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    customer_id VARCHAR(32) NOT NULL,                 -- 客户 ID
    symbol VARCHAR(32) NOT NULL,                      -- 合约代码
    qty DECIMAL(20,4) NOT NULL DEFAULT 0,             -- 净持仓数量（正=多头，负=空头）
    avg_cost DECIMAL(20,8) DEFAULT 0,                 -- 持仓均价
    realized_pnl DECIMAL(20,8) DEFAULT 0,             -- 已实现盈亏
    version INT NOT NULL DEFAULT 0,                   -- 乐观锁版本号
    created_at BIGINT,                                -- 创建时间（epoch 毫秒）
    updated_at BIGINT                                 -- 更新时间（epoch 毫秒）
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_pos_cust_sym ON position(customer_id, symbol);
CREATE INDEX IF NOT EXISTS idx_pos_customer ON position(customer_id);

-- 对冲持仓表：记录做市商对冲头寸，按合约维度汇总（不区分客户）
-- qty 正=多头对冲，负=空头对冲；按 symbol 唯一
CREATE TABLE IF NOT EXISTS hedge_position (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    symbol VARCHAR(32) NOT NULL UNIQUE,               -- 合约代码
    qty DECIMAL(20,4) NOT NULL DEFAULT 0,             -- 净对冲持仓（正=多头对冲，负=空头对冲）
    avg_cost DECIMAL(20,8) DEFAULT 0,                 -- 对冲持仓均价
    version INT NOT NULL DEFAULT 0,                   -- 乐观锁版本号
    updated_at BIGINT                                 -- 更新时间（epoch 毫秒）
);

-- 已处理事件表：用于幂等去重（Kafka 至少一次投递下保证业务幂等）
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,    -- 事件 ID（与 outbox.event_id 对应）
    processed_at TIMESTAMP               -- 处理完成时间
);
