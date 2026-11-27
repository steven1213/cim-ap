package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 工序可变参数仓储（V29）。
 */
public interface CimOperationVariableParamRepository
        extends JpaRepository<CimOperationVariableParam, String> {

    List<CimOperationVariableParam> findByOpIdAndDeletedFalseOrderBySortNoAscParamKeyAsc(String opId);

    /** 物理级联（软删会撞 uk_operation_variable_param；同事务先删后插须 flush）。 */
    void deleteByOpId(String opId);
}
