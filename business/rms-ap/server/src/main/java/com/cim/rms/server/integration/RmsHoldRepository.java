package com.cim.rms.server.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Hold 仓储。 */
public interface RmsHoldRepository extends JpaRepository<RmsHold, String> {

    List<RmsHold> findByActiveTrueAndDeletedFalse();

    List<RmsHold> findByDeletedFalseOrderByCreateTimeDesc();

    List<RmsHold> findByDeviceIdAndActiveTrueAndDeletedFalse(String deviceId);

    long countByActiveTrueAndDeletedFalse();
}
