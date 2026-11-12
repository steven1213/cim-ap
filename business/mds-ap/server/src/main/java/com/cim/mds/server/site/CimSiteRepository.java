package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 基地仓储。过滤条件一律下推到 SQL（不在应用层全表捞出后再 filter）。 */
public interface CimSiteRepository extends JpaRepository<CimSite, String> {

    Optional<CimSite> findBySiteCodeAndDeletedFalse(String siteCode);

    /** 列表 + 关键字（编码/名称模糊，忽略大小写）；关键字为空则返回全部。 */
    @Query("""
            select s from CimSite s
             where s.deleted = false
               and (:kw is null
                    or lower(s.siteCode) like lower(concat('%', :kw, '%'))
                    or lower(s.siteName) like lower(concat('%', :kw, '%')))
             order by s.siteCode asc
            """)
    List<CimSite> search(@Param("kw") String keyword);
}
