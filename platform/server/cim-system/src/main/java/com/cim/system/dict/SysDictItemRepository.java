package com.cim.system.dict;

import com.cim.jpa.support.BaseRepository;

import java.util.List;

/** 字典明细仓储。 */
public interface SysDictItemRepository extends BaseRepository<SysDictItem, String> {

    /** 某字典下的明细（按排序号）。 */
    List<SysDictItem> findByDictIdOrderBySortNoAsc(String dictId);

    /** 清除某字典的全部明细（删除字典前调用）。 */
    void deleteByDictId(String dictId);
}
