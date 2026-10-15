package com.cim.rms.server.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** GEM 设备事件仓储（按 {@code idemKey} 去重是幂等入口）。 */
public interface RmsEquipmentEventRepository extends JpaRepository<RmsEquipmentEvent, String> {

    Optional<RmsEquipmentEvent> findFirstByIdemKeyAndDeletedFalse(String idemKey);

    List<RmsEquipmentEvent> findByDeletedFalseOrderByCreateTimeDesc();

    List<RmsEquipmentEvent> findByToolIdAndDeletedFalseOrderByCreateTimeDesc(String toolId);

    List<RmsEquipmentEvent> findByEventTypeAndDeletedFalseOrderByCreateTimeDesc(EquipmentEventType eventType);

    List<RmsEquipmentEvent> findByToolIdAndEventTypeAndDeletedFalseOrderByCreateTimeDesc(
            String toolId, EquipmentEventType eventType);
}
