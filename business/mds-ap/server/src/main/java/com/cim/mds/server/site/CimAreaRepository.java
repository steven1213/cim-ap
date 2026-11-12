package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Area 仓储。 */
public interface CimAreaRepository extends JpaRepository<CimArea, String> {

    Optional<CimArea> findByFabCodeAndAreaCodeAndDeletedFalse(String fabCode, String areaCode);

    long countByFabCodeAndDeletedFalse(String fabCode);

    /** 列表 + 归属过滤 + 关键字（{@code fabCode}/{@code kw} 皆可空）。 */
    @Query("""
            select a from CimArea a
             where a.deleted = false
               and (:fabCode is null or a.fabCode = :fabCode)
               and (:kw is null
                    or lower(a.areaCode) like lower(concat('%', :kw, '%'))
                    or lower(a.areaName) like lower(concat('%', :kw, '%')))
             order by a.fabCode asc, a.areaCode asc
            """)
    List<CimArea> search(@Param("fabCode") String fabCode, @Param("kw") String keyword);
}
