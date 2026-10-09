package com.cim.iam.server.watermark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DirectoryWatermarkRepository extends JpaRepository<DirectoryWatermark, String> {

    Optional<DirectoryWatermark> findByScope(WatermarkScope scope);
}
