package com.cim.mq.core.exception;

/** 生产者投递失败（broker 不可达 / 未确认 / 超时）。 */
public class MqSendException extends MqException {

    public MqSendException(String message) {
        super(message);
    }

    public MqSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
