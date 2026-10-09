package com.cim.iam.server.audit;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.core.port.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审计服务：写入与查询跨实体动作流水。
 *
 * <p><b>为什么用 {@code REQUIRES_NEW}</b>：审计埋点常出现在「业务即将抛异常」的路径上
 * （如登录失败、锁定拒绝、参数校验失败）。若与业务同事务，业务回滚会把审计一并回滚，
 * 导致「失败事件查不到」。独立事务确保审计先落地、且审计自身异常不会污染业务事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;
    private final IdGenerator idGenerator;

    /** 记录一条审计事件（独立事务）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditType type, String actor, String subject, boolean success, String detail) {
        try {
            AuditEvent e = new AuditEvent();
            e.setId(idGenerator.nextId());
            e.setDeleted(false);
            e.setType(type.name());
            e.setActor(actor != null ? actor : currentActor());
            e.setSubject(subject);
            e.setResult(success ? "SUCCESS" : "FAILURE");
            e.setDetail(truncate(detail));
            repository.save(e);
        } catch (Exception ex) {
            // 审计失败绝不影响业务：仅记日志
            log.warn("[audit] 写入失败 type={} actor={} subject={}: {}", type, actor, subject, ex.getMessage());
        }
    }

    public void success(AuditType type, String actor, String subject, String detail) {
        record(type, actor, subject, true, detail);
    }

    public void failure(AuditType type, String actor, String subject, String detail) {
        record(type, actor, subject, false, detail);
    }

    /** 最近事件（可按类型过滤）。 */
    @Transactional(readOnly = true)
    public List<AuditEvent> recent(int limit, String type) {
        PageRequest page = PageRequest.of(0, clamp(limit));
        if (type == null || type.isBlank()) {
            return repository.findAllByOrderByIdDesc(page);
        }
        return repository.findByTypeOrderByIdDesc(type, page);
    }

    /** 最近某对象的事件。 */
    @Transactional(readOnly = true)
    public List<AuditEvent> recentForSubject(String subject, int limit) {
        return repository.findBySubjectOrderByIdDesc(subject, PageRequest.of(0, clamp(limit)));
    }

    @Transactional(readOnly = true)
    public long countByType(AuditType type) {
        return repository.countByType(type.name());
    }

    /** 取当前登录用户标识作为 actor（无登录上下文时返回 null）。 */
    public static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CimUserPrincipal p) {
            return p.username();
        }
        return null;
    }

    private static int clamp(int limit) {
        if (limit <= 0) return 50;
        return Math.min(limit, 500);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 512 ? s : s.substring(0, 512);
    }
}
