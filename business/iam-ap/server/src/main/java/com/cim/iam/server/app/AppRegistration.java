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
 * 业务 ap 注册表（design.md §8 边界表「ap 注册/准入管理」）。
 *
 * <p>每个业务 ap（mds-ap / mes-ap …）在此登记唯一接入码 {@code appCode}；
 * 用户能否进入某 ap 由其 {@link UserAppAssignment} 与本条 {@code status} 共同决定。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "app_registration", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_registration_code", columnNames = "app_code")
})
public class AppRegistration extends BaseDefData {

    /** 接入码（如 mds-ap），令牌 apps claim 的取值来源。 */
    @Column(name = "app_code", length = 64, nullable = false)
    private String appCode;

    @Column(name = "app_name", length = 128)
    private String appName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private AppStatus status = AppStatus.ENABLED;

    @Column(name = "sort_no")
    private int sortNo = 0;
}
