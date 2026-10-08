package com.cim.system.rbac;

import com.cim.auth.token.TokenClaims;
import com.cim.system.user.SysUser;
import com.cim.system.user.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 身份对齐解析：把「已认证的外部身份」（IAM 令牌 / AD 账号）映射到本 ap 的
 * {@link SysUser 授权档案}。
 *
 * <p>单点收敛这件事，避免 {@code DbLocalAuthorityLoader}（认证期）与
 * {@code /sys/me/**}（已登录后的自助接口）各自实现一套匹配规则而漂移。</p>
 *
 * <p>匹配顺序：{@code externalId}（IAM 令牌 {@code sub}，稳定）→ {@code username}（AD 账号）。
 * 两者都取不到即「未建档」。</p>
 *
 * <p>本类方法<b>不加鉴权注解</b>：认证过滤器在 {@code SecurityContext} 写入前就会调用它。</p>
 */
@Service
public class LocalUserResolver {

    private final SysUserRepository userRepository;

    public LocalUserResolver(SysUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 按令牌声明解析本地档案。 */
    @Transactional(readOnly = true)
    public Optional<SysUser> resolve(TokenClaims claims) {
        if (claims == null) {
            return Optional.empty();
        }
        return resolve(claims.userId(), claims.username());
    }

    /** 按外部标识解析本地档案（externalId 优先，username 兜底）。 */
    @Transactional(readOnly = true)
    public Optional<SysUser> resolve(String externalId, String username) {
        if (externalId != null && !externalId.isBlank()) {
            Optional<SysUser> byExternalId =
                    userRepository.findFirstByExternalIdOrderByCreateTimeAsc(externalId);
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }
        if (username != null && !username.isBlank()) {
            return userRepository.findFirstByUsernameOrderByCreateTimeAsc(username);
        }
        return Optional.empty();
    }
}
