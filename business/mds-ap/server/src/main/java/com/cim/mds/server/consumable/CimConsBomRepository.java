package com.cim.mds.server.consumable;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 消耗品 BOM（头）仓储。 */
public interface CimConsBomRepository extends JpaRepository<CimConsBom, String> {

    Optional<CimConsBom> findByBomCodeAndDeletedFalse(String bomCode);

    boolean existsByBomCodeAndDeletedFalse(String bomCode);

    /** 列表：按绑定上下文/状态过滤（可空）+ 关键字（编码/名称模糊）。 */
    @Query("""
            select b from CimConsBom b
             where b.deleted = false
               and (:productId is null or b.productId = :productId)
               and (:flowId is null or b.flowId = :flowId)
               and (:operationId is null or b.operationId = :operationId)
               and (:status is null or b.status = :status)
               and (:kw is null
                    or lower(b.bomCode) like lower(concat('%', :kw, '%'))
                    or lower(b.bomName) like lower(concat('%', :kw, '%')))
             order by b.bomCode asc
            """)
    List<CimConsBom> search(@Param("productId") String productId,
                            @Param("flowId") String flowId,
                            @Param("operationId") String operationId,
                            @Param("status") String status,
                            @Param("kw") String keyword);

    /** 同上下文全部未删头（唯一 ACTIVE 守门 + 激活序号计算）。 */
    List<CimConsBom> findByProductIdAndFlowIdAndOperationIdAndDeletedFalse(
            String productId, String flowId, String operationId);
}
