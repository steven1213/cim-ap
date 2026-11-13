package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 设备类型仓储。 */
public interface CimEquipmentTypeRepository extends JpaRepository<CimEquipmentType, String> {

    Optional<CimEquipmentType> findByTypeCodeAndDeletedFalse(String typeCode);

    boolean existsByTypeCodeAndDeletedFalse(String typeCode);

    /** 列表 + 关键字（编码/名称模糊，{@code kw} 可空）。 */
    @Query("""
            select t from CimEquipmentType t
             where t.deleted = false
               and (:kw is null
                    or lower(t.typeCode) like lower(concat('%', :kw, '%'))
                    or lower(t.typeName) like lower(concat('%', :kw, '%')))
             order by t.typeCode asc
            """)
    List<CimEquipmentType> search(@Param("kw") String keyword);
}
