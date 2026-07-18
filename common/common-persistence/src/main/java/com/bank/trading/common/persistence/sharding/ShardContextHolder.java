package com.bank.trading.common.persistence.sharding;

public class ShardContextHolder {

    private static final ThreadLocal<Integer> CONTEXT = new ThreadLocal<>();

    private ShardContextHolder() {}

    public static void setShardId(int shardId) {
        CONTEXT.set(shardId);
    }

    public static Integer getShardId() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
