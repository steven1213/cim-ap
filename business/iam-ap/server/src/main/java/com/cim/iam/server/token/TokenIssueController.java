package com.cim.iam.server.token;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 令牌签发端点（IAM 内部签发接口）。
 *
 * <p><b>生产前置</b>：签发前须由登录流程（LDAP/AD + 口令前端加密 / 服务端二次派生）完成身份校验，
 * 此处仅执行「已认证身份 → 令牌」。登录流程属后续里程碑（M-login），当前为签发能力打通</p>
 *
 * <p>返回 {@code {token}}，调用方（业务 ap 登录页 / 网关）据此换取 IAM 令牌。</p>
 */
@RestController
@RequestMapping("/api/v1/token")
@RequiredArgsConstructor
public class TokenIssueController {

    private final TokenIssuerService issuer;

    public record IssuedToken(String token) {}

    @PostMapping("/issue")
    public IssuedToken issue(@RequestBody TokenIssueRequest req) {
        return new IssuedToken(issuer.issue(req));
    }
}
