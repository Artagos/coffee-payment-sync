package com.coffeeplace.sync.config;

public class ShardContextHolder {

    private static final ThreadLocal<Integer> CONTEXT = ThreadLocal.withInitial(() -> 0);

    public static void set(int shardIndex) {
        CONTEXT.set(shardIndex);
    }

    public static Integer get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
