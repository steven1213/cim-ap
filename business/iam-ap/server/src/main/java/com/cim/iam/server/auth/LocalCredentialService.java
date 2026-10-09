package com.cim.iam.server.auth;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.token.TokenVersionService;
import com.cim.spring.support.web.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 本地凭证管理（M-login 本地认证源）。
 *
 * <p>注册时仅接收客户端第一层派生的 {@code clientHash}，服务端再做第二层派生入库；
 * 校验时复用同一 pepper 重新派生并与已存 {@code serverHash} 恒定时间比较。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalCredentialService {

    private final LocalCredentialRepository repository;
    private final IdGenerator idGenerator;
    private final IamAuthProperties properties;
    private final TokenVersionService tokenVersionService;
    private final AuditService auditService;

    /** 注册本地用户（入参为客户端第一层派生后的 clientHash）。 */
    @Transactional
    public void registerLocalUser(String username, String clientHash, String clientSalt) {
        if (repository.findByUsername(username).isPresent()) {
            throw BizException.paramInvalid("本地用户已存在: " + username);
        }
        LocalCredential c = new LocalCredential();
        c.setId(idGenerator.nextId());
        c.setDeleted(false);
        c.setUsername(username);
        c.setUserId(username); // 本地用户 userId 即用户名
        c.setClientSalt(clientSalt);
        c.setServerHash(deriveServerHash(clientHash));
        c.setEnabled(true);
        repository.save(c);
        auditService.success(AuditType.USER_CREATED, null, username, "创建本地账号");
        log.info("[local-cred] 注册本地用户 username={}", username);
    }

    public Optional<LocalCredential> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    public Optional<LocalCredential> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    /** 本地账号清单（管理面用户列表用）。 */
    public List<LocalCredential> listAll() {
        return repository.findAll();
    }

    /** 启用 / 禁用本地账号（禁用即 bump 令牌版本强制下线）。 */
    @Transactional
    public void setEnabled(String userId, boolean enabled) {
        LocalCredential c = repository.findByUserId(userId)
                .orElseThrow(() -> BizException.notFound("本地账号不存在: " + userId));
        if (c.isEnabled() == enabled) {
            return;
        }
        c.setEnabled(enabled);
        repository.save(c);
        if (!enabled) {
            tokenVersionService.bump(userId); // 禁用即强制下线
        }
        auditService.success(enabled ? AuditType.USER_ENABLED : AuditType.USER_DISABLED,
                null, userId, enabled ? "启用账号" : "禁用账号（已强制下线）");
    }

    /** 管理员重置本地账号口令（入参为客户端第一层派生后的 clientHash + 随机盐）。 */
    @Transactional
    public void resetPassword(String userId, String clientHash, String clientSalt) {
        LocalCredential c = repository.findByUserId(userId)
                .orElseThrow(() -> BizException.notFound("本地账号不存在: " + userId));
        c.setClientSalt(clientSalt);
        c.setServerHash(deriveServerHash(clientHash));
        repository.save(c);
        tokenVersionService.bump(userId); // 重置即强制下线
        auditService.success(AuditType.PASSWORD_RESET, null, userId, "管理员重置口令（已强制下线）");
        log.info("[local-cred] 管理员重置用户 {} 口令（已 bump 令牌版本）", userId);
    }

    /** 删除本地账号（同时 bump 使其存量令牌失效）。 */
    @Transactional
    public void deleteUser(String userId) {
        LocalCredential c = repository.findByUserId(userId)
                .orElseThrow(() -> BizException.notFound("本地账号不存在: " + userId));
        tokenVersionService.bump(userId);
        repository.delete(c);
        auditService.success(AuditType.USER_DELETED, null, userId, "删除本地账号（已强制下线）");
        log.info("[local-cred] 删除本地用户 {}", userId);
    }

    /** 第二层派生：serverHash = PBKDF2(clientHash, serverPepper)。 */
    String deriveServerHash(String clientHash) {
        return PasswordDerivation.derive(
                clientHash,
                properties.getPassword().getPepper(),
                properties.getPassword().getRounds());
    }

    /** 校验本地账号明文口令（两层派生后恒定时间比对），不存在的账号返回 false。 */
    public boolean verifyPassword(String username, String rawPassword) {
        return findByUsername(username)
                .map(c -> {
                    String clientHash = PasswordDerivation.derive(
                            rawPassword, c.getClientSalt(), properties.getPassword().getRounds());
                    return PasswordDerivation.constantTimeEquals(deriveServerHash(clientHash), c.getServerHash());
                })
                .orElse(false);
    }

    /**
     * 自助改密（与登录一致，明文口令不出浏览器）。
     *
     * <p>入参 {@code oldCredential}/{@code newCredential} 为客户端第一层派生的 {@code clientHash}，
     * 服务端仅做第二层派生校验与入库：先用 {@code pepper} 对 {@code oldCredential} 二次派生并与已存
     * {@code serverHash} 恒定时间比对，再对 {@code newCredential} 二次派生后入库，并写入新的
     * {@code newClientSalt}（由客户端随机生成，随新口令的第一层派生一起下发）。</p>
     */
    @Transactional
    public void changePassword(String username, String oldCredential, String newCredential, String newClientSalt) {
        LocalCredential c = findByUsername(username)
                .orElseThrow(() -> BizException.paramInvalid("本地账号不存在: " + username));
        // 旧口令校验：第二层派生 oldCredential 并与已存 serverHash 恒定时间比对
        if (!PasswordDerivation.constantTimeEquals(deriveServerHash(oldCredential), c.getServerHash())) {
            throw BizException.paramInvalid("原口令校验失败");
        }
        c.setClientSalt(newClientSalt);
        c.setServerHash(deriveServerHash(newCredential));
        repository.save(c);
        // 改密即让该用户所有已签发令牌失效：bump 令牌版本，验证端旧 ver 不再匹配 → 401 强制重登
        // （本地用户 userId == username，与令牌 uid claim 一致）。
        tokenVersionService.bump(c.getUserId());
        auditService.success(AuditType.PASSWORD_CHANGED, username, c.getUserId(), "自助改密（已强制下线）");
        log.info("[local-cred] 用户 {} 修改口令（已 bump 令牌版本强制旧会话失效）", username);
    }

    private static String randomHex(int bytes) {
        java.security.SecureRandom r = new java.security.SecureRandom();
        byte[] b = new byte[bytes];
        r.nextBytes(b);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
