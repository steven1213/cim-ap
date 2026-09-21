package com.cim.auth.token;

/**
 * 令牌无效（格式错误 / 算法不支持 / 验签失败 / 过期）。由过滤器统一映射为 401。
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
