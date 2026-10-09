package com.cim.iam.server.config;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.PasswordDerivation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Set;

/**
 * 首管理员环境变量引导（IAM 管理面初始化）。
 *
 * <p>若库内尚不存在引导管理员（按 {@code cim.iam.auth.bootstrap.admin-username} 判定），
 * 则：确保注册 {@code iam-ap} 应用（IAM 控制台自身）→ 用两层派生创建本地凭证
 * → 赋 {@code iam-ap:ADMIN} 角色。此后该用户登录拿到的令牌即具 {@code iam-ap:ADMIN} 权威，
 * 可访问 {@code /api/v1/apps/**} 管理端点。</p>
 *
 * <p>口令取自 {@code cim.iam.auth.bootstrap.admin-password}；若为空则生成随机 16 位口令并打印日志
 * （仅 dev 用，生产务必注入强口令）。重复启动（管理员已存在）自动跳过。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final String IAM_APP_CODE = "iam-ap";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppRegistrationService appRegistrationService;
    private final LocalCredentialService credentialService;
    private final IamAuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        IamAuthProperties.Bootstrap bootstrap = properties.getBootstrap();
        if (bootstrap != null && !bootstrap.isEnabled()) {
            log.info("[iam-bootstrap] 首管理员引导已禁用（cim.iam.auth.bootstrap.enabled=false）");
            return;
        }
        String adminUsername = bootstrap == null || bootstrap.getAdminUsername().isBlank()
                ? "admin" : bootstrap.getAdminUsername();

        if (credentialService.findByUsername(adminUsername).isPresent()) {
            log.info("[iam-bootstrap] 首管理员 {} 已存在，跳过引导", adminUsername);
            return;
        }

        // 1) 确保 IAM 控制台自身作为 app 注册（管理端点鉴权依赖 iam-ap:ADMIN）
        if (appRegistrationService.listApps().stream()
                .noneMatch(a -> IAM_APP_CODE.equals(a.getAppCode()))) {
            appRegistrationService.registerApp(IAM_APP_CODE, "IAM 控制台", 0);
        }

        // 2) 创建本地凭证（两层派生：server 端完成 clientHash + serverHash）
        int rounds = properties.getPassword().getRounds();
        String rawPassword = (bootstrap != null && bootstrap.getAdminPassword() != null
                && !bootstrap.getAdminPassword().isBlank())
                ? bootstrap.getAdminPassword() : generatePassword();
        String clientSalt = randomHex(16);
        String clientHash = PasswordDerivation.derive(rawPassword, clientSalt, rounds);
        credentialService.registerLocalUser(adminUsername, clientHash, clientSalt);

        // 3) 赋 IAM 控制台管理员角色
        appRegistrationService.assignUserToApp(adminUsername, IAM_APP_CODE, Set.of(ADMIN_ROLE));

        if (bootstrap == null || bootstrap.getAdminPassword() == null || bootstrap.getAdminPassword().isBlank()) {
            log.warn("[iam-bootstrap] 首管理员 {} 已创建，随机口令（仅本次启动可见）：{}",
                    adminUsername, rawPassword);
        } else {
            log.info("[iam-bootstrap] 首管理员 {} 已使用配置口令创建", adminUsername);
        }
    }

    private static String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
