package com.cim.notify.transport;

import com.cim.notify.config.NotifyProperties;
import com.cim.spring.support.tenant.TenantContext;
import com.cim.spring.support.web.RawResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * SSE 订阅端点 {@code GET /sys/notify/stream}。
 *
 * <p>以 {@code @Bean} 注册（库 jar 不依赖宿主组件扫描）。主体取自 {@link SecurityContextHolder}，
 * 租户取自 {@link TenantContext}（请求线程已写入）。端点须由宿主 ap 纳入 {@code authenticated()}。
 * dev 直连（{@code cim.notify.dev-accept-query-identity=true}）时允许 {@code ?userId=&tenantId=} 建连。</p>
 */
@RestController
public class SseController {

    public static final String STREAM = "/sys/notify/stream";

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final SseRegistry registry;
    private final NotifyProperties props;

    public SseController(SseRegistry registry, NotifyProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @RawResponse(reason = RawResponse.Reason.STREAM,
            value = "SSE 长连接：连接期内持续推送事件帧，一次性信封不适用")
    @GetMapping(STREAM)
    public SseEmitter stream(HttpServletRequest request) {
        String tenantId = TenantContext.get();
        String userId = null;
        Set<String> roles = Set.of();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            userId = auth.getName();
            roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
        } else if (props.isDevAcceptQueryIdentity()) {
            userId = request.getParameter("userId");
            String tq = request.getParameter("tenantId");
            if (tq != null && !tq.isBlank()) {
                tenantId = tq;
            }
        }

        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "notify stream requires authenticated identity");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notify stream requires tenant");
        }
        return registry.subscribe(tenantId, userId, roles);
    }
}
