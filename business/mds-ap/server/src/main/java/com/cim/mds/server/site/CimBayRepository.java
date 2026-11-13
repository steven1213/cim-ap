package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Bay 仓储。 */
public interface CimBayRepository extends JpaRepository<CimBay, String> {

    Optional<CimBay> findByFabCodeAndBayCodeAndDeletedFalse(String fabCode, String bayCode);

    /** 下游引用计数：删除 Area / 搬迁 Bay 前的守门依据。 */
    long countByFabCodeAndAreaCodeAndDeletedFalse(String fabCode, String areaCode);

    long countByFabCodeAndBayCodeAndDeletedFalse(String fabCode, String bayCode);

    /** 列表 + 归属过滤 + 关键字（{@code fabCode}/{@code areaCode}/{@code kw} 皆可空）。 */
    @Query("""
            select b from CimBay b
             where b.deleted = false
               and (:fabCode is null or b.fabCode = :fabCode)
               and (:areaCode is null or b.areaCode = :areaCode)
               and (:kw is null
                    or lower(b.bayCode) like lower(concat('%', :kw, '%'))
                    or lower(b.bayName) like lower(concat('%', :kw, '%')))
             order by b.fabCode asc, b.areaCode asc, b.bayCode asc
            """)
    List<CimBay> search(@Param("fabCode") String fabCode, @Param("areaCode") String areaCode,
                        @Param("kw") String keyword);
}
