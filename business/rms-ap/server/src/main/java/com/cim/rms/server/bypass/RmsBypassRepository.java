package com.cim.rms.server.bypass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Bypass 仓储。 */
public interface RmsBypassRepository extends JpaRepository<RmsBypass, String> {

    List<RmsBypass> findByDeletedFalseOrderByCreateTimeDesc();

    List<RmsBypass> findByScopeAndTargetIdAndDeletedFalse(String scope, String targetId);

    List<RmsBypass> findByActiveTrueAndDeletedFalse();

    long countByActiveTrueAndDeletedFalse();
}
