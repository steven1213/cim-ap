package com.cim.mds.server.shift;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 班次定义仓储（V17）。清单按 sort_no 升序（下拉与日历图例顺序）。
 */
public interface CimShiftDefRepository extends JpaRepository<CimShiftDef, String> {

    Optional<CimShiftDef> findByIdAndDeletedFalse(String id);

    Optional<CimShiftDef> findByShiftCodeAndDeletedFalse(String shiftCode);

    boolean existsByShiftCodeAndDeletedFalse(String shiftCode);

    /** 全量（sort_no 升序）。 */
    List<CimShiftDef> findByDeletedFalseOrderBySortNoAscShiftCodeAsc();

    /** 启用班次（排班/铺排下拉口径）。 */
    List<CimShiftDef> findByEnabledTrueAndDeletedFalseOrderBySortNoAscShiftCodeAsc();
}
