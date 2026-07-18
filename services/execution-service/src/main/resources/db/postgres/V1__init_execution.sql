-- 对冲订单表：记录做市商向交易所提交的对冲单
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

CREATE TABLE IF NOT EXISTS hedge_orders (
    id BIGSERIAL PRIMARY KEY,
    hedge_order_id VARCHAR(64) NOT NULL UNIQUE,
    exchange_order_id VARCHAR(64),
    original_trade_id VARCHAR(64),
    customer_id VARCHAR(32),
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    type VARCHAR(16) NOT NULL,
    qty DECIMAL(20,4) NOT NULL,
    price DECIMAL(20,4),
    filled_qty DECIMAL(20,4) DEFAULT 0,
    avg_price DECIMAL(20,4) DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_hedge_orders_exchange ON hedge_orders(exchange_order_id);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_status ON hedge_orders(status);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_symbol ON hedge_orders(symbol);

COMMENT ON TABLE hedge_orders IS '对冲订单表：记录做市商向交易所提交的对冲单';
COMMENT ON COLUMN hedge_orders.id IS '自增主键';
COMMENT ON COLUMN hedge_orders.hedge_order_id IS '对冲订单业务 ID';
COMMENT ON COLUMN hedge_orders.exchange_order_id IS '交易所返回的订单 ID';
COMMENT ON COLUMN hedge_orders.original_trade_id IS '触发本次对冲的客户成交 ID';
COMMENT ON COLUMN hedge_orders.customer_id IS '关联客户 ID（聚合对冲时可为空）';
COMMENT ON COLUMN hedge_orders.symbol IS '合约代码';
COMMENT ON COLUMN hedge_orders.side IS '买卖方向（BUY/SELL）';
COMMENT ON COLUMN hedge_orders.type IS '订单类型（MARKET/LIMIT）';
COMMENT ON COLUMN hedge_orders.qty IS '委托数量';
COMMENT ON COLUMN hedge_orders.price IS '委托价格（市价单为空）';
COMMENT ON COLUMN hedge_orders.filled_qty IS '已成交数量';
COMMENT ON COLUMN hedge_orders.avg_price IS '成交均价';
COMMENT ON COLUMN hedge_orders.status IS '订单状态（NEW/ACCEPTED/FILLED/REJECTED）';
COMMENT ON COLUMN hedge_orders.created_at IS '创建时间（epoch 毫秒）';
COMMENT ON COLUMN hedge_orders.updated_at IS '更新时间（epoch 毫秒）';

-- 对冲成交流水表：记录对冲单在交易所的成交结果
CREATE TABLE IF NOT EXISTS hedge_trades (
    id BIGSERIAL PRIMARY KEY,
    hedge_order_id VARCHAR(64) NOT NULL,
    exchange_order_id VARCHAR(64),
    exchange_trade_id VARCHAR(64) NOT NULL UNIQUE,
    original_trade_id VARCHAR(64),
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    qty DECIMAL(20,4) NOT NULL,
    price DECIMAL(20,4) NOT NULL,
    amount DECIMAL(20,4) NOT NULL,
    trade_time BIGINT,
    created_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_hedge_trades_order ON hedge_trades(hedge_order_id);
CREATE INDEX IF NOT EXISTS idx_hedge_trades_exchange ON hedge_trades(exchange_order_id);
CREATE INDEX IF NOT EXISTS idx_hedge_trades_symbol ON hedge_trades(symbol);

COMMENT ON TABLE hedge_trades IS '对冲成交流水表：记录对冲单在交易所的成交结果';
COMMENT ON COLUMN hedge_trades.id IS '自增主键';
COMMENT ON COLUMN hedge_trades.hedge_order_id IS '对应的对冲订单 ID';
COMMENT ON COLUMN hedge_trades.exchange_order_id IS '交易所订单 ID';
COMMENT ON COLUMN hedge_trades.exchange_trade_id IS '交易所成交 ID（用于幂等去重）';
COMMENT ON COLUMN hedge_trades.original_trade_id IS '关联的客户成交 ID';
COMMENT ON COLUMN hedge_trades.symbol IS '合约代码';
COMMENT ON COLUMN hedge_trades.side IS '买卖方向（BUY/SELL）';
COMMENT ON COLUMN hedge_trades.qty IS '成交数量';
COMMENT ON COLUMN hedge_trades.price IS '成交价格';
COMMENT ON COLUMN hedge_trades.amount IS '成交金额（= qty * price）';
COMMENT ON COLUMN hedge_trades.trade_time IS '交易所成交时间（epoch 毫秒）';
COMMENT ON COLUMN hedge_trades.created_at IS '本地记录创建时间（epoch 毫秒）';
