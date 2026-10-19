package com.cim.approval.server.controller;

import com.cim.approval.server.notification.WeComSignService;
import com.cim.approval.server.notification.WeComSignService.Verified;
import com.cim.approval.server.security.CurrentUserResolver;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
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
 * 企微免登（G8 / D9）。
 *
 * <p>生成仅绑定当前用户+任务的短期签名链接；企微内置浏览器打开回调页携带令牌，后端核销后前端免登录进入办理。
 * 令牌过期/篡改给出明确引导。</p>
 */
@RestController
@RequestMapping("/api/v1/approval")
@RequiredArgsConstructor
public class WeComController {

    private final WeComSignService signService;
    private final CurrentUserResolver currentUser;

    /** 生成企微免登链接（绑定当前操作人 + 指定任务）。 */
    @PostMapping("/tasks/{taskId}/wecom-link")
    public Result<Map<String, String>> createLink(@PathVariable String taskId,
                                                  @RequestParam(required = false) String userId) {
        String actor = currentUser.resolve(userId);
        String token = signService.generate(actor, taskId);
        return Result.ok(Map.of(
                "token", token,
                "link", "/wecom/callback?token=" + token));
    }

    /** 核销免登令牌：校验通过返回 userId + taskId；过期/篡改返回明确错误。 */
    @GetMapping("/wecom/callback")
    public Result<Map<String, Object>> callback(@RequestParam String token) {
        Verified v = signService.verify(token);
        if (v.expired()) {
            throw new BizException(BizCode.PARAM_INVALID, "免登链接已过期，请重新获取");
        }
        return Result.ok(Map.of("userId", v.userId(), "taskId", v.taskId(), "valid", true));
    }
}
