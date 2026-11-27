package com.cim.mds.server.consumable;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 耗材（Consumable）仓储。 */
public interface CimConsumableRepository extends JpaRepository<CimConsumable, String> {

    Optional<CimConsumable> findByConsCodeAndDeletedFalse(String consCode);

    boolean existsByConsCodeAndDeletedFalse(String consCode);

    /** 列表：按类别/存放设备过滤（可空）+ 关键字（编码/名称/规格模糊）。 */
    @Query("""
            select c from CimConsumable c
             where c.deleted = false
               and (:consType is null or c.consType = :consType)
               and (:equipmentId is null or c.homeEquipmentId = :equipmentId)
               and (:kw is null
                    or lower(c.consCode) like lower(concat('%', :kw, '%'))
                    or lower(c.consName) like lower(concat('%', :kw, '%'))
                    or lower(c.specModel) like lower(concat('%', :kw, '%')))
             order by c.consCode asc
            """)
    List<CimConsumable> search(@Param("consType") String consType,
                               @Param("equipmentId") String equipmentId,
                               @Param("kw") String keyword);
}
