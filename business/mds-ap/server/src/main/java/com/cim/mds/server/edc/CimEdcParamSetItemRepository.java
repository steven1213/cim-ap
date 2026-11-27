package com.cim.mds.server.edc;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EDC 参数集成员仓储。
 */
public interface CimEdcParamSetItemRepository extends JpaRepository<CimEdcParamSetItem, String> {

    List<CimEdcParamSetItem> findBySetIdAndDeletedFalseOrderBySortNoAsc(String setId);

    boolean existsBySetIdAndDeletedFalse(String setId);

    /** 删除参数集时成员物理级联（软删会撞 uk_edc_param_set_item）。 */
    void deleteBySetId(String setId);
}
