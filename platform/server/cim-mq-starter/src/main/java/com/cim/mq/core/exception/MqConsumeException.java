package com.cim.mq.core.exception;

/** 消费者处理失败（业务异常 / 反序列化失败）。 */
public class MqConsumeException extends MqException {

    public MqConsumeException(String message) {
        super(message);
    }

    public MqConsumeException(String message, Throwable cause) {
        super(message, cause);
    }
}
