package com.cim.jpa.it;

import com.cim.jpa.support.BaseRepository;

/**
 * 示例仓储：继承通用 {@link BaseRepository} 即获得 CRUD + 动态查询。
 */
public interface EquipmentDefRepository extends BaseRepository<EquipmentDef, String> {
}
