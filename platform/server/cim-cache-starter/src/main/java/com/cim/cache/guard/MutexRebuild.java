package com.cim.cache.guard;

import java.util.function.Supplier;

/**
 * 互斥重建（防击穿）执行器：在 {@link LockProvider} 保护下回源，确保同一 key 同一时刻仅一个线程重建。
 *
 * <p>典型用法（double-check）：先读缓存，未命中再 {@link #compute}——若抢到锁，内部会再读一次缓存
 * （double check）后回源；若未抢到锁，调用方应短暂等待后重试读缓存，由正在重建的线程填充。</p>
 */
public final class MutexRebuild {

    private final LockProvider lockProvider;
    private final long leaseSeconds;

    public MutexRebuild(LockProvider lockProvider, long leaseSeconds) {
        this.lockProvider = lockProvider;
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * 在锁保护下执行回源（loader）。未抢到锁则返回 {@code null}，由调用方重试读缓存。
     */
    public <T> T compute(String key, Supplier<T> loader) {
        if (!lockProvider.tryLock(key, leaseSeconds)) {
            return null;
        }
        try {
            return loader.get();
        } finally {
            lockProvider.unlock(key);
        }
    }

    /** 尝试加锁（委托 {@link LockProvider}）。 */
    public boolean tryLock(String key) {
        return lockProvider.tryLock(key, leaseSeconds);
    }

    /** 释放锁（委托 {@link LockProvider}）。 */
    public void unlock(String key) {
        lockProvider.unlock(key);
    }
}
