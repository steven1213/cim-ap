package com.cim.system.dict;

import com.cim.jpa.support.BaseRepository;

import java.util.Optional;

/** 字典仓储。 */
public interface SysDictRepository extends BaseRepository<SysDict, String> {

    /** 按字典编码查找。 */
    Optional<SysDict> findFirstByCodeOrderByCreateTimeAsc(String code);
}
