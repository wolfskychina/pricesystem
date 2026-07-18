package com.bank.trading.common.persistence.sharding;

import com.bank.trading.common.core.sharding.ShardRouter;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 分片路由数据源，继承 Spring 的 {@link AbstractRoutingDataSource}。
 *
 * <p>本类是分片架构在持久层的实现核心。它持有多份物理数据源（每个分片一份），
 * 在每次获取连接时根据 {@link ShardContextHolder} 中的分片 ID 动态选择目标数据源，
 * 从而实现"同一逻辑数据源，多份物理库"的透明分片。</p>
 *
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>业务代码在执行数据库操作前，通过 {@link ShardContextHolder#setShardId(int)}
 *       设置当前线程的目标分片；</li>
 *   <li>MyBatis/JdbcTemplate 从 Spring 容器获取数据源（即本类的实例 @Primary）；</li>
 *   <li>获取连接时，{@link AbstractRoutingDataSource} 调用
 *       {@link #determineCurrentLookupKey()} 得到分片 ID；</li>
 *   <li>根据分片 ID 从 {@code targetDataSources} 中选取对应的物理数据源，
 *       从中获取连接执行 SQL。</li>
 * </ol>
 * 整个过程对业务代码透明，业务层只需操作"一个数据源"。</p>
 *
 * <p><b>事务一致性：</b>同一事务内的所有数据库操作会复用同一条连接，
 * 因此分片 ID 在整个事务期间保持不变，保证事件存储、Outbox 等同事务多表操作
 * 落到同一分片。</p>
 *
 * @see ShardContextHolder
 * @see ShardRouter
 */
public class ShardRoutingDataSource extends AbstractRoutingDataSource {

    /** 分片路由器，用于计算分片编号（本类主要持有，实际路由 key 由上下文提供） */
    private final ShardRouter shardRouter;
    /** 分片编号 → 物理数据源 的映射 */
    private final Map<Integer, DataSource> dataSourceMap = new HashMap<>();

    /**
     * 构造分片路由数据源。
     *
     * @param shardRouter 分片路由器
     */
    public ShardRoutingDataSource(ShardRouter shardRouter) {
        this.shardRouter = shardRouter;
    }

    /**
     * 返回持有的分片路由器。
     *
     * @return 分片路由器实例
     */
    public ShardRouter getShardRouter() {
        return shardRouter;
    }

    /**
     * 注册一个分片对应的物理数据源。
     *
     * <p>在 {@link com.bank.trading.common.persistence.config.DataSourceConfig} 中
     * 遍历所有分片编号，为每个分片创建独立数据源后调用本方法注册。
     * 必须在 {@link #afterInit()} 之前完成所有注册。</p>
     *
     * @param shardId    分片编号
     * @param dataSource 该分片对应的物理数据源
     */
    public void addDataSource(int shardId, DataSource dataSource) {
        dataSourceMap.put(shardId, dataSource);
    }

    /**
     * 完成初始化，将已注册的分片数据源设置到父类。
     *
     * <p>将 {@link #dataSourceMap} 转换为 {@code targetDataSources} 传给父类，
     * 并选取第一个数据源作为默认数据源（当上下文未设置分片 ID 时使用）。
     * 最后调用 {@link #afterPropertiesSet()} 使配置生效。</p>
     */
    public void afterInit() {
        Map<Object, Object> targetDataSources = new HashMap<>(dataSourceMap);
        setTargetDataSources(targetDataSources);
        if (!dataSourceMap.isEmpty()) {
            // 默认数据源：当上下文未设置分片 ID 时的兜底，通常为 0 号分片
            setDefaultTargetDataSource(dataSourceMap.values().iterator().next());
        }
        afterPropertiesSet();
    }

    /**
     * 决定当前请求使用哪个分片数据源。
     *
     * <p>从 {@link ShardContextHolder} 读取当前线程的分片 ID；
     * 若未设置（如系统初始化、跨分片管理操作），兜底返回 0 号分片。
     * 父类 {@link AbstractRoutingDataSource} 会用此 key 从
     * {@code targetDataSources} 中选取对应数据源。</p>
     *
     * @return 当前分片编号（作为数据源查找 key）
     */
    @Override
    protected Object determineCurrentLookupKey() {
        Integer shardId = ShardContextHolder.getShardId();
        if (shardId == null) {
            // 上下文未设置时兜底到 0 号分片，保证系统初始化等场景可用
            shardId = 0;
        }
        return shardId;
    }
}
