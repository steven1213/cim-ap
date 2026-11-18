package com.cim.mds.server.security;

import com.cim.system.support.PermissionCodes;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MDS 权限码常量（前端侧镜像见 web {@code lib/permCodes.ts}）。
 *
 * <p>三段式 {@code module:res:action}，与平台 {@code PermissionCodes} 约定一致。
 * 新增权限码时<b>三处都要加</b>：本类（入库/鉴权）、{@code MdsConsoleCatalog.permissions()}
 * （种子写库）、前端 {@code permCodes.ts}（{@code <Can>} 引用）。</p>
 *
 * <p>当前范围覆盖场地主数据五层（Site / Fab / Area / Bay / Stage）——其余域（设备、产品、工艺路线、
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

    // ---------------- 湾（Bay） ----------------
    public static final String BAY_VIEW = "mds:bay:view";
    public static final String BAY_CREATE = "mds:bay:create";
    public static final String BAY_UPDATE = "mds:bay:update";
    public static final String BAY_DELETE = "mds:bay:delete";

    // ---------------- 设备位（Stage） ----------------
    public static final String STAGE_VIEW = "mds:stage:view";
    public static final String STAGE_CREATE = "mds:stage:create";
    public static final String STAGE_UPDATE = "mds:stage:update";
    public static final String STAGE_DELETE = "mds:stage:delete";

    // ---------------- 制程技术（Technology，产品管理域） ----------------
    public static final String TECH_VIEW = "mds:tech:view";
    public static final String TECH_CREATE = "mds:tech:create";
    public static final String TECH_UPDATE = "mds:tech:update";
    public static final String TECH_DELETE = "mds:tech:delete";

    // ---------------- 产品（Product，产品管理域） ----------------
    public static final String PRODUCT_VIEW = "mds:product:view";
    public static final String PRODUCT_CREATE = "mds:product:create";
    public static final String PRODUCT_UPDATE = "mds:product:update";
    public static final String PRODUCT_DELETE = "mds:product:delete";

    // ---------------- 设备类型（EquipmentType，设备管理域） ----------------
    public static final String EQP_TYPE_VIEW = "mds:equipmentType:view";
    public static final String EQP_TYPE_CREATE = "mds:equipmentType:create";
    public static final String EQP_TYPE_UPDATE = "mds:equipmentType:update";
    public static final String EQP_TYPE_DELETE = "mds:equipmentType:delete";

    // ---------------- 设备（Equipment，设备管理域） ----------------
    public static final String EQUIPMENT_VIEW = "mds:equipment:view";
    public static final String EQUIPMENT_CREATE = "mds:equipment:create";
    public static final String EQUIPMENT_UPDATE = "mds:equipment:update";
    public static final String EQUIPMENT_DELETE = "mds:equipment:delete";

    // ---------------- 设备能力（Capability，设备管理域） ----------------
    public static final String CAPABILITY_VIEW = "mds:capability:view";
    public static final String CAPABILITY_CREATE = "mds:capability:create";
    public static final String CAPABILITY_UPDATE = "mds:capability:update";
    public static final String CAPABILITY_DELETE = "mds:capability:delete";

    // ---------------- 存储区 Bank（StockerBank，设备管理域，SEMI E88 zone 口径） ----------------
    public static final String BANK_VIEW = "mds:bank:view";
    public static final String BANK_CREATE = "mds:bank:create";
    public static final String BANK_UPDATE = "mds:bank:update";
    public static final String BANK_DELETE = "mds:bank:delete";

    // ---------------- 状态模型（StateModel，系统管理域，SEMI E30 状态机口径） ----------------
    public static final String STATE_MODEL_VIEW = "mds:stateModel:view";
    public static final String STATE_MODEL_CREATE = "mds:stateModel:create";
    public static final String STATE_MODEL_UPDATE = "mds:stateModel:update";
    public static final String STATE_MODEL_DELETE = "mds:stateModel:delete";

    // ---------------- 编码规则（NamingRule，系统管理域，配置化 Key Generation 口径） ----------------
    public static final String NAMING_RULE_VIEW = "mds:namingRule:view";
    public static final String NAMING_RULE_CREATE = "mds:namingRule:create";
    public static final String NAMING_RULE_UPDATE = "mds:namingRule:update";
    public static final String NAMING_RULE_DELETE = "mds:namingRule:delete";

    // ---------------- 制造流程（Flow，产品管理域，Route 口径：XML 画布 + 有向图） ----------------
    public static final String FLOW_VIEW = "mds:flow:view";
    public static final String FLOW_CREATE = "mds:flow:create";
    public static final String FLOW_UPDATE = "mds:flow:update";
    public static final String FLOW_DELETE = "mds:flow:delete";

    // ---------------- 原因码（ReasonCode，系统管理域：WIP 事件归因主数据 + 快照治理） ----------------
    public static final String REASON_VIEW = "mds:reason:view";
    public static final String REASON_CREATE = "mds:reason:create";
    public static final String REASON_UPDATE = "mds:reason:update";
    public static final String REASON_DELETE = "mds:reason:delete";

    // ---------------- 平台管理域（菜单维护页用；端点来自 cim-system，码值与 PermissionCodes 对齐） ----------------
    public static final String SYS_MENU_LIST = PermissionCodes.MENU_LIST;
    public static final String SYS_MENU_SAVE = PermissionCodes.MENU_SAVE;
    public static final String SYS_MENU_REMOVE = PermissionCodes.MENU_REMOVE;
    public static final String SYS_ROLE_LIST = PermissionCodes.ROLE_LIST;
    public static final String SYS_ROLE_SAVE = PermissionCodes.ROLE_SAVE;
    public static final String SYS_ROLE_REMOVE = PermissionCodes.ROLE_REMOVE;
    public static final String SYS_ROLE_GRANT = PermissionCodes.ROLE_GRANT;
    public static final String SYS_PERM_LIST = PermissionCodes.PERMISSION_LIST;
    public static final String SYS_PERM_SAVE = PermissionCodes.PERMISSION_SAVE;
    public static final String SYS_PERM_REMOVE = PermissionCodes.PERMISSION_REMOVE;
    public static final String SYS_I18N_LIST = PermissionCodes.I18N_LIST;
    public static final String SYS_I18N_SAVE = PermissionCodes.I18N_SAVE;
    public static final String SYS_I18N_REMOVE = PermissionCodes.I18N_REMOVE;
    public static final String SYS_I18N_LOCALE = PermissionCodes.I18N_LOCALE;

    // ---------------- 目录只读（组织/人员；主数据在 IAM，本系统只读——经代理取目录 API） ----------------
    public static final String ORG_VIEW = "mds:org:view";
    public static final String USER_VIEW = "mds:user:view";

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
        codes.add(BAY_VIEW);
        codes.add(BAY_CREATE);
        codes.add(BAY_UPDATE);
        codes.add(BAY_DELETE);
        codes.add(STAGE_VIEW);
        codes.add(STAGE_CREATE);
        codes.add(STAGE_UPDATE);
        codes.add(STAGE_DELETE);
        codes.add(TECH_VIEW);
        codes.add(TECH_CREATE);
        codes.add(TECH_UPDATE);
        codes.add(TECH_DELETE);
        codes.add(PRODUCT_VIEW);
        codes.add(PRODUCT_CREATE);
        codes.add(PRODUCT_UPDATE);
        codes.add(PRODUCT_DELETE);
        codes.add(EQP_TYPE_VIEW);
        codes.add(EQP_TYPE_CREATE);
        codes.add(EQP_TYPE_UPDATE);
        codes.add(EQP_TYPE_DELETE);
        codes.add(EQUIPMENT_VIEW);
        codes.add(EQUIPMENT_CREATE);
        codes.add(EQUIPMENT_UPDATE);
        codes.add(EQUIPMENT_DELETE);
        codes.add(CAPABILITY_VIEW);
        codes.add(CAPABILITY_CREATE);
        codes.add(CAPABILITY_UPDATE);
        codes.add(CAPABILITY_DELETE);
        codes.add(BANK_VIEW);
        codes.add(BANK_CREATE);
        codes.add(BANK_UPDATE);
        codes.add(BANK_DELETE);
        codes.add(STATE_MODEL_VIEW);
        codes.add(STATE_MODEL_CREATE);
        codes.add(STATE_MODEL_UPDATE);
        codes.add(STATE_MODEL_DELETE);
        codes.add(NAMING_RULE_VIEW);
        codes.add(NAMING_RULE_CREATE);
        codes.add(NAMING_RULE_UPDATE);
        codes.add(NAMING_RULE_DELETE);
        codes.add(FLOW_VIEW);
        codes.add(FLOW_CREATE);
        codes.add(FLOW_UPDATE);
        codes.add(FLOW_DELETE);
        codes.add(REASON_VIEW);
        codes.add(REASON_CREATE);
        codes.add(REASON_UPDATE);
        codes.add(REASON_DELETE);
        codes.add(SYS_MENU_LIST);
        codes.add(SYS_MENU_SAVE);
        codes.add(SYS_MENU_REMOVE);
        codes.add(SYS_ROLE_LIST);
        codes.add(SYS_ROLE_SAVE);
        codes.add(SYS_ROLE_REMOVE);
        codes.add(SYS_ROLE_GRANT);
        codes.add(SYS_PERM_LIST);
        codes.add(SYS_PERM_SAVE);
        codes.add(SYS_PERM_REMOVE);
        codes.add(SYS_I18N_LIST);
        codes.add(SYS_I18N_SAVE);
        codes.add(SYS_I18N_REMOVE);
        codes.add(SYS_I18N_LOCALE);
        codes.add(ORG_VIEW);
        codes.add(USER_VIEW);
        return codes;
    }
}
