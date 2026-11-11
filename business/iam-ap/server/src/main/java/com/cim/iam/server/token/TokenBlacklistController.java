package com.cim.iam.server.token;

import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 令牌黑名单内部查询端点（design.md §8.1(h) 验证侧对接）。
 *
 * <p>{@code GET /api/v1/internal/token-blacklist?jti=} → {@code Result<Boolean>}，data 为该 jti 是否
 * 已被拉黑。由 platform 的 {@code IamTokenBlacklistChecker} 调用；该 checker 同时兼容旧纯文本响应，
 * 便于 IAM 先升级、各 ap 后升级的滚动发布。</p>
 *
 * <p>注意：此端点面向内部网络（验证侧 ap 与 IAM 之间），生产应置于内网 / mTLS 之后。</p>
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class TokenBlacklistController {

    private final TokenBlacklistService service;

    @GetMapping("/token-blacklist")
    public Result<Boolean> isRevoked(@RequestParam String jti) {
        return Result.ok(service.isRevoked(jti));
    }
}
