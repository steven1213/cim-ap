package com.cim.system.user;

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

/**
 * 用户（授权档案）服务。
 *
 * <p><b>鉴权放在 Service 而非 Controller</b>：{@code @EnableMethodSecurity} 对任意 Bean 生效，
 * 把权限语义放在业务边界上与 README §21.1「配合 §5 的 {@code @PreAuthorize}」一致，
 * 同时让 {@code BaseController} 保持零重复代码（无需为加注解而重写方法）。</p>
 *
 * <p>覆盖父类方法时<b>必须重新声明 {@code @Transactional}</b>：Spring 的事务属性解析不会
 * 沿方法继承链回溯（父类方法上的注解对覆盖方法无效）。</p>
 */
@Service
public class SysUserService extends AbstractJpaService<SysUser> {

    private final SysUserRepository userRepository;

    public SysUserService(SysUserRepository userRepository,
                          EntityManager entityManager,
                          HistoryRecorder historyRecorder,
                          TenantFilterApplier tenantFilterApplier) {
        super(userRepository, entityManager, historyRecorder, tenantFilterApplier);
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_LIST + "')")
    public PageResult<SysUser> page(PageQuery query) {
        return super.page(query);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_LIST + "')")
    public SysUser load(String id) {
        return super.load(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_SAVE + "')")
    public SysUser save(SysUser entity) {
        if (entity.getUsername() == null || entity.getUsername().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "username is required");
        }
        if (userRepository.existsByUsername(entity.getUsername())) {
            throw new BizException(BizCode.DATA_DUPLICATE, "username already exists: " + entity.getUsername());
        }
        return super.save(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_SAVE + "')")
    public SysUser update(SysUser entity) {
        return super.update(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_REMOVE + "')")
    public void remove(String id) {
        super.remove(id);
    }
}
