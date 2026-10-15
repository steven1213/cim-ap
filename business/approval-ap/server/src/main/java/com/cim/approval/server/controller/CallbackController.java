package com.cim.approval.server.controller;

import com.cim.approval.server.dto.CallbackRegistrationRequest;
import com.cim.approval.server.dto.CallbackRegistrationView;
import com.cim.approval.server.dto.OutboxView;
import com.cim.approval.server.service.CallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/approval/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    private final CallbackService callbackService;

    @PostMapping("/register")
    public CallbackRegistrationView register(@RequestBody CallbackRegistrationRequest req) {
        return callbackService.register(req);
    }

    @GetMapping
    public List<CallbackRegistrationView> list() {
        return callbackService.listRegistrations();
    }

    @GetMapping("/outbox")
    public List<OutboxView> outbox(@RequestParam(required = false) String appCode,
                                   @RequestParam(required = false) String businessKey) {
        return callbackService.queryOutbox(appCode, businessKey);
    }

    /** 手动触发单次投递（测试/运维用）。 */
    @PostMapping("/deliver/{messageId}")
    public void deliver(@PathVariable String messageId) {
        callbackService.deliverMessage(messageId);
    }
}
