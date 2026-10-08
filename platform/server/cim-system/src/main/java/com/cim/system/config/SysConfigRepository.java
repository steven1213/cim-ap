package com.cim.system.config;

import com.cim.jpa.support.BaseRepository;

import java.util.Optional;

/** 参数配置仓储。 */
public interface SysConfigRepository extends BaseRepository<SysConfig, String> {

    /** 按参数键查找。 */
    Optional<SysConfig> findFirstByConfigKeyOrderByCreateTimeAsc(String configKey);
}
