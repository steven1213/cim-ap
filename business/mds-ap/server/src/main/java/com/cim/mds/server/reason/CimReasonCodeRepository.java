package com.cim.mds.server.reason;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 原因码仓储（V16）。清单按 node_path 升序返回（= 树的深度优先展开序）。
 */
public interface CimReasonCodeRepository extends JpaRepository<CimReasonCode, String> {

    Optional<CimReasonCode> findByIdAndDeletedFalse(String id);

    Optional<CimReasonCode> findByReasonCodeAndDeletedFalse(String reasonCode);

    boolean existsByReasonCodeAndDeletedFalse(String reasonCode);

    /** 全量（node_path 升序 = 深度优先树序）。 */
    List<CimReasonCode> findByDeletedFalseOrderByNodePathAsc();

    /** 按语义域过滤。 */
    List<CimReasonCode> findByTypeKeyAndDeletedFalseOrderByNodePathAsc(String typeKey);

    /** 子节点（挪树/删码守门用）。 */
    List<CimReasonCode> findByParentIdAndDeletedFalse(String parentId);

    /** 子树（含自身前缀的所有后代，重挂树时整段重算路径）。 */
    List<CimReasonCode> findByNodePathStartingWithAndDeletedFalse(String nodePath);

    /** 语义域内启用码（WIP 下拉口径：type 匹配 + enabled）。 */
    List<CimReasonCode> findByTypeKeyAndEnabledTrueAndDeletedFalseOrderBySortNoAscNodePathAsc(
            String typeKey);
}
