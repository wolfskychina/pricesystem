package com.bank.trading.common.core.sharding;

public class SingleShardRouter implements ShardRouter {

    @Override
    public int shardOf(String key) {
        return 0;
    }

    @Override
    public int totalShards() {
        return 1;
    }
}
