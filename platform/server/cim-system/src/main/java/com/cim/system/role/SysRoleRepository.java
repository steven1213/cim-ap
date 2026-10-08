package com.cim.system.role;

import com.cim.jpa.support.BaseRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 角色仓储。 */
public interface SysRoleRepository extends BaseRepository<SysRole, String> {

    /** 按角色编码查找。 */
    Optional<SysRole> findFirstByCodeOrderByCreateTimeAsc(String code);

    /** 按 ID 批量加载（授权解析用）。 */
    List<SysRole> findByIdIn(Collection<String> ids);
}
