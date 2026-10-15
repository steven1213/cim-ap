package com.cim.rms.server.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 机台腔体仓储。 */
public interface DeviceChamberRepository extends JpaRepository<DeviceChamber, String> {

    List<DeviceChamber> findByDeviceIdAndDeletedFalseOrderBySortNoAscCode(String deviceId);

    List<DeviceChamber> findByDeletedFalseOrderByCreateTimeDesc();

    Optional<DeviceChamber> findByDeviceIdAndCodeAndDeletedFalse(String deviceId, String code);
}
