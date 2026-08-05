package com.bank.trading.common.core.trace;

import org.springframework.core.task.TaskDecorator;

/**
 * MDC 跨线程传播装饰器。
 * <p>
 * 应用于 {@code @Async} / {@code @Scheduled} / 线程池，确保子线程能继承父线程的 traceId；
 * 若父线程无 traceId（如定时任务场景），则为子任务生成新的 traceId。
 * <p>
 * <b>使用场景</b>：
 * <ul>
 *   <li>定时任务：父线程（调度线程）无 traceId，子任务生成新 ID，保证定时任务日志可追踪</li>
 *   <li>异步任务：父线程（请求线程）有 traceId，子任务继承，保证异步链路连续</li>
 * </ul>
 * <p>
 * <b>注册方式</b>：通过 {@link TraceAutoConfiguration} 装配为 Bean，
 * Spring Boot 会自动应用到 {@code TaskExecutor}。
 *
 * @see TraceContext#setTraceId(String)
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 捕获父线程的 traceId（定时任务场景为 null）
        String parentTraceId = TraceContext.getTraceId();
        return () -> {
            try {
                if (parentTraceId != null && !parentTraceId.isBlank()) {
                    // 异步任务：继承父线程 traceId
                    TraceContext.setTraceId(parentTraceId);
                } else {
                    // 定时任务：生成新 traceId
                    TraceContext.setTraceId(null);
                }
                runnable.run();
            } finally {
                // 子线程结束，清理 MDC 避免线程池复用串流
                TraceContext.clear();
            }
        };
    }
}
