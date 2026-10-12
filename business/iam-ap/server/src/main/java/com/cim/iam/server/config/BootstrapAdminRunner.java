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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 首管理员环境变量引导（IAM 管理面初始化）。
 *
 * <p>若库内尚不存在引导管理员（按 {@code cim.iam.auth.bootstrap.admin-username} 判定），
 * 则：用两层派生创建本地凭证。此后该用户登录拿到的令牌即具 {@code iam-ap:ADMIN} 权威，
 * 可访问 {@code /api/v1/apps/**} 管理端点。</p>
 *
 * <p><b>接入码与准入（每次启动都补齐，幂等）</b>：注册 {@code iam-ap}（IAM 控制台自身）以及
 * {@code cim.iam.auth.bootstrap.apps} 配置的业务接入码，并把首管理员授予这些 ap 的准入。
 * 这一步刻意放在「管理员是否已存在」判断<b>之外</b>——否则库内已有管理员时新增的接入码
 * 永远不会被登记，业务 ap（如 {@code rms-ap}）将拿不到含该码的 {@code apps} claim，
 * 表现为 SSO 回跳后业务 ap 403、两端登录页互相弹跳。</p>
 *
 * <p>口令取自 {@code cim.iam.auth.bootstrap.admin-password}；若为空则生成随机 16 位口令并打印日志
 * （仅 dev 用，生产务必注入强口令）。重复启动（管理员已存在）只补齐接入码，不再重建凭证。</p>
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

        // 1) 首管理员凭证（仅首次创建；已存在则跳过，避免覆盖线上口令）
        if (credentialService.findByUsername(adminUsername).isEmpty()) {
            int rounds = properties.getPassword().getRounds();
            String rawPassword = (bootstrap != null && bootstrap.getAdminPassword() != null
                    && !bootstrap.getAdminPassword().isBlank())
                    ? bootstrap.getAdminPassword() : generatePassword();
            String clientSalt = randomHex(16);
            String clientHash = PasswordDerivation.derive(rawPassword, clientSalt, rounds);
            credentialService.registerLocalUser(adminUsername, clientHash, clientSalt);

            if (bootstrap == null || bootstrap.getAdminPassword() == null
                    || bootstrap.getAdminPassword().isBlank()) {
                log.warn("[iam-bootstrap] 首管理员 {} 已创建，随机口令（仅本次启动可见）：{}",
                        adminUsername, rawPassword);
            } else {
                log.info("[iam-bootstrap] 首管理员 {} 已使用配置口令创建", adminUsername);
            }
        }

        // 2) 接入码注册 + 管理员准入（幂等；每次启动都补齐）
        ensureApp(adminUsername, IAM_APP_CODE, "IAM 控制台", List.of(ADMIN_ROLE));

        List<IamAuthProperties.BootstrapApp> apps =
                bootstrap == null ? List.of() : bootstrap.getApps();
        if (apps != null) {
            for (IamAuthProperties.BootstrapApp app : apps) {
                if (app.getCode() == null || app.getCode().isBlank()) {
                    continue;
                }
                String name = (app.getName() == null || app.getName().isBlank())
                        ? app.getCode() : app.getName();
                ensureApp(adminUsername, app.getCode(), name, app.getRoles());
            }
        }
    }

    /**
     * 幂等注册单个接入码，并把首管理员授予该 ap 准入。
     *
     * <p>仅在「接入码未登记」或「管理员未获准入」时才写库，故重复启动不会反复 bump
     * 用户令牌版本（否则每次重启都会强制管理员下线）。失败仅告警、不阻断启动——
     * 管理员随后可在控制台「应用注册」页手动登记。</p>
     */
    private void ensureApp(String adminUsername, String code, String name, List<String> roles) {
        try {
            if (appRegistrationService.listApps().stream()
                    .noneMatch(a -> code.equals(a.getAppCode()))) {
                appRegistrationService.registerApp(code, name, appRegistrationService.listApps().size());
                log.info("[iam-bootstrap] 已登记接入码 {}（{}）", code, name);
            }
            boolean admitted = appRegistrationService.listAssignmentsForUser(adminUsername).stream()
                    .anyMatch(a -> code.equals(a.getAppCode()));
            if (!admitted) {
                Set<String> roleSet = (roles == null || roles.isEmpty())
                        ? Set.of() : new LinkedHashSet<>(roles);
                appRegistrationService.assignUserToApp(adminUsername, code, roleSet);
                log.info("[iam-bootstrap] 已授予 {} 准入 {}（角色组 {}）",
                        adminUsername, code, roleSet.isEmpty() ? "无" : roleSet);
            }
        } catch (Exception e) {
            log.warn("[iam-bootstrap] 接入码 {} 引导失败：{}", code, e.getMessage());
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
