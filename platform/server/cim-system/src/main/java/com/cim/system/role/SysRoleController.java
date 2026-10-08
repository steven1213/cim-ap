package com.cim.system.role;

import com.cim.spring.support.service.CrudService;
import com.cim.spring.support.web.BaseController;
import com.cim.spring.support.web.Result;
import com.cim.system.rbac.SysRbacService;
import com.cim.system.support.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口。
 *
 * <p>「角色 → 权限」与「角色 → 菜单可见性」是<b>两张独立的授权表</b>（README §21.1 的
 * 菜单/权限分离），故对应两个端点，不做合并——合并会让「能调用 API」与「看得见菜单」
 * 重新耦合，正是 §21.1 要避免的退化。</p>
 */
@RestController
@RequestMapping("/sys/roles")
public class SysRoleController extends BaseController<SysRole, String> {

    private final SysRoleService roleService;
    private final SysRbacService rbacService;

    public SysRoleController(SysRoleService roleService, SysRbacService rbacService) {
        this.roleService = roleService;
        this.rbacService = rbacService;
    }

    @Override
    protected CrudService<SysRole, String> service() {
        return roleService;
    }

    /** 查询角色已授予的权限 ID。 */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_LIST + "')")
    public Result<List<String>> permissions(@PathVariable String id) {
        return Result.ok(rbacService.permissionIdsOf(id));
    }

    /** 覆盖式授予角色权限。 */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_GRANT + "')")
    public Result<Void> assignPermissions(@PathVariable String id, @RequestBody List<String> permIds) {
        rbacService.assignPermissions(id, permIds);
        return Result.ok();
    }

    /** 查询角色已关联的菜单 ID（导航可见性）。 */
    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_LIST + "')")
    public Result<List<String>> menus(@PathVariable String id) {
        return Result.ok(rbacService.menuIdsOf(id));
    }

    /** 覆盖式授予角色菜单可见性。 */
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_GRANT + "')")
    public Result<Void> assignMenus(@PathVariable String id, @RequestBody List<String> menuIds) {
        rbacService.assignMenus(id, menuIds);
        return Result.ok();
    }
}
