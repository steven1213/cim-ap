package com.cim.system.user;

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
 * 用户管理接口。
 *
 * <p>CRUD 全部由 {@link BaseController} 继承而来（{@code /page}、{@code /{id}}、POST、PUT、
 * DELETE），校验与鉴权在 {@link SysUserService}；本类只补「角色授予」这类控制器专属端点。</p>
 */
@RestController
@RequestMapping("/sys/users")
public class SysUserController extends BaseController<SysUser, String> {

    private final SysUserService userService;
    private final SysRbacService rbacService;

    public SysUserController(SysUserService userService, SysRbacService rbacService) {
        this.userService = userService;
        this.rbacService = rbacService;
    }

    @Override
    protected CrudService<SysUser, String> service() {
        return userService;
    }

    /** 查询某用户已授予的角色 ID（授权界面回显）。 */
    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_LIST + "')")
    public Result<List<String>> roles(@PathVariable String id) {
        return Result.ok(rbacService.roleIdsOf(id));
    }

    /** 覆盖式授予某用户的角色（先删后插，幂等）。 */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_GRANT + "')")
    public Result<Void> assignRoles(@PathVariable String id, @RequestBody List<String> roleIds) {
        rbacService.assignRoles(id, roleIds);
        return Result.ok();
    }
}
