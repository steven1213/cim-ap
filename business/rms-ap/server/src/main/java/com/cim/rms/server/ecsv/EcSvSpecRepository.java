package com.cim.rms.server.ecsv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** EC/SV 规格仓储。 */
public interface EcSvSpecRepository extends JpaRepository<EcSvSpec, String> {

    List<EcSvSpec> findByScopeAndRefIdAndDeletedFalseOrderByParamKeyAsc(String scope, String refId);

    List<EcSvSpec> findByDeletedFalseOrderByCreateTimeDesc();

    long countByDeletedFalse();
}
