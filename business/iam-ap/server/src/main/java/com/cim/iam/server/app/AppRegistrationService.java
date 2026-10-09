package com.cim.iam.server.app;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.token.TokenVersionService;
import com.cim.spring.support.web.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * ap 注册与准入（design.md §8 边界表）。
 *
 * <p>职责：登记业务 ap 接入码、把用户分配到 ap（准入依据）、计算令牌 {@code apps}/{@code roles} claim 的来源。
 * 任何分配/撤销都会 bump 该用户令牌版本，使已签发令牌即时失效（配合 §8.1(g)）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppRegistrationService {

    private final AppRegistrationRepository appRepo;
    private final UserAppAssignmentRepository assignRepo;
    private final IdGenerator idGenerator;
    private final TokenVersionService tokenVersionService;
    private final AuditService auditService;

    @Transactional
    public AppRegistration registerApp(String appCode, String appName, int sortNo) {
        if (appRepo.findByAppCode(appCode).isPresent()) {
            throw BizException.paramInvalid("appCode 已存在: " + appCode);
        }
        AppRegistration app = new AppRegistration();
        app.setId(idGenerator.nextId());
        app.setAppCode(appCode);
        app.setAppName(appName);
        app.setSortNo(sortNo);
        app.setStatus(AppStatus.ENABLED);
        app.setDeleted(false);
        AppRegistration saved = appRepo.save(app);
        auditService.success(AuditType.APP_REGISTERED, null, appCode, "注册应用：" + appName);
        return saved;
    }

    @Transactional
    public AppRegistration updateApp(String appCode, String appName, AppStatus status) {
        AppRegistration app = requireApp(appCode);
        app.setAppName(appName);
        app.setStatus(status);
        AppRegistration saved = appRepo.save(app);
        auditService.success(AuditType.APP_UPDATED, null, appCode,
                "更新应用：" + appName + "，状态 " + status);
        return saved;
    }

    @Transactional
    public void disableApp(String appCode) {
        AppRegistration app = requireApp(appCode);
        app.setStatus(AppStatus.DISABLED);
        appRepo.save(app);
        // 已分配该 app 的用户令牌需失效
        for (UserAppAssignment a : assignRepo.findByAppCode(appCode)) {
            tokenVersionService.bump(a.getUserId());
        }
        log.info("[app-admission] 禁用 app={}，相关用户令牌已 bump", appCode);
    }

    @Transactional
    public void assignUserToApp(String userId, String appCode, Set<String> roles) {
        AppRegistration app = requireApp(appCode);
        if (app.getStatus() != AppStatus.ENABLED) {
            throw BizException.noAdmission("app 未启用: " + appCode);
        }
        UserAppAssignment a = assignRepo.findByUserIdAndAppCode(userId, appCode)
                .orElseGet(() -> {
                    UserAppAssignment t = new UserAppAssignment();
                    t.setId(idGenerator.nextId());
                    t.setUserId(userId);
                    t.setAppCode(appCode);
                    t.setDeleted(false);
                    return t;
                });
        a.setRoles(roles == null || roles.isEmpty() ? null : String.join(",", roles));
        a.setStatus(AppStatus.ENABLED);
        assignRepo.save(a);
        tokenVersionService.bump(userId); // 触发重新签发
        auditService.success(AuditType.ADMISSION_GRANTED, null, userId,
                "授予 " + appCode + " 准入，角色组 " + (a.getRoles() == null ? "无" : a.getRoles()) + "（已强制下线）");
    }

    @Transactional
    public void revokeUserFromApp(String userId, String appCode) {
        assignRepo.findByUserIdAndAppCode(userId, appCode).ifPresent(a -> {
            assignRepo.deleteByUserIdAndAppCode(userId, appCode);
            tokenVersionService.bump(userId); // 已含该 app 的令牌立即失效
            auditService.success(AuditType.ADMISSION_REVOKED, null, userId,
                    "撤销 " + appCode + " 准入（已强制下线）");
        });
    }

    /** 用户可进入的 ap 接入码集合（分配启用 且 app 启用）。 */
    public Set<String> enabledAppsForUser(String userId) {
        List<UserAppAssignment> list = assignRepo.findByUserIdAndStatus(userId, AppStatus.ENABLED);
        Set<String> result = new LinkedHashSet<>();
        for (UserAppAssignment a : list) {
            AppRegistration app = appRepo.findByAppCode(a.getAppCode()).orElse(null);
            if (app != null && app.getStatus() == AppStatus.ENABLED) {
                result.add(a.getAppCode());
            }
        }
        return result;
    }

    /** 用户在各 ap 的粗角色组。 */
    public Map<String, Set<String>> rolesByAppForUser(String userId) {
        List<UserAppAssignment> list = assignRepo.findByUserIdAndStatus(userId, AppStatus.ENABLED);
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (UserAppAssignment a : list) {
            Set<String> roles = a.getRoles() == null || a.getRoles().isBlank()
                    ? Set.of()
                    : Set.of(a.getRoles().split(","));
            map.put(a.getAppCode(), roles);
        }
        return map;
    }

    public List<AppRegistration> listApps() {
        return appRepo.findAll();
    }

    /** 全部准入分配记录（管理面用）。 */
    public List<UserAppAssignment> listAllAssignments() {
        return assignRepo.findAll();
    }

    /**
     * 角色组聚合：{ 接入码 → { 角色名 → 使用该角色的用户数 } }。
     *
     * <p>用于「角色组」页展示各 ap 实际在用的粗角色组分布，避免凭空造字典。</p>
     */
    public Map<String, Map<String, Integer>> roleAggregate() {
        Map<String, Map<String, Integer>> result = new TreeMap<>();
        for (UserAppAssignment a : assignRepo.findAll()) {
            if (a.getRoles() == null || a.getRoles().isBlank()) {
                continue;
            }
            Map<String, Integer> perApp = result.computeIfAbsent(a.getAppCode(), k -> new TreeMap<>());
            for (String r : a.getRoles().split(",")) {
                String role = r.trim();
                if (!role.isEmpty()) {
                    perApp.merge(role, 1, Integer::sum);
                }
            }
        }
        return result;
    }

    /** 用户的准入明细（含角色组），管理面查看/编辑用。 */
    public List<UserAppAssignment> listAssignmentsForUser(String userId) {
        return assignRepo.findByUserIdAndStatus(userId, AppStatus.ENABLED);
    }

    public AppRegistration requireApp(String appCode) {
        return appRepo.findByAppCode(appCode)
                .orElseThrow(() -> BizException.notFound("app 不存在: " + appCode));
    }
}
