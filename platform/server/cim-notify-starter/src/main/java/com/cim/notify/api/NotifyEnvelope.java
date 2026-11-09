package com.cim.notify.api;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 推送给前端的统一信封（SSE {@code data} 体）。
 *
 * <p>前端恒定按 {@code {id, ts, appCode, type, payload}} 解析，与具体业务解耦；
 * {@code payload} 内为业务自定义 JSON。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class NotifyEnvelope {

    private String id;
    private long ts;
    private String appCode;
    private String type;
    private String payload;

    public NotifyEnvelope(NotifyEvent event) {
        this.id = event.getId();
        this.ts = event.getTs();
        this.appCode = event.getAppCode();
        this.type = event.getType();
        this.payload = event.getPayload();
    }
}
