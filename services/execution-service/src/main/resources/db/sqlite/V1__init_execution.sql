-- 对冲订单表：记录做市商向交易所提交的对冲单
CREATE TABLE IF NOT EXISTS hedge_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
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

-- 对冲成交流水表：记录对冲单在交易所的成交结果
CREATE TABLE IF NOT EXISTS hedge_trades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
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
