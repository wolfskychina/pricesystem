-- SQLite 初始化脚本 - 公共表 (outbox, event_store, processed_events)
-- 注意：SQLite 数据库文件默认使用 UTF-8 编码，无需显式设置

-- outbox 表：事件转发表（事务性发件箱模式，保证业务表写入与事件发布的原子性）
CREATE TABLE IF NOT EXISTS outbox (
  id          BIGINT PRIMARY KEY, -- 分布式 ID 主键（应用层 Snowflake 发号器生成）
  event_id    VARCHAR(64) NOT NULL UNIQUE,       -- 事件唯一 ID（用于幂等去重）
  topic       VARCHAR(64) NOT NULL,              -- Kafka 目标 topic
  partition_key VARCHAR(64),                     -- Kafka 分区键（通常为 symbol/customerId）
  payload     TEXT NOT NULL,                     -- 事件负载（JSON 序列化后的业务数据）
  status      VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- 投递状态：PENDING/SENT/FAILED
  retry_count INTEGER NOT NULL DEFAULT 0,        -- 已重试次数（超过上限则永久失败）
  created_at  TIMESTAMP NOT NULL,                -- 创建时间
  sent_at     TIMESTAMP,                         -- 成功投递时间
  shard_id    INTEGER NOT NULL DEFAULT 0         -- 分片 ID（多分片并行中继时使用）
);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox(status, id);
CREATE INDEX IF NOT EXISTS idx_outbox_shard ON outbox(shard_id, status, id);

-- event_store 表：事件存储（事件溯源用，按 partition_key 顺序重放）
CREATE TABLE IF NOT EXISTS event_store (
  event_id        VARCHAR(64) PRIMARY KEY, -- 事件唯一 ID
  topic           VARCHAR(64),             -- 事件所属 topic
  partition_key   VARCHAR(64),             -- 分区键
  event_seq       BIGINT,                  -- 事件序号（同分区内单调递增）
  aggregate_type  VARCHAR(32),             -- 聚合根类型（如 Order/Position）
  aggregate_id    VARCHAR(64),             -- 聚合根 ID
  event_type      VARCHAR(32),             -- 事件类型（如 OrderCreated/TradeFilled）
  payload         TEXT,                    -- 事件负载（JSON）
  occurred_at     TIMESTAMP,               -- 事件发生时间
  produced_by     VARCHAR(64),             -- 事件产生方（服务名）
  trace_id        VARCHAR(64),             -- 链路追踪 ID
  shard_id        INTEGER NOT NULL DEFAULT 0 -- 分片 ID
);
CREATE INDEX IF NOT EXISTS idx_es_key_seq ON event_store(partition_key, event_seq);
CREATE INDEX IF NOT EXISTS idx_es_aggregate ON event_store(aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_es_shard ON event_store(shard_id, partition_key, event_seq);

-- processed_events 表：已处理事件记录（幂等消费去重表）
CREATE TABLE IF NOT EXISTS processed_events (
  event_id     VARCHAR(64) PRIMARY KEY,    -- 事件 ID（与 outbox.event_id 对应）
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 处理完成时间
);
