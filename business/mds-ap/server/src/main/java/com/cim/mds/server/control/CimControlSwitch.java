package com.cim.mds.server.control;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 卡控开关（V18）——通用「卡控点启停注册表」：一行一个卡控点，消费方（MES 等）
 * 拉取后决定自己的卡控代码是否生效。
 *
 * <p>机制分工：<b>卡控逻辑常驻消费方代码里，启停裁判权在本表</b>——enabled=true
 * 消费方执行卡控判定，false 逻辑存在但直接放行；消费方 TTL 缓存快照，本服务不可达时
 * 沿用最近缓存值。不建独立管理页：开关 UI 就地嵌在各域业务页（用户动线不割裂），
 * 本表只是后端事实源。</p>
 *
 * <p>治理纪律：{@code control_code} 全局唯一、建后不可改（消费方按 code 引用，
 * 改名等于断约）；{@code domain_code} 分组（mshift/mrms/mes…），查询按域批量拉取；
 * 默认 {@code enabled=false}——卡控默认不生效，由运营显式打开。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_control_switch", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_control_switch", columnNames = {"control_code", "deleted"})
})
public class CimControlSwitch extends BaseDefData {

    /** 卡控点编码（如 shift.on_duty_check；全局唯一、建后不可改）。 */
    @Column(name = "control_code", length = 64, nullable = false)
    private String controlCode;

    /** 域分组（mshift/mrms/mes…；查询按域批量拉取）。 */
    @Column(name = "domain_code", length = 32, nullable = false)
    private String domainCode;

    /** 开关名（就地嵌入业务页的展示文案）。 */
    @Column(name = "name", length = 64, nullable = false)
    private String name;

    /** 是否生效（true = 消费方执行卡控判定；false = 逻辑常驻但不卡控）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.FALSE;
}
