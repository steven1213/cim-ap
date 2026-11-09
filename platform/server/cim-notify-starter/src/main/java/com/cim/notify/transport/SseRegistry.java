package com.cim.notify.transport;

import com.cim.notify.api.NotifyEnvelope;
import com.cim.notify.api.NotifyEvent;
import com.cim.notify.config.NotifyProperties;
import com.cim.notify.metrics.NotifyMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 连接注册表：订阅、按 (tenant, user/role/topic) 投递、心跳保活、容量护栏。
 *
 * <p>纯内存、线程安全；多实例扇出由 {@code SseFanout} 负责（本类只处理本 Pod 内的连接）。
 * 注册表一律按 {@code (tenantId, ...)} 双键索引，杜绝跨租户误投。</p>
 */
public class SseRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseRegistry.class);

    private final NotifyProperties props;
    private final ObjectMapper objectMapper;
    private final NotifyMetrics metrics;
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    /** tenant|user → 连接集合。 */
    private final Map<String, Set<SseConnection>> byUser = new ConcurrentHashMap<>();
    /** tenant|role → 连接集合。 */
    private final Map<String, Set<SseConnection>> byRole = new ConcurrentHashMap<>();
    /** tenant|topic → 连接集合。 */
    private final Map<String, Set<SseConnection>> byTopic = new ConcurrentHashMap<>();
    /** connId → 连接（全量，用于心跳遍历 / 摘链）。 */
    private final Map<String, SseConnection> byId = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cim-notify-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public SseRegistry(NotifyProperties props, ObjectMapper objectMapper, NotifyMetrics metrics) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        long hb = Math.max(1, props.getHeartbeat().toSeconds());
        heartbeatScheduler.scheduleAtFixedRate(this::heartbeat, hb, hb, TimeUnit.SECONDS);
        String appCode = props.getAppCode() == null ? "?" : props.getAppCode();
        metrics.gauge("notify.connections", connectionCount, "appCode", appCode);
    }

    /**
     * 建立订阅，返回 {@link SseEmitter}。
     *
     * @throws IllegalStateException 单用户并发连接超过 {@code maxConnectionsPerUser}
     */
    public SseEmitter subscribe(String tenantId, String userId, Set<String> roles) {
        String userKey = tenantId + "|" + userId;
        Set<SseConnection> userConns = byUser.computeIfAbsent(userKey, k -> ConcurrentHashMap.newKeySet());
        if (userConns.size() >= props.getMaxConnectionsPerUser()) {
            throw new IllegalStateException(
                    "cim-notify: max connections per user exceeded (" + props.getMaxConnectionsPerUser() + ")");
        }
        SseEmitter emitter = new SseEmitter(props.getTimeout().toMillis());
        SseConnection conn = new SseConnection(UUID.randomUUID().toString(), tenantId, userId, roles, emitter);
        byId.put(conn.getId(), conn);
        userConns.add(conn);
        for (String role : roles) {
            byRole.computeIfAbsent(tenantId + "|" + role, k -> ConcurrentHashMap.newKeySet()).add(conn);
        }
        connectionCount.incrementAndGet();
        emitter.onCompletion(() -> unregister(conn));
        emitter.onTimeout(() -> unregister(conn));
        emitter.onError(e -> unregister(conn));
        log.debug("[cim-notify] subscribe tenant={} user={} conn={} total={}", tenantId, userId, conn.getId(), connectionCount.get());
        return emitter;
    }

    /** 把连接挂到某主题（租户隔离：key = tenant|topic）。 */
    public void registerTopic(String tenantId, String topic, SseConnection conn) {
        conn.addTopic(topic);
        byTopic.computeIfAbsent(tenantId + "|" + topic, k -> ConcurrentHashMap.newKeySet()).add(conn);
    }

    /** 本地投递：仅处理本 Pod 内挂着的连接（扇出已把事件送到本 Pod）。 */
    public void deliverLocal(NotifyEvent event) {
        Set<SseConnection> matched = resolveTargets(event);
        String json;
        try {
            json = objectMapper.writeValueAsString(new NotifyEnvelope(event));
        } catch (IOException e) {
            log.warn("[cim-notify] envelope serialize failed: {}", e.getMessage());
            return;
        }
        for (SseConnection conn : matched) {
            try {
                conn.emit(SseEmitter.event().id(event.getId()).name(event.getType()).data(json));
                metrics.increment("notify.sent", "tenant", event.getTenantId(),
                        "appCode", event.getAppCode() == null ? "?" : event.getAppCode(),
                        "target", event.getTargetType().name());
            } catch (IOException e) {
                log.debug("[cim-notify] emit failed, unregister conn={}", conn.getId());
                unregister(conn);
            }
        }
    }

    /** 解析事件命中连接（不含发送，便于单测）。 */
    Set<SseConnection> resolveTargets(NotifyEvent event) {
        Set<SseConnection> matched = new LinkedHashSet<>();
        switch (event.getTargetType()) {
            case USER -> {
                if (event.getTargets() != null) {
                    for (String u : event.getTargets()) {
                        Set<SseConnection> s = byUser.get(event.getTenantId() + "|" + u);
                        if (s != null) matched.addAll(s);
                    }
                }
            }
            case ROLE -> {
                if (event.getTargets() != null) {
                    for (String r : event.getTargets()) {
                        Set<SseConnection> s = byRole.get(event.getTenantId() + "|" + r);
                        if (s != null) matched.addAll(s);
                    }
                }
            }
            case TOPIC -> {
                if (event.getTargets() != null) {
                    for (String t : event.getTargets()) {
                        Set<SseConnection> s = byTopic.get(event.getTenantId() + "|" + t);
                        if (s != null) matched.addAll(s);
                    }
                }
            }
        }
        return matched;
    }

    private void heartbeat() {
        long now = System.currentTimeMillis();
        for (SseConnection conn : byId.values()) {
            try {
                conn.emit(SseEmitter.event().comment("hb"));
                conn.setLastHeartbeatAt(now);
            } catch (IOException e) {
                unregister(conn);
            }
        }
    }

    private void unregister(SseConnection conn) {
        if (byId.remove(conn.getId()) != null) {
            connectionCount.decrementAndGet();
        }
        Set<SseConnection> uc = byUser.get(conn.getTenantId() + "|" + conn.getUserId());
        if (uc != null) uc.remove(conn);
        for (String role : conn.getRoles()) {
            Set<SseConnection> rc = byRole.get(conn.getTenantId() + "|" + role);
            if (rc != null) rc.remove(conn);
        }
        for (String topic : conn.getTopics()) {
            Set<SseConnection> tc = byTopic.get(conn.getTenantId() + "|" + topic);
            if (tc != null) tc.remove(conn);
        }
        conn.complete();
    }

    @PreDestroy
    public void destroy() {
        heartbeatScheduler.shutdownNow();
    }
}
