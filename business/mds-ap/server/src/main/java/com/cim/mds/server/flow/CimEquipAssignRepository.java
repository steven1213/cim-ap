package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 统一指派表仓储（ICM：ContextChain 三 scope 一张表）。
 * 三上下文列 NOT NULL DEFAULT '' 空串占位，按 scope 精确查询。
 */
public interface CimEquipAssignRepository extends JpaRepository<CimEquipAssign, String> {

    /** L0 工序默认指派。 */
    List<CimEquipAssign> findByScopeTypeAndOperationCodeAndDeletedFalse(String scopeType, String operationCode);

    /** L1 流程级覆盖指派。 */
    List<CimEquipAssign> findByScopeTypeAndFlowIdAndDeletedFalse(String scopeType, String flowId);

    /** L2 节点覆盖指派。 */
    /** L3 产品级覆盖行（product_code+family_code 定位；step_id 空串=整条流程）。 */
    List<CimEquipAssign> findByScopeTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
            String scopeType, String productCode, String familyCode);
    List<CimEquipAssign> findByScopeTypeAndFlowIdAndStepIdAndDeletedFalse(
            String scopeType, String flowId, String stepId);

    /** 删节点级联清理（同事务物理删）。 */
    List<CimEquipAssign> findByFlowIdAndScopeTypeAndDeletedFalse(String flowId, String scopeType);

    /** 删工序级联清理（L0 行）。 */
    List<CimEquipAssign> findByOperationCodeAndScopeTypeAndDeletedFalse(String operationCode, String scopeType);

    /** assign_ref 引用守门（机台/机型/机组删除前查据）。 */
    List<CimEquipAssign> findByAssignTypeAndAssignRefAndDeletedFalse(String assignType, String assignRef);

    /** 引用守门：某机型是否被任一指派行引用（删 equipmentClass 前）。 */
    boolean existsByAssignTypeAndAssignRefAndDeletedFalse(String assignType, String assignRef);
}
