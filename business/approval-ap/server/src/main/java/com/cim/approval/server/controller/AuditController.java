package com.cim.approval.server.controller;

import com.cim.approval.server.domain.ApprovalAudit;
import com.cim.approval.server.dto.AuditView;
import com.cim.approval.server.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/approval/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public List<AuditView> list(@RequestParam String businessKey) {
        List<AuditView> views = new ArrayList<>();
        for (ApprovalAudit a : auditService.listByBusinessKey(businessKey)) {
            views.add(new AuditView(a.getId(), a.getSeq(), a.getBusinessKey(), a.getProcessInstanceId(),
                    a.getTaskId(), a.getAction(), a.getActor(), a.getBeforeState(), a.getAfterState(),
                    a.getDetail(), a.getPrevHash(), a.getHash(), a.getCreatedAt()));
        }
        return views;
    }
}
