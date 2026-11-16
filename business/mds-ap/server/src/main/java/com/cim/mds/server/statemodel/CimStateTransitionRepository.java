package com.cim.mds.server.statemodel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 状态转换规则仓储。 */
public interface CimStateTransitionRepository extends JpaRepository<CimStateTransition, String> {

    List<CimStateTransition> findByModelCodeAndDeletedFalse(String modelCode);

    Optional<CimStateTransition> findByModelCodeAndFromStateAndToStateAndDeletedFalse(
            String modelCode, String fromState, String toState);

    /** 矩阵取消勾选 = 物理删（软删会撞 uk_cim_state_transition，与能力关联同口径）。 */
    @Modifying
    @Query("delete from CimStateTransition t where t.modelCode = :modelCode")
    void deleteByModelCode(@Param("modelCode") String modelCode);
}
