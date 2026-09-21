package com.cim.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 事件 / 流水表基类（追加不可变：操作日志、告警、采样）。
 *
 * <p>本身即流水，无独立历史表；{@code payload} 以 JSON 文本承载扩展字段
 * （经 {@code JpaJsonConverter} 或直接 String，见 README §3）。</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEventData extends Auditable {

    /** 主键（应用层时间有序 ID，见 README §3）。 */
    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    /** 业务键（如 equipmentCode）。 */
    @Column(name = "biz_key", length = 128)
    private String bizKey;

    /** 事件类型。 */
    @Column(name = "event_type", length = 64)
    private String eventType;

    /** JSON 扩展载荷。 */
    @Lob
    @Column(name = "payload")
    private String payload;
}
