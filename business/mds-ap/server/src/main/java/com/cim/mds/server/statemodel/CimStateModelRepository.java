package com.cim.mds.server.statemodel;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 状态模型仓储。 */
public interface CimStateModelRepository extends JpaRepository<CimStateModel, String> {

    Optional<CimStateModel> findByModelCodeAndDeletedFalse(String modelCode);
}
