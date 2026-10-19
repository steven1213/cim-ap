package com.cim.approval.server.security;

import java.util.List;

/**
 * 单次请求的业务域作用域（G3 / G9）。
 *
 * <p>仅当请求经 {@link ApiAuthFilter} 以开放 API 身份（X-App-Key）鉴权通过后才被填充，
 * 此时 {@link #viaOpenApi} = true，{@link #allowedAppCodes} 收敛为该 appKey 绑定的单一业务域，
 * 调用方不可越权访问其他业务域。JWT 用户态与 dev 放行态下该作用域为空（由
 * {@link SecurityGuard} 按认证态推导）。</p>
 *
 * @param allowedAppCodes 调用方可访问的业务域集合；"*" 表示全部（超管/信任态）
 * @param resolvedAppCode 开放 API 解析出的业务域（即 X-App-Key 对应的 appCode）
 * @param viaOpenApi      是否经开放 API 鉴权
 * @param superAdmin      是否超管（开放 API 恒为 false，跨域仅由 JWT 超管权威授予）
 */
public record RequestScope(List<String> allowedAppCodes, String resolvedAppCode,
                           boolean viaOpenApi, boolean superAdmin) {

    public static RequestScope openApi(String appCode) {
        return new RequestScope(java.util.List.of(appCode), appCode, true, false);
    }
}
