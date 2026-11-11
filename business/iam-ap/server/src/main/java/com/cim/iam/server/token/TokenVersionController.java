package com.cim.iam.server.token;

import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 令牌版本「下发」端点（供验证端 {@code IamTokenVersionChecker} 消费，§8.1(g)）。
 *
 * <p>{@code GET /api/v1/internal/token-version?uid=} → {@code Result<Long>}，data 为该用户当前版本号；
 * 验证端本地缓存 + TTL 后比对令牌 {@code ver}（{@code IamTokenVersionChecker} 兼容旧纯文本响应）。</p>
 *
 * <p><b>部署注意</b>：此端点仅对内部网络/服务网格开放（建议 mTLS 或网络策略），不应暴露公网。</p>
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class TokenVersionController {

    private final TokenVersionService service;

    @GetMapping("/token-version")
    public Result<Long> current(@RequestParam("uid") String uid) {
        return Result.ok(service.currentVersion(uid));
    }

    @PostMapping("/token-version/bump")
    public Result<Map<String, Object>> bump(@RequestParam("uid") String uid) {
        long version = service.bump(uid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uid", uid);
        body.put("version", version);
        return Result.ok(body);
    }
}
