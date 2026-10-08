package com.cim.mq.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量熔断器（Closed/Open/Half-Open），语义对齐 Resilience4j，零外部依赖。
 *
 * <p>用于发件箱中继：当 broker 持续不可达，熔断后暂停投递避免空转与级联雪崩，
 * 冷却后进入半开探测，恢复正常则闭合。</p>
 */
public class CircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration cooldown;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openSince = new AtomicLong(0);
    private volatile State state = State.CLOSED;

    public CircuitBreaker(int failureThreshold, Duration cooldown) {
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
    }

    /** 是否允许本次执行（OPEN 且未到冷却期则拒绝）。 */
    public synchronized boolean allow() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openSince.get() >= cooldown.toMillis()) {
                state = State.HALF_OPEN;
                consecutiveFailures.set(0);
                return true;
            }
            return false;
        }
        return true;
    }

    public synchronized void onSuccess() {
        if (state != State.CLOSED) {
            state = State.CLOSED;
        }
        consecutiveFailures.set(0);
    }

    public synchronized void onFailure() {
        int f = consecutiveFailures.incrementAndGet();
        if (f >= failureThreshold) {
            state = State.OPEN;
            openSince.set(System.currentTimeMillis());
        }
    }

    public State state() {
        return state;
    }
}
