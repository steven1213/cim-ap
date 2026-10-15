package com.cim.rms.server.spec;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 规格模板仓储。 */
public interface SpecTemplateRepository extends JpaRepository<SpecTemplate, String> {

    List<SpecTemplate> findByDeletedFalseOrderByCodeAsc();

    Optional<SpecTemplate> findByCodeAndDeletedFalse(String code);

    long countByDeletedFalse();
}
