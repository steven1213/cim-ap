package com.cim.iam.server.profile;

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
 * 用户档案（identity-directory.md §3.2）。
 *
 * <p>{@code user_id} 与 {@code local_credential.user_id}、令牌 {@code uid} <b>同源</b>，不另造 ID。
 * 与本地凭证<b>解耦</b>：AD 人员可能没有本地凭证；本地凭证账号（厂商 / 服务账号）可能没有 AD 来源。
 * 二者靠 {@code user_id} 关联，互不要求存在。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "user_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profile_user", columnNames = "user_id")
})
public class UserProfile extends BaseDefData {

    /** 与凭证、令牌 uid 同源。 */
    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    /** 工号（AD {@code employeeID}）。 */
    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    /** 姓名。 */
    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "mobile", length = 128)
    private String mobile;

    /** 岗位（IAM 维护，AD 常无此维度）。 */
    @Column(name = "job_title", length = 64)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private DataOrigin source = DataOrigin.IAM_MANAGED;

    /** 最近一次 AD 同步时间。 */
    @Column(name = "synced_at")
    private Instant syncedAt;
}
