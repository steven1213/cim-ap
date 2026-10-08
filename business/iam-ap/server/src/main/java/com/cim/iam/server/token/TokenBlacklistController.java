package com.cim.iam.server.token;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 令牌黑名单内部查询端点（design.md §8.1(h) 验证侧对接）。
 *
 * <p>{@code GET /api/v1/internal/token-blacklist?jti=} → 响应体为该 jti 是否已被拉黑
 * （{@code true}/{@code false}）。由 platform 的 {@code IamTokenBlacklistChecker} 调用。</p>
 *
 * <p>注意：此端点面向内部网络（验证侧 ap 与 IAM 之间），生产应置于内网 / mTLS 之后。</p>
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class TokenBlacklistController {

    private final TokenBlacklistService service;

    @GetMapping("/token-blacklist")
    public String isRevoked(@RequestParam String jti) {
        return Boolean.toString(service.isRevoked(jti));
    }
}
