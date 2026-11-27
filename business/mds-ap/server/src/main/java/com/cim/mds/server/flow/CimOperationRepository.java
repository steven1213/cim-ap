package com.cim.mds.server.flow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 工序库仓储——查询方法语义与 CimFlowRepository 对齐（版本族激活唯一性守门用）。
 */
public interface CimOperationRepository extends JpaRepository<CimOperation, String> {

    Optional<CimOperation> findByOpCodeAndDeletedFalse(String opCode);

    /** 同版本族当前 ACTIVE（激活时旧版自动降级 FROZEN 的查据）。 */
    Optional<CimOperation> findByOpFamilyCodeAndDeletedFalseAndStatus(String opFamilyCode, String status);

    /** 族内当前最大版本号（复制递增用）。 */
    Optional<CimOperation> findFirstByOpFamilyCodeAndDeletedFalseOrderByVersionNoDesc(String opFamilyCode);

    /** 光罩反向引用守门（V32）：reticleRef 引用计数查据。 */
    boolean existsByReticleRefAndDeletedFalse(String reticleRef);

    /** 列表 + 关键字（编码/名称/工艺编码模糊，{@code kw} 可空）。 */
    @Query("""
            select o from CimOperation o
             where o.deleted = false
               and (:kw is null
                    or lower(o.opCode) like lower(concat('%', :kw, '%'))
                    or lower(o.opName) like lower(concat('%', :kw, '%'))
                    or lower(o.techCode) like lower(concat('%', :kw, '%')))
             order by o.opCode asc
            """)
    List<CimOperation> search(@Param("kw") String keyword);
}
