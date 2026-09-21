package com.cim.spring.support.web;

/**
 * 系统异常：未预期的服务端错误。对外仅返回 {@code traceId} + 通用语，**不泄露**内部细节
 * （见 README §4）。
 */
public class SystemException extends RuntimeException {

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

    public SystemException(Throwable cause) {
        super(cause);
    }
}
