package com.cim.iam.server.org;

import com.cim.iam.server.common.DataOrigin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrgNodeRepository extends JpaRepository<OrgNode, String> {

    List<OrgNode> findByParentId(String parentId);

    Optional<OrgNode> findBySourceAndCode(DataOrigin source, String code);

    Optional<OrgNode> findBySourceAndExternalId(DataOrigin source, String externalId);

    /** 物化路径前缀匹配：{@code /{orgId}/%} 命中自身与全部后代。 */
    List<OrgNode> findByPathStartingWith(String pathPrefix);

    List<OrgNode> findBySource(DataOrigin source);
}
