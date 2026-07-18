-- 对冲聚合子项表：记录聚合对冲订单与每笔原始客户成交的对应关系
-- 注意：SQLite 数据库文件默认使用 UTF-8 编码，无需显式设置
CREATE TABLE IF NOT EXISTS hedge_batch_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,             -- 自增主键
    hedge_order_id VARCHAR(64),                       -- 归属的聚合对冲订单 ID（出桶后填充）
    original_trade_id VARCHAR(64) NOT NULL UNIQUE,    -- 原始客户成交 ID（用于幂等去重）
    customer_id VARCHAR(32),                          -- 客户 ID
    symbol VARCHAR(32) NOT NULL,                      -- 合约代码
    side VARCHAR(8) NOT NULL,                         -- 买卖方向（BUY/SELL）
    qty DECIMAL(20,4) NOT NULL,                       -- 客户成交数量
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',    -- 子项状态（PENDING/HEDGED/FILLED）
    filled_qty DECIMAL(20,4) DEFAULT 0,               -- 已对冲成交数量
    avg_price DECIMAL(20,4) DEFAULT 0,                -- 对冲成交均价
    created_at BIGINT,                                -- 创建时间（epoch 毫秒）
    updated_at BIGINT                                 -- 更新时间（epoch 毫秒）
);

CREATE INDEX IF NOT EXISTS idx_batch_items_order ON hedge_batch_items(hedge_order_id);
CREATE INDEX IF NOT EXISTS idx_batch_items_trade ON hedge_batch_items(original_trade_id);
CREATE INDEX IF NOT EXISTS idx_batch_items_symbol ON hedge_batch_items(symbol);
CREATE INDEX IF NOT EXISTS idx_batch_items_status ON hedge_batch_items(status);

-- 对冲订单表增加聚合相关字段
ALTER TABLE hedge_orders ADD COLUMN is_batched INTEGER DEFAULT 0;       -- 是否为聚合对冲单（0=普通单，1=聚合单）
ALTER TABLE hedge_orders ADD COLUMN batch_item_count INTEGER DEFAULT 0; -- 聚合子项数量
