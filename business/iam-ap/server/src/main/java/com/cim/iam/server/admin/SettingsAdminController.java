package com.cim.iam.server.admin;

import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.config.IamProperties;
import com.cim.iam.server.config.IamTokenProperties;
import com.cim.iam.server.config.IamWebProperties;
import com.cim.iam.server.directory.AdDirectorySyncProperties;
import com.cim.iam.server.directory.DirectoryApiProperties;
import com.cim.iam.server.support.IamPermissionCodes;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统设置：暴露**生效中的**运行时策略参数（只读）。方法级 {@code iam:settings:view}。
 *
 * <p>刻意不提供在线修改：这些参数影响签发与验证的一致性，改配置应走发布流程（配置中心 / 环境变量）。
 * 此处让管理员能一眼看清当前生效值，便于排障与审计对齐。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IamPermissionCodes.CONSOLE_ADMIN + "')")
public class SettingsAdminController {

    private final IamAuthProperties authProperties;
    private final IamProperties jwtProperties;
    private final IamTokenProperties tokenProperties;
    private final IamWebProperties webProperties;
    private final DirectoryApiProperties directoryApiProperties;
    private final AdDirectorySyncProperties adSyncProperties;

    public record SettingsDto(
            String authSource,
            int passwordRounds,
            boolean pepperConfigured,
            long accessTokenTtlMinutes,
            long refreshTokenTtlMinutes,
            String jwtIssuer,
            String jwtKid,
            boolean rsaKeyInjected,
            int lockoutMaxAttempts,
            int lockoutLockMinutes,
            int lockoutWindowMinutes,
            boolean bootstrapEnabled,
            String bootstrapAdminUsername,
            List<String> webAllowedOrigins,
            String jwksPath,
            boolean directoryApiEnabled,
            boolean adSyncEnabled,
            boolean adSyncConfigured,
            String adBaseDn,
            long adSyncIntervalMs) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.SETTINGS_VIEW + "')")
    public Result<SettingsDto> settings() {
        IamAuthProperties.Password pw = authProperties.getPassword();
        IamAuthProperties.Lockout lk = authProperties.getLockout();
        return Result.ok(new SettingsDto(
                authProperties.getSource(),
                pw.getRounds(),
                pw.getPepper() != null && !pw.getPepper().isBlank(),
                jwtProperties.getAccessTokenTtlMinutes(),
                tokenProperties.getRefreshTokenTtlMinutes(),
                jwtProperties.getIssuer(),
                jwtProperties.getKid(),
                jwtProperties.getPrivateKeyPem() != null && !jwtProperties.getPrivateKeyPem().isBlank(),
                lk.getMaxAttempts(),
                lk.getLockMinutes(),
                lk.getWindowMinutes(),
                authProperties.getBootstrap().isEnabled(),
                authProperties.getBootstrap().getAdminUsername(),
                webProperties.getAllowedOrigins(),
                "/.well-known/jwks.json",
                directoryApiProperties.isEnabled(),
                adSyncProperties.isEnabled(),
                adSyncProperties.isConfigured(),
                adSyncProperties.getBaseDn(),
                adSyncProperties.getSyncIntervalMs()));
    }
}
