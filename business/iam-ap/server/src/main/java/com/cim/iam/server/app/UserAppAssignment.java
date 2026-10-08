package com.cim.iam.server.app;

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
 * 用户-应用分配（准入依据）。
 *
 * <p>用户被分配到某 appCode 后，其令牌 {@code apps} claim 即包含该接入码（准入通过）；
 * {@code roles} 为该 ap 内的粗角色组（如 ADMIN/OPERATOR），逗号分隔，写入令牌 {@code roles} claim。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "user_app_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_app", columnNames = {"user_id", "app_code"})
})
public class UserAppAssignment extends BaseDefData {

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "app_code", length = 64, nullable = false)
    private String appCode;

    /** ap 内粗角色组，逗号分隔（ADMIN,OPERATOR）。 */
    @Column(name = "roles", length = 512)
    private String roles;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private AppStatus status = AppStatus.ENABLED;
}
