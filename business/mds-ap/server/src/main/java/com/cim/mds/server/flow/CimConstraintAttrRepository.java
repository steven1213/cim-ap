package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * KV 约束值表仓储（ContextChain 三 context 一张表）。
 */
public interface CimConstraintAttrRepository extends JpaRepository<CimConstraintAttr, String> {

    List<CimConstraintAttr> findByContextTypeAndOperationCodeAndDeletedFalse(
            String contextType, String operationCode);

    List<CimConstraintAttr> findByContextTypeAndFlowIdAndDeletedFalse(String contextType, String flowId);

    /** L3 产品级 KV 行（product_code+family_code 定位）。 */
    List<CimConstraintAttr> findByContextTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
            String contextType, String productCode, String familyCode);
    List<CimConstraintAttr> findByContextTypeAndFlowIdAndStepIdAndDeletedFalse(
            String contextType, String flowId, String stepId);

    /** 删节点级联清理。 */
    List<CimConstraintAttr> findByFlowIdAndContextTypeAndDeletedFalse(String flowId, String contextType);

    /** 引用守门：attr_key 是否被任何值行引用（param_def 删除前查据）。 */
    boolean existsByAttrKeyAndDeletedFalse(String attrKey);
}
