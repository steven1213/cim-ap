package com.cim.rms.server.golden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Golden 机差覆盖层仓储。 */
public interface GoldenOverrideRepository extends JpaRepository<GoldenOverride, String> {

    List<GoldenOverride> findByDeletedFalseOrderByCreateTimeDesc();

    List<GoldenOverride> findByDeviceIdAndStatusAndDeletedFalse(String deviceId, String status);

    List<GoldenOverride> findByStatusAndDeletedFalse(String status);

    long countByStatusAndDeletedFalse(String status);
}
