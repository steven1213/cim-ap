package com.cim.approval.server.controller;

import com.cim.approval.server.dto.CallbackRegistrationRequest;
import com.cim.approval.server.dto.CallbackRegistrationView;
import com.cim.approval.server.dto.OutboxView;
import com.cim.approval.server.security.CurrentUserResolver;
import com.cim.approval.server.security.SecurityGuard;
import com.cim.approval.server.service.CallbackService;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 回调配置与投递监控（G1 + G3）。
 * <ul>
 *   <li>G1：注册/测试/重投/查看均要求 {@code approval:callback:manage}；dev 放行态默认跳过、dev-enforce 可开启 deny 路径。</li>
 *   <li>G3：回调与配置按 appCode 收敛；开放 API 强制以令牌绑定域为准，跨域访问 403。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/approval/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    private final CallbackService callbackService;
    private final CurrentUserResolver currentUser;
    private final SecurityGuard securityGuard;

    @PostMapping("/register")
    public Result<CallbackRegistrationView> register(@RequestBody CallbackRegistrationRequest req,
                                                     @RequestParam(required = false) String actor,
                                                     @RequestParam(required = false) String confirmToken) {
        String actorVal = currentUser.resolveNullable(actor);
        securityGuard.assertPermission(com.cim.approval.server.security.ApprovalPermissions.CALLBACK_MANAGE, actorVal);
        securityGuard.assertAppCodeScope(req.appCode());
        return Result.ok(callbackService.register(req, confirmToken, actorVal));
    }

    @GetMapping
    public Result<List<CallbackRegistrationView>> list(@RequestParam(required = false) String appCode) {
        // G3：未指定 appCode 时收敛为调用方的授权域，而不是「列出全部」
        List<String> domains = securityGuard.resolveDomainFilter(appCode);
        List<CallbackRegistrationView> all = callbackService.listRegistrations();
        if (!domains.contains("*")) {
            all = all.stream().filter(v -> domains.contains(v.appCode())).toList();
        }
        return Result.ok(all);
    }

    @GetMapping("/outbox")
    public Result<List<OutboxView>> outbox(@RequestParam(required = false) String appCode,
                                           @RequestParam(required = false) String businessKey) {
        List<String> domains = securityGuard.resolveDomainFilter(appCode);
        return Result.ok(callbackService.queryOutbox(domains, businessKey));
    }

    /** 手动触发单次投递（测试/运维用）。 */
    @PostMapping("/deliver/{messageId}")
    public Result<Void> deliver(@PathVariable String messageId,
                                @RequestParam(required = false) String actor) {
        securityGuard.assertPermission(com.cim.approval.server.security.ApprovalPermissions.CALLBACK_MANAGE,
                currentUser.resolveNullable(actor));
        callbackService.deliverMessage(messageId);
        return Result.ok();
    }

    /** 回调投递统计：成功/失败/重试中/成功率/平均耗时（看板 KPI）。 */
    @GetMapping("/outbox/stats")
    public Result<Map<String, Object>> outboxStats() {
        return Result.ok(callbackService.outboxStats());
    }

    /** 测试请求：向指定 appCode 发送一条 TEST 事件并立即投递，返回投递结果。 */
    @PostMapping("/test/{appCode}")
    public Result<Map<String, Object>> testCallback(@PathVariable String appCode,
                                                    @RequestParam(required = false) String actor) {
        securityGuard.assertPermission(com.cim.approval.server.security.ApprovalPermissions.CALLBACK_MANAGE,
                currentUser.resolveNullable(actor));
        // G3：开放 API 下 appCode 必须等于令牌绑定域，认证态下须在授权域内（越域 403）
        securityGuard.assertAppCodeScope(appCode);
        return Result.ok(callbackService.sendTestCallback(appCode));
    }
}
