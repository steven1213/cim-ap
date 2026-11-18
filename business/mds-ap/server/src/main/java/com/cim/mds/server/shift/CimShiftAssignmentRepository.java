package com.cim.mds.server.shift;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 排班安排仓储（V17）。
 */
public interface CimShiftAssignmentRepository extends JpaRepository<CimShiftAssignment, String> {

    Optional<CimShiftAssignment> findByIdAndDeletedFalse(String id);

    /** 区间排班（周视图数据源；work_date 升序）。 */
    List<CimShiftAssignment> findByWorkDateBetweenAndDeletedFalseOrderByWorkDateAscUserIdAsc(
            LocalDate from, LocalDate to);

    /** 一人一天唯一性守门。 */
    boolean existsByWorkDateAndUserIdAndDeletedFalse(LocalDate workDate, String userId);

    Optional<CimShiftAssignment> findByWorkDateAndUserIdAndDeletedFalse(LocalDate workDate, String userId);

    /** 某班次被排班引用守门。 */
    boolean existsByShiftCodeAndDeletedFalse(String shiftCode);

    /** 某日期区间内指定人员的排班（复制上周与生成覆盖判断用）。 */
    List<CimShiftAssignment> findByWorkDateBetweenAndUserIdInAndDeletedFalse(
            LocalDate from, LocalDate to, List<String> userIds);
}
