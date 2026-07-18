-- 对冲订单表：记录做市商向交易所提交的对冲单
-- 注意：SQLite 数据库文件默认使用 UTF-8 编码，无需显式设置
CREATE TABLE IF NOT EXISTS hedge_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    hedge_order_id VARCHAR(64) NOT NULL UNIQUE,       -- 对冲订单业务 ID
    exchange_order_id VARCHAR(64),                    -- 交易所返回的订单 ID
    original_trade_id VARCHAR(64),                    -- 触发本次对冲的客户成交 ID
    customer_id VARCHAR(32),                          -- 关联客户 ID（聚合对冲时可为空）
    symbol VARCHAR(32) NOT NULL,                      -- 合约代码
    side VARCHAR(8) NOT NULL,                         -- 买卖方向（BUY/SELL）
    type VARCHAR(16) NOT NULL,                        -- 订单类型（MARKET/LIMIT）
    qty DECIMAL(20,4) NOT NULL,                       -- 委托数量
    price DECIMAL(20,4),                              -- 委托价格（市价单为空）
    filled_qty DECIMAL(20,4) DEFAULT 0,               -- 已成交数量
    avg_price DECIMAL(20,4) DEFAULT 0,                -- 成交均价
    status VARCHAR(32) NOT NULL,                      -- 订单状态（NEW/ACCEPTED/FILLED/REJECTED）
    created_at BIGINT,                                -- 创建时间（epoch 毫秒）
    updated_at BIGINT                                 -- 更新时间（epoch 毫秒）
);

CREATE INDEX IF NOT EXISTS idx_hedge_orders_exchange ON hedge_orders(exchange_order_id);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_status ON hedge_orders(status);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_symbol ON hedge_orders(symbol);

-- 对冲成交流水表：记录对冲单在交易所的成交结果
CREATE TABLE IF NOT EXISTS hedge_trades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    hedge_order_id VARCHAR(64) NOT NULL,              -- 对应的对冲订单 ID
    exchange_order_id VARCHAR(64),                    -- 交易所订单 ID
    exchange_trade_id VARCHAR(64) NOT NULL UNIQUE,    -- 交易所成交 ID（用于幂等去重）
    original_trade_id VARCHAR(64),                    -- 关联的客户成交 ID
    symbol VARCHAR(32) NOT NULL,                      -- 合约代码
    side VARCHAR(8) NOT NULL,                         -- 买卖方向（BUY/SELL）
    qty DECIMAL(20,4) NOT NULL,                       -- 成交数量
    price DECIMAL(20,4) NOT NULL,                     -- 成交价格
    amount DECIMAL(20,4) NOT NULL,                    -- 成交金额（= qty * price）
    trade_time BIGINT,                                -- 交易所成交时间（epoch 毫秒）
    created_at BIGINT                                 -- 本地记录创建时间（epoch 毫秒）
);

CREATE INDEX IF NOT EXISTS idx_hedge_trades_order ON hedge_trades(hedge_order_id);
CREATE INDEX IF NOT EXISTS idx_hedge_trades_exchange ON hedge_trades(exchange_order_id);
CREATE INDEX IF NOT EXISTS idx_hedge_trades_symbol ON hedge_trades(symbol);
