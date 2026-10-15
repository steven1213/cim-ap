package com.cim.approval.server.service;

import com.cim.approval.server.common.HashChainUtil;
import com.cim.approval.server.domain.ApprovalAudit;
import com.cim.approval.server.repository.ApprovalAuditRepository;
import lombok.RequiredArgsConstructor;
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
        record.setCreatedAt(now);
        auditRepository.save(record);
    }

    public List<ApprovalAudit> listByBusinessKey(String businessKey) {
        return auditRepository.findByBusinessKeyOrderBySeqAsc(businessKey);
    }
}
