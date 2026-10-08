package com.cim.system.permission;

import com.cim.jpa.support.BaseRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 权限仓储。 */
public interface SysPermissionRepository extends BaseRepository<SysPermission, String> {

    /** 按权限码查找。 */
    Optional<SysPermission> findFirstByCodeOrderByCreateTimeAsc(String code);

    /** 按 ID 批量加载（授权解析用；仅返回启用且未删除的行）。 */
    List<SysPermission> findByIdIn(Collection<String> ids);
}
