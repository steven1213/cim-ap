package com.cim.approval.server.security;

import com.cim.auth.principal.CimUserPrincipal;
import java.util.List;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前操作者解析器：授权唯一可信来源。
 *
 * <p><b>认证态（cim.auth.enabled=true）</b>：身份强制取自 JWT（{@link CimUserPrincipal}），
 * 前端传入的 userId/actor 参数<b>一律忽略</b>——防止伪造身份代办、删证。
 * <b>开发/测试态（enabled=false）</b>：回退使用前端传参（保留 dev 身份切换与 E2E 工作流）。</p>
 *
 * <p><b>开放 API（X-App-Key 已验签）</b>：走「参数优先」——此时请求体与查询串已被 HMAC 签名
 * 覆盖，改不动，`initiator`/`actor` 由业务系统代传是契约的一部分。若沿用「认证态忽略参数」，
 * 服务间发起会因「令牌缺少用户标识」被拒（服务身份没有 JWT）。</p>
 */
@Component
public class CurrentUserResolver {

    @Value("${cim.auth.enabled:false}")
    private boolean authEnabled;

    /** 必须有身份的接口：认证态从 JWT 取，dev 态用参数（缺参报错）。 */
    public String resolve(String paramUser) {
        if (viaOpenApi()) {
            if (paramUser == null || paramUser.isBlank()) {
                throw new BizException(BizCode.PARAM_INVALID, "开放 API 调用须指定操作人（initiator / actor）");
            }
            return paramUser;
        }
        if (authEnabled) {
            String jwtUser = jwtUser();
            if (jwtUser == null) {
                throw new BizException(BizCode.ACCESS_DENIED, "未认证或令牌缺少用户标识");
            }
            return jwtUser;
        }
        if (paramUser == null || paramUser.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "缺少用户标识参数（dev 模式）");
        }
        return paramUser;
    }

    /** 可选身份的接口（如附件下载）：认证态取 JWT；dev 态可能返回 null（由调用方决定是否跳过校验）。 */
    public String resolveNullable(String paramUser) {
        if (viaOpenApi()) {
            return paramUser;
        }
        if (authEnabled) {
            return jwtUser();
        }
        return paramUser;
    }

    public boolean authEnabled() {
        return authEnabled;
    }

    /**
     * 解析当前用户角色集合（G6 / D8）：认证态从 JWT 的 {@code roles} claim 取，
     * dev 态用前端传入的 {@code roles} 参数（缺省为空，仅影响角色型候选命中）。
     */
    public List<String> resolveRoles(List<String> paramRoles) {
        if (viaOpenApi()) {
            return paramRoles != null ? paramRoles : List.of();
        }
        if (authEnabled) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof CimUserPrincipal p && p.roles() != null) {
                return List.copyOf(p.roles());
            }
            return List.of();
        }
        return paramRoles != null ? paramRoles : List.of();
    }

    /** 当前请求是否来自已验签的开放 API 调用（X-App-Key 通道）。 */
    private boolean viaOpenApi() {
        RequestScope scope = RequestScopeHolder.get();
        return scope != null && scope.viaOpenApi();
    }

    private String jwtUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CimUserPrincipal p && p.userId() != null) {
            return p.userId();
        }
        return null;
    }
}
