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
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Transactional
    public CallbackRegistrationView register(CallbackRegistrationRequest req) {
        CallbackRegistration r = new CallbackRegistration();
        r.setId(UUID.randomUUID().toString());
        r.setAppCode(req.appCode());
        r.setBaseUrl(req.baseUrl());
        String secret = (req.secret() != null && !req.secret().isBlank())
                ? req.secret() : randomHex(32);
        r.setSecret(secret);
        r.setEnabled(true);
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        registrationRepository.save(r);
        return new CallbackRegistrationView(r.getId(), r.getAppCode(), r.getBaseUrl(),
                r.isEnabled(), r.getCreatedAt(), r.getUpdatedAt());
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
                auditService.append(o.getBusinessKey(), null, null, "CALLBACK_SENT", "system",
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
            auditService.append(o.getBusinessKey(), null, null, "CALLBACK_FAILED", "system",
                    "RETRY", "DEAD", "messageId=" + o.getMessageId() + " attempts=" + o.getAttempts());
        } else {
            o.setStatus(CallbackOutbox.Status.PENDING);
            o.setNextRetryAt(Instant.now().plusSeconds(backoff(o.getAttempts())));
        }
    }

    private long backoff(int attempts) {
        return Math.min(30L * (1L << Math.min(attempts, 6)), 600L); // 30s 起指数退避，封顶 10 分钟
    }

    public List<OutboxView> queryOutbox(String appCode, String businessKey) {
        List<CallbackOutbox> list = (businessKey != null)
                ? outboxRepository.findByBusinessKey(businessKey)
                : (appCode != null ? outboxRepository.findByAppCode(appCode)
                : outboxRepository.findAll());
        List<OutboxView> views = new ArrayList<>();
        for (CallbackOutbox o : list) {
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
                    r.isEnabled(), r.getCreatedAt(), r.getUpdatedAt()));
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
