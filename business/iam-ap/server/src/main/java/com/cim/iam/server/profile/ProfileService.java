package com.cim.iam.server.profile;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.common.DataOrigin;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeRepository;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.org.UserOrg;
import com.cim.iam.server.org.UserOrgRepository;
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
 * 用户档案与组织归属维护（identity-directory.md §3.2 / §3.3 / §8 T0-3）。
 *
 * <p>权威源分工：AD 同步只写 {@code source=AD_SYNCED} 的档案与归属；IAM 自建人员（厂商 / 服务账号）
 * 与手工归属写 {@code IAM_MANAGED}。<b>混源靠 source 隔离，互不覆盖。</b></p>
 *
 * <p>归属变更会 bump 该用户令牌版本 + USER 水位：准入可能变化 → 旧令牌 401 → 重签带新准入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository repository;
    private final UserOrgRepository userOrgRepository;
    private final OrgNodeRepository orgNodeRepository;
    private final OrgNodeService orgNodeService;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final DirectoryWatermarkService watermarkService;
    private final TokenVersionService tokenVersionService;

    /** 归属引用（{@code orgId} + 是否主属）。 */
    public record OrgRef(String orgId, boolean primary) {
    }

    public List<UserProfile> listAll() {
        return repository.findAll();
    }

    public Optional<UserProfile> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    public UserProfile require(String userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> BizException.notFound("用户档案不存在: " + userId));
    }

    public List<UserProfile> findAllByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return repository.findByUserIdIn(userIds);
    }

    /** 新建 IAM 自建档案（设备厂商 / 访客 / 服务账号，AD 里不存在）。 */
    @Transactional
    public UserProfile createManaged(String userId, String displayName, String employeeNo,
                                    String email, String mobile, String jobTitle) {
        if (repository.findByUserId(userId).isPresent()) {
            throw BizException.paramInvalid("用户档案已存在: " + userId);
        }
        UserProfile p = new UserProfile();
        p.setId(idGenerator.nextId());
        p.setDeleted(false);
        p.setUserId(userId);
        p.setDisplayName(displayName);
        p.setEmployeeNo(employeeNo);
        p.setEmail(email);
        p.setMobile(mobile);
        p.setJobTitle(jobTitle);
        p.setStatus(UserStatus.ACTIVE);
        p.setSource(DataOrigin.IAM_MANAGED);
        UserProfile saved = repository.save(p);
        watermarkService.bump(WatermarkScope.USER);
        auditService.success(AuditType.PROFILE_CREATED, null, userId, "新建用户档案（IAM 自建）：" + displayName);
        return saved;
    }

    /**
     * 更新档案。
     *
     * <p>AD 来源的档案只允许改 IAM 自建属性（岗位 {@code jobTitle}），其余字段只读——
     * 避免用户误以为能在 IAM 改 AD 字段（identity-directory.md §7）。</p>
     */
    @Transactional
    public UserProfile updateProfile(String userId, String displayName, String email,
                                     String mobile, String jobTitle) {
        UserProfile p = require(userId);
        if (p.getSource() == DataOrigin.IAM_MANAGED) {
            if (displayName != null && !displayName.isBlank()) {
                p.setDisplayName(displayName);
            }
            if (email != null) {
                p.setEmail(email);
            }
            if (mobile != null) {
                p.setMobile(mobile);
            }
        }
        if (jobTitle != null) {
            p.setJobTitle(jobTitle);
        }
        UserProfile saved = repository.save(p);
        watermarkService.bump(WatermarkScope.USER);
        auditService.success(AuditType.PROFILE_UPDATED, null, userId, "更新用户档案");
        return saved;
    }

    /**
     * 设置用户组织归属（替换式，支持多归属）。
     *
     * <p>只替换 {@code IAM_MANAGED} 的行，AD 同步来的归属不被本地操作触碰（混源隔离）。
     * 校验组织存在；变更后 bump 该用户令牌版本。</p>
     */
    @Transactional
    public void setUserOrgs(String userId, List<OrgRef> refs) {
        List<OrgRef> desired = refs == null ? List.of() : refs;
        Set<String> desiredIds = new LinkedHashSet<>();
        for (OrgRef r : desired) {
            desiredIds.add(r.orgId());
            if (orgNodeRepository.findById(r.orgId()).isEmpty()) {
                throw BizException.paramInvalid("组织不存在: " + r.orgId());
            }
        }
        // 删除本地管理的、不在目标集合中的归属
        for (UserOrg uo : userOrgRepository.findByUserIdAndSource(userId, DataOrigin.IAM_MANAGED)) {
            if (!desiredIds.contains(uo.getOrgId())) {
                userOrgRepository.delete(uo);
            }
        }
        // upsert 目标归属
        for (OrgRef r : desired) {
            UserOrg uo = userOrgRepository.findByUserIdAndOrgId(userId, r.orgId())
                    .orElseGet(() -> {
                        UserOrg t = new UserOrg();
                        t.setId(idGenerator.nextId());
                        t.setDeleted(false);
                        t.setUserId(userId);
                        t.setOrgId(r.orgId());
                        t.setSource(DataOrigin.IAM_MANAGED);
                        return t;
                    });
            uo.setPrimary(r.primary());
            userOrgRepository.save(uo);
        }
        tokenVersionService.bump(userId);
        watermarkService.bump(WatermarkScope.USER);
        auditService.success(AuditType.USER_ORGS_CHANGED, null, userId,
                "设置组织归属 " + desiredIds + "（已强制下线）");
        log.info("[profile] 用户 {} 归属更新为 {}", userId, desiredIds);
    }

    /** 用户所属组织（含 AD 同步与本地自建）。 */
    public List<UserOrg> orgsOfUser(String userId) {
        return userOrgRepository.findByUserId(userId);
    }

    /** 用户的有效组织 ID 集合。 */
    public Set<String> orgIdsOfUser(String userId) {
        Set<String> ids = new LinkedHashSet<>();
        for (UserOrg uo : userOrgRepository.findByUserId(userId)) {
            ids.add(uo.getOrgId());
        }
        return ids;
    }

    /** 用户主属组织 ID（无主属则取首个，均无则 null）。 */
    public String primaryOrgIdOfUser(String userId) {
        List<UserOrg> list = userOrgRepository.findByUserId(userId);
        for (UserOrg uo : list) {
            if (uo.isPrimary()) {
                return uo.getOrgId();
            }
        }
        return list.isEmpty() ? null : list.get(0).getOrgId();
    }

    // ---------- AD 同步写入（只写 AD_SYNCED，绝不覆盖 IAM_MANAGED） ----------

    /** AD 同步 upsert 档案。 */
    @Transactional
    public UserProfile upsertAdProfile(String userId, String employeeNo, String displayName,
                                       String email, String mobile, UserStatus status) {
        UserProfile p = repository.findByUserId(userId).orElseGet(() -> {
            UserProfile t = new UserProfile();
            t.setId(idGenerator.nextId());
            t.setDeleted(false);
            t.setUserId(userId);
            t.setSource(DataOrigin.AD_SYNCED);
            return t;
        });
        if (p.getSource() == DataOrigin.IAM_MANAGED && p.getId() != null
                && repository.findByUserId(userId).isPresent()) {
            // 本地自建档案不被 AD 覆盖（混源隔离）：仅跳过
            return p;
        }
        p.setEmployeeNo(employeeNo);
        p.setDisplayName(displayName);
        p.setEmail(email);
        p.setMobile(mobile);
        p.setStatus(status == null ? UserStatus.ACTIVE : status);
        p.setSource(DataOrigin.AD_SYNCED);
        p.setSyncedAt(Instant.now());
        return repository.save(p);
    }

    /** AD 同步 upsert 归属（幂等；已存在则不重复插入）。 */
    @Transactional
    public void upsertAdMembership(String userId, String orgId) {
        if (userOrgRepository.findByUserIdAndOrgId(userId, orgId).isPresent()) {
            return;
        }
        UserOrg uo = new UserOrg();
        uo.setId(idGenerator.nextId());
        uo.setDeleted(false);
        uo.setUserId(userId);
        uo.setOrgId(orgId);
        uo.setPrimary(false);
        uo.setSource(DataOrigin.AD_SYNCED);
        userOrgRepository.save(uo);
    }

    /** 对账：AD 侧已不见的 AD_SYNCED 档案置为 INACTIVE（不物理删除，保留审计与准入历史）。 */
    @Transactional
    public int deactivateMissingAdProfiles(Set<String> seenUserIds) {
        int n = 0;
        for (UserProfile p : repository.findBySource(DataOrigin.AD_SYNCED)) {
            if (!seenUserIds.contains(p.getUserId()) && p.getStatus() != UserStatus.INACTIVE) {
                p.setStatus(UserStatus.INACTIVE);
                p.setSyncedAt(Instant.now());
                repository.save(p);
                tokenVersionService.bump(p.getUserId());
                n++;
            }
        }
        return n;
    }

    /** 变更档案状态（启用/停用），停用即 bump 用户版本。 */
    @Transactional
    public void setStatus(String userId, UserStatus status) {
        UserProfile p = require(userId);
        p.setStatus(status);
        repository.save(p);
        if (status == UserStatus.INACTIVE) {
            tokenVersionService.bump(userId);
        }
        watermarkService.bump(WatermarkScope.USER);
        auditService.success(AuditType.PROFILE_UPDATED, null, userId, "档案状态 -> " + status);
    }

    /** 组织全路径名（用于展示，如 "A厂/蚀刻车间/蚀刻1线"）。 */
    public String orgPathName(String orgId) {
        OrgNode n = orgNodeRepository.findById(orgId).orElse(null);
        if (n == null || n.getPath() == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String seg : n.getPath().split("/")) {
            if (seg.isBlank()) {
                continue;
            }
            orgNodeRepository.findById(seg).ifPresent(o -> names.add(o.getName()));
        }
        return String.join(" / ", names);
    }
}
