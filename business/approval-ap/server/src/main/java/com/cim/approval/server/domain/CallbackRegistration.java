package com.cim.approval.server.domain;

import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 回调注册表：业务系统（如 rms-ap）在接入时登记其回调地址与签名密钥。
 *
 * <p>定义/配置表，审计维度走平台 {@link BaseDefData}：注册时间=create_time、
 * 最近变更=event_time/event_user（回调自动填充）；唯一键含 deleted，支持删后重注册。</p>
 */
@Entity
@Table(name = "callback_registration")
@Getter
@Setter
@NoArgsConstructor
public class CallbackRegistration extends BaseDefData {

    /** 业务系统标识（与 IAM apps claim 对齐），删除态外全局唯一。 */
    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    /** 回调基础地址，如 https://rms.internal/api/approval/callback */
    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    /** 回调签名密钥（HMAC-SHA256），明文仅落库一次，建议生产用 KMS 托管。 */
    @Column(name = "secret", nullable = false, length = 128)
    private String secret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
