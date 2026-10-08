package com.cim.system.menu;

import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseDefData;
import com.cim.system.support.EnableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * 菜单（design.md §2.10 / README §21.1「菜单即导航」）。
 *
 * <p><b>名称走 i18n</b>：本表不存中文/英文名，只存 {@code i18n_code}，文案由 §7 数据库 i18n
 * 按用户语言偏好（{@code sys_user.lang}）解析——避免「名称硬编码」与全量 DB i18n 相矛盾。</p>
 *
 * <p>{@code type=BUTTON} 的节点只承载 {@code perm_code}，不出现在导航中，用于「按钮级权限」；
 * 导航可见性由 {@code sys_role_menu} 决定，接口可调用性由 {@code sys_role_perm} 决定。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_menu")
@History(HistoryStrategy.SNAPSHOT)
public class SysMenu extends BaseDefData {

    /** 父菜单 ID（顶级为 {@code null} 或 {@code "0"}）。 */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    /** 前端路由路径。 */
    @Column(name = "path", length = 256)
    private String path;

    /** 前端组件标识。 */
    @Column(name = "component", length = 256)
    private String component;

    /** 名称 i18n 键（§7）；本表不存字面量名称。 */
    @Column(name = "i18n_code", length = 128)
    private String i18nCode;

    /** 图标标识。 */
    @Column(name = "icon", length = 64)
    private String icon;

    /** 节点类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 16, nullable = false)
    private MenuType type = MenuType.MENU;

    /** 关联权限码（按钮节点必填；菜单节点可空）。 */
    @Column(name = "perm_code", length = 128)
    private String permCode;

    /** 排序号。 */
    @Column(name = "sort_no")
    private Integer sortNo = 0;

    /** 是否在导航中可见。 */
    @Column(name = "visible", nullable = false)
    private Boolean visible = Boolean.TRUE;

    /** 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnableStatus status = EnableStatus.ENABLED;
}
