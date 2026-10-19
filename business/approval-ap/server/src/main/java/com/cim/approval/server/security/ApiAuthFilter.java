package com.cim.approval.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cim.approval.server.service.WebhookNotifyService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 开放 API（服务间调用）安全拦截器（G9 / D2）。
 *
 * <p><b>激活条件</b>：{@code approval.api.security.enabled=true} 且请求携带 {@code X-App-Key}。
 * 未携带该头的请求（JWT 用户态、dev 放行态、SSE 等）一律透传，不受影响——确保 G9 仅约束
 * 服务间开放面，不与用户认证冲突。</p>
 *
 * <p><b>校验链</b>（任一失败 → 403 + 告警）：未知 appKey → 签名缺失/错误 → 时间戳超重放窗口
 * → 源 IP 不在白名单 → 超速率配额 → 跨业务域（请求域≠appKey 绑定域）。</p>
 *
 * <p>校验通过后将调用方收敛为单一业务域写入 {@link RequestScopeHolder}，供 {@link SecurityGuard}
 * 实施 G3 跨域拦截。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "approval.api.security", name = "enabled", havingValue = "true")
public class ApiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAuthFilter.class);

    private final ApiSecurityProperties props;
    private final AppKeyRegistry registry;
    private final RateLimiter rateLimiter;
    private final WebhookNotifyService alertSink;

    @Autowired
    public ApiAuthFilter(ApiSecurityProperties props, AppKeyRegistry registry, RateLimiter rateLimiter,
                         WebhookNotifyService alertSink) {
        this.props = props;
        this.registry = registry;
        this.rateLimiter = rateLimiter;
        this.alertSink = alertSink;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅对携带 X-App-Key 的开放 API 调用启用；其余透传
        return request.getHeader("X-App-Key") == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String appKey = request.getHeader("X-App-Key");
        String timestamp = request.getHeader("X-Timestamp");
        String signature = request.getHeader("X-Signature");

        if (!registry.containsAppCode(appKey)) {
            reject(response, "未知 AppKey", "unknown-app-key appCode=" + mask(appKey));
            return;
        }
        if (signature == null || signature.isBlank()) {
            reject(response, "缺少签名", "missing-signature appCode=" + appKey);
            return;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, "时间戳非法", "bad-timestamp appCode=" + appKey);
            return;
        }
        long skew = Math.abs(Instant.now().toEpochMilli() - ts);
        if (skew > props.getReplayWindowMs()) {
            reject(response, "时间戳超出重放窗口", "replay appCode=" + appKey + " skew=" + skew);
            return;
        }
        if (!props.getIpWhitelist().isEmpty()
                && !props.getIpWhitelist().contains(request.getRemoteAddr())) {
            reject(response, "源 IP 不在白名单", "ip-denied ip=" + request.getRemoteAddr());
            return;
        }
        if (!rateLimiter.tryAcquire(appKey, registry.limitFor(appKey))) {
            reject(response, "超过速率配额", "rate-limited appCode=" + appKey);
            return;
        }

        CachedBodyRequestWrapper wrapped = CachedBodyRequestWrapper.from(request);
        String canonical = ts + "|" + request.getMethod() + "|" + request.getRequestURI() + "|"
                + new String(wrapped.getBody(), StandardCharsets.UTF_8);
        String expected = hmac(registry.secretOf(appKey), canonical);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signature);
        } catch (Exception e) {
            reject(response, "签名格式错误", "bad-signature-format appCode=" + appKey);
            return;
        }
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected), provided)) {
            reject(response, "签名校验失败", "bad-signature appCode=" + appKey);
            return;
        }

        // 跨业务域：请求参数中指定的业务域须等于 appKey 绑定域（G3）
        String requestedDomain = request.getParameter("tenantId");
        if (requestedDomain == null) {
            requestedDomain = request.getParameter("appCode");
        }
        if (requestedDomain != null && !requestedDomain.equals(appKey)) {
            reject(response, "跨业务域访问被拒绝",
                    "cross-domain appCode=" + appKey + " requested=" + requestedDomain);
            return;
        }

        RequestScopeHolder.set(RequestScope.openApi(appKey));
        try {
            chain.doFilter(wrapped, response);
        } finally {
            RequestScopeHolder.clear();
        }
    }

    private void reject(HttpServletResponse response, String reason, String alertDetail) throws IOException {
        log.warn("[API-SEC-ALERT] 开放 API 调用被拒: {} :: {}", reason, alertDetail);
        if (alertSink != null) {
            try {
                alertSink.alert("【审批平台开放 API 安全告警】" + reason + " :: " + alertDetail);
            } catch (Exception ignored) {
                // 告警发送失败不阻断主流程
            }
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, reason);
    }

    private static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String mask(String s) {
        if (s == null || s.length() <= 2) {
            return "***";
        }
        return s.substring(0, 2) + "***";
    }

    /** 缓存请求体的包装器：供签名计算与下游 @RequestBody 解析共用，避免重复消费原始流。 */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        static CachedBodyRequestWrapper from(HttpServletRequest request) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream in = request.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
            }
            return new CachedBodyRequestWrapper(request, bos.toByteArray());
        }

        byte[] getBody() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            final ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    // 同步实现，无需监听器
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }
    }
}
