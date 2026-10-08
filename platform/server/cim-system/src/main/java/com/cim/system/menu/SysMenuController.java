package com.cim.system.menu;

import com.cim.spring.support.service.CrudService;
import com.cim.spring.support.web.BaseController;
import com.cim.spring.support.web.Result;
import com.cim.system.support.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 菜单管理接口（含管理视角的全量菜单树，供角色授权界面选树）。 */
@RestController
@RequestMapping("/sys/menus")
public class SysMenuController extends BaseController<SysMenu, String> {

    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    protected CrudService<SysMenu, String> service() {
        return menuService;
    }

    /**
     * 管理视角菜单树（不做角色过滤）。
     *
     * @param includeButtons 是否包含按钮节点，默认包含（授权界面需为按钮授权）
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('" + PermissionCodes.MENU_LIST + "')")
    public Result<List<MenuNode>> tree(
            @RequestParam(name = "includeButtons", defaultValue = "true") boolean includeButtons) {
        return Result.ok(menuService.tree(includeButtons));
    }
}
