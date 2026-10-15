package com.cim.rms.server.spec;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 规格模板（FR-S2，Req 35）：一组可整体应用到配方的参数规格集合，避免重复录入。
 *
 * <p>{@code code} 为业务键；{@code templateVer} 支持模板自身版本化。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "spec_template")
public class SpecTemplate extends BaseDefData {

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 模板版本（模板内容变更时递增）。 */
    @Column(name = "template_ver", nullable = false)
    private int templateVer = 1;

    /** 适用范围提示（如机型/工艺段，仅供筛选，不强制）。 */
    @Column(name = "scope_type", length = 32)
    private String scopeType;
}
