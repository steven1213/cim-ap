package com.cim.rms.server.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BodyTemplateRepository extends JpaRepository<BodyTemplate, String> {

    Optional<BodyTemplate> findByCodeAndDeletedFalse(String code);

    List<BodyTemplate> findByDeletedFalseOrderByCreateTimeDesc();

    List<BodyTemplate> findByEnforcedTrueAndDeletedFalse();

    List<BodyTemplate> findByScopeDeviceTypeIdAndDeletedFalse(String deviceTypeId);
}
