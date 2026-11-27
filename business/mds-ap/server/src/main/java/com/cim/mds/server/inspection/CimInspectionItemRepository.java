package com.cim.mds.server.inspection;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 检验项（明细）仓储。 */
public interface CimInspectionItemRepository extends JpaRepository<CimInspectionItem, String> {

    /** 标准下有效明细（sort_no 升序）。 */
    List<CimInspectionItem> findByStdIdAndDeletedFalseOrderBySortNoAsc(String stdId);

    /** 标准下有效明细数（item_code 查重用）。 */
    long countByStdIdAndDeletedFalse(String stdId);
}
