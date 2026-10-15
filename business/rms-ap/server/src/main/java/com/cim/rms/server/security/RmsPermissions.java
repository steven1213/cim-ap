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

    // ---------------- 签核流 ----------------
    public static final String SIGNOFF_VIEW = "rms:signoff:view";
    public static final String SIGNOFF_APPROVE = "rms:signoff:approve";

    // ---------------- 比对 ----------------
    public static final String COMPARE_VIEW = "rms:compare:view";

    // ---------------- 审计 ----------------
    public static final String AUDIT_VIEW = "rms:audit:view";

    // ---------------- 参数规格 / 模板（FR-S1~S4） ----------------
    public static final String SPEC_VIEW = "rms:spec:view";
    public static final String SPEC_MANAGE = "rms:spec:manage";

    // ---------------- 设备授权（FR-P4） ----------------
    public static final String DEVICE_AUTH_VIEW = "rms:device-auth:view";
    public static final String DEVICE_AUTH_MANAGE = "rms:device-auth:manage";

    // ---------------- Bypass（FR-B1~B3） ----------------
    public static final String BYPASS_VIEW = "rms:bypass:view";
    public static final String BYPASS_MANAGE = "rms:bypass:manage";

    // ---------------- 资格矩阵（FR-Q1~Q4） ----------------
    public static final String QUALIFICATION_VIEW = "rms:qualification:view";
    public static final String QUALIFICATION_MANAGE = "rms:qualification:manage";

    // ---------------- Golden 机差（FR-G1~G3） ----------------
    public static final String GOLDEN_VIEW = "rms:golden:view";
    public static final String GOLDEN_MANAGE = "rms:golden:manage";

    // ---------------- EC/SV（FR-D2~D4） ----------------
    public static final String ECSV_VIEW = "rms:ecsv:view";
    public static final String ECSV_MANAGE = "rms:ecsv:manage";

    // ---------------- Body IP 保护（FR-SEC2） ----------------
    public static final String BODY_VIEW = "rms:body:view";
    public static final String BODY_EXPORT = "rms:body:export";

    // ---------------- Body 模板（W10，对齐 camLine ROBDEF） ----------------
    public static final String BODY_TEMPLATE_VIEW = "rms:btemplate:view";
    public static final String BODY_TEMPLATE_MANAGE = "rms:btemplate:manage";

    // ---------------- 实验配方通道（W11） ----------------
    public static final String EXPERIMENT_VIEW = "rms:experiment:view";
    public static final String EXPERIMENT_MANAGE = "rms:experiment:manage";

    // ---------------- 报表 / 集成 ----------------
    public static final String REPORT_VIEW = "rms:report:view";
    public static final String INTEGRATION_VIEW = "rms:integration:view";
    public static final String INTEGRATION_OPERATE = "rms:integration:operate";

    /** 全量权限（超管 / RMS 管理员短路展开）。 */
    public static Set<String> full() {
        Set<String> s = new LinkedHashSet<>();
        s.add(RECIPE_VIEW); s.add(RECIPE_CREATE); s.add(RECIPE_UPDATE);
        s.add(RECIPE_DELETE); s.add(RECIPE_ACTIVATE); s.add(RECIPE_VERSION);
        s.add(DEVICE_TYPE_VIEW); s.add(DEVICE_TYPE_CREATE); s.add(DEVICE_TYPE_UPDATE); s.add(DEVICE_TYPE_DELETE);
        s.add(DEVICE_AREA_VIEW); s.add(DEVICE_AREA_CREATE); s.add(DEVICE_AREA_UPDATE); s.add(DEVICE_AREA_DELETE);
        s.add(DEVICE_VIEW); s.add(DEVICE_CREATE); s.add(DEVICE_UPDATE); s.add(DEVICE_DELETE);
        s.add(SIGNOFF_VIEW); s.add(SIGNOFF_APPROVE);
        s.add(COMPARE_VIEW);
        s.add(AUDIT_VIEW);
        s.add(SPEC_VIEW); s.add(SPEC_MANAGE);
        s.add(DEVICE_AUTH_VIEW); s.add(DEVICE_AUTH_MANAGE);
        s.add(BYPASS_VIEW); s.add(BYPASS_MANAGE);
        s.add(QUALIFICATION_VIEW); s.add(QUALIFICATION_MANAGE);
        s.add(GOLDEN_VIEW); s.add(GOLDEN_MANAGE);
        s.add(ECSV_VIEW); s.add(ECSV_MANAGE);
        s.add(BODY_VIEW); s.add(BODY_EXPORT);
        s.add(BODY_TEMPLATE_VIEW); s.add(BODY_TEMPLATE_MANAGE);
        s.add(EXPERIMENT_VIEW); s.add(EXPERIMENT_MANAGE);
        s.add(REPORT_VIEW); s.add(INTEGRATION_VIEW); s.add(INTEGRATION_OPERATE);
        return s;
    }

    /** 只读权限（受限角色）。 */
    public static Set<String> readonly() {
        Set<String> s = new LinkedHashSet<>();
        s.add(RECIPE_VIEW); s.add(RECIPE_VERSION);
        s.add(DEVICE_TYPE_VIEW); s.add(DEVICE_AREA_VIEW); s.add(DEVICE_VIEW);
        s.add(SIGNOFF_VIEW); s.add(COMPARE_VIEW); s.add(AUDIT_VIEW);
        s.add(SPEC_VIEW);
        s.add(DEVICE_AUTH_VIEW);
        s.add(BYPASS_VIEW);
        s.add(QUALIFICATION_VIEW);
        s.add(GOLDEN_VIEW);
        s.add(ECSV_VIEW);
        s.add(BODY_TEMPLATE_VIEW);
        s.add(EXPERIMENT_VIEW);
        s.add(REPORT_VIEW); s.add(INTEGRATION_VIEW);
        return s;
    }
}
