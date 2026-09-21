package com.cim.spring.support.web;

import lombok.Getter;

/**
 * 业务异常：携带稳定 {@link BizCode} 与 i18n 占位参数，由全局异常处理器解析为最终消息
 * （见 README §4）。
 */
@Getter
public class BizException extends RuntimeException {

    private final BizCode bizCode;

    /** i18n 消息占位参数（对应 {@code {0}}、{@code {1}}…）。 */
    private final transient Object[] args;

    public BizException(BizCode bizCode, Object... args) {
        // 覆盖 message 以便无 i18n 组件时日志可读；最终消息仍由 handler 经 MessageSource 解析
        super(bizCode.getDefaultMessage());
        this.bizCode = bizCode;
        this.args = args;
    }

    public BizException(BizCode bizCode, String message, Object... args) {
        super(message);
        this.bizCode = bizCode;
        this.args = args;
    }

    /** 快捷构造：数据不存在。 */
    public static BizException notFound(Object... args) {
        return new BizException(BizCode.DATA_NOT_FOUND, args);
    }

    /** 快捷构造：参数非法。 */
    public static BizException paramInvalid(Object... args) {
        return new BizException(BizCode.PARAM_INVALID, args);
    }

    /** 快捷构造：无准入权限。 */
    public static BizException noAdmission(Object... args) {
        return new BizException(BizCode.NO_ADMISSION, args);
    }
}
