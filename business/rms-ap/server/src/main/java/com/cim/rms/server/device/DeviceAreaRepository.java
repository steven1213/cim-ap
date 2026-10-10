package com.cim.rms.server.device;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 设备区域仓储。 */
public interface DeviceAreaRepository extends JpaRepository<DeviceArea, String> {

    Optional<DeviceArea> findByCodeAndDeletedFalse(String code);

    List<DeviceArea> findByDeletedFalseOrderBySortNoAscCode();
}
