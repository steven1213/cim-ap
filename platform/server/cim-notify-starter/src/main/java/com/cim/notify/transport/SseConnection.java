package com.cim.notify.transport;

import com.cim.spring.support.tenant.TenantContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个 SSE 连接值对象。
 *
 * <p>⚠️ 关键：{@code tenantId} 在订阅时从请求线程捕获并<b>固化</b>到对象。
 * 心跳与 {@link #emit} 跑在调度线程，请求线程的 {@link TenantContext} 已丢失，
 * 故每次发送前必须 {@code TenantContext.set(tenantId)}，结束 {@code clear()}，否则租户过滤器静默失效、跨租户串消息。</p>
 */
public class SseConnection {

    private final String id;
    private final String tenantId;
    private final String userId;
    private final Set<String> roles;
    private final Set<String> topics = ConcurrentHashMap.newKeySet();
    private final SseEmitter emitter;
    private volatile long lastHeartbeatAt = System.currentTimeMillis();

    public SseConnection(String id, String tenantId, String userId, Set<String> roles, SseEmitter emitter) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.roles = roles;
        this.emitter = emitter;
    }

    /** 在租户上下文固化下发送；抛 IOException 由调用方决定摘链。 */
    public void emit(SseEmitter.SseEventBuilder builder) throws IOException {
        try {
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            emitter.send(builder);
        } finally {
            TenantContext.clear();
        }
    }

    public void complete() {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 已关闭的连接 complete 会抛 IllegalStateException，忽略
        }
    }

    public void addTopic(String topic) {
        topics.add(topic);
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getTopics() {
        return topics;
    }

    public long getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(long lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
