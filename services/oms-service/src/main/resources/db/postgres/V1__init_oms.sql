-- OMS-service PostgreSQL 初始化脚本
-- 适配差异：INTEGER PRIMARY KEY AUTOINCREMENT -> BIGSERIAL PRIMARY KEY
--          DATETIME -> TIMESTAMP
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

-- 客户订单表：记录客户下达的订单及撮合状态
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    client_order_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    type VARCHAR(16) NOT NULL,
    qty DECIMAL(20,4) NOT NULL,
    filled_qty DECIMAL(20,4) DEFAULT 0,
    price DECIMAL(20,4),
    avg_price DECIMAL(20,4) DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    reject_reason VARCHAR(256),
    trace_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_client ON orders(client_order_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

COMMENT ON TABLE orders IS '客户订单表：记录客户下达的订单及撮合状态';
COMMENT ON COLUMN orders.id IS '自增主键';
COMMENT ON COLUMN orders.order_id IS '订单业务 ID（系统生成，全局唯一）';
COMMENT ON COLUMN orders.client_order_id IS '客户自定义订单 ID（用于幂等去重）';
COMMENT ON COLUMN orders.customer_id IS '客户 ID';
COMMENT ON COLUMN orders.symbol IS '合约代码';
COMMENT ON COLUMN orders.side IS '买卖方向（BUY/SELL）';
COMMENT ON COLUMN orders.type IS '订单类型（MARKET/LIMIT）';
COMMENT ON COLUMN orders.qty IS '委托数量';
COMMENT ON COLUMN orders.filled_qty IS '已成交数量';
COMMENT ON COLUMN orders.price IS '委托价格（市价单为空）';
COMMENT ON COLUMN orders.avg_price IS '成交均价';
COMMENT ON COLUMN orders.status IS '订单状态（NEW/FILLED/REJECTED/CANCELLED）';
COMMENT ON COLUMN orders.reject_reason IS '拒单原因（风控拒绝时填充）';
COMMENT ON COLUMN orders.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN orders.created_at IS '创建时间';
COMMENT ON COLUMN orders.updated_at IS '更新时间';

-- 客户成交流水表：记录客户订单的成交结果
CREATE TABLE IF NOT EXISTS trades (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL,
    client_order_id VARCHAR(64),
    customer_id VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    qty DECIMAL(20,4) NOT NULL,
    price DECIMAL(20,4) NOT NULL,
    amount DECIMAL(20,4) NOT NULL,
    trade_type VARCHAR(16) NOT NULL,
    trade_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trades_order ON trades(order_id);
CREATE INDEX IF NOT EXISTS idx_trades_customer ON trades(customer_id);

COMMENT ON TABLE trades IS '客户成交流水表：记录客户订单的成交结果';
COMMENT ON COLUMN trades.id IS '自增主键';
COMMENT ON COLUMN trades.trade_id IS '成交业务 ID（系统生成，全局唯一）';
COMMENT ON COLUMN trades.order_id IS '关联订单 ID';
COMMENT ON COLUMN trades.client_order_id IS '关联客户自定义订单 ID';
COMMENT ON COLUMN trades.customer_id IS '客户 ID';
COMMENT ON COLUMN trades.symbol IS '合约代码';
COMMENT ON COLUMN trades.side IS '买卖方向（BUY/SELL）';
COMMENT ON COLUMN trades.qty IS '成交数量';
COMMENT ON COLUMN trades.price IS '成交价格';
COMMENT ON COLUMN trades.amount IS '成交金额（= qty * price）';
COMMENT ON COLUMN trades.trade_type IS '成交类型（如 NORMAL/BAI）';
COMMENT ON COLUMN trades.trade_time IS '成交时间';
