package com.cim.iam.server.console;

import com.cim.i18n.model.I18nScope;
import com.cim.i18n.model.LocaleStatus;
import com.cim.i18n.model.SysLocale;
import com.cim.i18n.model.SysLocaleRepository;
import com.cim.i18n.service.I18nService;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.system.menu.MenuType;
import com.cim.system.menu.SysMenu;
import com.cim.system.menu.SysMenuRepository;
import com.cim.system.permission.SysPermission;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRoleMenu;
import com.cim.system.role.SysRoleMenuRepository;
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
import java.util.List;
import java.util.Map;

/**
 * IAM 控制台种子服务：把 {@link IamConsoleCatalog} 的目录（菜单树 / 权限码 / 角色 / 译文）
 * 幂等写入本 ap 的库。
 *
 * <p><b>幂等口径：已存在即跳过</b>——管理端在线维护过的菜单/权限/译文不被重启冲掉；
 * 仅「首次出现」时写入。授权同理：某角色「尚无任何授权」时才写，避免覆盖管理端调整。</p>
 *
 * <p><b>为什么用 Java 而不是 Flyway 种子 SQL</b>：与平台 {@code I18nSeedLoader} 同源做法。
 * 走 SQL 需为每种方言各写一份且无法复用实体校验；菜单父子关系里父 ID 由 {@code IdGenerator}
 * 生成，<b>只有运行期才拿得到</b>，SQL 种子无法表达。Java 侧按唯一键查找即天然幂等。</p>
 *
 * <p><b>直接调仓储而非服务</b>：{@code cim-system} 的 {@code SysRbacService.assignXxx} 带
 * {@code @PreAuthorize}（ROLE_GRANT/USER_GRANT），启动期无 {@code SecurityContext}，
 * 调用会抛 {@code AccessDeniedException}；故授权写入直接落关联表仓储。</p>
 */
@Slf4j
@Service
public class IamConsoleSeedService {

    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;
    private final SysRoleRepository roleRepository;
    private final SysRolePermRepository rolePermRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysUserRepository userRepository;
    private final SysLocaleRepository localeRepository;
    private final I18nService i18nService;
    private final IamConsoleProperties properties;
    private final IamAuthProperties authProperties;

    public IamConsoleSeedService(SysPermissionRepository permissionRepository,
                                 SysMenuRepository menuRepository,
                                 SysRoleRepository roleRepository,
                                 SysRolePermRepository rolePermRepository,
                                 SysRoleMenuRepository roleMenuRepository,
                                 SysUserRoleRepository userRoleRepository,
                                 SysUserRepository userRepository,
                                 SysLocaleRepository localeRepository,
                                 I18nService i18nService,
                                 IamConsoleProperties properties,
                                 IamAuthProperties authProperties) {
        this.permissionRepository = permissionRepository;
        this.menuRepository = menuRepository;
        this.roleRepository = roleRepository;
        this.rolePermRepository = rolePermRepository;
        this.roleMenuRepository = roleMenuRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.localeRepository = localeRepository;
        this.i18nService = i18nService;
        this.properties = properties;
        this.authProperties = authProperties;
    }

    /** 种子结果（供日志与测试断言）。 */
    public record SeedReport(int permissions, int menus, int roles, int grants,
                             boolean adminProfileCreated, int zhTexts, int enTexts) {
    }

    /** 菜单种子中间结果。 */
    private record MenuSeed(Map<String, String> idByKey, int created) {
    }

    /** 执行种子（幂等，全在一个事务内）。 */
    @Transactional
    public SeedReport seed() {
        int permissions = seedPermissions();
        MenuSeed menus = seedMenus();
        Map<String, String> roleIdByCode = seedRoles();
        int grants = seedGrants(roleIdByCode, menus.idByKey());
        boolean adminCreated = properties.isSeedAdminProfile() && seedAdminProfile(roleIdByCode);
        int zhTexts = seedTranslationsZh();
        int enTexts = seedTranslationsEn();
        SeedReport report = new SeedReport(permissions, menus.created(), roleIdByCode.size(),
                grants, adminCreated, zhTexts, enTexts);
        log.info("[iam-console] 种子完成：权限 +{} · 菜单 +{} · 角色 {} · 授权 +{} · 管理员档案 {} · 译文 zh +{}/en +{}",
                report.permissions(), report.menus(), report.roles(), report.grants(),
                report.adminProfileCreated() ? "新建" : "已存在", report.zhTexts(), report.enTexts());
        return report;
    }

    // ------------------------------------------------------------------ 权限

    private int seedPermissions() {
        int n = 0;
        for (IamConsoleCatalog.PermDef def : IamConsoleCatalog.permissions()) {
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

    // ------------------------------------------------------------------ 菜单

    private MenuSeed seedMenus() {
        Map<String, String> idByI18nCode = new HashMap<>();
        for (SysMenu existing : menuRepository.findByOrderBySortNoAsc()) {
            if (existing.getI18nCode() != null) {
                idByI18nCode.put(existing.getI18nCode(), existing.getId());
            }
        }
        Map<String, String> idByKey = new LinkedHashMap<>();
        int created = 0;
        for (IamConsoleCatalog.MenuDef def : IamConsoleCatalog.menus()) {
            String id = idByI18nCode.get(def.i18nCode());
            if (id == null) {
                SysMenu menu = new SysMenu();
                menu.setI18nCode(def.i18nCode());
                menu.setType(def.type());
                menu.setPath(def.path());
                menu.setComponent(def.component());
                menu.setIcon(def.icon());
                menu.setPermCode(def.permCode());
                menu.setSortNo(def.sortNo());
                menu.setVisible(def.type() != MenuType.BUTTON);
                menu.setStatus(EnableStatus.ENABLED);
                menu.setParentId(def.parentKey() == null ? null : idByKey.get(def.parentKey()));
                id = menuRepository.save(menu).getId();
                idByI18nCode.put(def.i18nCode(), id);
                created++;
            }
            idByKey.put(def.key(), id);
        }
        return new MenuSeed(idByKey, created);
    }

    // ------------------------------------------------------------------ 角色

    private Map<String, String> seedRoles() {
        Map<String, String> idByCode = new LinkedHashMap<>();
        for (IamConsoleCatalog.RoleDef def : IamConsoleCatalog.roles()) {
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

    private int seedGrants(Map<String, String> roleIdByCode, Map<String, String> menuIdByKey) {
        Map<String, String> permIdByCode = new HashMap<>();
        for (SysPermission p : permissionRepository.findAll()) {
            if (p.getCode() != null) {
                permIdByCode.putIfAbsent(p.getCode(), p.getId());
            }
        }
        int n = 0;
        for (IamConsoleCatalog.RoleDef def : IamConsoleCatalog.roles()) {
            String roleId = roleIdByCode.get(def.code());
            if (roleId == null) {
                continue;
            }
            // 权限授权：仅在该角色「尚无授权」时写入，避免覆盖管理端调整
            if (rolePermRepository.findByRoleId(roleId).isEmpty()) {
                for (String code : def.perms()) {
                    String permId = permIdByCode.get(code);
                    if (permId != null) {
                        rolePermRepository.save(SysRolePerm.of(roleId, permId));
                        n++;
                    }
                }
            }
            // 菜单可见性：超管全量（含按钮节点）；非超管仅导航节点（DIR/MENU）
            if (roleMenuRepository.findByRoleId(roleId).isEmpty()) {
                for (IamConsoleCatalog.MenuDef menuDef : IamConsoleCatalog.menus()) {
                    if (!def.superRole() && menuDef.type() == MenuType.BUTTON) {
                        continue;
                    }
                    String menuId = menuIdByKey.get(menuDef.key());
                    if (menuId != null) {
                        roleMenuRepository.save(SysRoleMenu.of(roleId, menuId));
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 管理员档案

    private boolean seedAdminProfile(Map<String, String> roleIdByCode) {
        String username = adminUsername();
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
        String adminRoleId = roleIdByCode.get(IamConsoleCatalog.ROLE_ADMIN);
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

    private String adminUsername() {
        IamAuthProperties.Bootstrap bootstrap = authProperties.getBootstrap();
        if (bootstrap != null && bootstrap.getAdminUsername() != null
                && !bootstrap.getAdminUsername().isBlank()) {
            return bootstrap.getAdminUsername();
        }
        return "admin";
    }

    // ------------------------------------------------------------------ 译文

    private int seedTranslationsZh() {
        ensureLocale("zh-CN", "简体中文", true, 0);
        return i18nService.seedMessages("zh-CN", IamConsoleCatalog.zhMap(), I18nScope.USER, "iam");
    }

    private int seedTranslationsEn() {
        if (!properties.isSeedEnglish()) {
            return 0;
        }
        ensureLocale("en-US", "English", false, 10);
        return i18nService.seedMessages("en-US", IamConsoleCatalog.enMap(), I18nScope.USER, "iam");
    }

    private void ensureLocale(String code, String name, boolean asDefault, int sortNo) {
        if (localeRepository.findByCode(code).isPresent()) {
            return;
        }
        SysLocale locale = new SysLocale();
        locale.setCode(code);
        locale.setName(name);
        locale.setIsDefault(asDefault);
        locale.setSortNo(sortNo);
        locale.setStatus(LocaleStatus.ENABLED);
        i18nService.saveLocale(locale);
    }

    /** 供测试/排障：目录条目（不含落库）。 */
    public List<IamConsoleCatalog.MenuDef> catalogMenus() {
        return IamConsoleCatalog.menus();
    }
}
