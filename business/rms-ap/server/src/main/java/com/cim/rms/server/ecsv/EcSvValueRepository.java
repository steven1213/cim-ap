package com.cim.rms.server.ecsv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** EC/SV 上传值仓储。 */
public interface EcSvValueRepository extends JpaRepository<EcSvValue, String> {

    List<EcSvValue> findByDeviceIdAndDeletedFalseOrderByCreateTimeDesc(String deviceId);

    List<EcSvValue> findByDeletedFalseOrderByCreateTimeDesc();

    List<EcSvValue> findByResultAndDeletedFalse(String result);

    long countByResultAndDeletedFalse(String result);
}
