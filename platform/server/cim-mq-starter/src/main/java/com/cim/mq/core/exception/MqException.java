package com.cim.mq.core.exception;

/** MQ 框架统一异常根类。 */
public class MqException extends RuntimeException {

    public MqException(String message) {
        super(message);
    }

    public MqException(String message, Throwable cause) {
        super(message, cause);
    }
}
