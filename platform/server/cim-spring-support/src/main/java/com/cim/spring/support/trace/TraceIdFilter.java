package com.cim.spring.support.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路过滤器：注入/透传 {@code traceId} 到 MDC 与响应头，请求结束清理
 * （见 README §4 / §14 / §24）。
 *
 * <p>优先级最高，保证后续过滤器与业务日志都能拿到 traceId。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TraceContext.TRACE_HEADER);
        String traceId = TraceContext.put(incoming);
        response.setHeader(TraceContext.TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
