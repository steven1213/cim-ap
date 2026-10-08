package com.cim.system.role;

import com.cim.core.shared.PageResult;
import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.support.AbstractJpaService;
import com.cim.jpa.tenant.TenantFilterApplier;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.PageQuery;
import com.cim.system.support.PermissionCodes;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色服务（三级授权的中间层）。
 *
 * <p>角色—权限、角色—菜单的授予关系由 {@code SysRbacService.assignPermissions/assignMenus}
 * 维护（跨表原子操作），本服务只负责角色本体的 CRUD。</p>
 */
@Service
public class SysRoleService extends AbstractJpaService<SysRole> {

    private final SysRoleRepository roleRepository;

    public SysRoleService(SysRoleRepository roleRepository,
                          EntityManager entityManager,
                          HistoryRecorder historyRecorder,
                          TenantFilterApplier tenantFilterApplier) {
        super(roleRepository, entityManager, historyRecorder, tenantFilterApplier);
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_LIST + "')")
    public PageResult<SysRole> page(PageQuery query) {
        return super.page(query);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_LIST + "')")
    public SysRole load(String id) {
        return super.load(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_SAVE + "')")
    public SysRole save(SysRole entity) {
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "role code is required");
        }
        if (roleRepository.findFirstByCodeOrderByCreateTimeAsc(entity.getCode()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "role code already exists: " + entity.getCode());
        }
        return super.save(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_SAVE + "')")
    public SysRole update(SysRole entity) {
        return super.update(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_REMOVE + "')")
    public void remove(String id) {
        super.remove(id);
    }
}
