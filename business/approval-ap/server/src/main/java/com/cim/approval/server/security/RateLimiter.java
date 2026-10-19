package com.cim.approval.server.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 appCode 的滑动窗口限流（G9）。窗口长度 60s，超出配额返回 false。
 * 内存态，随进程生命周期；审批平台为单实例编排服务，足以覆盖开放面限流需求。
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000L;

    /**
     * 尝试获取一次配额。返回 true 表示放行并计入；false 表示超限。
     *
     * @param appCode 业务域
     * @param limit   每分钟上限
     */
    public synchronized boolean tryAcquire(String appCode, int limit) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> q = windows.computeIfAbsent(appCode, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
            q.pollFirst();
        }
        if (q.size() >= limit) {
            return false;
        }
        q.addLast(now);
        return true;
    }
}
