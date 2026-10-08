package com.cim.cache.guard;

/**
 * 互斥重建锁抽象（防击穿）：同一 key 同一时刻仅一个线程回源，其余线程等待或命中已重建结果。
 *
 * <p>实现：{@link LocalLockProvider}（单实例，进程内）、{@link RedisLockProvider}（跨实例，分布式）。</p>
 */
public interface LockProvider {

    /**
     * 尝试加锁。
     *
     * @param key          锁键（建议带命名空间）
     * @param leaseSeconds 锁租约（秒），到期自动释放，防止持锁线程崩溃导致死锁
     * @return 是否成功获得锁
     */
    boolean tryLock(String key, long leaseSeconds);

    /**
     * 释放锁（仅释放当前线程持有的锁）。
     */
    void unlock(String key);
}
