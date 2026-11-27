package com.cim.rms.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * IF-M2 PPID 解析端点的服务身份过滤器（镜像 IAM {@code DirectoryApiKeyFilter} 口径）。
 *
 * <p>仅拦 {@code GET /api/v1/rms/integration/recipe-ppid}，校验请求头
 * {@code X-Integration-Key}（配置 {@code cim.rms.integration.api-key}，与调用方
 * MDS 的 {@code cim.rms.integration-key} 同值）。常量时间比较避免时序侧信道；
 * 未配置密钥 → {@code 503}（安全默认，不静默放行）。安全链对该路径
 * {@code permitAll} 后由本过滤器守门——认证关闭的 dev 态同样受保护。</p>
 */
@Slf4j
@Component
public class IntegrationApiKeyFilter extends OncePerRequestFilter {

    /** 请求头名（调用方侧配置同名）。 */
    public static final String HEADER = "X-Integration-Key";

    private static final String PATH = "/api/v1/rms/integration/recipe-ppid";

    private final ObjectMapper objectMapper;

    private final String apiKey;

    public IntegrationApiKeyFilter(ObjectMapper objectMapper,
                                   @Value("${cim.rms.integration.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!StringUtils.hasText(apiKey)) {
            reject(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "PPID 解析 API 未启用（未配置 cim.rms.integration.api-key）");
            return;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, apiKey)) {
            log.warn("[integration-api] 拒绝未授权访问 {} {}", request.getMethod(), request.getRequestURI());
            reject(response, HttpStatus.UNAUTHORIZED.value(), "无效或缺省 X-Integration-Key");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorBody(status, msg)));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private record ErrorBody(int code, String msg) {
    }
}
