package com.cim.rms.server.template;

import com.cim.core.model.BaseDefData;
import com.cim.rms.server.recipe.BodyFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 配方 Body 模板（W10，对齐 camLine ROBDEF：强制键 + 默认值 + 骨架）。
 *
 * <p>价值：新配方结构不再「靠自觉」——建档/编辑时可从模板取骨架（新建向导），
 * 保存前按模板<b>强制键</b>校验（{@code enforced=true} 时缺键进 errors 拦截）。模板属主数据，
 * {@code scopeDeviceTypeId} 限定适用机型（空 = 全机型通用；匹配时机型专属优先于通用）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_body_template")
public class BodyTemplate extends BaseDefData {

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", length = 32, nullable = false)
    private BodyFormat bodyFormat = BodyFormat.JSON_STRUCTURED;

    /** 适用机型（空 = 通用）。 */
    @Column(name = "scope_device_type_id", length = 64)
    private String scopeDeviceTypeId;

    /** 强制校验：true 时校验服务把缺键记为 error（拦截保存/签核）；false 仅警告。 */
    @Column(name = "enforced", nullable = false)
    private boolean enforced = true;

    /** 骨架 Body 文本（明文，非 Base64——模板是主数据，不走 D9 加密）。 */
    @Column(name = "template_body", columnDefinition = "CLOB")
    private String templateBody;
}
