package com.cim.iam.server.org;

import com.cim.core.model.BaseDefData;
import com.cim.iam.server.common.DataOrigin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 组织节点（单表自引用树 + 物化路径，identity-directory.md §3.1）。
 *
 * <p>{@code path} 形如 {@code /{rootId}/{...}/{selfId}/}，前缀匹配即可一次查出全部祖先授予
 * （{@code path LIKE '/{orgId}/%'} 命中全部后代）。{@code source} 隔离 AD 同步层与 IAM 自建层。</p>
 *
 * <p>编码 {@code code} 与 {@code source} 组合唯一：<b>若 MES 侧已有既定编码规则，直接采用同一套编码</b>，
 * 不再另造第二套（identity-directory.md §9 决策 3）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "org_node", uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_node_source_code", columnNames = {"source", "code"})
})
public class OrgNode extends BaseDefData {

    /** 父节点 ID；根节点为 {@code null}。 */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    /** 组织编码（与 MES 侧共用同一套，勿另造）。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 显示名。 */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", length = 16, nullable = false)
    private OrgNodeType nodeType;

    /** 物化路径 {@code /{rootId}/{...}/{selfId}/}。 */
    @Column(name = "path", length = 512)
    private String path;

    /** 同级排序。 */
    @Column(name = "sort_no")
    private int sortNo = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private DataOrigin source = DataOrigin.IAM_MANAGED;

    /** AD 侧标识（DN / objectGUID），仅 {@code AD_SYNCED} 节点有。 */
    @Column(name = "external_id", length = 128)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private OrgStatus status = OrgStatus.ENABLED;

    /** 最近变更时间（供业务侧增量比对）。 */
    @Column(name = "updated_at")
    private Instant updatedAt;
}
