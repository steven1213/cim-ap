package com.cim.mds.server.edc;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * EDC 参数集（V28）——按工序/工艺类型把参数模板打包归类（行业 DCP Template 口径：
 * 模板 = 一组参数 + 各自采样属性），供 EDC 方案一键带入，避免逐参数重复建模。
 *
 * <p>op_code 归属工序可空 = 通用参数集；参数成员见 {@link CimEdcParamSetItem}，
 * attr_key 逻辑引用 cim_param_def（服务层守门存在性）。编辑锁三列随基类。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_edc_param_set", uniqueConstraints = {
        @UniqueConstraint(name = "uk_edc_param_set", columnNames = {"set_code", "deleted"})
})
public class CimEdcParamSet extends EditLockDefData {

    /** 参数集编码（全局唯一、建后不可改——方案/明细引用锚点）。 */
    @Column(name = "set_code", length = 64, nullable = false)
    private String setCode;

    /** 参数集名称（例：刻蚀工序参数集）。 */
    @Column(name = "set_name", length = 128, nullable = false)
    private String setName;

    /** 归属工序（可空 = 通用参数集，不限工序）。 */
    @Column(name = "op_code", length = 64)
    private String opCode;

    /** 是否启用（停用集不进新建引用）。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
