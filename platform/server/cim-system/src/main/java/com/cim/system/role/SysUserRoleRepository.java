package com.cim.system.role;

import com.cim.jpa.support.BaseRepository;

import java.util.List;

/** 用户—角色关联仓储。 */
public interface SysUserRoleRepository extends BaseRepository<SysUserRole, String> {

    /** 某用户的全部角色关联。 */
    List<SysUserRole> findByUserId(String userId);

    /** 清除某用户的全部角色关联（重新授权前调用，先删后插）。 */
    void deleteByUserId(String userId);

    /** 关联是否已存在。 */
    boolean existsByUserIdAndRoleId(String userId, String roleId);
}
