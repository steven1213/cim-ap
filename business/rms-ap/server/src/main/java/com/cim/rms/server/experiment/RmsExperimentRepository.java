package com.cim.rms.server.experiment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RmsExperimentRepository extends JpaRepository<RmsExperiment, String> {

    Optional<RmsExperiment> findByCodeAndDeletedFalse(String code);

    List<RmsExperiment> findByDeletedFalseOrderByCreateTimeDesc();

    List<RmsExperiment> findByDeviceIdAndStatusAndDeletedFalse(String deviceId, String status);

    List<RmsExperiment> findByStatusAndDeletedFalse(String status);
}
