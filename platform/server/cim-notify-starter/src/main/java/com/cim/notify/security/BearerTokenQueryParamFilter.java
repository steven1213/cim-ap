package com.cim.notify.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 把 {@code ?token=} 查询参数注入为 {@code Authorization: Bearer} 头。
 *
 * <p>SSE 长连接无法用 {@code EventSource} 设置请求头，只能走查询参数带令牌。
 * 本过滤器在 {@link com.cim.auth.filter.JwtAuthenticationFilter} <b>之前</b>把令牌还原为 Bearer 头，
 * 使既有验签 / 版本 / 黑名单 / 准入判定完全复用，不动认证链路。</p>
 *
 * <p>仅当无 Authorization 头且存在 {@code token} 参数时生效；否则透传。无 token 参数时不改写，零副作用。</p>
 */
public class BearerTokenQueryParamFilter extends OncePerRequestFilter {

    public static final String TOKEN_PARAM = "token";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = request.getParameter(TOKEN_PARAM);
        if ((header == null || !header.startsWith("Bearer ")) && token != null && !token.isBlank()) {
            chain.doFilter(new BearerWrappingRequest(request, "Bearer " + token), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    static class BearerWrappingRequest extends HttpServletRequestWrapper {

        private final String bearer;

        BearerWrappingRequest(HttpServletRequest request, String bearer) {
            super(request);
            this.bearer = bearer;
        }

        @Override
        public String getHeader(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return bearer;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(bearer));
            }
            return super.getHeaders(name);
        }
    }
}
