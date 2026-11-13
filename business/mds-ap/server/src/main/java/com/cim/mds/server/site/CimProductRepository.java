package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 产品仓储（产品管理域）。 */
public interface CimProductRepository extends JpaRepository<CimProduct, String> {

    Optional<CimProduct> findByProductCodeAndDeletedFalse(String productCode);

    /** 制程技术删除守门：是否存在仍引用该技术的产品（含未启用）。 */
    boolean existsByTechCodeAndDeletedFalse(String techCode);

    /** 列表 + 关键字（编码/名称/分组/技术编码模糊，{@code kw} 可空）。 */
    @Query("""
            select p from CimProduct p
             where p.deleted = false
               and (:kw is null
                    or lower(p.productCode) like lower(concat('%', :kw, '%'))
                    or lower(p.productName) like lower(concat('%', :kw, '%'))
                    or lower(p.productGroup) like lower(concat('%', :kw, '%'))
                    or lower(p.techCode) like lower(concat('%', :kw, '%')))
             order by p.productCode asc
            """)
    List<CimProduct> search(@Param("kw") String keyword);
}
