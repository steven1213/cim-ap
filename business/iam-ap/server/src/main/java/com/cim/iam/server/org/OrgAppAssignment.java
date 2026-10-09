package com.cim.iam.server.org;

import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织级准入授予（identity-directory.md §3.4）。
 *
 * <p>「给某个组织授予某 ap + 粗角色组」，再由 {@code EffectiveAccessResolver} 在运行时
 * 按用户归属（含祖先链）展开到人——<b>不落派生行</b>，避免组织改了派生行没同步的不一致。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "org_app_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_app", columnNames = {"org_id", "app_code"})
})
public class OrgAppAssignment extends BaseDefData {

    @Column(name = "org_id", length = 64, nullable = false)
    private String orgId;

    @Column(name = "app_code", length = 64, nullable = false)
    private String appCode;

    /** ap 内粗角色组，逗号分隔（与 {@code user_app_assignment.roles} 同构）。 */
    @Column(name = "roles", length = 512)
    private String roles;

    /** 是否覆盖子组织（默认覆盖，配合 {@link OrgNode#getPath()} 前缀匹配生效）。 */
    @Column(name = "include_children", nullable = false)
    private boolean includeChildren = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private OrgStatus status = OrgStatus.ENABLED;
}
