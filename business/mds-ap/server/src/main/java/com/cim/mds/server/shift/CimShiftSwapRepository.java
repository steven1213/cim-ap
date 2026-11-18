package com.cim.mds.server.shift;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 换班申请仓储（V19）。 */
public interface CimShiftSwapRepository extends JpaRepository<CimShiftSwap, String> {

    Optional<CimShiftSwap> findByIdAndDeletedFalse(String id);

    /** 全量（status 过滤在服务层做；create_time 倒序）。 */
    List<CimShiftSwap> findByDeletedFalseOrderByCreateTimeDesc();

    /** 同一班次是否有未完结申请（PENDING 禁重申请守门）。 */
    boolean existsByInitiatorWorkDateAndInitiatorShiftCodeAndStatusAndDeletedFalse(
            java.time.LocalDate workDate, String shiftCode, String status);

    /** 对方班次是否有未完结申请（SWAP 双槽位禁重）。 */
    boolean existsByTargetWorkDateAndTargetShiftCodeAndStatusAndDeletedFalse(
            java.time.LocalDate workDate, String shiftCode, String status);

    /** 我发起的 + 待我确认的（「我的申请」与「待我确认」两个视角）。 */
    List<CimShiftSwap> findByInitiatorIdAndDeletedFalseOrderByCreateTimeDesc(String userId);

    List<CimShiftSwap> findByTargetIdAndDeletedFalseOrderByCreateTimeDesc(String userId);
}
