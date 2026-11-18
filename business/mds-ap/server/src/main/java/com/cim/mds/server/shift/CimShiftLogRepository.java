package com.cim.mds.server.shift;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 交接班记录仓储（V17）。
 */
public interface CimShiftLogRepository extends JpaRepository<CimShiftLog, String> {

    Optional<CimShiftLog> findByIdAndDeletedFalse(String id);

    /** 按值守日倒序（最新交接在最前）。 */
    List<CimShiftLog> findByDeletedFalseOrderByWorkDateDescCreateTimeDesc();

    /** 按值守日区间过滤（work_date 升序）。 */
    List<CimShiftLog> findByWorkDateBetweenAndDeletedFalseOrderByWorkDateAscCreateTimeAsc(
            LocalDate from, LocalDate to);

    /** 按班次过滤（work_date 倒序）。 */
    List<CimShiftLog> findByShiftCodeAndDeletedFalseOrderByWorkDateDescCreateTimeDesc(String shiftCode);

    /** 按值守日 + 班次定位（同班次可有多条记录，取列表而非唯一）。 */
    List<CimShiftLog> findByWorkDateAndShiftCodeAndDeletedFalseOrderByCreateTimeAsc(
            LocalDate workDate, String shiftCode);
}
