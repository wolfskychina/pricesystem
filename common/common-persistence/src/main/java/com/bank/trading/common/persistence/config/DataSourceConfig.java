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

@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConditionalOnMissingBean(ShardRouter.class)
    public ShardRouter shardRouter() {
        return new SingleShardRouter();
    }

    @Bean
    @Primary
    public DataSource dataSource(ShardRouter shardRouter, DataSourceProperties properties) {
        ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource(shardRouter);

        int totalShards = shardRouter.totalShards();
        for (int i = 0; i < totalShards; i++) {
            DataSource ds = createDataSource(properties, i);
            routingDataSource.addDataSource(i, ds);
        }
        routingDataSource.afterInit();
        return routingDataSource;
    }

    private DataSource createDataSource(DataSourceProperties properties, int shardId) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(rewriteUrl(properties.getUrl(), shardId));
        ds.setDriverClassName(properties.getDriverClassName());
        if (properties.getUsername() != null) {
            ds.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null) {
            ds.setPassword(properties.getPassword());
        }
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setIdleTimeout(60000);
        ds.setConnectionTimeout(30000);
        return ds;
    }

    private String rewriteUrl(String url, int shardId) {
        if (url == null) {
            return url;
        }
        if (url.contains("{shard}")) {
            return url.replace("{shard}", String.format("%02d", shardId));
        }
        return url;
    }
}
