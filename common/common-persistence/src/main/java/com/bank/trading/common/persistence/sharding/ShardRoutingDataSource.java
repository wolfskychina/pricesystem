package com.bank.trading.common.persistence.sharding;

import com.bank.trading.common.core.sharding.ShardRouter;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class ShardRoutingDataSource extends AbstractRoutingDataSource {

    private final ShardRouter shardRouter;

    public ShardRoutingDataSource(ShardRouter shardRouter) {
        this.shardRouter = shardRouter;
    }

    public ShardRouter getShardRouter() {
        return shardRouter;
    }

    public ShardRoutingDataSource(ShardRouter shardRouter) {
        this.shardRouter = shardRouter;
    }

    private final Map<Integer, DataSource> dataSourceMap = new HashMap<>();

    public ShardRoutingDataSource(ShardRouter shardRouter) {
        this.shardRouter = shardRouter;
    }

    public void addDataSource(int shardId, DataSource dataSource) {
        dataSourceMap.put(shardId, dataSource);
    }

    public void afterInit() {
        Map<Object, Object> targetDataSources = new HashMap<>(dataSourceMap);
        setTargetDataSources(targetDataSources);
        if (!dataSourceMap.isEmpty()) {
            setDefaultTargetDataSource(dataSourceMap.values().iterator().next());
        }
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        Integer shardId = ShardContextHolder.getShardId();
        if (shardId == null) {
            shardId = 0;
        }
        return shardId;
    }
}
