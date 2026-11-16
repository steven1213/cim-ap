package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Stocker 存储区（Bank）仓储。 */
public interface CimStockerBankRepository extends JpaRepository<CimStockerBank, String> {

    Optional<CimStockerBank> findByEquipmentIdAndBankCodeAndDeletedFalse(String equipmentId, String bankCode);

    boolean existsByEquipmentIdAndBankCodeAndDeletedFalse(String equipmentId, String bankCode);

    /**
     * 列表：按所属设备过滤（可空=全量）+ 关键字（编码/名称模糊，{@code kw} 可空）。
     * 排序：设备内按 bank_code。
     */
    @Query("""
            select b from CimStockerBank b
             where b.deleted = false
               and (:equipmentId is null or b.equipmentId = :equipmentId)
               and (:kw is null
                    or lower(b.bankCode) like lower(concat('%', :kw, '%'))
                    or lower(b.bankName) like lower(concat('%', :kw, '%')))
             order by b.equipmentId asc, b.bankCode asc
            """)
    List<CimStockerBank> search(@Param("equipmentId") String equipmentId, @Param("kw") String keyword);
}
