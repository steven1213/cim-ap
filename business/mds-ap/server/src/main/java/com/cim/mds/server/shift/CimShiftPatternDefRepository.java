package com.cim.mds.server.shift;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 班制定义仓储（V17）。清单按 sort_no 升序。
 */
public interface CimShiftPatternDefRepository extends JpaRepository<CimShiftPatternDef, String> {

    Optional<CimShiftPatternDef> findByIdAndDeletedFalse(String id);

    Optional<CimShiftPatternDef> findByPatternCodeAndDeletedFalse(String patternCode);

    boolean existsByPatternCodeAndDeletedFalse(String patternCode);

    /** 全量（sort_no 升序）。 */
    List<CimShiftPatternDef> findByDeletedFalseOrderBySortNoAscPatternCodeAsc();

    /** 启用班制（铺排入口下拉口径）。 */
    List<CimShiftPatternDef> findByEnabledTrueAndDeletedFalseOrderBySortNoAscPatternCodeAsc();
}
