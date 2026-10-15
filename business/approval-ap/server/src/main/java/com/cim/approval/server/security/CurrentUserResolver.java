package com.cim.approval.server.security;

import com.cim.auth.principal.CimUserPrincipal;
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
 */
@Component
public class CurrentUserResolver {

    @Value("${cim.auth.enabled:false}")
    private boolean authEnabled;

    /** 必须有身份的接口：认证态从 JWT 取，dev 态用参数（缺参报错）。 */
    public String resolve(String paramUser) {
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
        if (authEnabled) {
            return jwtUser();
        }
        return paramUser;
    }

    public boolean authEnabled() {
        return authEnabled;
    }

    private String jwtUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CimUserPrincipal p && p.userId() != null) {
            return p.userId();
        }
        return null;
    }
}
