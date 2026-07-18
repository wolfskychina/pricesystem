package com.bank.trading.common.core.sharding;

/**
 * 单分片路由器，是 {@link ShardRouter} 的默认实现，始终将所有数据路由到 0 号分片。
 *
 * <p>该实现适用于以下场景：
 * <ul>
 *   <li>本地开发与单元测试环境，无需引入分片复杂度；</li>
 *   <li>数据量较小、尚未达到分片阈值的早期生产阶段；</li>
 *   <li>某些天然不需要分片的辅助服务（如参考数据服务 refdata-service）。</li>
 * </ul>
 * 在 {@code application.yml} 中通过 {@code trading.sharding.total-shards: 1} 配置即可启用本路由器，
 * 后续随数据量增长可平滑切换为 {@link HashShardRouter} 多分片方案。</p>
 */
public class SingleShardRouter implements ShardRouter {

    /**
     * 返回固定的 0 号分片。
     *
     * <p>无论传入何种 key，始终返回 0，即所有数据都写入唯一的分片库。
     * 这保证了不分片场景下数据全部集中，便于调试与运维。</p>
     *
     * @param key 分片键，本实现忽略该参数
     * @return 固定返回 0
     */
    @Override
    public int shardOf(String key) {
        return 0;
    }

    /**
     * 返回分片总数 1。
     *
     * @return 固定返回 1
     */
    @Override
    public int totalShards() {
        return 1;
    }
}
