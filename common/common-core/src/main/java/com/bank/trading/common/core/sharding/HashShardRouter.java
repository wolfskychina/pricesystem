package com.bank.trading.common.core.sharding;

import cn.hutool.core.util.HashUtil;

public class HashShardRouter implements ShardRouter {

    private final int totalShards;

    public HashShardRouter(int totalShards) {
        if (totalShards <= 0 || (totalShards & (totalShards - 1)) != 0) {
            throw new IllegalArgumentException("Total shards must be a positive power of 2");
        }
        this.totalShards = totalShards;
    }

    @Override
    public int shardOf(String key) {
        if (key == null) {
            return 0;
        }
        int hash = HashUtil.fnvHash(key.getBytes());
        return (hash & Integer.MAX_VALUE) % totalShards;
    }

    @Override
    public int totalShards() {
        return totalShards;
    }
}
