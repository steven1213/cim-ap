package com.cim.iam.server.org;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.watermark.DirectoryWatermarkService;
import com.cim.iam.server.watermark.WatermarkScope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组织级准入授予（identity-directory.md §3.4 / §8 T0-5）。
 *
 * <p>授予/撤销后 <b>bump 受影响用户的令牌版本</b>（该组织含其子组织内全部人员），
 * 旧令牌 401 → 重签即带新准入。与既有 tokenVersion 闭环直接复用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgGrantService {

    private final OrgAppAssignmentRepository repository;
    private final OrgNodeService orgNodeService;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final DirectoryWatermarkService watermarkService;

    public List<OrgAppAssignment> listAll() {
        return repository.findAll();
    }

    public List<OrgAppAssignment> listEnabled() {
        return repository.findByStatus(OrgStatus.ENABLED);
    }

    public List<OrgAppAssignment> listForOrg(String orgId) {
        return repository.findByOrgId(orgId);
    }

    /** 授予（幂等 upsert）：组织 → ap + 粗角色组；{@code includeChildren} 决定是否覆盖子组织。 */
    @Transactional
    public OrgAppAssignment grant(String orgId, String appCode, Set<String> roles, boolean includeChildren) {
        OrgNode org = orgNodeService.require(orgId);
        OrgAppAssignment a = repository.findByOrgIdAndAppCode(orgId, appCode).orElseGet(() -> {
            OrgAppAssignment t = new OrgAppAssignment();
            t.setId(idGenerator.nextId());
            t.setDeleted(false);
            t.setOrgId(orgId);
            t.setAppCode(appCode);
            return t;
        });
        a.setRoles(roles == null || roles.isEmpty() ? null : String.join(",", roles));
        a.setIncludeChildren(includeChildren);
        a.setStatus(OrgStatus.ENABLED);
        repository.save(a);

        Set<String> affected = new LinkedHashSet<>(orgNodeService.userIdsInOrg(orgId, includeChildren));
        orgNodeService.bumpUsers(affected);
        watermarkService.bump(WatermarkScope.USER);
        auditService.success(AuditType.ORG_GRANT_GRANTED, null, appCode,
                "组织 " + org.getName() + " 授予 " + appCode + "，角色组 "
                        + (a.getRoles() == null ? "无" : a.getRoles())
                        + (includeChildren ? "（含子组织）" : "（不含子组织）")
                        + "，受影响用户 " + affected.size());
        log.info("[org-grant] {} -> {} roles={} includeChildren={} affected={}",
                org.getCode(), appCode, a.getRoles(), includeChildren, affected.size());
        return a;
    }

    /** 撤销组织授予（bump 受影响用户）。 */
    @Transactional
    public void revoke(String orgId, String appCode) {
        OrgAppAssignment a = repository.findByOrgIdAndAppCode(orgId, appCode).orElse(null);
        if (a == null) {
            return;
        }
        OrgNode org = orgNodeService.require(orgId);
        Set<String> affected = new LinkedHashSet<>(orgNodeService.userIdsInOrg(orgId, a.isIncludeChildren()));
        repository.delete(a);
        orgNodeService.bumpUsers(affected);
        watermarkService.bump(WatermarkScope.USER);
        auditService.success(AuditType.ORG_GRANT_REVOKED, null, appCode,
                "组织 " + org.getName() + " 撤销 " + appCode + " 授予，受影响用户 " + affected.size());
    }
}
