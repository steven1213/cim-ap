package com.cim.spring.support.web;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码枚举：稳定契约 = 业务码 + HTTP 映射 + i18n 键。
 *
 * <p>按模块分段（见 README §4）：</p>
 * <ul>
 *   <li>{@code 0}        成功</li>
 *   <li>{@code 1000–1999} 用户 / 权限 / 认证</li>
 *   <li>{@code 2000–2999} 业务通用</li>
 *   <li>{@code 3000–3999} 集成 / 外部系统</li>
 *   <li>{@code 9000–9999} 系统 / 未知</li>
 * </ul>
 *
 * <p>{@link #i18nKey} 经 {@code cim-i18n-starter} 的数据库 {@code MessageSource} 解析；
 * 该 starter 缺席时降级为 {@link #defaultMessage}。</p>
 */
@Getter
public enum BizCode {

    // ===== 0 成功 =====
    SUCCESS(0, HttpStatus.OK, "biz.success", "success"),

    // ===== 1000–1999 认证 / 权限 =====
    UNAUTHENTICATED(1000, HttpStatus.UNAUTHORIZED, "biz.auth.unauthenticated", "未认证或令牌已失效"),
    BAD_CREDENTIALS(1001, HttpStatus.UNAUTHORIZED, "biz.auth.badCredentials", "用户名或密码错误"),
    TOKEN_EXPIRED(1002, HttpStatus.UNAUTHORIZED, "biz.auth.tokenExpired", "令牌已过期"),
    TOKEN_REVOKED(1003, HttpStatus.UNAUTHORIZED, "biz.auth.tokenRevoked", "令牌已失效，请重新登录"),
    NO_ADMISSION(1403, HttpStatus.FORBIDDEN, "biz.auth.noAdmission", "无该应用的准入权限"),
    ACCESS_DENIED(1404, HttpStatus.FORBIDDEN, "biz.auth.accessDenied", "无操作权限"),

    // ===== 2000–2999 业务通用 =====
    PARAM_INVALID(2000, HttpStatus.BAD_REQUEST, "biz.param.invalid", "参数校验失败"),
    DATA_NOT_FOUND(2001, HttpStatus.NOT_FOUND, "biz.data.notFound", "数据不存在"),
    DATA_CONFLICT(2002, HttpStatus.CONFLICT, "biz.data.conflict", "数据冲突"),
    DATA_DUPLICATE(2003, HttpStatus.CONFLICT, "biz.data.duplicate", "数据已存在"),
    OPERATION_NOT_ALLOWED(2004, HttpStatus.BAD_REQUEST, "biz.operation.notAllowed", "当前状态不允许该操作"),
    CONCURRENT_MODIFIED(2005, HttpStatus.CONFLICT, "biz.data.concurrentModified", "数据已被他人修改，请刷新后重试"),
    IDEMPOTENT_REPLAY(2006, HttpStatus.CONFLICT, "biz.idempotent.replay", "重复请求"),
    RATE_LIMITED(2007, HttpStatus.TOO_MANY_REQUESTS, "biz.rateLimited", "请求过于频繁，请稍后重试"),

    // ===== 3000–3999 集成 =====
    INTEGRATION_FAILED(3000, HttpStatus.BAD_GATEWAY, "biz.integration.failed", "外部系统调用失败"),
    INTEGRATION_TIMEOUT(3001, HttpStatus.GATEWAY_TIMEOUT, "biz.integration.timeout", "外部系统调用超时"),

    // ===== 9000–9999 系统 =====
    SYSTEM_ERROR(9000, HttpStatus.INTERNAL_SERVER_ERROR, "biz.system.error", "系统繁忙，请稍后重试");

    private final int code;
    private final HttpStatus httpStatus;
    private final String i18nKey;
    private final String defaultMessage;

    BizCode(int code, HttpStatus httpStatus, String i18nKey, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.i18nKey = i18nKey;
        this.defaultMessage = defaultMessage;
    }
}
