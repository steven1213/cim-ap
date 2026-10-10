package com.cim.rms.server.recipe;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 配方主档（Req 1/6；Req.md §3.1 领域模型）。
 *
 * <p>一个 Recipe 承载多版本（{@link RecipeVersion}），版本状态机见 Req.md §3.2；
 * {@code code} 为业务键（通配符查询入口之一），Golden 标记（Req 16）挂主档。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "recipe", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recipe_code", columnNames = {"code"})
})
public class Recipe extends BaseDefData {

    /** 配方编码（业务键）。 */
    @Column(name = "code", length = 128, nullable = false)
    private String code;

    /** 配方名称。 */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 适用机型（{@code DeviceType} id）。 */
    @Column(name = "device_type_id", length = 64, nullable = false)
    private String deviceTypeId;

    /** 归属区域（{@code DeviceArea} id），可空。 */
    @Column(name = "area_id", length = 64)
    private String areaId;

    /** 黄金配方标记（Req 16；同型号共享，W2 扩展 Golden 管控）。 */
    @Column(name = "golden", nullable = false)
    private boolean golden = false;

    /** 当前生效版本 ID（至多一个 ACTIVE 的快速索引；约束由服务层保证）。 */
    @Column(name = "active_version_id", length = 64)
    private String activeVersionId;
}
