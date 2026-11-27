package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 参数定义字典仓储。
 */
public interface CimParamDefRepository extends JpaRepository<CimParamDef, String> {

    java.util.Optional<CimParamDef> findByAttrKeyAndDeletedFalse(String attrKey);

    List<CimParamDef> findByDeletedFalseOrderBySortNoAsc();

    boolean existsByAttrKeyAndDeletedFalse(String attrKey);
}
