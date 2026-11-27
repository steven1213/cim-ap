package com.cim.mds.server.equipment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 机台仓储。 */
public interface CimEquipmentRepository extends JpaRepository<CimEquipment, String> {

    boolean existsByClassCodeAndDeletedFalse(String classCode);

    boolean existsByEquipmentCodeAndDeletedFalse(String equipmentCode);

    /** 按机台编码取行（指派候选池守门：引用存在 + enabled + classCode 一致性查据）。 */
    java.util.Optional<CimEquipment> findByEquipmentCodeAndDeletedFalse(String equipmentCode);

    boolean existsByLocationTypeAndLocationIdAndDeletedFalse(String locationType, String locationId);

    /**
     * 列表 + 过滤（全部可空）：关键字匹配编码/名称；classCode 精确；页面视图用
     * {@code processOnly}/{@code amhsOnly} 两个布尔表达（JPQL 对集合 in + null 判定不友好）：
     * 设备页=processOnly（kind≠MATERIAL），搬运设备页=amhsOnly（kind=MATERIAL），都不传=不过滤。
     * v2：ownerUser 精确过滤（设备责任人动线）。
     */
    @Query("""
            select e from CimEquipment e, CimEquipmentClass t
             where e.deleted = false
               and t.deleted = false
               and e.classCode = t.classCode
               and (:kw is null
                    or lower(e.equipmentCode) like lower(concat('%', :kw, '%'))
                    or lower(e.equipmentName) like lower(concat('%', :kw, '%')))
               and (:classCode is null or e.classCode = :classCode)
               and (:ownerUser is null or e.ownerUser = :ownerUser)
               and (:processOnly = false or t.kindCode <> 'MATERIAL')
               and (:amhsOnly = false or t.kindCode = 'MATERIAL')
             order by e.equipmentCode asc
            """)
    List<CimEquipment> search(@Param("kw") String keyword,
                              @Param("classCode") String classCode,
                              @Param("ownerUser") String ownerUser,
                              @Param("processOnly") boolean processOnly,
                              @Param("amhsOnly") boolean amhsOnly);
}
