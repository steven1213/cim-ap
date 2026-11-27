package com.cim.mds.server.edc;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EDC 参数集仓储。
 */
public interface CimEdcParamSetRepository extends JpaRepository<CimEdcParamSet, String> {

    Optional<CimEdcParamSet> findBySetCodeAndDeletedFalse(String setCode);

    List<CimEdcParamSet> findByDeletedFalseOrderByCreateTimeDesc();

    boolean existsBySetCodeAndDeletedFalse(String setCode);
}
