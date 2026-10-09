package com.cim.iam.server.org;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.common.DataOrigin;
import com.cim.iam.server.token.TokenVersionService;
import com.cim.iam.server.watermark.DirectoryWatermarkService;
import com.cim.iam.server.watermark.WatermarkScope;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 组织树维护（identity-directory.md §3.1 / §8 T0-2）。
 *
 * <p>职责：制造组织节点 CRUD、物化路径维护（{@code path}）、<b>跨源保护</b>
 * （AD_SYNCED 节点只读，不被本地编辑）+ 变更后 bump 受影响用户令牌版本。
 * AD 同步器只写自己那份（{@link #upsertAdSynced}），绝不覆盖 IAM 自建节点。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgNodeService {

    private final OrgNodeRepository repository;
    private final UserOrgRepository userOrgRepository;
    private final OrgAppAssignmentRepository grantRepository;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final DirectoryWatermarkService watermarkService;
    private final TokenVersionService tokenVersionService;

    public List<OrgNode> listAll() {
        return repository.findAll();
    }

    public Optional<OrgNode> find(String id) {
        return repository.findById(id);
    }

    public OrgNode require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("组织节点不存在: " + id));
    }

    /** 新建 IAM 自建组织节点（AD 同步层由 {@link #upsertAdSynced} 负责）。 */
    @Transactional
    public OrgNode createManaged(String parentId, String code, String name, OrgNodeType type, int sortNo) {
        if (repository.findBySourceAndCode(DataOrigin.IAM_MANAGED, code).isPresent()) {
            throw BizException.paramInvalid("组织编码已存在: " + code);
        }
        OrgNode parent = parentId == null || parentId.isBlank() ? null : require(parentId);
        OrgNode n = new OrgNode();
        n.setId(idGenerator.nextId());
        n.setDeleted(false);
        n.setParentId(parent == null ? null : parent.getId());
        n.setCode(code);
        n.setName(name);
        n.setNodeType(type);
        n.setSortNo(sortNo);
        n.setSource(DataOrigin.IAM_MANAGED);
        n.setStatus(OrgStatus.ENABLED);
        n.setPath(parent == null ? "/" + n.getId() + "/" : parent.getPath() + n.getId() + "/");
        n.setUpdatedAt(Instant.now());
        OrgNode saved = repository.save(n);
        watermarkService.bump(WatermarkScope.ORG);
        auditService.success(AuditType.ORG_CREATED, null, code,
                "新建组织节点 " + name + "（" + type + "）");
        log.info("[org] 新建组织 code={} name={} type={}", code, name, type);
        return saved;
    }

    /** 更新名称 / 排序 / 状态（仅 IAM_MANAGED）。 */
    @Transactional
    public OrgNode update(String id, String name, Integer sortNo, OrgStatus status) {
        OrgNode n = require(id);
        guardManaged(n, "修改");
        if (name != null && !name.isBlank()) {
            n.setName(name);
        }
        if (sortNo != null) {
            n.setSortNo(sortNo);
        }
        if (status != null) {
            n.setStatus(status);
        }
        n.setUpdatedAt(Instant.now());
        OrgNode saved = repository.save(n);
        watermarkService.bump(WatermarkScope.ORG);
        auditService.success(AuditType.ORG_UPDATED, null, n.getCode(), "更新组织节点 " + n.getName());
        return saved;
    }

    /**
     * 移动节点（改父），并重写整棵子树的物化路径。
     *
     * <p>移动后受影响用户的准入可能变化 → bump 其令牌版本，旧令牌 401 后重签即带新准入。</p>
     */
    @Transactional
    public OrgNode move(String id, String newParentId) {
        OrgNode n = require(id);
        guardManaged(n, "移动");
        OrgNode newParent = newParentId == null || newParentId.isBlank() ? null : require(newParentId);
        if (newParent != null) {
            if (newParent.getId().equals(id)) {
                throw BizException.paramInvalid("不能移动到自身");
            }
            if (newParent.getPath() != null && n.getPath() != null
                    && newParent.getPath().startsWith(n.getPath())) {
                throw BizException.paramInvalid("不能移动到自己的子节点下");
            }
        }

        String oldPath = n.getPath();
        String newPath = newParent == null ? "/" + id + "/" : newParent.getPath() + id + "/";
        n.setParentId(newParent == null ? null : newParent.getId());
        n.setPath(newPath);
        n.setUpdatedAt(Instant.now());
        repository.save(n);

        // 重写子树路径（所有以 oldPath 为前缀的后代）
        List<OrgNode> subtree = repository.findByPathStartingWith(oldPath);
        for (OrgNode d : subtree) {
            if (d.getId().equals(id)) {
                continue;
            }
            d.setPath(newPath + d.getPath().substring(oldPath.length()));
            d.setUpdatedAt(Instant.now());
            repository.save(d);
        }

        Set<String> affected = new LinkedHashSet<>(userIdsInOrg(id, true));
        bumpUsers(affected);
        watermarkService.bump(WatermarkScope.ORG);
        auditService.success(AuditType.ORG_MOVED, null, n.getCode(),
                "移动组织节点 " + n.getName() + "（受影响用户 " + affected.size() + " 已 bump）");
        return n;
    }

    /** 删除节点（仅 IAM_MANAGED；须无子节点、无人员归属、无组织授予）。 */
    @Transactional
    public void delete(String id) {
        OrgNode n = require(id);
        guardManaged(n, "删除");
        if (!repository.findByParentId(id).isEmpty()) {
            throw BizException.paramInvalid("存在子节点，不能删除: " + n.getCode());
        }
        if (!userOrgRepository.findByOrgId(id).isEmpty()) {
            throw BizException.paramInvalid("存在人员归属，请先移除归属: " + n.getCode());
        }
        if (grantRepository.existsByOrgId(id)) {
            throw BizException.paramInvalid("存在组织授予，请先撤销授予: " + n.getCode());
        }
        Set<String> affected = new LinkedHashSet<>(userIdsInOrg(id, true));
        repository.delete(n);
        bumpUsers(affected);
        watermarkService.bump(WatermarkScope.ORG);
        auditService.success(AuditType.ORG_DELETED, null, n.getCode(), "删除组织节点 " + n.getName());
    }

    /**
     * AD 同步（幂等 upsert）：<b>只写 {@link DataOrigin#AD_SYNCED} 节点</b>，绝不触碰 IAM 自建节点。
     */
    @Transactional
    public OrgNode upsertAdSynced(String externalId, String parentExternalId, String code, String name,
                                 OrgNodeType type, int sortNo) {
        OrgNode parent = parentExternalId == null ? null
                : repository.findBySourceAndExternalId(DataOrigin.AD_SYNCED, parentExternalId).orElse(null);
        OrgNode n = repository.findBySourceAndExternalId(DataOrigin.AD_SYNCED, externalId)
                .orElseGet(() -> {
                    OrgNode t = new OrgNode();
                    t.setId(idGenerator.nextId());
                    t.setDeleted(false);
                    t.setSource(DataOrigin.AD_SYNCED);
                    t.setPath("/" + t.getId() + "/");
                    return t;
                });
        // 编码冲突保护：同 source 下 code 已被别的节点占用则退回用 externalId 作 code
        OrgNode byCode = repository.findBySourceAndCode(DataOrigin.AD_SYNCED, code).orElse(null);
        if (byCode != null && !byCode.getId().equals(n.getId())) {
            code = externalId;
        }
        n.setCode(code);
        n.setName(name);
        n.setNodeType(type);
        n.setSortNo(sortNo);
        n.setExternalId(externalId);
        n.setParentId(parent == null ? null : parent.getId());
        n.setStatus(OrgStatus.ENABLED);
        // 路径：父为 null 时自身即根路径
        String oldPath = n.getPath();
        String newPath = parent == null ? "/" + n.getId() + "/" : parent.getPath() + n.getId() + "/";
        n.setPath(newPath);
        n.setUpdatedAt(Instant.now());
        OrgNode saved = repository.save(n);
        // 父变更 → 重写整棵子树的物化路径（与 move 同一套前缀替换逻辑）
        if (oldPath != null && !oldPath.equals(newPath)) {
            for (OrgNode d : repository.findByPathStartingWith(oldPath)) {
                if (d.getId().equals(n.getId())) {
                    continue;
                }
                d.setPath(newPath + d.getPath().substring(oldPath.length()));
                d.setUpdatedAt(Instant.now());
                repository.save(d);
            }
        }
        watermarkService.bump(WatermarkScope.ORG);
        return saved;
    }

    /** 全部后代组织 ID（含自身）。 */
    public List<String> subtreeIds(String orgId) {
        List<String> ids = new ArrayList<>();
        ids.add(orgId);
        for (OrgNode d : repository.findByPathStartingWith("/" + orgId + "/")) {
            if (!d.getId().equals(orgId)) {
                ids.add(d.getId());
            }
        }
        return ids;
    }

    /** 组织内人员 ID（{@code includeChildren} 时含全部后代组织）。 */
    public List<String> userIdsInOrg(String orgId, boolean includeChildren) {
        List<String> orgIds = includeChildren ? subtreeIds(orgId) : List.of(orgId);
        return userOrgRepository.findByOrgIdIn(orgIds).stream()
                .map(UserOrg::getUserId)
                .distinct()
                .toList();
    }

    /** 批量 bump 令牌版本（准入可能变化的用户）。 */
    public void bumpUsers(Collection<String> userIds) {
        for (String uid : userIds) {
            tokenVersionService.bump(uid);
        }
    }

    private void guardManaged(OrgNode n, String action) {
        if (n.getSource() == DataOrigin.AD_SYNCED) {
            throw BizException.paramInvalid("AD 同步的组织不可在 IAM 侧" + action + "（只读）: " + n.getCode());
        }
    }
}
