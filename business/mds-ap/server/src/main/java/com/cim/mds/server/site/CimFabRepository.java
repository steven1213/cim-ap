package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Fab 仓储。 */
public interface CimFabRepository extends JpaRepository<CimFab, String> {

    Optional<CimFab> findByFabCodeAndDeletedFalse(String fabCode);

    long countBySiteCodeAndDeletedFalse(String siteCode);

    /** 列表 + 归属过滤 + 关键字（{@code siteCode}/{@code kw} 皆可空）。 */
    @Query("""
            select f from CimFab f
             where f.deleted = false
               and (:siteCode is null or f.siteCode = :siteCode)
               and (:kw is null
                    or lower(f.fabCode) like lower(concat('%', :kw, '%'))
                    or lower(f.fabName) like lower(concat('%', :kw, '%')))
             order by f.siteCode asc, f.fabCode asc
            """)
    List<CimFab> search(@Param("siteCode") String siteCode, @Param("kw") String keyword);
}
