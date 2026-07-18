CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
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
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_client ON orders(client_order_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

CREATE TABLE IF NOT EXISTS trades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
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
    trade_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trades_order ON trades(order_id);
CREATE INDEX IF NOT EXISTS idx_trades_customer ON trades(customer_id);
