-- 对冲聚合子项表：记录聚合对冲订单与每笔原始客户成交的对应关系
CREATE TABLE IF NOT EXISTS hedge_batch_items (
    id BIGSERIAL PRIMARY KEY,
    hedge_order_id VARCHAR(64),
    original_trade_id VARCHAR(64) NOT NULL UNIQUE,
    customer_id VARCHAR(32),
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    qty DECIMAL(20,4) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    filled_qty DECIMAL(20,4) DEFAULT 0,
    avg_price DECIMAL(20,4) DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_batch_items_order ON hedge_batch_items(hedge_order_id);
CREATE INDEX IF NOT EXISTS idx_batch_items_trade ON hedge_batch_items(original_trade_id);
CREATE INDEX IF NOT EXISTS idx_batch_items_symbol ON hedge_batch_items(symbol);
CREATE INDEX IF NOT EXISTS idx_batch_items_status ON hedge_batch_items(status);

-- 对冲订单表增加聚合相关字段
ALTER TABLE hedge_orders ADD COLUMN IF NOT EXISTS is_batched INTEGER DEFAULT 0;
ALTER TABLE hedge_orders ADD COLUMN IF NOT EXISTS batch_item_count INTEGER DEFAULT 0;
