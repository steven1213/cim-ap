package com.cim.mds.server.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MDS 权限码常量（前端侧镜像见 web {@code lib/permCodes.ts}）。
 *
 * <p>三段式 {@code module:res:action}，与平台 {@code PermissionCodes} 约定一致。
 * 新增权限码时<b>三处都要加</b>：本类（入库/鉴权）、{@code MdsConsoleCatalog.permissions()}
 * （种子写库）、前端 {@code permCodes.ts}（{@code <Can>} 引用）。</p>
 *
 * <p>当前范围只覆盖场地主数据（Site / Fab / Area）——其余域（设备、产品、工艺路线、
 * 载具、配方…）随业务展开逐个补，补法同此：先加码，再在 Catalog 里加菜单与授权。</p>
 */
public final class MdsPermissions {

    private MdsPermissions() {}

    // ---------------- 厂区（Site） ----------------
    public static final String SITE_VIEW = "mds:site:view";
    public static final String SITE_CREATE = "mds:site:create";
    public static final String SITE_UPDATE = "mds:site:update";
    public static final String SITE_DELETE = "mds:site:delete";

    // ---------------- 晶圆厂（Fab） ----------------
    public static final String FAB_VIEW = "mds:fab:view";
    public static final String FAB_CREATE = "mds:fab:create";
    public static final String FAB_UPDATE = "mds:fab:update";
    public static final String FAB_DELETE = "mds:fab:delete";

    // ---------------- 区域（Area） ----------------
    public static final String AREA_VIEW = "mds:area:view";
    public static final String AREA_CREATE = "mds:area:create";
    public static final String AREA_UPDATE = "mds:area:update";
    public static final String AREA_DELETE = "mds:area:delete";

    /** 全部权限码（供种子与测试断言，顺序稳定）。 */
    public static Set<String> all() {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(SITE_VIEW);
        codes.add(SITE_CREATE);
        codes.add(SITE_UPDATE);
        codes.add(SITE_DELETE);
        codes.add(FAB_VIEW);
        codes.add(FAB_CREATE);
        codes.add(FAB_UPDATE);
        codes.add(FAB_DELETE);
        codes.add(AREA_VIEW);
        codes.add(AREA_CREATE);
        codes.add(AREA_UPDATE);
        codes.add(AREA_DELETE);
        return codes;
    }
}
