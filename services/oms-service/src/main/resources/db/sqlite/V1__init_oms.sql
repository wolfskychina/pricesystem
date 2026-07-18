-- OMS-service SQLite 初始化脚本
-- 注意：SQLite 数据库文件默认使用 UTF-8 编码，无需显式设置

-- 客户订单表：记录客户下达的订单及撮合状态
CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    order_id VARCHAR(64) NOT NULL UNIQUE,             -- 订单业务 ID（系统生成，全局唯一）
    client_order_id VARCHAR(64) NOT NULL,             -- 客户自定义订单 ID（用于幂等去重）
    customer_id VARCHAR(32) NOT NULL,                 -- 客户 ID
    symbol VARCHAR(32) NOT NULL,                      -- 合约代码
    side VARCHAR(8) NOT NULL,                         -- 买卖方向（BUY/SELL）
    type VARCHAR(16) NOT NULL,                        -- 订单类型（MARKET/LIMIT）
    qty DECIMAL(20,4) NOT NULL,                       -- 委托数量
    filled_qty DECIMAL(20,4) DEFAULT 0,               -- 已成交数量
    price DECIMAL(20,4),                              -- 委托价格（市价单为空）
    avg_price DECIMAL(20,4) DEFAULT 0,                -- 成交均价
    status VARCHAR(32) NOT NULL,                      -- 订单状态（NEW/FILLED/REJECTED/CANCELLED）
    reject_reason VARCHAR(256),                       -- 拒单原因（风控拒绝时填充）
    trace_id VARCHAR(64),                             -- 链路追踪 ID
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,    -- 创建时间
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP     -- 更新时间
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_client ON orders(client_order_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

-- 客户成交流水表：记录客户订单的成交结果
CREATE TABLE IF NOT EXISTS trades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    trade_id VARCHAR(64) NOT NULL UNIQUE,             -- 成交业务 ID（系统生成，全局唯一）
    order_id VARCHAR(64) NOT NULL,                    -- 关联订单 ID
    client_order_id VARCHAR(64),                      -- 关联客户自定义订单 ID
    customer_id VARCHAR(32) NOT NULL,                 -- 客户 ID
    symbol VARCHAR(32) NOT NULL,                      -- 合约代码
    side VARCHAR(8) NOT NULL,                         -- 买卖方向（BUY/SELL）
    qty DECIMAL(20,4) NOT NULL,                       -- 成交数量
    price DECIMAL(20,4) NOT NULL,                     -- 成交价格
    amount DECIMAL(20,4) NOT NULL,                    -- 成交金额（= qty * price）
    trade_type VARCHAR(16) NOT NULL,                  -- 成交类型（如 NORMAL/BAI）
    trade_time DATETIME DEFAULT CURRENT_TIMESTAMP     -- 成交时间
);

CREATE INDEX IF NOT EXISTS idx_trades_order ON trades(order_id);
CREATE INDEX IF NOT EXISTS idx_trades_customer ON trades(customer_id);
