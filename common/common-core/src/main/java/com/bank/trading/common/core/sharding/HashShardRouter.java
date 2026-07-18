package com.bank.trading.common.core.sharding;

import cn.hutool.core.util.HashUtil;

/**
 * 基于 FNV 哈希的分片路由器，是生产环境默认采用的分片策略。
 *
 * <p>该路由器使用 FNV-1a（Fowler-Noll-Vo）哈希算法对分片键进行散列，
 * 再通过取模运算将哈希值映射到 [0, totalShards) 区间。FNV 哈希具有
 * 计算速度快、分布均匀、碰撞率低的特点，非常适合用于分片路由场景。</p>
 *
 * <p><b>为何要求分片数为 2 的幂次？</b>
 * 因为当 totalShards 为 2 的幂次时，取模运算 {@code hash % totalShards}
 * 可优化为位运算 {@code hash & (totalShards - 1)}，性能更高；同时便于
 * 后续通过"虚拟槽位 + 一致性哈希"的方式进行分片扩容，减少数据迁移量。</p>
 *
 * <p><b>分片流程：</b>
 * <ol>
 *   <li>将分片键（如客户 ID）转为字节数组；</li>
 *   <li>使用 FNV-1a 计算字节数组的 32 位哈希值；</li>
 *   <li>用 {@code hash & Integer.MAX_VALUE} 清除符号位，保证结果非负；</li>
 *   <li>对 totalShards 取模得到最终分片编号。</li>
 * </ol>
 * 同一个 key 在分片数不变的情况下，永远会路由到同一个分片，从而保证
 * 事件溯源、幂等消费、Outbox 中继等链路的数据一致性。</p>
 */
public class HashShardRouter implements ShardRouter {

    /** 分片总数，必须为 2 的正整数次幂 */
    private final int totalShards;

    /**
     * 构造哈希分片路由器。
     *
     * @param totalShards 分片总数，必须为正数且为 2 的幂次（如 1/2/4/8/16...）
     * @throws IllegalArgumentException 当 totalShards 非正数或不是 2 的幂次时抛出
     */
    public HashShardRouter(int totalShards) {
        // 校验 totalShards 必须为正数且为 2 的幂次：
        // (n & (n-1)) == 0 是判断 2 的幂次的经典位运算技巧（例如 8=1000, 7=0111, 8&7=0）
        if (totalShards <= 0 || (totalShards & (totalShards - 1)) != 0) {
            throw new IllegalArgumentException("Total shards must be a positive power of 2");
        }
        this.totalShards = totalShards;
    }

    /**
     * 根据分片键计算其所属分片编号。
     *
     * <p>实现细节：
     * <ul>
     *   <li>key 为 null 时统一落到 0 号分片，避免空指针异常影响主流程；</li>
     *   <li>使用 {@code Integer.MAX_VALUE} 进行按位与，清除哈希值的最高符号位，
     *       确保取模结果非负（FNV 哈希可能产生负数）；</li>
     *   <li>取模后结果即为分片编号。</li>
     * </ul>
     *
     * @param key 分片键，通常为客户 ID、订单 ID 等
     * @return 分片编号，范围 [0, totalShards)
     */
    @Override
    public int shardOf(String key) {
        if (key == null) {
            // null 键兜底到 0 号分片，保证不抛异常打断主流程
            return 0;
        }
        // 使用 Hutool 的 FNV-1a 哈希算法，分布均匀且性能优异
        int hash = HashUtil.fnvHash(key.getBytes());
        // & Integer.MAX_VALUE 清除符号位确保非负，再取模得到分片号
        return (hash & Integer.MAX_VALUE) % totalShards;
    }

    /**
     * 返回分片总数。
     *
     * @return 分片总数
     */
    @Override
    public int totalShards() {
        return totalShards;
    }
}
