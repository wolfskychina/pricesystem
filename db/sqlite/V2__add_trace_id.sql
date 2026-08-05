-- 全链路追踪升级：outbox 表增加 trace_id 列，用于审计追溯
-- trace_id 串联一次请求跨服务的所有事件，由 OutboxServiceImpl.saveEvent 从 MDC 写入
-- OutboxRelay 投递时从本列取值构造 Kafka RecordHeader，实现跨服务 traceId 透传
ALTER TABLE outbox ADD COLUMN trace_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_outbox_trace ON outbox(trace_id);
