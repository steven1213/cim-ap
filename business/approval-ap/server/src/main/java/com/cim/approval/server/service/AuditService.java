package com.cim.approval.server.service;

import com.cim.approval.server.common.HashChainUtil;
import com.cim.approval.server.domain.ApprovalAudit;
import com.cim.approval.server.repository.ApprovalAuditRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 不可篡改审计：顺序哈希链。每条记录 hash = SHA-256(prevHash | 关键字段)，首条 prevHash 取 GENESIS。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final ApprovalAuditRepository auditRepository;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Transactional
    public void append(String businessKey, String processInstanceId, String taskId,
                       String action, String actor, String before, String after, String detail) {
        long seq = auditRepository.countByBusinessKey(businessKey) + 1;
        String prevHash = auditRepository.findTopByBusinessKeyOrderBySeqDesc(businessKey)
                .map(ApprovalAudit::getHash)
                .orElse(HashChainUtil.GENESIS);
        Instant now = Instant.now();
        String hash = HashChainUtil.hashEvent(prevHash, businessKey, action, actor, before, after, detail, now.toEpochMilli());

        ApprovalAudit record = new ApprovalAudit();
        record.setId(UUID.randomUUID().toString());
        record.setSeq(seq);
        record.setBusinessKey(businessKey);
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setAction(action);
        record.setActor(actor);
        record.setBeforeState(before);
        record.setAfterState(after);
        record.setDetail(detail);
        record.setPrevHash(prevHash);
        record.setHash(hash);
        record.setAppCode(resolveAppCode(processInstanceId));
        record.setCreatedAt(now);
        auditRepository.save(record);
    }

    /**
     * 回调类审计（无关联流程实例，仅持 businessKey 与 appCode）。单独出口便于在调用处直接指定业务域。
     */
    @Transactional
    public void appendCallback(String appCode, String businessKey, String action, String actor,
                               String before, String after, String detail) {
        long seq = auditRepository.countByBusinessKey(businessKey) + 1;
        String prevHash = auditRepository.findTopByBusinessKeyOrderBySeqDesc(businessKey)
                .map(ApprovalAudit::getHash)
                .orElse(HashChainUtil.GENESIS);
        Instant now = Instant.now();
        String hash = HashChainUtil.hashEvent(prevHash, businessKey, action, actor, before, after, detail, now.toEpochMilli());

        ApprovalAudit record = new ApprovalAudit();
        record.setId(UUID.randomUUID().toString());
        record.setSeq(seq);
        record.setBusinessKey(businessKey);
        record.setProcessInstanceId(null);
        record.setTaskId(null);
        record.setAction(action);
        record.setActor(actor);
        record.setBeforeState(before);
        record.setAfterState(after);
        record.setDetail(detail);
        record.setPrevHash(prevHash);
        record.setAppCode(appCode);
        record.setHash(hash);
        record.setCreatedAt(now);
        auditRepository.save(record);
    }

    public List<ApprovalAudit> listByBusinessKey(String businessKey) {
        return auditRepository.findByBusinessKeyOrderBySeqAsc(businessKey);
    }

    public List<ApprovalAudit> listByBusinessKeyAndAppCode(String businessKey, String appCode) {
        return auditRepository.findByBusinessKeyAndAppCodeOrderBySeqAsc(businessKey, appCode);
    }

    /**
     * 从流程实例变量解析业务域（tenantId）。先查运行时、回退历史，确保实例结束后仍能定位 appCode（G3）。
     */
    private String resolveAppCode(String processInstanceId) {
        if (processInstanceId == null) {
            return null;
        }
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).includeProcessVariables().singleResult();
        if (pi != null && pi.getProcessVariables() != null) {
            Object v = pi.getProcessVariables().get("tenantId");
            if (v != null) {
                return v.toString();
            }
        }
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).includeProcessVariables().singleResult();
        if (hpi != null && hpi.getProcessVariables() != null) {
            Object v = hpi.getProcessVariables().get("tenantId");
            if (v != null) {
                return v.toString();
            }
        }
        return null;
    }
}
