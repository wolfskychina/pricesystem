-- PostgreSQL 初始化脚本 - 公共表 (outbox, event_store, processed_events)
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

-- outbox 表：事件转发表（事务性发件箱模式，保证业务表写入与事件发布的原子性）
CREATE TABLE IF NOT EXISTS outbox (
  id          BIGSERIAL PRIMARY KEY,
  event_id    VARCHAR(64) NOT NULL UNIQUE,
  topic       VARCHAR(64) NOT NULL,
  partition_key VARCHAR(64),
  payload     TEXT NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  retry_count INTEGER NOT NULL DEFAULT 0,
  created_at  TIMESTAMP NOT NULL,
  sent_at     TIMESTAMP,
  shard_id    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox(status, id);
CREATE INDEX IF NOT EXISTS idx_outbox_shard ON outbox(shard_id, status, id);

COMMENT ON TABLE outbox IS '事件转发表（事务性发件箱模式，保证业务表写入与事件发布的原子性）';
COMMENT ON COLUMN outbox.id IS '自增主键';
COMMENT ON COLUMN outbox.event_id IS '事件唯一 ID（用于幂等去重）';
COMMENT ON COLUMN outbox.topic IS 'Kafka 目标 topic';
COMMENT ON COLUMN outbox.partition_key IS 'Kafka 分区键（通常为 symbol/customerId）';
COMMENT ON COLUMN outbox.payload IS '事件负载（JSON 序列化后的业务数据）';
COMMENT ON COLUMN outbox.status IS '投递状态：PENDING/SENT/FAILED';
COMMENT ON COLUMN outbox.retry_count IS '已重试次数（超过上限则永久失败）';
COMMENT ON COLUMN outbox.created_at IS '创建时间';
COMMENT ON COLUMN outbox.sent_at IS '成功投递时间';
COMMENT ON COLUMN outbox.shard_id IS '分片 ID（多分片并行中继时使用）';

-- event_store 表：事件存储（事件溯源用，按 partition_key 顺序重放）
CREATE TABLE IF NOT EXISTS event_store (
  event_id        VARCHAR(64) PRIMARY KEY,
  topic           VARCHAR(64),
  partition_key   VARCHAR(64),
  event_seq       BIGINT,
  aggregate_type  VARCHAR(32),
  aggregate_id    VARCHAR(64),
  event_type      VARCHAR(32),
  payload         TEXT,
  occurred_at     TIMESTAMP,
  produced_by     VARCHAR(64),
  trace_id        VARCHAR(64),
  shard_id        INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_es_key_seq ON event_store(partition_key, event_seq);
CREATE INDEX IF NOT EXISTS idx_es_aggregate ON event_store(aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_es_shard ON event_store(shard_id, partition_key, event_seq);

COMMENT ON TABLE event_store IS '事件存储（事件溯源用，按 partition_key 顺序重放）';
COMMENT ON COLUMN event_store.event_id IS '事件唯一 ID';
COMMENT ON COLUMN event_store.topic IS '事件所属 topic';
COMMENT ON COLUMN event_store.partition_key IS '分区键';
COMMENT ON COLUMN event_store.event_seq IS '事件序号（同分区内单调递增）';
COMMENT ON COLUMN event_store.aggregate_type IS '聚合根类型（如 Order/Position）';
COMMENT ON COLUMN event_store.aggregate_id IS '聚合根 ID';
COMMENT ON COLUMN event_store.event_type IS '事件类型（如 OrderCreated/TradeFilled）';
COMMENT ON COLUMN event_store.payload IS '事件负载（JSON）';
COMMENT ON COLUMN event_store.occurred_at IS '事件发生时间';
COMMENT ON COLUMN event_store.produced_by IS '事件产生方（服务名）';
COMMENT ON COLUMN event_store.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN event_store.shard_id IS '分片 ID';

-- processed_events 表：已处理事件记录（幂等消费去重表）
CREATE TABLE IF NOT EXISTS processed_events (
  event_id     VARCHAR(64) PRIMARY KEY,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE processed_events IS '已处理事件记录（幂等消费去重表）';
COMMENT ON COLUMN processed_events.event_id IS '事件 ID（与 outbox.event_id 对应）';
COMMENT ON COLUMN processed_events.processed_at IS '处理完成时间';
