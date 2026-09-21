package com.cim.spring.support.web;

import lombok.Getter;

import java.util.Map;

/**
 * 参数校验异常：携带字段级错误（field → 提示），供全局异常处理器直接映射为
 * {@link Result#errors()}（见 README §4）。
 */
@Getter
public class ValidationException extends RuntimeException {

    private final transient Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(BizCode.PARAM_INVALID.getDefaultMessage());
        this.errors = errors;
    }

    public ValidationException(String field, String message) {
        super(BizCode.PARAM_INVALID.getDefaultMessage());
        this.errors = Map.of(field, message);
    }
}
