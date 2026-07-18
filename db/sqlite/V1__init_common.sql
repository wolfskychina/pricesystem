-- SQLite 初始化脚本 - 公共表 (outbox, event_store, processed_events)

-- outbox 表
CREATE TABLE IF NOT EXISTS outbox (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
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

-- event_store 表
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

-- processed_events 表 (幂等消费)
CREATE TABLE IF NOT EXISTS processed_events (
  event_id     VARCHAR(64) PRIMARY KEY,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
