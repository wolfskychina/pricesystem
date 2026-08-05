-- 全链路追踪升级：hedge_batch_items 增加 trace_id 列
-- trace_id 入桶时从 MDC 写入（关联原始客户请求），出桶发 hedge-fill-event 时取值赋给 event
-- 保证聚合模式下每条事件仍能关联回原始客户请求的 traceId（定时任务的 MDC 是新建的，无法续接）
ALTER TABLE hedge_batch_items ADD COLUMN trace_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_hedge_batch_items_trace ON hedge_batch_items(trace_id);
