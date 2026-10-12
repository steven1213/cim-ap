package com.cim.rms.server.console;

import com.cim.rms.server.security.RmsPermissions;

import java.util.ArrayList;
import java.util.List;

/**
 * RMS 控制台权限目录（权限码 / 角色 / 角色授权）。
 *
 * <p>权限码与 {@link RmsPermissions} 一一对应（同一份常量，避免「按钮可见但接口 403」）；
 * 角色→权限的授予关系在此声明，由 {@link RmsConsoleSeedService} 幂等落库到 {@code cim-system}
 * 的 {@code sys_role_perm}。业务权限源从此由「库内授权表」决定（design.md §2.10 / §8 边界表），
 * 而非早期 {@code RmsAuthorityLoader} 的「rms-ap:ADMIN 即全量」短路。</p>
 *
 * <p>当前范围聚焦 W1/W2 落地的配方与设备域；签核、比对、资格矩阵等权限码随对应波浪补齐。</p>
 */
public final class RmsConsoleCatalog {

    private RmsConsoleCatalog() {}

    // ================================================================ 权限

    /** 权限码目录（code 与 {@link RmsPermissions} 对齐）。 */
    public record PermDef(String code, String name) {
    }

    /** RMS 权限码目录。 */
    public static List<PermDef> permissions() {
        List<PermDef> p = new ArrayList<>();
        // 配方
        p.add(new PermDef(RmsPermissions.RECIPE_VIEW, "配方查看"));
        p.add(new PermDef(RmsPermissions.RECIPE_CREATE, "配方新建"));
        p.add(new PermDef(RmsPermissions.RECIPE_UPDATE, "配方编辑"));
        p.add(new PermDef(RmsPermissions.RECIPE_DELETE, "配方删除"));
        p.add(new PermDef(RmsPermissions.RECIPE_ACTIVATE, "配方激活"));
        p.add(new PermDef(RmsPermissions.RECIPE_VERSION, "配方版本"));
        // 设备类型
        p.add(new PermDef(RmsPermissions.DEVICE_TYPE_VIEW, "设备类型查看"));
        p.add(new PermDef(RmsPermissions.DEVICE_TYPE_CREATE, "设备类型新建"));
        p.add(new PermDef(RmsPermissions.DEVICE_TYPE_UPDATE, "设备类型编辑"));
        p.add(new PermDef(RmsPermissions.DEVICE_TYPE_DELETE, "设备类型删除"));
        // 设备区域
        p.add(new PermDef(RmsPermissions.DEVICE_AREA_VIEW, "设备区域查看"));
        p.add(new PermDef(RmsPermissions.DEVICE_AREA_CREATE, "设备区域新建"));
        p.add(new PermDef(RmsPermissions.DEVICE_AREA_UPDATE, "设备区域编辑"));
        p.add(new PermDef(RmsPermissions.DEVICE_AREA_DELETE, "设备区域删除"));
        // 设备台账
        p.add(new PermDef(RmsPermissions.DEVICE_VIEW, "设备查看"));
        p.add(new PermDef(RmsPermissions.DEVICE_CREATE, "设备新建"));
        p.add(new PermDef(RmsPermissions.DEVICE_UPDATE, "设备编辑"));
        p.add(new PermDef(RmsPermissions.DEVICE_DELETE, "设备删除"));
        return p;
    }

    // ================================================================ 角色

    /** RMS 管理员角色码（超管：短路展开为库内全部启用权限码）。 */
    public static final String ROLE_ADMIN = "RMS_ADMIN";

    /** RMS 操作员角色码（可写但不可删）。 */
    public static final String ROLE_OPERATOR = "RMS_OPERATOR";

    /** RMS 只读角色码。 */
    public static final String ROLE_VIEWER = "RMS_VIEWER";

    /**
     * 角色定义。
     *
     * @param code      角色码
     * @param name      名称
     * @param superRole 是否超管（短路展开为全部权限码）
     * @param perms     显式授予的权限码（超管此处仍给全量，便于授权界面回显）
     */
    public record RoleDef(String code, String name, boolean superRole, List<String> perms) {
    }

    /** RMS 内置角色。 */
    public static List<RoleDef> roles() {
        List<String> allPerms = permissions().stream().map(PermDef::code).toList();
        // 操作员：查看/新建/编辑/激活/版本，但不含任何 delete
        List<String> operator = List.of(
                RmsPermissions.RECIPE_VIEW, RmsPermissions.RECIPE_CREATE, RmsPermissions.RECIPE_UPDATE,
                RmsPermissions.RECIPE_ACTIVATE, RmsPermissions.RECIPE_VERSION,
                RmsPermissions.DEVICE_TYPE_VIEW, RmsPermissions.DEVICE_TYPE_CREATE, RmsPermissions.DEVICE_TYPE_UPDATE,
                RmsPermissions.DEVICE_AREA_VIEW, RmsPermissions.DEVICE_AREA_CREATE, RmsPermissions.DEVICE_AREA_UPDATE,
                RmsPermissions.DEVICE_VIEW, RmsPermissions.DEVICE_CREATE, RmsPermissions.DEVICE_UPDATE);
        // 只读：各域 view + 配方版本
        List<String> viewer = List.of(
                RmsPermissions.RECIPE_VIEW, RmsPermissions.RECIPE_VERSION,
                RmsPermissions.DEVICE_TYPE_VIEW, RmsPermissions.DEVICE_AREA_VIEW, RmsPermissions.DEVICE_VIEW);
        List<RoleDef> r = new ArrayList<>();
        r.add(new RoleDef(ROLE_ADMIN, "RMS 管理员", true, allPerms));
        r.add(new RoleDef(ROLE_OPERATOR, "RMS 操作员", false, operator));
        r.add(new RoleDef(ROLE_VIEWER, "RMS 只读", false, viewer));
        return r;
    }
}
