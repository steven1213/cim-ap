package com.cim.system.permission;

import com.cim.spring.support.service.CrudService;
import com.cim.spring.support.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 权限管理接口（CRUD 继承自 {@link BaseController}，鉴权在 {@link SysPermissionService}）。 */
@RestController
@RequestMapping("/sys/permissions")
public class SysPermissionController extends BaseController<SysPermission, String> {

    private final SysPermissionService permissionService;

    public SysPermissionController(SysPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    protected CrudService<SysPermission, String> service() {
        return permissionService;
    }
}
