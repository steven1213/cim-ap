package com.cim.approval.server.controller;

import com.cim.approval.server.dto.CallbackRegistrationRequest;
import com.cim.approval.server.dto.CallbackRegistrationView;
import com.cim.approval.server.dto.OutboxView;
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
 * 回调配置与投递监控。响应统一 {@link Result} 封装；
 * 统计（成功率/平均耗时）与测试请求对齐原型「回调管理」面板。
 */
@RestController
@RequestMapping("/api/v1/approval/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    private final CallbackService callbackService;

    @PostMapping("/register")
    public Result<CallbackRegistrationView> register(@RequestBody CallbackRegistrationRequest req) {
        return Result.ok(callbackService.register(req));
    }

    @GetMapping
    public Result<List<CallbackRegistrationView>> list() {
        return Result.ok(callbackService.listRegistrations());
    }

    @GetMapping("/outbox")
    public Result<List<OutboxView>> outbox(@RequestParam(required = false) String appCode,
                                           @RequestParam(required = false) String businessKey) {
        return Result.ok(callbackService.queryOutbox(appCode, businessKey));
    }

    /** 手动触发单次投递（测试/运维用）。 */
    @PostMapping("/deliver/{messageId}")
    public Result<Void> deliver(@PathVariable String messageId) {
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
    public Result<Map<String, Object>> testCallback(@PathVariable String appCode) {
        return Result.ok(callbackService.sendTestCallback(appCode));
    }
}
