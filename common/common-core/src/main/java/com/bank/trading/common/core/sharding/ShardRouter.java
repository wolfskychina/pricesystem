package com.bank.trading.common.core.sharding;

/**
 * 分片路由接口，是整个分片体系的核心抽象。
 *
 * <p>在银行做市商交易系统中，为了水平扩展数据库容量并提升并发写入能力，
 * 系统采用分片（Sharding）策略将数据按某种规则分散到多个物理分片库中。
 * 每个分片库拥有独立的数据源，承载部分客户/订单/事件数据。</p>
 *
 * <p>本接口定义了两个核心能力：
 * <ul>
 *   <li>{@link #shardOf(String)} —— 根据业务主键（如客户 ID、订单 ID）计算出应落地的分片编号；</li>
 *   <li>{@link #totalShards()} —— 返回当前集群的分片总数，供数据源初始化与路由校验使用。</li>
 * </ul>
 * 分片策略的选取直接影响数据分布的均匀度与查询的可路由性，需谨慎设计。</p>
 *
 * <p>实现类包括：
 * <ul>
 *   <li>{@link HashShardRouter} —— 基于 FNV 哈希的一致性分片路由，生产环境推荐使用；</li>
 *   <li>{@link SingleShardRouter} —— 单分片路由，用于开发/测试环境或不分片场景。</li>
 * </ul></p>
 *
 * @see HashShardRouter
 * @see SingleShardRouter
 */
public interface ShardRouter {

    /**
     * 根据业务主键计算其所属的分片编号。
     *
     * <p>该方法必须保证：<b>相同的 key 永远路由到相同的分片</b>（幂等性），
     * 否则会导致事件溯源、订单查询等链路数据错乱。同一 key 的所有事件、
     * 订单、成交记录都会落到同一分片，从而在该分片内完成局部事务，
     * 避免跨分片分布式事务的复杂性。</p>
     *
     * @param key 分片键，通常是客户 ID、订单 ID 等业务主键；为 null 时由实现决定默认分片
     * @return 分片编号，取值范围为 [0, totalShards())
     */
    int shardOf(String key);

    /**
     * 返回当前集群配置的分片总数。
     *
     * <p>分片总数一旦确定并投入生产后通常不可变更（除非进行数据迁移），
     * 因为分片数变化会改变 key 到分片的映射关系。这也是 {@link HashShardRouter}
     * 要求分片数必须为 2 的幂次的原因——便于后续通过虚拟槽位扩展。</p>
     *
     * @return 分片总数，必须为正整数
     */
    int totalShards();
}
