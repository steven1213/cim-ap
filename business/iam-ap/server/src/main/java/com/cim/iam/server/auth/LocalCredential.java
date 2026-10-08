package com.cim.iam.server.auth;

import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 本地凭证（M-login 本地认证源）。
 *
 * <p>仅承载「两层派生后的服务端散列」与每用户 {@code clientSalt}；<b>不存明文口令</b>，
 * {@code serverHash} 由 {@link PasswordDerivation} 第二层派生得到（salt = 服务端 pepper）。</p>
 *
 * <p>注意：员工主身份仍由 AD/LDAP 托管；本表用于无 AD 的开发环境、服务账号或本地兜底。
 * 结构同 {@code AppRegistration} 等继承 {@link BaseDefData}（含审计列）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "local_credential", uniqueConstraints = {
        @UniqueConstraint(name = "uk_local_credential_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_local_credential_user_id", columnNames = "user_id")
})
public class LocalCredential extends BaseDefData {

    @Column(name = "username", nullable = false, length = 128)
    private String username;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "client_salt", nullable = false, length = 255)
    private String clientSalt;

    @Column(name = "server_hash", nullable = false, length = 512)
    private String serverHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
