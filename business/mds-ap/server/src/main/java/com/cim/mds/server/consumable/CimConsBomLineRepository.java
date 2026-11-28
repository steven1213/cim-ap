package com.cim.mds.server.consumable;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 消耗品 BOM 明细行仓储（全量替换维护）。 */
public interface CimConsBomLineRepository extends JpaRepository<CimConsBomLine, String> {

    /** BOM 下有效明细行（sort_no 升序）。 */
    List<CimConsBomLine> findByBomIdAndDeletedFalseOrderBySortNoAsc(String bomId);

    /** BOM 下有效明细行数（激活前非空校验）。 */
    long countByBomIdAndDeletedFalse(String bomId);

    /** 同头同 spec 查重（行编码建后随头全量替换，无独立编码）。 */
    boolean existsByBomIdAndSpecIdAndDeletedFalse(String bomId, String specId);
}
