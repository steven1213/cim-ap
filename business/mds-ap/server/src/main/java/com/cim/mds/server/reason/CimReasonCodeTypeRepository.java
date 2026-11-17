package com.cim.mds.server.reason;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 原因码类型仓储（V16）。Type 集合小而稳定，按 sort_no 升序供下拉与列表。
 */
public interface CimReasonCodeTypeRepository extends JpaRepository<CimReasonCodeType, String> {

    Optional<CimReasonCodeType> findByIdAndDeletedFalse(String id);

    Optional<CimReasonCodeType> findByTypeKeyAndDeletedFalse(String typeKey);

    boolean existsByTypeKeyAndDeletedFalse(String typeKey);

    List<CimReasonCodeType> findByDeletedFalseOrderBySortNoAscTypeKeyAsc();
}
