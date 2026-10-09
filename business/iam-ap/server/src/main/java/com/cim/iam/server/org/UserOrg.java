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

/**
 * 用户—组织归属（identity-directory.md §3.3）。
 *
 * <p><b>支持多归属</b>（多能工 / 跨线支援：一人同属多个工序、产线，已与用户确认）。
 * {@code isPrimary} 标记主属组织，用于展示与默认数据权限范围。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "user_org", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_org", columnNames = {"user_id", "org_id"})
})
public class UserOrg extends BaseDefData {

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "org_id", length = 64, nullable = false)
    private String orgId;

    /** 是否主属组织。 */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private DataOrigin source = DataOrigin.IAM_MANAGED;
}
