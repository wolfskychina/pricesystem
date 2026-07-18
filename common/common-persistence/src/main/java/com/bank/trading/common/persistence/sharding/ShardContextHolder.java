package com.bank.trading.common.persistence.sharding;

/**
 * 分片上下文持有器，基于 ThreadLocal 在线程内传递当前分片 ID。
 *
 * <p>在分片架构中，每次数据库操作前需要明确目标分片。本类通过 ThreadLocal
 * 将分片 ID 绑定到当前执行线程，使 {@link ShardRoutingDataSource} 能在
 * 不改变业务代码签名的前提下获取目标分片。</p>
 *
 * <p><b>典型使用流程：</b>
 * <pre>
 *   int shardId = shardRouter.shardOf(customerId);  // 1. 计算分片
 *   ShardContextHolder.setShardId(shardId);          // 2. 设置上下文
 *   try {
 *       eventStoreService.appendEvent(...);          // 3. 执行分片数据库操作
 *       outboxService.saveEvent(...);                //    （同事务内多表操作共享分片）
 *   } finally {
 *       ShardContextHolder.clear();                  // 4. 清理上下文（必须！）
 *   }
 * </pre>
 *
 * <p><b>线程安全注意事项：</b>
 * <ul>
 *   <li>必须在使用后调用 {@link #clear()} 清理，否则在线程池复用场景下会导致
 *       分片串号（后续请求路由到错误的分片）；</li>
 *   <li>异步线程不会继承父线程的上下文，需手动传递分片 ID。</li>
 * </ul></p>
 *
 * @see ShardRoutingDataSource
 */
public class ShardContextHolder {

    /** ThreadLocal 持有当前线程的分片 ID */
    private static final ThreadLocal<Integer> CONTEXT = new ThreadLocal<>();

    /** 工具类私有构造，禁止实例化 */
    private ShardContextHolder() {}

    /**
     * 设置当前线程的分片 ID。
     *
     * @param shardId 分片编号
     */
    public static void setShardId(int shardId) {
        CONTEXT.set(shardId);
    }

    /**
     * 获取当前线程的分片 ID。
     *
     * @return 分片编号；若未设置则返回 null（路由数据源会兜底为 0）
     */
    public static Integer getShardId() {
        return CONTEXT.get();
    }

    /**
     * 清除当前线程的分片 ID。
     *
     * <p><b>必须在 finally 块中调用</b>，防止线程池复用时上下文串号。
     * 这是分片架构中最常见的 bug 来源。</p>
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
