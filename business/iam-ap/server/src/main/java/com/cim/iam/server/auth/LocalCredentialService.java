package com.cim.iam.server.auth;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.spring.support.web.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        log.info("[local-cred] 注册本地用户 username={}", username);
    }

    public Optional<LocalCredential> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    /** 第二层派生：serverHash = PBKDF2(clientHash, serverPepper)。 */
    String deriveServerHash(String clientHash) {
        return PasswordDerivation.derive(
                clientHash,
                properties.getPassword().getPepper(),
                properties.getPassword().getRounds());
    }
}
