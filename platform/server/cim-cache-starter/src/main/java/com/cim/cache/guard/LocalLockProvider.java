package com.cim.cache.guard;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 进程内互斥锁（单实例防击穿）。多实例部署必须改用 {@link RedisLockProvider} 才能跨实例互斥。
 */
public class LocalLockProvider implements LockProvider {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, long leaseSeconds) {
        // 本地锁不依赖租约（持锁线程崩溃时锁随线程消亡），lease 仅作占位兼容接口。
        return locks.computeIfAbsent(key, k -> new ReentrantLock()).tryLock();
    }

    @Override
    public void unlock(String key) {
        ReentrantLock lock = locks.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
