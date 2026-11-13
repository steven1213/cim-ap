package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 制程技术仓储（产品管理域）。 */
public interface CimTechnologyRepository extends JpaRepository<CimTechnology, String> {

    Optional<CimTechnology> findByTechCodeAndDeletedFalse(String techCode);

    /** 列表 + 关键字（编码/名称/节点值模糊，{@code kw} 可空）。 */
    @Query("""
            select t from CimTechnology t
             where t.deleted = false
               and (:kw is null
                    or lower(t.techCode) like lower(concat('%', :kw, '%'))
                    or lower(t.techName) like lower(concat('%', :kw, '%'))
                    or lower(t.techNode) like lower(concat('%', :kw, '%')))
             order by t.techCode asc
            """)
    List<CimTechnology> search(@Param("kw") String keyword);
}
