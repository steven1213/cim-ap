package com.cim.mq.resilience;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 轻量重试策略（指数退避 + 抖动），语义对齐 Resilience4j，零外部依赖。
 *
 * <p>用于：① 发件箱中继投递 broker 失败重试；② 消费者处理失败重试。
 * 与「at-least-once 投递 + 消费幂等」配合，做到不丢且不重。</p>
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double factor;
    private final long maxBackoffMs;

    private RetryPolicy(Builder b) {
        this.maxAttempts = b.maxAttempts;
        this.initialBackoffMs = b.initialBackoffMs;
        this.factor = b.factor;
        this.maxBackoffMs = b.maxBackoffMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 退避时长（含抖动，避免惊群）。 */
    public long backoffMillis(int attempt) {
        long base = (long) (initialBackoffMs * Math.pow(factor, Math.max(0, attempt - 1)));
        base = Math.min(base, maxBackoffMs);
        return base + ThreadLocalRandom.current().nextLong(0, Math.max(1, base / 4));
    }

    /** 带重试执行（返回结果）。 */
    public <T> T execute(Callable<T> task) throws Exception {
        int attempt = 0;
        Exception last;
        do {
            attempt++;
            try {
                return task.call();
            } catch (Exception e) {
                last = e;
                if (attempt >= maxAttempts) {
                    break;
                }
                sleep(backoffMillis(attempt));
            }
        } while (attempt < maxAttempts);
        throw last;
    }

    /** 带重试执行（无返回）。 */
    public void execute(Runnable task) throws Exception {
        execute(() -> {
            task.run();
            return null;
        });
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("重试退避被中断", ie);
        }
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public static final class Builder {
        private int maxAttempts = 5;
        private long initialBackoffMs = 500;
        private double factor = 2.0;
        private long maxBackoffMs = 30_000;

        public Builder maxAttempts(int v) {
            this.maxAttempts = v;
            return this;
        }

        public Builder initialBackoff(Duration d) {
            this.initialBackoffMs = d.toMillis();
            return this;
        }

        public Builder factor(double v) {
            this.factor = v;
            return this;
        }

        public Builder maxBackoff(Duration d) {
            this.maxBackoffMs = d.toMillis();
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
