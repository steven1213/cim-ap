package com.cim.approval.server.service;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.approval.server.domain.CallbackOutbox;
import com.cim.approval.server.domain.CallbackRegistration;
import com.cim.approval.server.dto.CallbackRegistrationRequest;
import com.cim.approval.server.dto.CallbackRegistrationView;
import com.cim.approval.server.dto.OutboxView;
import com.cim.approval.server.repository.CallbackOutboxRepository;
import com.cim.approval.server.repository.CallbackRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回调可靠投递：注册 → 流程结束写入 Outbox → 调度器指数退避重试 → 死信告警。
 *
 * <p>安全：每条回调携带 {@code X-Approval-Signature = HMAC-SHA256(secret, timestamp + "." + body)}，
 * 业务方据此验签防伪造；{@code X-Approval-Message-Id} 供业务方幂等去重。</p>
 */
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final CallbackRegistrationRepository registrationRepository;
    private final CallbackOutboxRepository outboxRepository;
    private final AuditService auditService;
    private final WebhookNotifyService webhookNotifyService;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    /** G2 二次确认：重复注册同 appCode 时，首调仅签发令牌、不覆盖；回填令牌且未过期才执行覆盖。单实例内存态，重启即失效（调用方重新发起即可）。 */
    private final Map<String, PendingToken> pendingConfirmations = new ConcurrentHashMap<>();
    private record PendingToken(String appCode, Instant expiresAt) {}

    private static final long CONFIRM_TTL_MINUTES = 5;

    @Transactional
    public CallbackRegistrationView register(CallbackRegistrationRequest req, String confirmToken, String actor) {
        String appCode = req.appCode();
        final String actorName = (actor == null || actor.isBlank()) ? "system" : actor;
        Optional<CallbackRegistration> existing = registrationRepository.findByAppCode(appCode);
        if (existing.isPresent()) {
            if (confirmToken == null || confirmToken.isBlank()) {
                // 重复注册首调：签发二次确认令牌，返回既有配置（不覆盖）
                String token = UUID.randomUUID().toString();
                pendingConfirmations.put(token, new PendingToken(appCode, Instant.now().plusSeconds(CONFIRM_TTL_MINUTES * 60)));
                CallbackRegistration e = existing.get();
                return new CallbackRegistrationView(e.getId(), e.getAppCode(), e.getBaseUrl(),
                        e.isEnabled(), e.getCreatedAt(), e.getUpdatedAt(), true, token);
            }
            // 二次确认回填：校验令牌归属与有效期
            PendingToken pt = pendingConfirmations.get(confirmToken);
            if (pt == null || !pt.appCode().equals(appCode) || pt.expiresAt().isBefore(Instant.now())) {
                pendingConfirmations.remove(confirmToken);
                throw new BizException(BizCode.PARAM_INVALID,
                        "回调二次确认令牌无效或已过期，请重新发起注册以覆盖 " + appCode);
            }
            pendingConfirmations.remove(confirmToken);
            CallbackRegistration e = existing.get();
            String before = e.getBaseUrl();
            e.setBaseUrl(req.baseUrl());
            if (req.secret() != null && !req.secret().isBlank()) {
                e.setSecret(req.secret());
            }
            e.setUpdatedAt(Instant.now());
            registrationRepository.save(e);
            auditService.appendCallback(appCode, "-", "CALLBACK_UPDATE", actorName, before, req.baseUrl(),
                    "二次确认覆盖回调配置");
            return new CallbackRegistrationView(e.getId(), e.getAppCode(), e.getBaseUrl(),
                    e.isEnabled(), e.getCreatedAt(), e.getUpdatedAt(), false, null);
        }
        // 首次注册：新建
        CallbackRegistration r = new CallbackRegistration();
        r.setId(UUID.randomUUID().toString());
        r.setAppCode(appCode);
        r.setBaseUrl(req.baseUrl());
        String secret = (req.secret() != null && !req.secret().isBlank())
                ? req.secret() : randomHex(32);
        r.setSecret(secret);
        r.setEnabled(true);
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        registrationRepository.save(r);
        auditService.appendCallback(appCode, "-", "CALLBACK_REGISTER", actorName, null, "REGISTERED",
                "baseUrl=" + req.baseUrl());
        return new CallbackRegistrationView(r.getId(), r.getAppCode(), r.getBaseUrl(),
                r.isEnabled(), r.getCreatedAt(), r.getUpdatedAt(), false, null);
    }

    @Transactional
    public void enqueueOnComplete(String processInstanceId, String businessKey, String tenantId, String result) {
        String appCode = tenantId; // 回调路由键：tenantId 即业务系统 appCode
        Optional<CallbackRegistration> reg = registrationRepository.findByAppCode(appCode);
        if (reg.isEmpty() || !reg.get().isEnabled()) {
            return; // 未注册回调则跳过
        }
        Map<String, Object> payload = Map.of(
                "processInstanceId", processInstanceId,
                "businessKey", businessKey,
                "result", result,
                "tenantId", tenantId,
                "completedAt", Instant.now().toString());
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        CallbackOutbox o = new CallbackOutbox();
        o.setId(UUID.randomUUID().toString());
        o.setMessageId(UUID.randomUUID().toString());
        o.setAppCode(appCode);
        o.setBusinessKey(businessKey);
        o.setEventType("PROCESS_COMPLETED");
        o.setPayload(body);
        o.setStatus(CallbackOutbox.Status.PENDING);
        o.setAttempts(0);
        o.setMaxAttempts(6);
        o.setNextRetryAt(Instant.now());
        o.setCreatedAt(Instant.now());
        outboxRepository.save(o);
    }

    /** 调度器：每 5s 拉取到期待投递记录。 */
    @Scheduled(fixedDelay = 5000)
    public void deliverDue() {
        List<CallbackOutbox> due = outboxRepository.findDue(Instant.now());
        for (CallbackOutbox o : due) {
            deliver(o);
        }
    }

    /** 供测试/手动触发：按 messageId 立即投递一次。 */
    @Transactional
    public void deliverMessage(String messageId) {
        CallbackOutbox o = outboxRepository.findByMessageId(messageId)
                .orElseThrow(() -> new BizException(BizCode.PARAM_INVALID, "outbox 不存在: " + messageId));
        deliver(o);
    }

    @Transactional
    public void deliver(CallbackOutbox o) {
        if (o.getStatus() == CallbackOutbox.Status.SENT
                || o.getStatus() == CallbackOutbox.Status.DEAD) {
            return;
        }
        CallbackRegistration reg = registrationRepository.findByAppCode(o.getAppCode()).orElse(null);
        if (reg == null) {
            o.setStatus(CallbackOutbox.Status.DEAD);
            o.setLastError("registration missing");
            outboxRepository.save(o);
            return;
        }
        Instant ts = Instant.now();
        String timestamp = String.valueOf(ts.toEpochMilli());
        String signature = hmacSha256(reg.getSecret(), timestamp + "." + o.getPayload());
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(reg.getBaseUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Approval-Message-Id", o.getMessageId())
                    .header("X-Approval-Timestamp", timestamp)
                    .header("X-Approval-Signature", signature)
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(o.getPayload()))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                o.setStatus(CallbackOutbox.Status.SENT);
                o.setSentAt(Instant.now());
                auditService.appendCallback(o.getAppCode(), o.getBusinessKey(), "CALLBACK_SENT", "system",
                        "PENDING", "SENT", "messageId=" + o.getMessageId());
            } else {
                fail(o, "HTTP " + resp.statusCode());
            }
        } catch (Exception ex) {
            fail(o, ex.getMessage());
        }
        outboxRepository.save(o);
    }

    private void fail(CallbackOutbox o, String err) {
        o.setAttempts(o.getAttempts() + 1);
        o.setLastError(truncate(err, 1000));
        if (o.getAttempts() >= o.getMaxAttempts()) {
            o.setStatus(CallbackOutbox.Status.DEAD);
            auditService.appendCallback(o.getAppCode(), o.getBusinessKey(), "CALLBACK_FAILED", "system",
                    "RETRY", "DEAD", "messageId=" + o.getMessageId() + " attempts=" + o.getAttempts());
            // 死信告警：通知运维人工介入（webhook 未配置时降级为日志）
            webhookNotifyService.alert("【审批平台告警】回调投递死信: businessKey=" + o.getBusinessKey()
                    + " appCode=" + o.getAppCode() + " messageId=" + o.getMessageId()
                    + " err=" + o.getLastError());
        } else {
            o.setStatus(CallbackOutbox.Status.PENDING);
            o.setNextRetryAt(Instant.now().plusSeconds(backoff(o.getAttempts())));
        }
    }

    private long backoff(int attempts) {
        return Math.min(30L * (1L << Math.min(attempts, 6)), 600L); // 30s 起指数退避，封顶 10 分钟
    }

    /**
     * 回调投递统计（原型「回调管理」KPI）：成功/失败/重试中/成功率/平均耗时。
     *
     * @param domains 允许统计的域集合（G3）；含 {@code "*"} 表示不限（超管 / dev）。
     *                由此前恒取全库改为按域收敛——否则多业务方接入后任一方都能读到别方的
     *                成功率与积压量
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> outboxStats(List<String> domains) {
        List<CallbackOutbox> all = domains.contains("*")
                ? outboxRepository.findAll()
                : outboxRepository.findByAppCodeIn(domains);
        long sent = all.stream().filter(o -> o.getStatus() == CallbackOutbox.Status.SENT).count();
        long dead = all.stream().filter(o -> o.getStatus() == CallbackOutbox.Status.DEAD).count();
        long pending = all.stream().filter(o -> o.getStatus() == CallbackOutbox.Status.PENDING).count();
        long total = all.size();
        double avgCostMs = all.stream()
                .filter(o -> o.getStatus() == CallbackOutbox.Status.SENT)
                .filter(o -> o.getSentAt() != null)
                .mapToLong(o -> java.time.Duration.between(o.getCreatedAt(), o.getSentAt()).toMillis())
                .average().orElse(0);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("sent", sent);
        m.put("dead", dead);
        m.put("pending", pending);
        m.put("successRate", total > 0 ? (double) sent / total : null);
        m.put("avgCostMs", avgCostMs);
        return m;
    }

    /** 测试请求：写入一条 TEST 事件并立即投递一次，返回投递结果（原型「测试」按钮）。 */
    @Transactional
    public Map<String, Object> sendTestCallback(String appCode) {
        CallbackRegistration reg = registrationRepository.findByAppCode(appCode)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "回调配置不存在: " + appCode));
        CallbackOutbox o = new CallbackOutbox();
        o.setId(UUID.randomUUID().toString());
        o.setMessageId(UUID.randomUUID().toString());
        o.setAppCode(appCode);
        o.setBusinessKey("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        o.setEventType("TEST");
        try {
            o.setPayload(objectMapper.writeValueAsString(Map.of(
                    "type", "TEST",
                    "message", "审批平台连通性测试",
                    "sentAt", Instant.now().toString())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        o.setStatus(CallbackOutbox.Status.PENDING);
        o.setAttempts(0);
        o.setMaxAttempts(1);
        o.setNextRetryAt(Instant.now());
        o.setCreatedAt(Instant.now());
        outboxRepository.save(o);
        deliver(o);
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("messageId", o.getMessageId());
        r.put("status", o.getStatus().name());
        r.put("lastError", o.getLastError());
        return r;
    }

    /**
     * 投递记录查询（G3）。
     *
     * @param domains 允许查询的域集合；含 {@code "*"} 表示不限（超管 / dev）。由
     *                {@code SecurityGuard.resolveDomainFilter} 给出，不接受调用方自报
     */
    public List<OutboxView> queryOutbox(List<String> domains, String businessKey) {
        boolean unrestricted = domains.contains("*");
        List<CallbackOutbox> list;
        if (businessKey != null) {
            list = outboxRepository.findByBusinessKey(businessKey);
        } else if (unrestricted) {
            list = outboxRepository.findAll();
        } else {
            list = outboxRepository.findByAppCodeIn(domains);
        }
        List<OutboxView> views = new ArrayList<>();
        for (CallbackOutbox o : list) {
            if (!unrestricted && !domains.contains(o.getAppCode())) {
                continue; // businessKey 路径也要按域收敛
            }
            views.add(new OutboxView(o.getId(), o.getMessageId(), o.getAppCode(), o.getBusinessKey(),
                    o.getEventType(), o.getStatus().name(), o.getAttempts(), o.getCreatedAt(),
                    o.getSentAt(), o.getLastError()));
        }
        return views;
    }

    public static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verifySignature(String secret, String timestamp, String body, String signature) {
        return hmacSha256(secret, timestamp + "." + body).equals(signature);
    }

    public List<CallbackRegistrationView> listRegistrations() {
        List<CallbackRegistrationView> views = new ArrayList<>();
        for (CallbackRegistration r : registrationRepository.findAll()) {
            views.add(new CallbackRegistrationView(r.getId(), r.getAppCode(), r.getBaseUrl(),
                    r.isEnabled(), r.getCreatedAt(), r.getUpdatedAt(), false, null));
        }
        return views;
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new java.security.SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
