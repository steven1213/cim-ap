package com.cim.rms.server.device;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 设备类型仓储（软删行由查询侧过滤 {@code deleted=false}）。 */
public interface DeviceTypeRepository extends JpaRepository<DeviceType, String> {

    Optional<DeviceType> findByCodeAndDeletedFalse(String code);

    List<DeviceType> findByDeletedFalseOrderByCode();
}
