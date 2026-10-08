package com.cim.system.menu;

import com.cim.core.shared.PageResult;
import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.support.AbstractJpaService;
import com.cim.jpa.tenant.TenantFilterApplier;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.PageQuery;
import com.cim.system.support.PermissionCodes;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 菜单服务（菜单本体 CRUD；导航可见性授权在 {@code SysRbacService.assignMenus}）。
 */
@Service
public class SysMenuService extends AbstractJpaService<SysMenu> {

    private final SysMenuRepository menuRepository;

    public SysMenuService(SysMenuRepository menuRepository,
                          EntityManager entityManager,
                          HistoryRecorder historyRecorder,
                          TenantFilterApplier tenantFilterApplier) {
        super(menuRepository, entityManager, historyRecorder, tenantFilterApplier);
        this.menuRepository = menuRepository;
    }

    /**
     * <b>管理视角</b>的菜单树：不做角色过滤（授权界面需要看到全部节点）。
     *
     * @param includeButtons 是否包含按钮节点
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.MENU_LIST + "')")
    public List<MenuNode> tree(boolean includeButtons) {
        return MenuTrees.build(menuRepository.findByOrderBySortNoAsc(), includeButtons);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.MENU_LIST + "')")
    public PageResult<SysMenu> page(PageQuery query) {
        return super.page(query);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.MENU_LIST + "')")
    public SysMenu load(String id) {
        return super.load(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.MENU_SAVE + "')")
    public SysMenu save(SysMenu entity) {
        if (entity.getType() == MenuType.BUTTON
                && (entity.getPermCode() == null || entity.getPermCode().isBlank())) {
            // 按钮节点的唯一价值是承载权限码；缺失即无意义，提前拦截而不是留脏数据
            throw new BizException(BizCode.PARAM_INVALID, "BUTTON menu requires perm_code");
        }
        return super.save(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.MENU_SAVE + "')")
    public SysMenu update(SysMenu entity) {
        return super.update(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.MENU_REMOVE + "')")
    public void remove(String id) {
        super.remove(id);
    }
}
