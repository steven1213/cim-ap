package com.cim.mds.server.carrier;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 载具（Carrier）仓储。 */
public interface CimCarrierRepository extends JpaRepository<CimCarrier, String> {

    Optional<CimCarrier> findByCarrierCodeAndDeletedFalse(String carrierCode);

    boolean existsByCarrierCodeAndDeletedFalse(String carrierCode);

    /**
     * 列表：按状态/所属设备过滤（可空=全量）+ 关键字（编码/名称/内容物批次模糊）。
     * 排序：载具编码。
     */
    @Query("""
            select c from CimCarrier c
             where c.deleted = false
               and (:status is null or c.status = :status)
               and (:equipmentId is null or c.homeEquipmentId = :equipmentId)
               and (:kw is null
                    or lower(c.carrierCode) like lower(concat('%', :kw, '%'))
                    or lower(c.carrierName) like lower(concat('%', :kw, '%'))
                    or lower(c.currentLotId) like lower(concat('%', :kw, '%')))
             order by c.carrierCode asc
            """)
    List<CimCarrier> search(@Param("status") String status,
                            @Param("equipmentId") String equipmentId,
                            @Param("kw") String keyword);
}
