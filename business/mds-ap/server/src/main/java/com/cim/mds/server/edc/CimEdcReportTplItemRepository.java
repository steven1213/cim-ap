package com.cim.mds.server.edc;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EDC 报告模板成员仓储。
 */
public interface CimEdcReportTplItemRepository extends JpaRepository<CimEdcReportTplItem, String> {

    List<CimEdcReportTplItem> findByTplIdAndDeletedFalseOrderBySortNoAscSvidAsc(String tplId);

    /** 物理级联（软删会撞 uk_edc_report_tpl_item；同事务先删后插须 flush）。 */
    void deleteByTplId(String tplId);
}
