package com.cim.rms.server.notification;

import com.cim.rms.server.security.RmsPermissions;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内通知 API（W9）。路径 {@code /api/v1/rms/notifications}。
 *
 * <p>鉴权：{@code /api/**} 已由 {@code RmsSecurityConfig} 强制认证；通知是个人数据，
 * 凡登录用户皆可读写自己的通知，不再叠加 RMS 权限码。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /** 通知列表（当前用户可见，含已读标记；{@code unreadOnly=true} 只回未读）。 */
    @GetMapping
    public Result<NotificationService.NotificationPage> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.ok(service.visible(me(), approver(), integrator(), unreadOnly));
    }

    /** 未读数（顶栏铃铛红点轮询）。 */
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount() {
        return Result.ok(Map.of("count",
                service.visible(me(), approver(), integrator(), true).unread()));
    }

    /** 标记单条已读（幂等）。 */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable String id) {
        service.markRead(id, me());
        return Result.ok(null);
    }

    /** 全部已读（当前可见范围）。 */
    @PostMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.ok(Map.of("marked",
                service.markAllRead(me(), approver(), integrator())));
    }

    // ---------- helpers ----------

    private static String me() {
        String actor = com.cim.rms.server.audit.RmsAuditService.currentActor();
        return actor == null ? "anonymous" : actor;
    }

    private static boolean approver() {
        return com.cim.rms.server.security.AuthorityUtil.has(RmsPermissions.SIGNOFF_APPROVE);
    }

    private static boolean integrator() {
        return com.cim.rms.server.security.AuthorityUtil.has(RmsPermissions.INTEGRATION_OPERATE);
    }
}
