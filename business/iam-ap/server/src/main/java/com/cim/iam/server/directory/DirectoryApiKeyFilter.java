package com.cim.iam.server.directory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 目录只读 API 的服务身份过滤器（identity-directory.md §5.3 方案 A）。
 *
 * <p>仅拦截 {@code /api/v1/directory/**}，校验请求头 {@code X-Directory-Key}。
 * 常量时间比较避免时序侧信道。未配置密钥 → {@code 503}（安全默认，不静默放行）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectoryApiKeyFilter extends OncePerRequestFilter {

    /** 请求头名（业务 ap 侧配置同名）。 */
    public static final String HEADER = "X-Directory-Key";

    private static final String PREFIX = "/api/v1/directory/";

    private final DirectoryApiProperties props;
    private final ObjectMapper objectMapper;

    /** 仅对目录 API 生效。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!props.isEnabled()) {
            reject(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "目录 API 未启用（未配置 cim.iam.directory.api.key）");
            return;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, props.getKey())) {
            log.warn("[directory-api] 拒绝未授权访问 {} {}", request.getMethod(), request.getRequestURI());
            reject(response, HttpStatus.UNAUTHORIZED.value(), "无效或缺省 X-Directory-Key");
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
