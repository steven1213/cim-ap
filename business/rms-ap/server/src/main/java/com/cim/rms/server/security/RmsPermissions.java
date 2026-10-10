package com.cim.rms.server.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * RMS 权限码常量（前端侧镜像见 web {@code lib/permCodes.ts}）。
 *
 * <p>三段式 {@code module:res:action}，与平台 {@code PermissionCodes} 约定一致。
 * 新增权限码时**两侧都要加**：后端本类负责入库/鉴权，前端负责 {@code <Perms>} 引用。</p>
 *
 * <p>当前范围聚焦 W1/W2 落地的配方与设备域；签核、比对、资格矩阵等权限码随对应波浪补齐。</p>
 */
public final class RmsPermissions {

    private RmsPermissions() {}

    // ---------------- 配方 ----------------
    public static final String RECIPE_VIEW = "rms:recipe:view";
    public static final String RECIPE_CREATE = "rms:recipe:create";
    public static final String RECIPE_UPDATE = "rms:recipe:update";
    public static final String RECIPE_DELETE = "rms:recipe:delete";
    public static final String RECIPE_ACTIVATE = "rms:recipe:activate";
    public static final String RECIPE_VERSION = "rms:recipe:version";

    // ---------------- 设备类型 ----------------
    public static final String DEVICE_TYPE_VIEW = "rms:device-type:view";
    public static final String DEVICE_TYPE_CREATE = "rms:device-type:create";
    public static final String DEVICE_TYPE_UPDATE = "rms:device-type:update";
    public static final String DEVICE_TYPE_DELETE = "rms:device-type:delete";

    // ---------------- 设备区域 ----------------
    public static final String DEVICE_AREA_VIEW = "rms:device-area:view";
    public static final String DEVICE_AREA_CREATE = "rms:device-area:create";
    public static final String DEVICE_AREA_UPDATE = "rms:device-area:update";
    public static final String DEVICE_AREA_DELETE = "rms:device-area:delete";

    // ---------------- 设备台账 ----------------
    public static final String DEVICE_VIEW = "rms:device:view";
    public static final String DEVICE_CREATE = "rms:device:create";
    public static final String DEVICE_UPDATE = "rms:device:update";
    public static final String DEVICE_DELETE = "rms:device:delete";

    /** 全量权限（超管 / RMS 管理员短路展开）。 */
    public static Set<String> full() {
        Set<String> s = new LinkedHashSet<>();
        s.add(RECIPE_VIEW); s.add(RECIPE_CREATE); s.add(RECIPE_UPDATE);
        s.add(RECIPE_DELETE); s.add(RECIPE_ACTIVATE); s.add(RECIPE_VERSION);
        s.add(DEVICE_TYPE_VIEW); s.add(DEVICE_TYPE_CREATE); s.add(DEVICE_TYPE_UPDATE); s.add(DEVICE_TYPE_DELETE);
        s.add(DEVICE_AREA_VIEW); s.add(DEVICE_AREA_CREATE); s.add(DEVICE_AREA_UPDATE); s.add(DEVICE_AREA_DELETE);
        s.add(DEVICE_VIEW); s.add(DEVICE_CREATE); s.add(DEVICE_UPDATE); s.add(DEVICE_DELETE);
        return s;
    }

    /** 只读权限（受限角色）。 */
    public static Set<String> readonly() {
        Set<String> s = new LinkedHashSet<>();
        s.add(RECIPE_VIEW); s.add(RECIPE_VERSION);
        s.add(DEVICE_TYPE_VIEW); s.add(DEVICE_AREA_VIEW); s.add(DEVICE_VIEW);
        return s;
    }
}
