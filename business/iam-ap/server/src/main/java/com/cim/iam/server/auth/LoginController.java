package com.cim.iam.server.auth;

import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 登录 / 令牌端点（IAM 统一登录入口，M-login + M-refresh）。
 *
 * <p>{@code POST /api/v1/login}：{@code {username, credential, clientSalt?}} →
 * {@code {token, refreshToken, expiresInSeconds, tokenType}}。{@code credential} 语义随认证源（见
 * {@link LoginCredentials}）。</p>
 *
 * <p>{@code POST /api/v1/token/refresh}：用刷新令牌换取新访问令牌 + 新刷新令牌（轮转）。</p>
 *
 * <p>{@code POST /api/v1/logout}：登出当前会话（拉黑访问令牌 jti + 撤销刷新令牌 + bump 版本）。</p>
 *
 * <p>{@code GET /api/v1/login/salt?username=}：下发该用户的 {@code clientSalt}，供客户端完成
 * 第一层派生（明文口令不出浏览器）。用户不存在时返回空盐，避免账号枚举。</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final LocalCredentialService credentialService;

    public record LoginRequest(String username, String credential, String clientSalt) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record SaltResponse(String username, String clientSalt) {
    }

    @PostMapping("/login")
    public Result<LoginService.LoginResult> login(@RequestBody LoginRequest req) {
        return Result.ok(loginService.login(
                new LoginCredentials(req.username(), req.credential(), req.clientSalt())));
    }

    @PostMapping("/token/refresh")
    public Result<LoginService.LoginResult> refresh(@RequestBody RefreshRequest req) {
        return Result.ok(loginService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestBody(required = false) LogoutRequest req) {
        String accessToken = (authorization != null && authorization.startsWith("Bearer "))
                ? authorization.substring(7).trim() : null;
        String refreshToken = req != null ? req.refreshToken() : null;
        loginService.logout(accessToken, refreshToken);
        return Result.ok();
    }

    @GetMapping("/login/salt")
    public Result<SaltResponse> salt(@RequestParam String username) {
        String salt = credentialService.findByUsername(username)
                .map(LocalCredential::getClientSalt)
                .orElse("");
        return Result.ok(new SaltResponse(username, salt));
    }
}
