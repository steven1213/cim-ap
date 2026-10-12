package com.cim.rms.server.console;

import com.cim.system.permission.SysPermission;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRolePerm;
import com.cim.system.role.SysRolePermRepository;
import com.cim.system.role.SysRoleRepository;
import com.cim.system.role.SysUserRole;
import com.cim.system.role.SysUserRoleRepository;
import com.cim.system.support.EnableStatus;
import com.cim.system.user.SysUser;
import com.cim.system.user.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RMS 控制台种子服务：把 {@link RmsConsoleCatalog} 的目录（权限码 / 角色 / 角色授权 / 管理员档案）
 * 幂等写入本 ap 的库（{@code sys_permission} / {@code sys_role*} / {@code sys_user}）。
 *
 * <p><b>幂等口径：已存在即跳过</b>——管理端在线维护过的权限/角色/授权不被重启冲掉；仅「首次出现」时写入。
 * 授权同理：某角色「尚无任何授权」时才写，避免覆盖管理端调整。</p>
 *
 * <p><b>为什么用 Java 而非 Flyway 种子 SQL</b>：与平台 {@code I18nSeedLoader} / IAM {@code IamConsoleSeedService}
 * 同源做法。走 SQL 需为每种方言各写一份且无法复用实体校验；角色父子关系里父 ID 由 {@code IdGenerator}
 * 生成，<b>只有运行期才拿得到</b>，SQL 种子无法表达。Java 侧按唯一键查找即天然幂等。</p>
 *
 * <p><b>直接调仓储而非服务</b>：{@code cim-system} 的 {@code SysRbacService.assignXxx} 带
 * {@code @PreAuthorize}（ROLE_GRANT/USER_GRANT），启动期无 {@code SecurityContext}，调用会抛
 * {@code AccessDeniedException}；故授权写入直接落关联表仓储。</p>
 *
 * <p><b>失败即失败</b>（不吞异常）：种子没跑成 = 管理员没有任何 RMS 权限，属「启动即故障」，让应用
 * 启动失败比带病运行更容易被发现与修复。幂等，故重启安全；如需临时跳过用 {@code cim.rms.console.seed-enabled=false}。</p>
 */
@Slf4j
@Service
public class RmsConsoleSeedService {

    private final SysPermissionRepository permissionRepository;
    private final SysRoleRepository roleRepository;
    private final SysRolePermRepository rolePermRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysUserRepository userRepository;
    private final RmsConsoleProperties properties;

    public RmsConsoleSeedService(SysPermissionRepository permissionRepository,
                                SysRoleRepository roleRepository,
                                SysRolePermRepository rolePermRepository,
                                SysUserRoleRepository userRoleRepository,
                                SysUserRepository userRepository,
                                RmsConsoleProperties properties) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermRepository = rolePermRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /** 种子结果（供日志与测试断言）。 */
    public record SeedReport(int permissions, int roles, int grants, boolean adminProfileCreated) {
    }

    /** 执行种子（幂等，全在一个事务内）。 */
    @Transactional
    public SeedReport seed() {
        int permissions = seedPermissions();
        Map<String, String> roleIdByCode = seedRoles();
        int grants = seedGrants(roleIdByCode);
        boolean adminCreated = properties.isSeedAdminProfile() && seedAdminProfile(roleIdByCode);
        SeedReport report = new SeedReport(permissions, roleIdByCode.size(), grants, adminCreated);
        log.info("[rms-console] 种子完成：权限 +{} · 角色 {} · 授权 +{} · 管理员档案 {}",
                report.permissions(), report.roles(), report.grants(),
                report.adminProfileCreated() ? "新建" : "已存在");
        return report;
    }

    // ------------------------------------------------------------------ 权限

    private int seedPermissions() {
        int n = 0;
        for (RmsConsoleCatalog.PermDef def : RmsConsoleCatalog.permissions()) {
            if (permissionRepository.findFirstByCodeOrderByCreateTimeAsc(def.code()).isPresent()) {
                continue;
            }
            SysPermission permission = new SysPermission();
            permission.applyCode(def.code());
            permission.setName(def.name());
            permission.setStatus(EnableStatus.ENABLED);
            permissionRepository.save(permission);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ 角色

    private Map<String, String> seedRoles() {
        Map<String, String> idByCode = new LinkedHashMap<>();
        for (RmsConsoleCatalog.RoleDef def : RmsConsoleCatalog.roles()) {
            SysRole role = roleRepository.findFirstByCodeOrderByCreateTimeAsc(def.code()).orElse(null);
            if (role == null) {
                role = new SysRole();
                role.setCode(def.code());
                role.setName(def.name());
                role.setIsSuper(def.superRole());
                role.setSortNo(def.superRole() ? 0 : 10);
                role.setStatus(EnableStatus.ENABLED);
                role = roleRepository.save(role);
            }
            idByCode.put(def.code(), role.getId());
        }
        return idByCode;
    }

    // ------------------------------------------------------------------ 授权

    private int seedGrants(Map<String, String> roleIdByCode) {
        Map<String, String> permIdByCode = new HashMap<>();
        for (SysPermission p : permissionRepository.findAll()) {
            if (p.getCode() != null) {
                permIdByCode.putIfAbsent(p.getCode(), p.getId());
            }
        }
        int n = 0;
        for (RmsConsoleCatalog.RoleDef def : RmsConsoleCatalog.roles()) {
            String roleId = roleIdByCode.get(def.code());
            if (roleId == null) {
                continue;
            }
            // 仅在该角色「尚无授权」时写入，避免覆盖管理端调整
            if (rolePermRepository.findByRoleId(roleId).isEmpty()) {
                for (String code : def.perms()) {
                    String permId = permIdByCode.get(code);
                    if (permId != null) {
                        rolePermRepository.save(SysRolePerm.of(roleId, permId));
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 管理员档案

    private boolean seedAdminProfile(Map<String, String> roleIdByCode) {
        String username = properties.adminUsername();
        SysUser user = userRepository.findFirstByUsernameOrderByCreateTimeAsc(username).orElse(null);
        boolean created = false;
        if (user == null) {
            user = new SysUser();
            user.setUsername(username);
            user.setExternalId(username);
            user.setDisplayName(properties.getAdminDisplayName());
            user.setLang("zh-CN");
            user.setStatus(EnableStatus.ENABLED);
            user = userRepository.save(user);
            created = true;
        }
        String adminRoleId = roleIdByCode.get(RmsConsoleCatalog.ROLE_ADMIN);
        if (adminRoleId != null) {
            String userId = user.getId();
            boolean granted = userRoleRepository.findByUserId(userId).stream()
                    .anyMatch(ur -> adminRoleId.equals(ur.getRoleId()));
            if (!granted) {
                userRoleRepository.save(SysUserRole.of(userId, adminRoleId));
            }
        }
        return created;
    }
}
