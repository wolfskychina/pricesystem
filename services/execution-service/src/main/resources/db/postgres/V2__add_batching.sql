-- 对冲聚合子项表：记录聚合对冲订单与每笔原始客户成交的对应关系
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

CREATE TABLE IF NOT EXISTS hedge_batch_items (
    id BIGINT PRIMARY KEY,
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

COMMENT ON TABLE hedge_batch_items IS '对冲聚合子项表：记录聚合对冲订单与每笔原始客户成交的对应关系';
COMMENT ON COLUMN hedge_batch_items.id IS '分布式 ID 主键（应用层 Snowflake 发号器生成）';
COMMENT ON COLUMN hedge_batch_items.hedge_order_id IS '归属的聚合对冲订单 ID（出桶后填充）';
COMMENT ON COLUMN hedge_batch_items.original_trade_id IS '原始客户成交 ID（用于幂等去重）';
COMMENT ON COLUMN hedge_batch_items.customer_id IS '客户 ID';
COMMENT ON COLUMN hedge_batch_items.symbol IS '合约代码';
COMMENT ON COLUMN hedge_batch_items.side IS '买卖方向（BUY/SELL）';
COMMENT ON COLUMN hedge_batch_items.qty IS '客户成交数量';
COMMENT ON COLUMN hedge_batch_items.status IS '子项状态（PENDING/HEDGED/FILLED）';
COMMENT ON COLUMN hedge_batch_items.filled_qty IS '已对冲成交数量';
COMMENT ON COLUMN hedge_batch_items.avg_price IS '对冲成交均价';
COMMENT ON COLUMN hedge_batch_items.created_at IS '创建时间（epoch 毫秒）';
COMMENT ON COLUMN hedge_batch_items.updated_at IS '更新时间（epoch 毫秒）';

-- 对冲订单表增加聚合相关字段
ALTER TABLE hedge_orders ADD COLUMN IF NOT EXISTS is_batched INTEGER DEFAULT 0;
ALTER TABLE hedge_orders ADD COLUMN IF NOT EXISTS batch_item_count INTEGER DEFAULT 0;

COMMENT ON COLUMN hedge_orders.is_batched IS '是否为聚合对冲单（0=普通单，1=聚合单）';
COMMENT ON COLUMN hedge_orders.batch_item_count IS '聚合子项数量';
