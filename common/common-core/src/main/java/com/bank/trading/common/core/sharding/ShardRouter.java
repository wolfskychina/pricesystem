package com.bank.trading.common.core.sharding;

public interface ShardRouter {

    int shardOf(String key);

    int totalShards();
}
