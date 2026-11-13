package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 区域组仓储（location-design.md §9.1，Area 的统计口径分类）。 */
public interface CimAreaGroupRepository extends JpaRepository<CimAreaGroup, String> {

    Optional<CimAreaGroup> findByFabCodeAndGroupCodeAndDeletedFalse(String fabCode, String groupCode);

    /** 列表 + 归属过滤 + 关键字（{@code fabCode}/{@code kw} 皆可空）。 */
    @Query("""
            select g from CimAreaGroup g
             where g.deleted = false
               and (:fabCode is null or g.fabCode = :fabCode)
               and (:kw is null
                    or lower(g.groupCode) like lower(concat('%', :kw, '%'))
                    or lower(g.groupName) like lower(concat('%', :kw, '%')))
             order by g.fabCode asc, g.groupCode asc
            """)
    List<CimAreaGroup> search(@Param("fabCode") String fabCode, @Param("kw") String keyword);
}
