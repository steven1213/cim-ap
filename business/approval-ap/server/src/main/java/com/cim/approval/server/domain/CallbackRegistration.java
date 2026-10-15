package com.cim.approval.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 回调注册表：业务系统（如 rms-ap）在接入时登记其回调地址与签名密钥。
 */
@Entity
@Table(name = "callback_registration")
@Getter
@Setter
@NoArgsConstructor
public class CallbackRegistration {

    @Id
    @Column(length = 40)
    private String id;

    /** 业务系统标识（与 IAM apps claim 对齐），全局唯一。 */
    @Column(name = "app_code", nullable = false, unique = true, length = 64)
    private String appCode;

    /** 回调基础地址，如 https://rms.internal/api/approval/callback */
    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    /** 回调签名密钥（HMAC-SHA256），明文仅落库一次，建议生产用 KMS 托管。 */
    @Column(name = "secret", nullable = false, length = 128)
    private String secret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
