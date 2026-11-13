package com.cim.mds.server.equipment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 设备仓储。 */
public interface CimEquipmentRepository extends JpaRepository<CimEquipment, String> {

    boolean existsByTypeCodeAndDeletedFalse(String typeCode);

    boolean existsByEquipmentCodeAndDeletedFalse(String equipmentCode);

    boolean existsByLocationTypeAndLocationIdAndDeletedFalse(String locationType, String locationId);

    /**
     * 列表 + 过滤（全部可空）：关键字匹配编码/名称；typeCode 精确；页面视图用
     * {@code processOnly}/{@code amhsOnly} 两个布尔表达（JPQL 对集合 in + null 判定不友好）：
     * 设备页=processOnly（kind≠MATERIAL），搬运设备页=amhsOnly（kind=MATERIAL），都不传=不过滤。
     */
    @Query("""
            select e from CimEquipment e, CimEquipmentType t
             where e.deleted = false
               and t.deleted = false
               and e.typeCode = t.typeCode
               and (:kw is null
                    or lower(e.equipmentCode) like lower(concat('%', :kw, '%'))
                    or lower(e.equipmentName) like lower(concat('%', :kw, '%')))
               and (:typeCode is null or e.typeCode = :typeCode)
               and (:processOnly = false or t.kindCode <> 'MATERIAL')
               and (:amhsOnly = false or t.kindCode = 'MATERIAL')
             order by e.equipmentCode asc
            """)
    List<CimEquipment> search(@Param("kw") String keyword,
                              @Param("typeCode") String typeCode,
                              @Param("processOnly") boolean processOnly,
                              @Param("amhsOnly") boolean amhsOnly);
}
