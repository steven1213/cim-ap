package com.cim.approval.server.controller;

import com.cim.approval.server.security.CurrentUserResolver;
import com.cim.approval.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 通知流。认证态经 {@code ?token=<JWT>} 注入 Authorization 头完成鉴权
 * （EventSource 无法自带 header，见 ApprovalSecurityConfig 注入过滤器），
 * 订阅用户强制取 JWT 身份；dev 态回退 userId 参数。
 */
@RestController
@RequestMapping("/api/v1/approval")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUser;

    @GetMapping("/notifications/stream")
    public SseEmitter stream(@RequestParam(required = false) String userId) {
        return notificationService.subscribe(currentUser.resolve(userId));
    }
}
