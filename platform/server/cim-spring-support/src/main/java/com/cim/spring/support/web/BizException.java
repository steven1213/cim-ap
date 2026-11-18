package com.cim.spring.support.web;

import lombok.Getter;

/**
 * 业务异常：携带稳定 {@link BizCode}、可选业务级 i18n 键与占位参数，由全局异常处理器
 * 解析为最终消息（见 README §4）。
 *
 * <p>键优先级：业务级 {@code i18nKey}（{@link #of} 创建）&gt; code 级 {@code bizCode.i18nKey}。
 * 业务级键（如 {@code mds.biz.operation.duplicate}=「工序编码 {0} 已存在」）由各 ap 种子进
 * {@code sys_i18n}，管理后台可维护；键缺失时兜底 code 级默认文案。</p>
 */
@Getter
public class BizException extends RuntimeException {

    private final BizCode bizCode;

    /** 业务级 i18n 键（{@code null} = 用 {@code bizCode.i18nKey}）。 */
    private final String i18nKey;

    /** i18n 消息占位参数（对应 {@code {0}}、{@code {1}}…）。 */
    private final transient Object[] args;

    /** code 级：键 = {@code bizCode.i18nKey}，可带占位参数。 */
    public BizException(BizCode bizCode, Object... args) {
        super(bizCode.getDefaultMessage());
        this.bizCode = bizCode;
        this.i18nKey = null;
        this.args = args;
    }

    /** 自定义明细消息（仅日志可读；响应文案仍按 code 级键解析——明细参数化请用 {@link #of}）。 */
    public BizException(BizCode bizCode, String message, Object... args) {
        super(message);
        this.bizCode = bizCode;
        this.i18nKey = null;
        this.args = args;
    }

    private BizException(BizCode bizCode, String i18nKey, boolean keyed, Object... args) {
        // 覆盖 message 以便无 i18n 组件时日志可读；最终消息由 handler 经 MessageResolver 解析
        super(bizCode.getDefaultMessage());
        this.bizCode = bizCode;
        this.i18nKey = keyed ? i18nKey : null;
        this.args = args;
    }

    /** 业务级键构造：响应文案按 {@code i18nKey} 解析（键由 ap 种子进库，管理端可维护）。 */
    public static BizException of(BizCode bizCode, String i18nKey, Object... args) {
        return new BizException(bizCode, i18nKey, true, args);
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

    /** 快捷构造：账户被锁定（暴力破解防护触发）。 */
    public static BizException accountLocked(Object... args) {
        return new BizException(BizCode.ACCOUNT_LOCKED, args);
    }
}
