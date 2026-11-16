package com.cim.mds.server.flow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 制造流程仓储（Flow 域主档）。 */
public interface CimFlowRepository extends JpaRepository<CimFlow, String> {

    Optional<CimFlow> findByFlowCodeAndDeletedFalse(String flowCode);

    boolean existsByFlowCodeAndDeletedFalse(String flowCode);

    /** 版本族内全部版本（版本号降序；复制取 max+1、激活降级扫旧 ACTIVE 用）。 */
    List<CimFlow> findByFamilyCodeAndDeletedFalse(String familyCode);

    /** 族内当前 ACTIVE 版本（单 Active 约束：同族最多一条）。 */
    Optional<CimFlow> findByFamilyCodeAndDeletedFalseAndStatus(String familyCode, String status);

    /** 列表 + 关键字（编码/名称/技术编码模糊，{@code kw} 可空）。 */
    @Query("""
            select f from CimFlow f
             where f.deleted = false
               and (:kw is null
                    or lower(f.flowCode) like lower(concat('%', :kw, '%'))
                    or lower(f.flowName) like lower(concat('%', :kw, '%'))
                    or lower(f.techCode) like lower(concat('%', :kw, '%')))
             order by f.flowCode asc
            """)
    List<CimFlow> search(@Param("kw") String keyword);
}
