package com.cim.mds.server.namingrule;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 编码规则（NamingRule）——「一类对象怎么起编码」的配置化模板（有序段列表模型）。
 *
 * <p>行业口径：半导体行业无 SEMI 统一编码标准，业界通行「区域码 + 机型码 + 流水号」
 * 分段模板，MES 产品普遍内置配置化 Key Generation。本表即其主数据落点：
 * {@code segments} 为 JSON 段列表（列表顺序即编码顺序），每段声明类型与参数
 * （LITERAL 常量 / ENUM 枚举 / DATE 日期 / SEQ 序号），由各段解析器渲染
 * （见 {@link SegmentResolver} 注册表），每段声明字符长度、整码定长。</p>
 *
 * <p><b>「同一 object_type 只允许一条启用规则」的落库保证</b>：active_marker 列——
 * 启用时恒为 {@code 'ENABLED'}（(object_type, active_marker) 唯一索引挡并发双开），
 * 停用时为随机 UUID（互不冲突，可存多条停用规则备切换）。写入走 {@link #syncActiveMarker()}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_naming_rule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_naming_rule_active",
                columnNames = {"object_type", "active_marker", "deleted"})
})
public class CimNamingRule extends BaseDefData {

    /** 归属对象域（十四对象白名单，见 {@link NamingRuleAdminService#objectSpecs()}）。 */
    @Column(name = "object_type", length = 32, nullable = false)
    private String objectType;

    /** 规则名称。 */
    @Column(name = "rule_name", length = 128, nullable = false)
    private String ruleName;

    /**
     * 段列表 JSON（列表顺序即编码顺序）。元素结构见 {@link SegmentSpec}：
     * type/text/var/dictKey/values/length/pattern/start/scope。
     */
    @Column(name = "segments", length = 2000, nullable = false)
    private String segments;

    /** 是否启用（启用时参与 create 守门；停用=放行回退人工命名）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /**
     * 启用占位标记：enabled=TRUE 恒为 'ENABLED'（唯一索引保证单条启用），
     * 停用=随机 UUID（persist 前应用层生成，互不冲突）。写入走 {@link #syncActiveMarker()}。
     */
    @Column(name = "active_marker", nullable = false)
    private String activeMarker;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 按 enabled 同步占位标记（保存前调用；停用行用随机 UUID 避免互撞）。 */
    public void syncActiveMarker() {
        this.activeMarker = Boolean.TRUE.equals(this.enabled)
                ? "ENABLED" : java.util.UUID.randomUUID().toString();
    }
}
