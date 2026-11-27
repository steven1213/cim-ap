package com.cim.mds.server.edc;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EDC 报告模板仓储。
 */
public interface CimEdcReportTplRepository extends JpaRepository<CimEdcReportTpl, String> {

    List<CimEdcReportTpl> findByDeletedFalseOrderByCreateTimeDesc();

    Optional<CimEdcReportTpl> findByTplCodeAndDeletedFalse(String tplCode);

    boolean existsByTplCodeAndDeletedFalse(String tplCode);
}
