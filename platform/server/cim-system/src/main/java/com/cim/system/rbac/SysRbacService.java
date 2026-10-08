package com.cim.system.rbac;

import com.cim.system.menu.MenuNode;
import com.cim.system.menu.MenuTrees;
import com.cim.system.menu.SysMenu;
import com.cim.system.menu.SysMenuRepository;
import com.cim.system.permission.SysPermission;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRoleMenuRepository;
import com.cim.system.role.SysRolePermRepository;
import com.cim.system.role.SysRoleRepository;
import com.cim.system.role.SysUserRole;
import com.cim.system.role.SysUserRoleRepository;
import com.cim.system.support.EnableStatus;
import com.cim.system.support.PermissionCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 三级授权核心（design.md §2.10 / plan.md T6.2）：用户 → 角色 → 权限，并叠加菜单可见性。
 *
 * <p><b>为什么方法上是「解析」与「授权写入」两类、且鉴权注解只加在后者</b>：
 * {@link #resolveAuthorities(String)} 等方法由 {@code DbLocalAuthorityLoader} 在
 * <b>认证过程中</b>调用（此刻 {@code SecurityContext} 尚未写入），若加 {@code @PreAuthorize}
 * 会把自己锁死。</p>
 */
@Slf4j
@Service
public class SysRbacService {

    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleRepository roleRepository;
    private final SysRolePermRepository rolePermRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;

    public SysRbacService(SysUserRoleRepository userRoleRepository,
                          SysRoleRepository roleRepository,
                          SysRolePermRepository rolePermRepository,
                          SysRoleMenuRepository roleMenuRepository,
                          SysPermissionRepository permissionRepository,
                          SysMenuRepository menuRepository) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermRepository = rolePermRepository;
        this.roleMenuRepository = roleMenuRepository;
        this.permissionRepository = permissionRepository;
        this.menuRepository = menuRepository;
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 解析某用户的最终权限（角色 → 权限码；超管短路为全量）。
     *
     * <p><b>无鉴权注解</b>（认证期调用，见类注释）。</p>
     *
     * @param userId {@code sys_user.id}
     * @return 解析结果（无角色时为 {@link ResolvedAuthorities#none()}）
     */
    @Transactional(readOnly = true)
    public ResolvedAuthorities resolveAuthorities(String userId) {
        List<SysRole> roles = enabledRolesOf(userId);
        if (roles.isEmpty()) {
            return ResolvedAuthorities.none();
        }
        if (roles.stream().anyMatch(r -> Boolean.TRUE.equals(r.getIsSuper()))) {
            log.debug("[cim-system] user {} hit super role, expand all permissions", userId);
            return ResolvedAuthorities.superRole(allEnabledPermissionCodes());
        }
        List<String> roleIds = roles.stream().map(SysRole::getId).toList();
        Set<String> permIds = rolePermRepository.findByRoleIdIn(roleIds).stream()
                .map(rp -> rp.getPermId())
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (permIds.isEmpty()) {
            return ResolvedAuthorities.of(Set.of());
        }
        Set<String> codes = permissionRepository.findByIdIn(permIds).stream()
                .filter(p -> p.getStatus() == null || p.getStatus().enabled())
                .map(SysPermission::getCode)
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return ResolvedAuthorities.of(codes);
    }

    /** 某用户的有效（启用）角色。 */
    @Transactional(readOnly = true)
    public List<SysRole> enabledRolesOf(String userId) {
        List<String> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findByIdIn(roleIds).stream()
                .filter(r -> r.getStatus() == null || r.getStatus().enabled())
                .sorted((a, b) -> Integer.compare(nullSafe(a.getSortNo()), nullSafe(b.getSortNo())))
                .toList();
    }

    /**
     * 某用户可见的菜单扁平列表（超管为全部菜单；否则为角色关联菜单的并集）。
     *
     * <p>不受 {@code cim.system.menu.include-buttons} 影响——过滤发生在 {@link #menuTree}。</p>
     */
    @Transactional(readOnly = true)
    public List<SysMenu> resolveMenus(String userId) {
        List<SysRole> roles = enabledRolesOf(userId);
        if (roles.isEmpty()) {
            return List.of();
        }
        if (roles.stream().anyMatch(r -> Boolean.TRUE.equals(r.getIsSuper()))) {
            return menuRepository.findByOrderBySortNoAsc();
        }
        List<String> roleIds = roles.stream().map(SysRole::getId).toList();
        Set<String> menuIds = roleMenuRepository.findByRoleIdIn(roleIds).stream()
                .map(rm -> rm.getMenuId())
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuRepository.findByIdInOrderBySortNoAsc(menuIds);
    }

    /**
     * 构建<b>用户视角</b>的菜单树（先按角色过滤可见菜单，再入树）。
     *
     * @param userId         用户 ID
     * @param includeButtons 是否保留 {@code BUTTON} 节点
     * @return 森林（顶级节点列表）
     */
    @Transactional(readOnly = true)
    public List<MenuNode> menuTree(String userId, boolean includeButtons) {
        return MenuTrees.build(resolveMenus(userId), includeButtons);
    }

    /** 用户已授予的<b>全部</b>角色 ID（含停用角色，供授权界面回显；解析权限走 {@link #enabledRolesOf}）。 */
    @Transactional(readOnly = true)
    public List<String> roleIdsOf(String userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** 角色已授予的权限 ID（供授权界面回显）。 */
    @Transactional(readOnly = true)
    public List<String> permissionIdsOf(String roleId) {
        return rolePermRepository.findByRoleId(roleId).stream()
                .map(rp -> rp.getPermId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** 角色已关联的菜单 ID（供授权界面回显）。 */
    @Transactional(readOnly = true)
    public List<String> menuIdsOf(String roleId) {
        return roleMenuRepository.findByRoleId(roleId).stream()
                .map(rm -> rm.getMenuId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    // -------------------------------------------------------------- 授权写入

    /** 用户的角色授权（先删后插，幂等）。 */
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_GRANT + "')")
    public void assignRoles(String userId, Collection<String> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds == null) {
            return;
        }
        roleIds.stream().filter(Objects::nonNull).distinct()
                .forEach(roleId -> userRoleRepository.save(SysUserRole.of(userId, roleId)));
    }

    /** 角色的权限授权（先删后插，幂等）。 */
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_GRANT + "')")
    public void assignPermissions(String roleId, Collection<String> permIds) {
        rolePermRepository.deleteByRoleId(roleId);
        if (permIds == null) {
            return;
        }
        permIds.stream().filter(Objects::nonNull).distinct()
                .forEach(permId -> rolePermRepository.save(
                        com.cim.system.role.SysRolePerm.of(roleId, permId)));
    }

    /** 角色的菜单可见性授权（先删后插，幂等）。 */
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_GRANT + "')")
    public void assignMenus(String roleId, Collection<String> menuIds) {
        roleMenuRepository.deleteByRoleId(roleId);
        if (menuIds == null) {
            return;
        }
        menuIds.stream().filter(Objects::nonNull).distinct()
                .forEach(menuId -> roleMenuRepository.save(
                        com.cim.system.role.SysRoleMenu.of(roleId, menuId)));
    }

    /** 全部启用权限码（超管展开用）。 */
    @Transactional(readOnly = true)
    public Set<String> allEnabledPermissionCodes() {
        return permissionRepository.findAll().stream()
                .filter(p -> p.getStatus() == null || p.getStatus().enabled())
                .filter(p -> p.getStatus() != EnableStatus.DISABLED)
                .map(SysPermission::getCode)
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
