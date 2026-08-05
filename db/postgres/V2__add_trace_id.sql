-- 全链路追踪升级：outbox 表增加 trace_id 列，用于审计追溯
-- trace_id 串联一次请求跨服务的所有事件，由 OutboxServiceImpl.saveEvent 从 MDC 写入
-- OutboxRelay 投递时从本列取值构造 Kafka RecordHeader，实现跨服务 traceId 透传
-- 强制使用 UTF-8 客户端编码，确保中文字段注释正确写入
SET client_encoding TO 'UTF8';

ALTER TABLE outbox ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_outbox_trace ON outbox(trace_id);

COMMENT ON COLUMN outbox.trace_id IS '分布式链路追踪 ID，串联一次请求跨服务的所有事件（由 OutboxServiceImpl 从 MDC 写入，OutboxRelay 投递时构造 Kafka header）';
