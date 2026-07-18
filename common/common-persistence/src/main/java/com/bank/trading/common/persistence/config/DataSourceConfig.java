package com.bank.trading.common.persistence.config;

import com.bank.trading.common.core.sharding.ShardRouter;
import com.bank.trading.common.core.sharding.SingleShardRouter;
import com.bank.trading.common.persistence.sharding.ShardRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置类，负责构建支持分片路由的分布式数据源。
 *
 * <p>本配置是分片架构的装配核心，完成以下工作：
 * <ol>
 *   <li>读取 {@code spring.datasource.*} 配置（URL、用户名、密码、驱动）；</li>
 *   <li>装配 {@link ShardRouter} Bean，若无自定义实现则默认使用 {@link SingleShardRouter}
 *       （单分片，开发环境）；生产环境可通过配置切换为 {@code HashShardRouter}；</li>
 *   <li>根据分片总数，为每个分片创建独立的 HikariCP 连接池数据源；</li>
 *   <li>将所有分片数据源注册到 {@link ShardRoutingDataSource}，作为 {@code @Primary} 数据源
 *       供 MyBatis/JdbcTemplate 使用，运行时按分片上下文路由到目标分片。</li>
 * </ol>
 *
 * <p><b>分片 URL 占位符机制：</b>JDBC URL 中可使用 {@code {shard}} 占位符，
 * 如 {@code jdbc:sqlite:db/shard_{shard}.db}，运行时会被替换为两位数字编号（如 00、01），
 * 从而为不同分片指向不同的物理库文件/实例。不带占位符的 URL 则所有分片共用同一库
 * （单分片场景或测试场景）。</p>
 *
 * @see ShardRoutingDataSource
 * @see ShardRouter
 */
@Configuration
public class DataSourceConfig {

    /**
     * 注册数据源属性 Bean，绑定 {@code spring.datasource.*} 配置。
     *
     * @return 数据源属性对象
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 注册分片路由器 Bean。
     *
     * <p>当容器中不存在自定义 {@link ShardRouter} 时，默认使用 {@link SingleShardRouter}
     * （单分片）。业务服务可通过自定义 Bean 覆盖此默认实现以启用多分片。</p>
     *
     * @return 分片路由器实例
     */
    @Bean
    @ConditionalOnMissingBean(ShardRouter.class)
    public ShardRouter shardRouter() {
        return new SingleShardRouter();
    }

    /**
     * 构建主数据源（分片路由数据源）。
     *
     * <p>遍历所有分片编号，为每个分片创建独立的 HikariCP 连接池，注册到
     * {@link ShardRoutingDataSource}。该数据源标记为 {@code @Primary}，
     * 作为 Spring 容器中默认的数据源供 MyBatis、JdbcTemplate 等使用。</p>
     *
     * <p>运行时，{@link ShardRoutingDataSource#determineCurrentLookupKey()} 会从
     * {@link com.bank.trading.common.persistence.sharding.ShardContextHolder} 读取当前线程
     * 的分片 ID，路由到对应的物理数据源。若上下文未设置分片 ID，默认路由到 0 号分片。</p>
     *
     * @param shardRouter 分片路由器
     * @param properties  数据源属性
     * @return 分片路由数据源（@Primary）
     */
    @Bean
    @Primary
    public DataSource dataSource(ShardRouter shardRouter, DataSourceProperties properties) {
        ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource(shardRouter);

        // 为每个分片创建独立的数据源并注册
        int totalShards = shardRouter.totalShards();
        for (int i = 0; i < totalShards; i++) {
            DataSource ds = createDataSource(properties, i);
            routingDataSource.addDataSource(i, ds);
        }
        // 完成初始化（设置目标数据源与默认数据源）
        routingDataSource.afterInit();
        return routingDataSource;
    }

    /**
     * 为指定分片创建 HikariCP 数据源。
     *
     * <p>连接池参数：
     * <ul>
     *   <li>最大连接数 10、最小空闲连接 2（适合单服务实例的中小规模场景）；</li>
     *   <li>空闲连接超时 60 秒、连接获取超时 30 秒。</li>
     * </ul>
     *
     * @param properties 数据源属性
     * @param shardId    分片编号
     * @return 配置好的 HikariCP 数据源
     */
    private DataSource createDataSource(DataSourceProperties properties, int shardId) {
        HikariDataSource ds = new HikariDataSource();
        // 将 URL 中的 {shard} 占位符替换为分片编号
        ds.setJdbcUrl(rewriteUrl(properties.getUrl(), shardId));
        ds.setDriverClassName(properties.getDriverClassName());
        if (properties.getUsername() != null) {
            ds.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null) {
            ds.setPassword(properties.getPassword());
        }
        // 连接池参数：兼顾资源占用与并发性能
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setIdleTimeout(60000);
        ds.setConnectionTimeout(30000);
        return ds;
    }

    /**
     * 重写 JDBC URL，将 {@code {shard}} 占位符替换为两位数字分片编号。
     *
     * <p>例如 {@code jdbc:sqlite:db/shard_{shard}.db} + shardId=3 →
     * {@code jdbc:sqlite:db/shard_03.db}。两位数字格式支持最多 100 个分片（00-99）。
     * 若 URL 不含占位符，则原样返回（所有分片共用同一数据库，用于单分片或测试场景）。</p>
     *
     * @param url     原始 JDBC URL
     * @param shardId 分片编号
     * @return 替换占位符后的 JDBC URL
     */
    private String rewriteUrl(String url, int shardId) {
        if (url == null) {
            return url;
        }
        if (url.contains("{shard}")) {
            // %02d 保证两位数字，不足前补零（如 3 → 03）
            return url.replace("{shard}", String.format("%02d", shardId));
        }
        return url;
    }
}
