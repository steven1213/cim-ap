package com.cim.mds.server.flow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 产品↔流程绑定仓储。
 */
public interface CimProductFlowRepository extends JpaRepository<CimProductFlow, String> {

    /** 同 (product, family) 的现行绑定（唯一 ACTIVE 守门查据）。 */
    Optional<CimProductFlow> findByProductCodeAndFlowFamilyCodeAndBindStateAndDeletedFalse(
            String productCode, String flowFamilyCode, String bindState);

    /** 同 (product, family) 全部历史绑定。 */
    List<CimProductFlow> findByProductCodeAndFlowFamilyCodeAndDeletedFalseOrderByActiveTimeDesc(
            String productCode, String flowFamilyCode);

    /** 产品维度历史。 */
    List<CimProductFlow> findByProductCodeAndDeletedFalseOrderByActiveTimeDesc(String productCode);

    /** 该族是否被任何 ACTIVE 绑定引用（flow disable 前守门）。 */
    boolean existsByFlowFamilyCodeAndBindStateAndDeletedFalse(String flowFamilyCode, String bindState);

    /** 该产品全部 ACTIVE 绑定（MES 投批解析入口）。 */
    List<CimProductFlow> findByProductCodeAndBindStateAndDeletedFalse(
            String productCode, String bindState);
}
