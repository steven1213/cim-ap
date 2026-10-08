package com.cim.system.config;

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

import java.util.Optional;

/**
 * 参数配置服务。
 *
 * <p>取值入口 {@link #value(String)} / {@link #value(String, String)} 供业务无鉴权读取
 * （读参数不应要求「参数管理」权限）；写操作才受 {@code sys:config:save} 保护。</p>
 */
@Service
public class SysConfigService extends AbstractJpaService<SysConfig> {

    private final SysConfigRepository configRepository;

    public SysConfigService(SysConfigRepository configRepository,
                            EntityManager entityManager,
                            HistoryRecorder historyRecorder,
                            TenantFilterApplier tenantFilterApplier) {
        super(configRepository, entityManager, historyRecorder, tenantFilterApplier);
        this.configRepository = configRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.CONFIG_LIST + "')")
    public PageResult<SysConfig> page(PageQuery query) {
        return super.page(query);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.CONFIG_LIST + "')")
    public SysConfig load(String id) {
        return super.load(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.CONFIG_SAVE + "')")
    public SysConfig save(SysConfig entity) {
        if (entity.getConfigKey() == null || entity.getConfigKey().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "config key is required");
        }
        if (configRepository.findFirstByConfigKeyOrderByCreateTimeAsc(entity.getConfigKey()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "config key already exists: " + entity.getConfigKey());
        }
        return super.save(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.CONFIG_SAVE + "')")
    public SysConfig update(SysConfig entity) {
        return super.update(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.CONFIG_REMOVE + "')")
    public void remove(String id) {
        SysConfig config = super.load(id);
        if (Boolean.TRUE.equals(config.getBuiltIn())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "built-in config cannot be removed: " + config.getConfigKey());
        }
        super.remove(id);
    }

    /** 读取参数值（不存在返回空）。不加鉴权：业务读取参数不属管理动作。 */
    @Transactional(readOnly = true)
    public Optional<String> value(String configKey) {
        return configRepository.findFirstByConfigKeyOrderByCreateTimeAsc(configKey)
                .filter(c -> c.getStatus() == null || c.getStatus().enabled())
                .map(SysConfig::getConfigValue);
    }

    /** 读取参数值，缺失时返回默认值。 */
    @Transactional(readOnly = true)
    public String value(String configKey, String defaultValue) {
        return value(configKey).orElse(defaultValue);
    }
}
