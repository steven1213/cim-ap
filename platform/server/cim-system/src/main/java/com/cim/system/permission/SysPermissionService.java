package com.cim.system.permission;

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
 * 权限服务。
 *
 * <p>保存时统一经 {@link SysPermission#applyCode(String)} 由权限码拆解三段
 * （{@code module}/{@code res}/{@code action}），保证三段与 {@code code} 永不漂移——
 * 这是 {@code @PreAuthorize} 里的码与库里的码能对上的前提。</p>
 */
@Service
public class SysPermissionService extends AbstractJpaService<SysPermission> {

    private final SysPermissionRepository permissionRepository;

    public SysPermissionService(SysPermissionRepository permissionRepository,
                                EntityManager entityManager,
                                HistoryRecorder historyRecorder,
                                TenantFilterApplier tenantFilterApplier) {
        super(permissionRepository, entityManager, historyRecorder, tenantFilterApplier);
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.PERMISSION_LIST + "')")
    public PageResult<SysPermission> page(PageQuery query) {
        return super.page(query);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.PERMISSION_LIST + "')")
    public SysPermission load(String id) {
        return super.load(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.PERMISSION_SAVE + "')")
    public SysPermission save(SysPermission entity) {
        validate(entity);
        return super.save(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.PERMISSION_SAVE + "')")
    public SysPermission update(SysPermission entity) {
        validate(entity);
        return super.update(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.PERMISSION_REMOVE + "')")
    public void remove(String id) {
        super.remove(id);
    }

    private void validate(SysPermission entity) {
        String code = entity.getCode();
        if (code == null || code.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "permission code is required");
        }
        if (code.split(":").length != 3) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "permission code must be module:res:action, got: " + code);
        }
        if (permissionRepository.findFirstByCodeOrderByCreateTimeAsc(code)
                .filter(existing -> !existing.getId().equals(entity.getId()))
                .isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "permission code already exists: " + code);
        }
        entity.applyCode(code);
    }
}
