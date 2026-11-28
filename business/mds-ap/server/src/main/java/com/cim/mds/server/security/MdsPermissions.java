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

    // ---------------- 机型模板（EquipmentClass，设备管理域；v2 取代 EquipmentType） ----------------
    public static final String EQC_VIEW = "mds:equipmentClass:view";
    public static final String EQC_CREATE = "mds:equipmentClass:create";
    public static final String EQC_UPDATE = "mds:equipmentClass:update";
    public static final String EQC_DELETE = "mds:equipmentClass:delete";

    // ---------------- 机台组（ToolGroup，设备管理域；ISA-95 Work Center 派工候选池） ----------------
    public static final String TOOL_GROUP_VIEW = "mds:toolGroup:view";
    public static final String TOOL_GROUP_CREATE = "mds:toolGroup:create";
    public static final String TOOL_GROUP_UPDATE = "mds:toolGroup:update";
    public static final String TOOL_GROUP_DELETE = "mds:toolGroup:delete";

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

    // ---------------- 载具（Carrier，设备管理域，SEMI E87 Carrier 口径，V31） ----------------
    public static final String CARRIER_VIEW = "mds:carrier:view";
    public static final String CARRIER_CREATE = "mds:carrier:create";
    public static final String CARRIER_UPDATE = "mds:carrier:update";
    public static final String CARRIER_DELETE = "mds:carrier:delete";

    // ---------------- 光罩（Reticle，产品管理域，光刻关键耗材级主数据，V32） ----------------
    public static final String RETICLE_VIEW = "mds:reticle:view";
    public static final String RETICLE_CREATE = "mds:reticle:create";
    public static final String RETICLE_UPDATE = "mds:reticle:update";
    public static final String RETICLE_DELETE = "mds:reticle:delete";

    // ---------------- 检验标准（InspectionStd，质量域，AQL/检验项模板，V33） ----------------
    public static final String INSP_VIEW = "mds:inspection:view";
    public static final String INSP_CREATE = "mds:inspection:create";
    public static final String INSP_UPDATE = "mds:inspection:update";
    public static final String INSP_DELETE = "mds:inspection:delete";

    // ---------------- 耗材台账（Consumable，质量域，V34） ----------------
    public static final String CONS_VIEW = "mds:consumable:view";
    public static final String CONS_CREATE = "mds:consumable:create";
    public static final String CONS_UPDATE = "mds:consumable:update";
    public static final String CONS_DELETE = "mds:consumable:delete";

    // ---------------- 消耗品 BOM（ConsBom，质量域，「工序×规格」消耗契约，V38） ----------------
    public static final String CONS_BOM_VIEW = "mds:consBom:view";
    public static final String CONS_BOM_CREATE = "mds:consBom:create";
    public static final String CONS_BOM_UPDATE = "mds:consBom:update";
    public static final String CONS_BOM_DELETE = "mds:consBom:delete";

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
    /** 校验特例第五码：质检角色只需「跑校验+看报告」不需改流程，单独授权（ICM V22）。 */
    public static final String FLOW_VALIDATE = "mds:flow:validate";

    // ---------------- 统一指派（ICM V22：ContextChain 三 scope 一张表，三层分开授权） ----------------
    public static final String OP_ASSIGN_VIEW = "mds:opAssign:view";
    public static final String OP_ASSIGN_CREATE = "mds:opAssign:create";
    public static final String OP_ASSIGN_UPDATE = "mds:opAssign:update";
    public static final String OP_ASSIGN_DELETE = "mds:opAssign:delete";

    public static final String FLOW_ASSIGN_VIEW = "mds:flowAssign:view";
    public static final String FLOW_ASSIGN_CREATE = "mds:flowAssign:create";
    public static final String FLOW_ASSIGN_UPDATE = "mds:flowAssign:update";
    public static final String FLOW_ASSIGN_DELETE = "mds:flowAssign:delete";

    public static final String STEP_ASSIGN_VIEW = "mds:stepAssign:view";
    public static final String STEP_ASSIGN_CREATE = "mds:stepAssign:create";
    public static final String STEP_ASSIGN_UPDATE = "mds:stepAssign:update";
    public static final String STEP_ASSIGN_DELETE = "mds:stepAssign:delete";

    // ---------------- KV 约束参数（ICM V22：context 三层一组码） ----------------
    public static final String OP_ATTR_VIEW = "mds:opAttr:view";
    public static final String OP_ATTR_UPDATE = "mds:opAttr:update";

    public static final String FLOW_ATTR_VIEW = "mds:flowAttr:view";
    public static final String FLOW_ATTR_UPDATE = "mds:flowAttr:update";

    public static final String STEP_ATTR_VIEW = "mds:stepAttr:view";
    public static final String STEP_ATTR_UPDATE = "mds:stepAttr:update";

    // ---------------- 参数定义字典（ICM V22） ----------------
    public static final String PARAM_DEF_VIEW = "mds:paramDef:view";
    public static final String PARAM_DEF_CREATE = "mds:paramDef:create";
    public static final String PARAM_DEF_UPDATE = "mds:paramDef:update";
    public static final String PARAM_DEF_DELETE = "mds:paramDef:delete";

    // ---------------- EDC 工程数采（V28） ----------------
    public static final String EDC_SET_VIEW = "mds:edcSet:view";
    public static final String EDC_SET_CREATE = "mds:edcSet:create";
    public static final String EDC_SET_UPDATE = "mds:edcSet:update";
    public static final String EDC_SET_DELETE = "mds:edcSet:delete";
    public static final String EDC_TPL_VIEW = "mds:edcTpl:view";
    public static final String EDC_TPL_CREATE = "mds:edcTpl:create";
    public static final String EDC_TPL_UPDATE = "mds:edcTpl:update";
    public static final String EDC_TPL_DELETE = "mds:edcTpl:delete";
    public static final String EDC_MAP_VIEW = "mds:edcMap:view";
    public static final String EDC_MAP_CREATE = "mds:edcMap:create";
    public static final String EDC_MAP_UPDATE = "mds:edcMap:update";
    public static final String EDC_MAP_DELETE = "mds:edcMap:delete";
    public static final String EDC_PLAN_VIEW = "mds:edcPlan:view";
    public static final String EDC_PLAN_CREATE = "mds:edcPlan:create";
    public static final String EDC_PLAN_UPDATE = "mds:edcPlan:update";
    public static final String EDC_PLAN_DELETE = "mds:edcPlan:delete";

    // ---------------- 产品↔流程绑定（ICM V22） ----------------
    public static final String PRODUCT_FLOW_VIEW = "mds:productFlow:view";
    public static final String PRODUCT_FLOW_CREATE = "mds:productFlow:create";
    /** V25 L3：产品级覆盖维护（列型覆盖 / 产品指派 / 产品 KV）。 */
    public static final String PRODUCT_FLOW_UPDATE = "mds:productFlow:update";
    public static final String PRODUCT_FLOW_DELETE = "mds:productFlow:delete";

    // ---------------- 工序库（Operation，产品管理域，P-T-R-O 四层建模 O 层：V20） ----------------
    public static final String OPERATION_VIEW = "mds:operation:view";
    public static final String OPERATION_CREATE = "mds:operation:create";
    public static final String OPERATION_UPDATE = "mds:operation:update";
    public static final String OPERATION_DELETE = "mds:operation:delete";

    // ---------------- 原因码（ReasonCode，系统管理域：WIP 事件归因主数据 + 快照治理） ----------------
    public static final String REASON_VIEW = "mds:reason:view";
    public static final String REASON_CREATE = "mds:reason:create";
    public static final String REASON_UPDATE = "mds:reason:update";
    public static final String REASON_DELETE = "mds:reason:delete";

    // ---------------- 配方解算（V29：解算契约执行；可变参数维护挂 mds:operation:*） ----------------
    public static final String RECIPE_RESOLVE = "mds:recipe:resolve";

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

    // ---------------- 班组排班（Shift，日常操作域：班次/班制/排班三块共用一组码） ----------------
    public static final String SHIFT_VIEW = "mds:shift:view";
    public static final String SHIFT_CREATE = "mds:shift:create";
    public static final String SHIFT_UPDATE = "mds:shift:update";
    public static final String SHIFT_DELETE = "mds:shift:delete";

    // ---------------- 交接班记录（ShiftLog：值班人员全员可写，与排班管理员分开） ----------------
    public static final String SHIFTLOG_VIEW = "mds:shiftlog:view";
    public static final String SHIFTLOG_SAVE = "mds:shiftlog:save";

    // ---------------- 换班申请（ShiftSwap：员工自助发起，全员可写，与排班管理员分开） ----------------
    public static final String SHIFTSWAP_VIEW = "mds:shiftswap:view";
    public static final String SHIFTSWAP_SAVE = "mds:shiftswap:save";

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
        codes.add(EQC_VIEW);
        codes.add(EQC_CREATE);
        codes.add(EQC_UPDATE);
        codes.add(EQC_DELETE);
        codes.add(TOOL_GROUP_VIEW);
        codes.add(TOOL_GROUP_CREATE);
        codes.add(TOOL_GROUP_UPDATE);
        codes.add(TOOL_GROUP_DELETE);
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
        codes.add(CARRIER_VIEW);
        codes.add(CARRIER_CREATE);
        codes.add(CARRIER_UPDATE);
        codes.add(CARRIER_DELETE);
        codes.add(RETICLE_VIEW);
        codes.add(RETICLE_CREATE);
        codes.add(RETICLE_UPDATE);
        codes.add(RETICLE_DELETE);
        codes.add(INSP_VIEW);
        codes.add(INSP_CREATE);
        codes.add(INSP_UPDATE);
        codes.add(INSP_DELETE);
        codes.add(CONS_VIEW);
        codes.add(CONS_CREATE);
        codes.add(CONS_UPDATE);
        codes.add(CONS_DELETE);
        codes.add(CONS_BOM_VIEW);
        codes.add(CONS_BOM_CREATE);
        codes.add(CONS_BOM_UPDATE);
        codes.add(CONS_BOM_DELETE);
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
        codes.add(FLOW_VALIDATE);
        codes.add(OP_ASSIGN_VIEW);
        codes.add(OP_ASSIGN_CREATE);
        codes.add(OP_ASSIGN_UPDATE);
        codes.add(OP_ASSIGN_DELETE);
        codes.add(FLOW_ASSIGN_VIEW);
        codes.add(FLOW_ASSIGN_CREATE);
        codes.add(FLOW_ASSIGN_UPDATE);
        codes.add(FLOW_ASSIGN_DELETE);
        codes.add(STEP_ASSIGN_VIEW);
        codes.add(STEP_ASSIGN_CREATE);
        codes.add(STEP_ASSIGN_UPDATE);
        codes.add(STEP_ASSIGN_DELETE);
        codes.add(OP_ATTR_VIEW);
        codes.add(OP_ATTR_UPDATE);
        codes.add(FLOW_ATTR_VIEW);
        codes.add(FLOW_ATTR_UPDATE);
        codes.add(STEP_ATTR_VIEW);
        codes.add(STEP_ATTR_UPDATE);
        codes.add(PARAM_DEF_VIEW);
        codes.add(PARAM_DEF_CREATE);
        codes.add(PARAM_DEF_UPDATE);
        codes.add(PARAM_DEF_DELETE);
        codes.add(EDC_SET_VIEW);
        codes.add(EDC_SET_CREATE);
        codes.add(EDC_SET_UPDATE);
        codes.add(EDC_SET_DELETE);
        codes.add(EDC_TPL_VIEW);
        codes.add(EDC_TPL_CREATE);
        codes.add(EDC_TPL_UPDATE);
        codes.add(EDC_TPL_DELETE);
        codes.add(EDC_MAP_VIEW);
        codes.add(EDC_MAP_CREATE);
        codes.add(EDC_MAP_UPDATE);
        codes.add(EDC_MAP_DELETE);
        codes.add(EDC_PLAN_VIEW);
        codes.add(EDC_PLAN_CREATE);
        codes.add(EDC_PLAN_UPDATE);
        codes.add(EDC_PLAN_DELETE);
        codes.add(PRODUCT_FLOW_VIEW);
        codes.add(PRODUCT_FLOW_CREATE);
        codes.add(PRODUCT_FLOW_DELETE);
        codes.add(OPERATION_VIEW);
        codes.add(OPERATION_CREATE);
        codes.add(OPERATION_UPDATE);
        codes.add(OPERATION_DELETE);
        codes.add(REASON_VIEW);
        codes.add(REASON_CREATE);
        codes.add(REASON_UPDATE);
        codes.add(REASON_DELETE);
        codes.add(RECIPE_RESOLVE);
        codes.add(SHIFT_VIEW);
        codes.add(SHIFT_CREATE);
        codes.add(SHIFT_UPDATE);
        codes.add(SHIFT_DELETE);
        codes.add(SHIFTLOG_VIEW);
        codes.add(SHIFTLOG_SAVE);
        codes.add(SHIFTSWAP_VIEW);
        codes.add(SHIFTSWAP_SAVE);
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
