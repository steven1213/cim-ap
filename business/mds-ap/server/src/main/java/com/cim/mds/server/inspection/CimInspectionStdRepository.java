package com.cim.mds.server.inspection;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 检验标准（头）仓储。 */
public interface CimInspectionStdRepository extends JpaRepository<CimInspectionStd, String> {

    Optional<CimInspectionStd> findByStdCodeAndDeletedFalse(String stdCode);

    boolean existsByStdCodeAndDeletedFalse(String stdCode);

    /** 列表：按产品分组过滤（可空）+ 关键字（编码/名称模糊）。 */
    @Query("""
            select s from CimInspectionStd s
             where s.deleted = false
               and (:productGroup is null or s.productGroup = :productGroup)
               and (:kw is null
                    or lower(s.stdCode) like lower(concat('%', :kw, '%'))
                    or lower(s.stdName) like lower(concat('%', :kw, '%')))
             order by s.stdCode asc
            """)
    List<CimInspectionStd> search(@Param("productGroup") String productGroup,
                                  @Param("kw") String keyword);
}
