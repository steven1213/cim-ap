package com.cim.mds.server.console;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.system.menu.MenuType;
import com.cim.system.support.PermissionCodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MDS 控制台目录（菜单树 / 权限码 / 角色 / 译文）——种子的<b>单一事实源</b>。
 *
 * <p>把「控制台长什么样、有哪些页面、要什么权限、各语言怎么显示」集中在此，由
 * {@link MdsConsoleSeedService} 幂等写入本 ap 的库；改菜单/权限/文案只改本文件，
 * <b>不再改前端代码</b>。与 {@code RmsConsoleCatalog} / {@code IamConsoleCatalog} 同构。</p>
 *
 * <p>与 {@code cim-system} 约定一致：菜单节点只存 {@code i18n_code}，<b>不存中文字面量</b>；
 * 文案在 {@code sys_i18n} 里按语言给（命名空间 {@code mds.*}，与前端内置包对齐）。</p>
 */
public final class MdsConsoleCatalog {

    private MdsConsoleCatalog() {}

    /** 超级管理员角色码。 */
    public static final String ROLE_ADMIN = "MDS_ADMIN";
    /** 操作员角色码（无删除权限）。 */
    public static final String ROLE_OPERATOR = "MDS_OPERATOR";
    /** 只读角色码。 */
    public static final String ROLE_VIEWER = "MDS_VIEWER";

    // ================================================================ 菜单树

    /**
     * 菜单定义。
     *
     * @param key       逻辑键（供父子引用与幂等定位；不落库）
     * @param parentKey 父节点逻辑键（{@code null} 为顶级）
     * @param type      {@code DIR} 目录 / {@code MENU} 菜单 / {@code BUTTON} 按钮
     * @param path      前端路由（{@code DIR}/{@code BUTTON} 为 {@code null}）
     * @param component 前端组件标识（供动态路由映射）
     * @param i18nCode  名称 i18n 键（<b>幂等自然键</b>，全库唯一）
     * @param icon      图标标识（前端图标注册表的键）
     * @param permCode  关联权限码（{@code BUTTON} 必填；{@code MENU} 可空）
     * @param sortNo    同级排序
     */
    public record MenuDef(String key, String parentKey, MenuType type, String path, String component,
                          String i18nCode, String icon, String permCode, int sortNo) {
    }

    /** MDS 控制台菜单树（父先于子；顺序即种子写入顺序）。 */
    public static List<MenuDef> menus() {
        List<MenuDef> m = new ArrayList<>();
        // —— 概览（不绑权限：登录后的落脚页）——
        m.add(menu("overview", null, "/", "DashboardPage", "mds.nav.dashboard", "gauge", null, 10));
        // —— 场地主数据（Site → Fab → Area → Bay → Stage）——
        // 2026-11-13 收敛为一页「位置拓扑」（左树右详情），原 sites/fabs/areas 三菜单退场
        m.add(dir("grp.site", "mds.nav.group.site", 20));
        m.add(menu("topology", "grp.site", "/topology", "TopologyPage", "mds.nav.topology", "tree",
                MdsPermissions.SITE_VIEW, 10));

        // 按钮节点：供授权界面按动作细分（前端菜单不渲染 BUTTON）
        // i18n 码须全库唯一，单菜单下五层动作显式命名 {layer}.{action}
        m.add(button("btn.site.create", "topology", "mds.nav.topology.action.site.create",
                MdsPermissions.SITE_CREATE, 10));
        m.add(button("btn.site.update", "topology", "mds.nav.topology.action.site.update",
                MdsPermissions.SITE_UPDATE, 20));
        m.add(button("btn.site.delete", "topology", "mds.nav.topology.action.site.delete",
                MdsPermissions.SITE_DELETE, 30));
        m.add(button("btn.fab.create", "topology", "mds.nav.topology.action.fab.create",
                MdsPermissions.FAB_CREATE, 40));
        m.add(button("btn.fab.update", "topology", "mds.nav.topology.action.fab.update",
                MdsPermissions.FAB_UPDATE, 50));
        m.add(button("btn.fab.delete", "topology", "mds.nav.topology.action.fab.delete",
                MdsPermissions.FAB_DELETE, 60));
        m.add(button("btn.area.create", "topology", "mds.nav.topology.action.area.create",
                MdsPermissions.AREA_CREATE, 70));
        m.add(button("btn.area.update", "topology", "mds.nav.topology.action.area.update",
                MdsPermissions.AREA_UPDATE, 80));
        m.add(button("btn.area.delete", "topology", "mds.nav.topology.action.area.delete",
                MdsPermissions.AREA_DELETE, 90));
        m.add(button("btn.bay.create", "topology", "mds.nav.topology.action.bay.create",
                MdsPermissions.BAY_CREATE, 100));
        m.add(button("btn.bay.update", "topology", "mds.nav.topology.action.bay.update",
                MdsPermissions.BAY_UPDATE, 110));
        m.add(button("btn.bay.delete", "topology", "mds.nav.topology.action.bay.delete",
                MdsPermissions.BAY_DELETE, 120));
        m.add(button("btn.stage.create", "topology", "mds.nav.topology.action.stage.create",
                MdsPermissions.STAGE_CREATE, 130));
        m.add(button("btn.stage.update", "topology", "mds.nav.topology.action.stage.update",
                MdsPermissions.STAGE_UPDATE, 140));
        m.add(button("btn.stage.delete", "topology", "mds.nav.topology.action.stage.delete",
                MdsPermissions.STAGE_DELETE, 150));

        // —— 产品管理（菜单 order = 用户操作动线序：建产品 → 定义字典 → 建工序 → 建流程 → 配参数 → 绑产品；
        //    辅助页（规则/导入/对比/总览）置尾。前端 GuideBar 步骤索引与此前 7 步主链一致）——
        m.add(dir("grp.product", "mds.nav.group.product", 30));
        m.add(menu("technology", "grp.product", "/technologies", "TechnologyPage",
                "mds.nav.technology", "sliders", MdsPermissions.TECH_VIEW, 10));
        m.add(button("btn.technology.create", "technology", "mds.nav.technology.action.create",
                MdsPermissions.TECH_CREATE, 10));
        m.add(button("btn.technology.update", "technology", "mds.nav.technology.action.update",
                MdsPermissions.TECH_UPDATE, 20));
        m.add(button("btn.technology.delete", "technology", "mds.nav.technology.action.delete",
                MdsPermissions.TECH_DELETE, 30));
        m.add(menu("products", "grp.product", "/products", "ProductPage",
                "mds.nav.products", "flask", MdsPermissions.PRODUCT_VIEW, 20));
        m.add(button("btn.product.create", "products", "mds.nav.products.action.create",
                MdsPermissions.PRODUCT_CREATE, 10));
        m.add(button("btn.product.update", "products", "mds.nav.products.action.update",
                MdsPermissions.PRODUCT_UPDATE, 20));
        m.add(button("btn.product.delete", "products", "mds.nav.products.action.delete",
                MdsPermissions.PRODUCT_DELETE, 30));
        // —— 制造流程（Route 口径：XML 画布为源 + 拖拽编辑 + 图校验发布）——
        m.add(menu("flows", "grp.product", "/flows", "FlowPage",
                "mds.nav.flows", "tree", MdsPermissions.FLOW_VIEW, 35));
        m.add(button("btn.flow.create", "flows", "mds.nav.flows.action.create",
                MdsPermissions.FLOW_CREATE, 10));
        m.add(button("btn.flow.save", "flows", "mds.nav.flows.action.save",
                MdsPermissions.FLOW_UPDATE, 20));
        m.add(button("btn.flow.publish", "flows", "mds.nav.flows.action.publish",
                MdsPermissions.FLOW_UPDATE, 30));
        m.add(button("btn.flow.delete", "flows", "mds.nav.flows.action.delete",
                MdsPermissions.FLOW_DELETE, 40));
        // —— 流程规则（V15：独立规则层，锚定版本族，evaluate 契约供 MES/MCS）——
        m.add(menu("flowRules", "grp.product", "/flow-rules", "FlowRulePage",
                "mds.nav.flowRules", "branch", MdsPermissions.FLOW_VIEW, 40));
        m.add(button("btn.flowRule.create", "flowRules", "mds.nav.flowRules.action.create",
                MdsPermissions.FLOW_UPDATE, 10));
        m.add(button("btn.flowRule.save", "flowRules", "mds.nav.flowRules.action.save",
                MdsPermissions.FLOW_UPDATE, 20));
        m.add(button("btn.flowRule.delete", "flowRules", "mds.nav.flowRules.action.delete",
                MdsPermissions.FLOW_UPDATE, 30));
        // —— 工序库（V20：P-T-R-O 四层建模 O 层；Operation 独立主数据，跨路线复用）——
        m.add(menu("operations", "grp.product", "/operations", "OperationsPage",
                "mds.nav.operations", "layers", MdsPermissions.OPERATION_VIEW, 30));
        m.add(button("btn.operation.create", "operations", "mds.nav.operations.action.create",
                MdsPermissions.OPERATION_CREATE, 10));
        m.add(button("btn.operation.update", "operations", "mds.nav.operations.action.update",
                MdsPermissions.OPERATION_UPDATE, 20));
        m.add(button("btn.operation.delete", "operations", "mds.nav.operations.action.delete",
                MdsPermissions.OPERATION_DELETE, 30));
        // —— 光罩（V32：光刻关键耗材级主数据；工序/流程步骤 reticleRef 引用闭环）——
        m.add(menu("reticles", "grp.product", "/reticles", "ReticlePage",
                "mds.nav.reticles", "grid", MdsPermissions.RETICLE_VIEW, 31));
        m.add(button("btn.reticle.create", "reticles", "mds.nav.reticles.action.create",
                MdsPermissions.RETICLE_CREATE, 10));
        m.add(button("btn.reticle.update", "reticles", "mds.nav.reticles.action.update",
                MdsPermissions.RETICLE_UPDATE, 20));
        m.add(button("btn.reticle.delete", "reticles", "mds.nav.reticles.action.delete",
                MdsPermissions.RETICLE_DELETE, 30));
        // —— ICM 继承式约束模型（V22，2026-11-25 定稿：ContextChain 三级 + 统一指派 + KV + 校验闸）——
        m.add(menu("stepParams", "grp.product", "/step-params", "StepParamPage",
                "mds.nav.stepParams", "sliders", MdsPermissions.STEP_ASSIGN_VIEW, 36));
        m.add(menu("paramDefs", "grp.product", "/param-defs", "ParamDefPage",
                "mds.nav.paramDefs", "list", MdsPermissions.PARAM_DEF_VIEW, 25));
        m.add(menu("flowCompare", "grp.product", "/flow-compare", "FlowComparePage",
                "mds.nav.flowCompare", "columns", MdsPermissions.FLOW_VIEW, 42));
        // —— 产品↔流程绑定（动线第 7 步：流程建模/激活之后才可绑，FROZEN+VALID 可预绑）——
        m.add(menu("productFlows", "grp.product", "/product-bindings", "ProductFlowPage",
                "mds.nav.productFlows", "link", MdsPermissions.PRODUCT_FLOW_VIEW, 38));
        m.add(menu("flowImport", "grp.product", "/flow-import", "FlowImportPage",
                "mds.nav.flowImport", "upload", MdsPermissions.FLOW_VIEW, 41));
        // —— 流程总览（P3-4：MainProcessFlowOverAll 等价，前端实时推导不落库）——
        m.add(menu("flowOverview", "grp.product", "/flow-overview", "FlowOverviewPage",
                "mds.nav.flowOverview", "eye", MdsPermissions.FLOW_VIEW, 43));
        // —— 配方解算（V29：解算契约执行页；可变参数维护在工序库详情）——
        m.add(menu("recipeResolve", "grp.product", "/recipe-resolve", "RecipeResolvePage",
                "mds.nav.recipeResolve", "robot", MdsPermissions.RECIPE_RESOLVE, 45));
        // —— EDC 工程数采（V28：采集契约静态元数据；动线 = 参数定义(产品管理组) →
        //     参数集 → 设备接口映射 → EDC 方案；运行时采集与判定归 MES/SPC/FDC）——
        m.add(dir("grp.edc", "mds.nav.group.edc", 32));
        m.add(menu("edcParamSets", "grp.edc", "/edc/param-sets", "EdcParamSetPage",
                "mds.nav.edcParamSets", "boxes", MdsPermissions.EDC_SET_VIEW, 10));
        m.add(menu("edcReportTpls", "grp.edc", "/edc/report-tpls", "EdcReportTplPage",
                "mds.nav.edcReportTpls", "file", MdsPermissions.EDC_TPL_VIEW, 15));
        m.add(menu("edcEquipMaps", "grp.edc", "/edc/equip-maps", "EdcEquipMapPage",
                "mds.nav.edcEquipMaps", "link", MdsPermissions.EDC_MAP_VIEW, 20));
        m.add(menu("edcPlans", "grp.edc", "/edc/plans", "EdcPlanPage",
                "mds.nav.edcPlans", "sliders", MdsPermissions.EDC_PLAN_VIEW, 30));
        m.add(button("btn.edcSet.create", "edcParamSets", "mds.nav.edcParamSets.action.create",
                MdsPermissions.EDC_SET_CREATE, 10));
        m.add(button("btn.edcSet.import", "edcParamSets", "mds.nav.edcParamSets.action.import",
                MdsPermissions.EDC_SET_UPDATE, 20));
        m.add(button("btn.edcTpl.create", "edcReportTpls", "mds.nav.edcReportTpls.action.create",
                MdsPermissions.EDC_TPL_CREATE, 10));
        m.add(button("btn.edcTpl.import", "edcReportTpls", "mds.nav.edcReportTpls.action.import",
                MdsPermissions.EDC_TPL_UPDATE, 20));
        m.add(button("btn.edcMap.create", "edcEquipMaps", "mds.nav.edcEquipMaps.action.create",
                MdsPermissions.EDC_MAP_CREATE, 10));
        m.add(button("btn.edcMap.import", "edcEquipMaps", "mds.nav.edcEquipMaps.action.import",
                MdsPermissions.EDC_MAP_UPDATE, 20));
        m.add(button("btn.edcPlan.create", "edcPlans", "mds.nav.edcPlans.action.create",
                MdsPermissions.EDC_PLAN_CREATE, 10));
        m.add(button("btn.edcPlan.import", "edcPlans", "mds.nav.edcPlans.action.import",
                MdsPermissions.EDC_PLAN_UPDATE, 20));
        // —— 设备管理（v2，2026-11-24：Class—Instance—Capability，ISA-95 设备对象模型）——
        // 三页合一：机台台账 /equipments 覆盖加工与搬运设备（按机型大类筛选）；
        // 动线：机型模板先行 → 派生实例 → 机台组=派工候选池（Work Center）→ 能力 → 存储区
        m.add(dir("grp.equipment", "mds.nav.group.equipment", 35));
        m.add(menu("equipmentClasses", "grp.equipment", "/equipment-classes", "EquipmentClassPage",
                "mds.nav.equipmentClasses", "sliders", MdsPermissions.EQC_VIEW, 10));
        m.add(menu("equipments", "grp.equipment", "/equipments", "EquipmentPage",
                "mds.nav.equipments", "monitor", MdsPermissions.EQUIPMENT_VIEW, 20));
        // 搬运设备 = 机台台账的 AMHS 视图（kind=MATERIAL：OHT/OHS/Stocker/OHB/NTB），薄 wrapper 固化筛选
        m.add(menu("transportEquipments", "grp.equipment", "/transport-equipments", "TransportEquipmentsPage",
                "mds.nav.transportEquipments", "monitor", MdsPermissions.EQUIPMENT_VIEW, 21));
        m.add(menu("toolGroups", "grp.equipment", "/tool-groups", "ToolGroupPage",
                "mds.nav.toolGroups", "boxes", MdsPermissions.TOOL_GROUP_VIEW, 30));
        m.add(menu("capabilities", "grp.equipment", "/capabilities", "CapabilityPage",
                "mds.nav.capabilities", "shield", MdsPermissions.CAPABILITY_VIEW, 40));
        m.add(button("btn.equipment.create", "equipments", "mds.nav.equipments.action.create",
                MdsPermissions.EQUIPMENT_CREATE, 10));
        m.add(button("btn.equipment.update", "equipments", "mds.nav.equipments.action.update",
                MdsPermissions.EQUIPMENT_UPDATE, 20));
        m.add(button("btn.equipment.delete", "equipments", "mds.nav.equipments.action.delete",
                MdsPermissions.EQUIPMENT_DELETE, 30));
        m.add(button("btn.equipmentClass.create", "equipmentClasses", "mds.nav.equipmentClasses.action.create",
                MdsPermissions.EQC_CREATE, 10));
        m.add(button("btn.equipmentClass.update", "equipmentClasses", "mds.nav.equipmentClasses.action.update",
                MdsPermissions.EQC_UPDATE, 20));
        m.add(button("btn.equipmentClass.delete", "equipmentClasses", "mds.nav.equipmentClasses.action.delete",
                MdsPermissions.EQC_DELETE, 30));
        m.add(button("btn.equipmentClass.derive", "equipmentClasses", "mds.nav.equipmentClasses.action.derive",
                MdsPermissions.EQUIPMENT_CREATE, 40));
        m.add(button("btn.toolGroup.create", "toolGroups", "mds.nav.toolGroups.action.create",
                MdsPermissions.TOOL_GROUP_CREATE, 10));
        m.add(button("btn.toolGroup.update", "toolGroups", "mds.nav.toolGroups.action.update",
                MdsPermissions.TOOL_GROUP_UPDATE, 20));
        m.add(button("btn.toolGroup.delete", "toolGroups", "mds.nav.toolGroups.action.delete",
                MdsPermissions.TOOL_GROUP_DELETE, 30));
        m.add(button("btn.capability.create", "capabilities", "mds.nav.capabilities.action.create",
                MdsPermissions.CAPABILITY_CREATE, 10));
        m.add(button("btn.capability.update", "capabilities", "mds.nav.capabilities.action.update",
                MdsPermissions.CAPABILITY_UPDATE, 20));
        m.add(button("btn.capability.delete", "capabilities", "mds.nav.capabilities.action.delete",
                MdsPermissions.CAPABILITY_DELETE, 30));

        // —— 存储区 Bank（Stocker 内逻辑分区，SEMI E88 zone 口径；转运规则同页维护）——
        m.add(menu("banks", "grp.equipment", "/banks", "BankPage",
                "mds.nav.banks", "inbox", MdsPermissions.BANK_VIEW, 50));
        m.add(button("btn.bank.create", "banks", "mds.nav.banks.action.create",
                MdsPermissions.BANK_CREATE, 10));
        m.add(button("btn.bank.update", "banks", "mds.nav.banks.action.update",
                MdsPermissions.BANK_UPDATE, 20));
        m.add(button("btn.bank.delete", "banks", "mds.nav.banks.action.delete",
                MdsPermissions.BANK_DELETE, 30));

        // —— 载具（V31：SEMI E87 Carrier 口径，AMHS 全链物理载体台账）——
        m.add(menu("carriers", "grp.equipment", "/carriers", "CarrierPage",
                "mds.nav.carriers", "box", MdsPermissions.CARRIER_VIEW, 60));
        m.add(button("btn.carrier.create", "carriers", "mds.nav.carriers.action.create",
                MdsPermissions.CARRIER_CREATE, 10));
        m.add(button("btn.carrier.update", "carriers", "mds.nav.carriers.action.update",
                MdsPermissions.CARRIER_UPDATE, 20));
        m.add(button("btn.carrier.delete", "carriers", "mds.nav.carriers.action.delete",
                MdsPermissions.CARRIER_DELETE, 30));

        // —— 质量与耗材（V33/V34：检验标准 + 耗材台账）——
        m.add(dir("grp.quality", "mds.nav.group.quality", 34));
        m.add(menu("inspectionStds", "grp.quality", "/inspection-stds", "InspectionPage",
                "mds.nav.inspectionStds", "check", MdsPermissions.INSP_VIEW, 10));
        m.add(button("btn.inspection.create", "inspectionStds", "mds.nav.inspectionStds.action.create",
                MdsPermissions.INSP_CREATE, 10));
        m.add(button("btn.inspection.update", "inspectionStds", "mds.nav.inspectionStds.action.update",
                MdsPermissions.INSP_UPDATE, 20));
        m.add(button("btn.inspection.delete", "inspectionStds", "mds.nav.inspectionStds.action.delete",
                MdsPermissions.INSP_DELETE, 30));
        m.add(menu("consumables", "grp.quality", "/consumables", "ConsumablePage",
                "mds.nav.consumables", "box", MdsPermissions.CONS_VIEW, 20));
        m.add(button("btn.consumable.create", "consumables", "mds.nav.consumables.action.create",
                MdsPermissions.CONS_CREATE, 10));
        m.add(button("btn.consumable.update", "consumables", "mds.nav.consumables.action.update",
                MdsPermissions.CONS_UPDATE, 20));
        m.add(button("btn.consumable.delete", "consumables", "mds.nav.consumables.action.delete",
                MdsPermissions.CONS_DELETE, 30));

        // —— 数据字典（平台 sys_dict，系统级参照数据；挂「系统管理」组，与业务域分组分开）——
        // 权限码沿用平台 sys:dict:*（@PreAuthorize 在 cim-system 的字典服务上，另立编码只会 403）
        // shift ops group (V17)
        m.add(dir("grp.shift", "mds.nav.group.shift", 37));
        m.add(menu("shiftDefs", "grp.shift", "/shift-defs", "ShiftDefsPage",
                "mds.nav.shiftDefs", "clock", MdsPermissions.SHIFT_VIEW, 10));
        m.add(button("btn.shiftDef.create", "shiftDefs", "mds.nav.shiftDefs.action.create",
                MdsPermissions.SHIFT_CREATE, 10));
        m.add(button("btn.shiftDef.save", "shiftDefs", "mds.nav.shiftDefs.action.save",
                MdsPermissions.SHIFT_UPDATE, 20));
        m.add(button("btn.shiftDef.delete", "shiftDefs", "mds.nav.shiftDefs.action.delete",
                MdsPermissions.SHIFT_DELETE, 30));
        // V18 卡控开关启停（ShiftDefsPage 就地切换 chips；无独立管理页）
        m.add(button("btn.shiftDef.control", "shiftDefs", "mds.nav.shiftDefs.action.control",
                MdsPermissions.SHIFT_UPDATE, 40));
        m.add(menu("schedule", "grp.shift", "/schedule", "SchedulePage",
                "mds.nav.schedule", "calendar", MdsPermissions.SHIFT_VIEW, 20));
        m.add(button("btn.schedule.save", "schedule", "mds.nav.schedule.action.save",
                MdsPermissions.SHIFT_UPDATE, 10));
        // 换班申请（V19）：员工自助动线，独立 shiftswap 码组（与交接记录同理全员可写）
        m.add(menu("shiftSwaps", "grp.shift", "/shift-swaps", "ShiftSwapsPage",
                "mds.nav.shiftSwaps", "sync", MdsPermissions.SHIFTSWAP_VIEW, 25));
        m.add(button("btn.shiftSwap.apply", "shiftSwaps", "mds.nav.shiftSwaps.action.apply",
                MdsPermissions.SHIFTSWAP_SAVE, 10));
        m.add(button("btn.shiftSwap.confirm", "shiftSwaps", "mds.nav.shiftSwaps.action.confirm",
                MdsPermissions.SHIFTSWAP_SAVE, 20));
        m.add(button("btn.shiftSwap.cancel", "shiftSwaps", "mds.nav.shiftSwaps.action.cancel",
                MdsPermissions.SHIFTSWAP_SAVE, 30));
        m.add(menu("shiftLogs", "grp.shift", "/shift-logs", "ShiftLogsPage",
                "mds.nav.shiftLogs", "file", MdsPermissions.SHIFTLOG_VIEW, 30));
        m.add(button("btn.shiftLog.save", "shiftLogs", "mds.nav.shiftLogs.action.save",
                MdsPermissions.SHIFTLOG_SAVE, 10));
        m.add(button("btn.shiftLog.ack", "shiftLogs", "mds.nav.shiftLogs.action.ack",
                MdsPermissions.SHIFTLOG_SAVE, 20));

        m.add(dir("grp.system", "mds.nav.group.system", 40));
        m.add(menu("dicts", "grp.system", "/dicts", "DictPage",
                "mds.nav.dicts", "list", PermissionCodes.DICT_LIST, 10));
        m.add(button("btn.dict.save", "dicts", "mds.nav.dicts.action.save",
                PermissionCodes.DICT_SAVE, 10));
        m.add(button("btn.dict.delete", "dicts", "mds.nav.dicts.action.delete",
                PermissionCodes.DICT_REMOVE, 20));

        // —— 状态模型（SEMI E30 状态机口径：状态集合 + 转换 allowlist；
        //    模型/状态/矩阵均可页面维护，规则表轻量不走编辑锁）——
        m.add(menu("stateModels", "grp.system", "/state-models", "StateModelPage",
                "mds.nav.stateModels", "check", MdsPermissions.STATE_MODEL_VIEW, 20));
        m.add(button("btn.stateModel.create", "stateModels", "mds.nav.stateModels.action.create",
                MdsPermissions.STATE_MODEL_CREATE, 10));
        m.add(button("btn.stateModel.save", "stateModels", "mds.nav.stateModels.action.save",
                MdsPermissions.STATE_MODEL_UPDATE, 20));
        m.add(button("btn.stateModel.delete", "stateModels", "mds.nav.stateModels.action.delete",
                MdsPermissions.STATE_MODEL_DELETE, 30));

        // —— 编码规则（配置化 Key Generation：模板段 + 分桶计数器；
        //    各域 create 同事务占号/校验，规则表轻量不走编辑锁）——
        m.add(menu("namingRules", "grp.system", "/naming-rules", "NamingRulePage",
                "mds.nav.namingRules", "hash", MdsPermissions.NAMING_RULE_VIEW, 30));
        m.add(button("btn.namingRule.create", "namingRules", "mds.nav.namingRules.action.create",
                MdsPermissions.NAMING_RULE_CREATE, 10));
        m.add(button("btn.namingRule.save", "namingRules", "mds.nav.namingRules.action.save",
                MdsPermissions.NAMING_RULE_UPDATE, 20));
        m.add(button("btn.namingRule.delete", "namingRules", "mds.nav.namingRules.action.delete",
                MdsPermissions.NAMING_RULE_DELETE, 30));

        // —— 原因码（V16：WIP 事件归因主数据；树即码 + 事件快照治理演示）——
        m.add(menu("reasonCodes", "grp.system", "/reason-codes", "ReasonCodePage",
                "mds.nav.reasonCodes", "list", MdsPermissions.REASON_VIEW, 40));
        m.add(button("btn.reason.create", "reasonCodes", "mds.nav.reasonCodes.action.create",
                MdsPermissions.REASON_CREATE, 10));
        m.add(button("btn.reason.save", "reasonCodes", "mds.nav.reasonCodes.action.save",
                MdsPermissions.REASON_UPDATE, 20));
        m.add(button("btn.reason.delete", "reasonCodes", "mds.nav.reasonCodes.action.delete",
                MdsPermissions.REASON_DELETE, 30));

        // —— 菜单管理（sys_menu 在线维护 + 角色授权联动；平台权限码，端点来自 cim-system）——
        m.add(menu("menuAdmin", "grp.system", "/menus", "MenusAdminPage",
                "mds.nav.menus", "layers", MdsPermissions.SYS_MENU_LIST, 60));
        m.add(button("btn.menu.save", "menuAdmin", "mds.nav.menus.action.save",
                MdsPermissions.SYS_MENU_SAVE, 10));
        m.add(button("btn.menu.delete", "menuAdmin", "mds.nav.menus.action.delete",
                MdsPermissions.SYS_MENU_REMOVE, 20));
        m.add(button("btn.menu.grant", "menuAdmin", "mds.nav.menus.action.grant",
                MdsPermissions.SYS_ROLE_GRANT, 30));

        // —— 系统配置组其余五页（六页共享组件 @cim/admin；菜单/权限/角色/多语言走平台端点，
        //    组织/人员只读——主数据在 IAM，经本系统代理取目录 API）——
        m.add(menu("permissionsAdmin", "grp.system", "/permissions", "PermissionsAdminPage",
                "mds.nav.permissions", "shield", MdsPermissions.SYS_PERM_LIST, 61));
        m.add(button("btn.permission.save", "permissionsAdmin", "mds.nav.permissions.action.save",
                MdsPermissions.SYS_PERM_SAVE, 10));
        m.add(button("btn.permission.delete", "permissionsAdmin", "mds.nav.permissions.action.delete",
                MdsPermissions.SYS_PERM_REMOVE, 20));

        m.add(menu("rolesAdmin", "grp.system", "/roles-admin", "RolesAdminPage",
                "mds.nav.rolesAdmin", "users", MdsPermissions.SYS_ROLE_LIST, 62));
        m.add(button("btn.role.save", "rolesAdmin", "mds.nav.rolesAdmin.action.save",
                MdsPermissions.SYS_ROLE_SAVE, 10));
        m.add(button("btn.role.delete", "rolesAdmin", "mds.nav.rolesAdmin.action.delete",
                MdsPermissions.SYS_ROLE_REMOVE, 20));
        m.add(button("btn.role.grant", "rolesAdmin", "mds.nav.rolesAdmin.action.grant",
                MdsPermissions.SYS_ROLE_GRANT, 30));

        m.add(menu("i18nAdmin", "grp.system", "/i18n", "I18nAdminPage",
                "mds.nav.i18n", "check", MdsPermissions.SYS_I18N_LIST, 63));
        m.add(button("btn.i18n.save", "i18nAdmin", "mds.nav.i18n.action.save",
                MdsPermissions.SYS_I18N_SAVE, 10));
        m.add(button("btn.i18n.delete", "i18nAdmin", "mds.nav.i18n.action.delete",
                MdsPermissions.SYS_I18N_REMOVE, 20));
        m.add(button("btn.i18n.locale", "i18nAdmin", "mds.nav.i18n.action.locale",
                MdsPermissions.SYS_I18N_LOCALE, 30));

        // 组织/人员：只读（无 BUTTON 节点），入口码 mds:org:view / mds:user:view
        m.add(menu("orgs", "grp.system", "/orgs", "OrgPage",
                "mds.nav.orgs", "tree", MdsPermissions.ORG_VIEW, 64));
        m.add(menu("users", "grp.system", "/users", "UsersPage",
                "mds.nav.users", "user", MdsPermissions.USER_VIEW, 65));
        return m;
    }

    private static MenuDef menu(String key, String parentKey, String path, String component,
                                String i18nCode, String icon, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.MENU, path, component, i18nCode, icon, permCode, sortNo);
    }

    private static MenuDef dir(String key, String i18nCode, int sortNo) {
        return new MenuDef(key, null, MenuType.DIR, null, null, i18nCode, null, null, sortNo);
    }

    /** 按钮节点：单菜单承载五层动作，i18n 码显式传入（sys_menu.i18n_code 全库唯一）。 */
    private static MenuDef button(String key, String parentKey, String i18nCode, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.BUTTON, null, null, i18nCode, null, permCode, sortNo);
    }

    // ================================================================ 权限

    /** 权限码目录（code 与 {@link MdsPermissions} 对齐）。 */
    public record PermDef(String code, String name) {
    }

    /** MDS 权限码目录。 */
    public static List<PermDef> permissions() {
        List<PermDef> p = new ArrayList<>();
        // 厂区
        p.add(new PermDef(MdsPermissions.SITE_VIEW, "厂区查看"));
        p.add(new PermDef(MdsPermissions.SITE_CREATE, "厂区新建"));
        p.add(new PermDef(MdsPermissions.SITE_UPDATE, "厂区编辑"));
        p.add(new PermDef(MdsPermissions.SITE_DELETE, "厂区删除"));
        // Fab
        p.add(new PermDef(MdsPermissions.FAB_VIEW, "晶圆厂查看"));
        p.add(new PermDef(MdsPermissions.FAB_CREATE, "晶圆厂新建"));
        p.add(new PermDef(MdsPermissions.FAB_UPDATE, "晶圆厂编辑"));
        p.add(new PermDef(MdsPermissions.FAB_DELETE, "晶圆厂删除"));
        // Area
        p.add(new PermDef(MdsPermissions.AREA_VIEW, "Area 查看"));
        p.add(new PermDef(MdsPermissions.AREA_CREATE, "Area 新建"));
        p.add(new PermDef(MdsPermissions.AREA_UPDATE, "Area 编辑"));
        p.add(new PermDef(MdsPermissions.AREA_DELETE, "Area 删除"));
        // Bay
        p.add(new PermDef(MdsPermissions.BAY_VIEW, "Bay 查看"));
        p.add(new PermDef(MdsPermissions.BAY_CREATE, "Bay 新建"));
        p.add(new PermDef(MdsPermissions.BAY_UPDATE, "Bay 编辑"));
        p.add(new PermDef(MdsPermissions.BAY_DELETE, "Bay 删除"));
        // Stage
        p.add(new PermDef(MdsPermissions.STAGE_VIEW, "Stage 查看"));
        p.add(new PermDef(MdsPermissions.STAGE_CREATE, "Stage 新建"));
        p.add(new PermDef(MdsPermissions.STAGE_UPDATE, "Stage 编辑"));
        p.add(new PermDef(MdsPermissions.STAGE_DELETE, "Stage 删除"));
        // Technology（产品管理域）
        p.add(new PermDef(MdsPermissions.TECH_VIEW, "制程技术查看"));
        p.add(new PermDef(MdsPermissions.TECH_CREATE, "制程技术新建"));
        p.add(new PermDef(MdsPermissions.TECH_UPDATE, "制程技术编辑"));
        p.add(new PermDef(MdsPermissions.TECH_DELETE, "制程技术删除"));
        // Product（产品管理域）
        p.add(new PermDef(MdsPermissions.PRODUCT_VIEW, "产品查看"));
        p.add(new PermDef(MdsPermissions.PRODUCT_CREATE, "产品新建"));
        p.add(new PermDef(MdsPermissions.PRODUCT_UPDATE, "产品编辑"));
        p.add(new PermDef(MdsPermissions.PRODUCT_DELETE, "产品删除"));
        // 制造流程（产品管理域，Route 口径）
        p.add(new PermDef(MdsPermissions.FLOW_VIEW, "制造流程查看"));
        p.add(new PermDef(MdsPermissions.FLOW_CREATE, "制造流程新建/复制"));
        p.add(new PermDef(MdsPermissions.FLOW_UPDATE, "制造流程维护/发布"));
        p.add(new PermDef(MdsPermissions.FLOW_DELETE, "制造流程删除"));
        p.add(new PermDef(MdsPermissions.FLOW_VALIDATE, "流程参数校验（质检单独授权特例）"));
        // ICM 统一指派（ContextChain 三 scope 一张表，三层分开授权）
        p.add(new PermDef(MdsPermissions.OP_ASSIGN_VIEW, "工序默认指派查看"));
        p.add(new PermDef(MdsPermissions.OP_ASSIGN_CREATE, "工序默认指派新增"));
        p.add(new PermDef(MdsPermissions.OP_ASSIGN_UPDATE, "工序默认指派编辑"));
        p.add(new PermDef(MdsPermissions.OP_ASSIGN_DELETE, "工序默认指派删除"));
        p.add(new PermDef(MdsPermissions.FLOW_ASSIGN_VIEW, "流程级指派查看"));
        p.add(new PermDef(MdsPermissions.FLOW_ASSIGN_CREATE, "流程级指派新增"));
        p.add(new PermDef(MdsPermissions.FLOW_ASSIGN_UPDATE, "流程级指派编辑"));
        p.add(new PermDef(MdsPermissions.FLOW_ASSIGN_DELETE, "流程级指派删除"));
        p.add(new PermDef(MdsPermissions.STEP_ASSIGN_VIEW, "节点覆盖指派查看"));
        p.add(new PermDef(MdsPermissions.STEP_ASSIGN_CREATE, "节点覆盖指派新增"));
        p.add(new PermDef(MdsPermissions.STEP_ASSIGN_UPDATE, "节点覆盖指派编辑"));
        p.add(new PermDef(MdsPermissions.STEP_ASSIGN_DELETE, "节点覆盖指派删除"));
        // KV 约束参数（context 三层）
        p.add(new PermDef(MdsPermissions.OP_ATTR_VIEW, "工序 KV 参数查看"));
        p.add(new PermDef(MdsPermissions.OP_ATTR_UPDATE, "工序 KV 参数维护"));
        p.add(new PermDef(MdsPermissions.FLOW_ATTR_VIEW, "流程 KV 参数查看"));
        p.add(new PermDef(MdsPermissions.FLOW_ATTR_UPDATE, "流程 KV 参数维护"));
        p.add(new PermDef(MdsPermissions.STEP_ATTR_VIEW, "节点 KV 参数查看"));
        p.add(new PermDef(MdsPermissions.STEP_ATTR_UPDATE, "节点 KV 参数维护"));
        // 参数定义字典 / 产品绑定
        p.add(new PermDef(MdsPermissions.PARAM_DEF_VIEW, "参数定义查看"));
        p.add(new PermDef(MdsPermissions.PARAM_DEF_CREATE, "参数定义新建"));
        p.add(new PermDef(MdsPermissions.PARAM_DEF_UPDATE, "参数定义编辑"));
        p.add(new PermDef(MdsPermissions.PARAM_DEF_DELETE, "参数定义删除"));
        // EDC
        p.add(new PermDef(MdsPermissions.EDC_SET_VIEW, "EDC 参数集查看"));
        p.add(new PermDef(MdsPermissions.EDC_SET_CREATE, "EDC 参数集新建"));
        p.add(new PermDef(MdsPermissions.EDC_SET_UPDATE, "EDC 参数集编辑"));
        p.add(new PermDef(MdsPermissions.EDC_SET_DELETE, "EDC 参数集删除"));
        p.add(new PermDef(MdsPermissions.EDC_TPL_VIEW, "EDC 报告模板查看"));
        p.add(new PermDef(MdsPermissions.EDC_TPL_CREATE, "EDC 报告模板新建"));
        p.add(new PermDef(MdsPermissions.EDC_TPL_UPDATE, "EDC 报告模板编辑"));
        p.add(new PermDef(MdsPermissions.EDC_TPL_DELETE, "EDC 报告模板删除"));
        p.add(new PermDef(MdsPermissions.EDC_MAP_VIEW, "EDC 设备映射查看"));
        p.add(new PermDef(MdsPermissions.EDC_MAP_CREATE, "EDC 设备映射新建"));
        p.add(new PermDef(MdsPermissions.EDC_MAP_UPDATE, "EDC 设备映射编辑"));
        p.add(new PermDef(MdsPermissions.EDC_MAP_DELETE, "EDC 设备映射删除"));
        p.add(new PermDef(MdsPermissions.EDC_PLAN_VIEW, "EDC 方案查看"));
        p.add(new PermDef(MdsPermissions.EDC_PLAN_CREATE, "EDC 方案新建"));
        p.add(new PermDef(MdsPermissions.EDC_PLAN_UPDATE, "EDC 方案编辑/生命周期"));
        p.add(new PermDef(MdsPermissions.EDC_PLAN_DELETE, "EDC 方案删除"));
        p.add(new PermDef(MdsPermissions.PRODUCT_FLOW_VIEW, "产品绑定查看"));
        p.add(new PermDef(MdsPermissions.PRODUCT_FLOW_CREATE, "产品绑定/换版"));
        p.add(new PermDef(MdsPermissions.PRODUCT_FLOW_UPDATE, "产品覆盖维护"));
        p.add(new PermDef(MdsPermissions.PRODUCT_FLOW_DELETE, "产品解绑"));
        // 工序库（产品管理域，P-T-R-O 四层 O 层）
        p.add(new PermDef(MdsPermissions.OPERATION_VIEW, "工序库查看"));
        p.add(new PermDef(MdsPermissions.OPERATION_CREATE, "工序新建/复制"));
        p.add(new PermDef(MdsPermissions.OPERATION_UPDATE, "工序维护/生命周期"));
        p.add(new PermDef(MdsPermissions.OPERATION_DELETE, "工序删除"));
        // EquipmentClass（设备管理域，v2 取代 EquipmentType）
        p.add(new PermDef(MdsPermissions.EQC_VIEW, "机型模板查看"));
        p.add(new PermDef(MdsPermissions.EQC_CREATE, "机型模板新建"));
        p.add(new PermDef(MdsPermissions.EQC_UPDATE, "机型模板编辑"));
        p.add(new PermDef(MdsPermissions.EQC_DELETE, "机型模板删除"));
        // ToolGroup（设备管理域，ISA-95 Work Center）
        p.add(new PermDef(MdsPermissions.TOOL_GROUP_VIEW, "机台组查看"));
        p.add(new PermDef(MdsPermissions.TOOL_GROUP_CREATE, "机台组新建"));
        p.add(new PermDef(MdsPermissions.TOOL_GROUP_UPDATE, "机台组编辑/成员维护"));
        p.add(new PermDef(MdsPermissions.TOOL_GROUP_DELETE, "机台组删除"));
        // Equipment（设备管理域）
        p.add(new PermDef(MdsPermissions.EQUIPMENT_VIEW, "设备查看"));
        p.add(new PermDef(MdsPermissions.EQUIPMENT_CREATE, "设备新建"));
        p.add(new PermDef(MdsPermissions.EQUIPMENT_UPDATE, "设备编辑"));
        p.add(new PermDef(MdsPermissions.EQUIPMENT_DELETE, "设备删除"));
        // Capability（设备管理域）
        p.add(new PermDef(MdsPermissions.CAPABILITY_VIEW, "设备能力查看"));
        p.add(new PermDef(MdsPermissions.CAPABILITY_CREATE, "设备能力新建"));
        p.add(new PermDef(MdsPermissions.CAPABILITY_UPDATE, "设备能力编辑"));
        p.add(new PermDef(MdsPermissions.CAPABILITY_DELETE, "设备能力删除"));
        // 存储区 Bank（SEMI E88 zone 口径）
        p.add(new PermDef(MdsPermissions.BANK_VIEW, "存储区查看"));
        p.add(new PermDef(MdsPermissions.BANK_CREATE, "存储区新建"));
        p.add(new PermDef(MdsPermissions.BANK_UPDATE, "存储区编辑"));
        p.add(new PermDef(MdsPermissions.BANK_DELETE, "存储区删除"));
        // 载具（SEMI E87 Carrier 口径，V31）
        p.add(new PermDef(MdsPermissions.CARRIER_VIEW, "载具查看"));
        p.add(new PermDef(MdsPermissions.CARRIER_CREATE, "载具新建"));
        p.add(new PermDef(MdsPermissions.CARRIER_UPDATE, "载具编辑"));
        p.add(new PermDef(MdsPermissions.CARRIER_DELETE, "载具删除"));
        // 光罩（V32）
        p.add(new PermDef(MdsPermissions.RETICLE_VIEW, "光罩查看"));
        p.add(new PermDef(MdsPermissions.RETICLE_CREATE, "光罩新建"));
        p.add(new PermDef(MdsPermissions.RETICLE_UPDATE, "光罩编辑"));
        p.add(new PermDef(MdsPermissions.RETICLE_DELETE, "光罩删除"));
        // 检验标准（V33）
        p.add(new PermDef(MdsPermissions.INSP_VIEW, "检验标准查看"));
        p.add(new PermDef(MdsPermissions.INSP_CREATE, "检验标准新建"));
        p.add(new PermDef(MdsPermissions.INSP_UPDATE, "检验标准编辑/检验项维护"));
        p.add(new PermDef(MdsPermissions.INSP_DELETE, "检验标准删除"));
        // 耗材台账（V34）
        p.add(new PermDef(MdsPermissions.CONS_VIEW, "耗材查看"));
        p.add(new PermDef(MdsPermissions.CONS_CREATE, "耗材新建"));
        p.add(new PermDef(MdsPermissions.CONS_UPDATE, "耗材编辑"));
        p.add(new PermDef(MdsPermissions.CONS_DELETE, "耗材删除"));
        // 状态模型（SEMI E30 状态机口径，模型/状态/转换矩阵页面维护）
        p.add(new PermDef(MdsPermissions.STATE_MODEL_VIEW, "状态模型查看"));
        p.add(new PermDef(MdsPermissions.STATE_MODEL_CREATE, "状态模型新建"));
        p.add(new PermDef(MdsPermissions.STATE_MODEL_UPDATE, "状态模型维护"));
        p.add(new PermDef(MdsPermissions.STATE_MODEL_DELETE, "状态模型删除"));
        // 编码规则（配置化 Key Generation，各域 create 守门）
        p.add(new PermDef(MdsPermissions.NAMING_RULE_VIEW, "编码规则查看"));
        p.add(new PermDef(MdsPermissions.NAMING_RULE_CREATE, "编码规则新建"));
        p.add(new PermDef(MdsPermissions.NAMING_RULE_UPDATE, "编码规则维护"));
        p.add(new PermDef(MdsPermissions.NAMING_RULE_DELETE, "编码规则删除"));
        // 原因码（WIP 事件归因主数据 + 快照治理）
        p.add(new PermDef(MdsPermissions.REASON_VIEW, "原因码查看"));
        p.add(new PermDef(MdsPermissions.REASON_CREATE, "原因码新建"));
        p.add(new PermDef(MdsPermissions.REASON_UPDATE, "原因码维护"));
        p.add(new PermDef(MdsPermissions.REASON_DELETE, "原因码删除"));
        // 配方解算（V29）
        p.add(new PermDef(MdsPermissions.RECIPE_RESOLVE, "配方解算执行"));
        // 数据字典（平台域，码源 cim-system PermissionCodes）
        p.add(new PermDef(PermissionCodes.DICT_LIST, "数据字典查看"));
        p.add(new PermDef(PermissionCodes.DICT_SAVE, "数据字典维护"));
        p.add(new PermDef(PermissionCodes.DICT_REMOVE, "数据字典删除"));
        // 菜单管理（平台域：sys_menu 在线维护 + 角色授权联动 + 译文改名）
        p.add(new PermDef(MdsPermissions.SYS_MENU_LIST, "菜单查看"));
        p.add(new PermDef(MdsPermissions.SYS_MENU_SAVE, "菜单维护"));
        p.add(new PermDef(MdsPermissions.SYS_MENU_REMOVE, "菜单删除"));
        p.add(new PermDef(MdsPermissions.SYS_ROLE_LIST, "角色查看"));
        p.add(new PermDef(MdsPermissions.SYS_ROLE_SAVE, "角色维护"));
        p.add(new PermDef(MdsPermissions.SYS_ROLE_REMOVE, "角色删除"));
        p.add(new PermDef(MdsPermissions.SYS_ROLE_GRANT, "角色授权"));
        p.add(new PermDef(MdsPermissions.SYS_PERM_LIST, "权限查看"));
        p.add(new PermDef(MdsPermissions.SYS_PERM_SAVE, "权限维护"));
        p.add(new PermDef(MdsPermissions.SYS_PERM_REMOVE, "权限删除"));
        p.add(new PermDef(MdsPermissions.SYS_I18N_LIST, "译文查看"));
        p.add(new PermDef(MdsPermissions.SYS_I18N_SAVE, "译文维护"));
        p.add(new PermDef(MdsPermissions.SYS_I18N_REMOVE, "译文删除"));
        p.add(new PermDef(MdsPermissions.SYS_I18N_LOCALE, "语言目录维护"));
        // 班组排班（V17）
        p.add(new PermDef(MdsPermissions.SHIFT_VIEW, "班次/班制/排班查看"));
        p.add(new PermDef(MdsPermissions.SHIFT_CREATE, "班次/班制新建"));
        p.add(new PermDef(MdsPermissions.SHIFT_UPDATE, "排班维护/班制铺排"));
        p.add(new PermDef(MdsPermissions.SHIFT_DELETE, "班次/班制删除"));
        p.add(new PermDef(MdsPermissions.SHIFTLOG_VIEW, "交接班记录查看"));
        p.add(new PermDef(MdsPermissions.SHIFTLOG_SAVE, "交接班记录提交/签收"));
        p.add(new PermDef(MdsPermissions.SHIFTSWAP_VIEW, "换班申请查看"));
        p.add(new PermDef(MdsPermissions.SHIFTSWAP_SAVE, "换班申请发起/确认/取消"));
        p.add(new PermDef(MdsPermissions.ORG_VIEW, "组织目录查看（只读，主数据在 IAM）"));
        p.add(new PermDef(MdsPermissions.USER_VIEW, "人员目录查看（只读，主数据在 IAM）"));
        return p;
    }

    // ================================================================ 角色

    /**
     * 角色定义。
     *
     * @param code      角色码
     * @param name      显示名
     * @param superRole 是否超管（平台侧会把超管展开为库内全部权限码）
     * @param perms     显式授予的权限码（超管此处仍给全量，便于授权界面回显）
     */
    public record RoleDef(String code, String name, boolean superRole, List<String> perms) {
    }

    /** MDS 内置角色。 */
    public static List<RoleDef> roles() {
        List<String> allPerms = permissions().stream().map(PermDef::code).toList();
        // 操作员：可维护但不能删（主数据删除的影响面比新建大，默认不给）
        List<String> operator = List.of(
                MdsPermissions.SITE_VIEW, MdsPermissions.SITE_CREATE, MdsPermissions.SITE_UPDATE,
                MdsPermissions.FAB_VIEW, MdsPermissions.FAB_CREATE, MdsPermissions.FAB_UPDATE,
                MdsPermissions.AREA_VIEW, MdsPermissions.AREA_CREATE, MdsPermissions.AREA_UPDATE,
                MdsPermissions.BAY_VIEW, MdsPermissions.BAY_CREATE, MdsPermissions.BAY_UPDATE,
                MdsPermissions.STAGE_VIEW, MdsPermissions.STAGE_CREATE, MdsPermissions.STAGE_UPDATE,
                MdsPermissions.TECH_VIEW, MdsPermissions.TECH_CREATE, MdsPermissions.TECH_UPDATE,
                MdsPermissions.PRODUCT_VIEW, MdsPermissions.PRODUCT_CREATE, MdsPermissions.PRODUCT_UPDATE,
                MdsPermissions.FLOW_VIEW, MdsPermissions.FLOW_CREATE, MdsPermissions.FLOW_UPDATE,
                MdsPermissions.EQC_VIEW, MdsPermissions.EQC_CREATE, MdsPermissions.EQC_UPDATE,
                MdsPermissions.TOOL_GROUP_VIEW, MdsPermissions.TOOL_GROUP_CREATE, MdsPermissions.TOOL_GROUP_UPDATE,
                MdsPermissions.EQUIPMENT_VIEW, MdsPermissions.EQUIPMENT_CREATE, MdsPermissions.EQUIPMENT_UPDATE,
                MdsPermissions.CAPABILITY_VIEW, MdsPermissions.CAPABILITY_CREATE, MdsPermissions.CAPABILITY_UPDATE,
                MdsPermissions.BANK_VIEW, MdsPermissions.BANK_CREATE, MdsPermissions.BANK_UPDATE,
                MdsPermissions.CARRIER_VIEW, MdsPermissions.CARRIER_CREATE, MdsPermissions.CARRIER_UPDATE,
                MdsPermissions.RETICLE_VIEW, MdsPermissions.RETICLE_CREATE, MdsPermissions.RETICLE_UPDATE,
                MdsPermissions.INSP_VIEW, MdsPermissions.INSP_CREATE, MdsPermissions.INSP_UPDATE,
                MdsPermissions.CONS_VIEW, MdsPermissions.CONS_CREATE, MdsPermissions.CONS_UPDATE,
                MdsPermissions.STATE_MODEL_VIEW, MdsPermissions.STATE_MODEL_CREATE, MdsPermissions.STATE_MODEL_UPDATE,
                MdsPermissions.NAMING_RULE_VIEW, MdsPermissions.NAMING_RULE_CREATE, MdsPermissions.NAMING_RULE_UPDATE,
                MdsPermissions.REASON_VIEW, MdsPermissions.REASON_CREATE, MdsPermissions.REASON_UPDATE,
                PermissionCodes.DICT_LIST, PermissionCodes.DICT_SAVE);
        // 只读：各域 view
        List<String> viewer = List.of(
                MdsPermissions.SITE_VIEW, MdsPermissions.FAB_VIEW, MdsPermissions.AREA_VIEW,
                MdsPermissions.BAY_VIEW, MdsPermissions.STAGE_VIEW, MdsPermissions.TECH_VIEW,
                MdsPermissions.PRODUCT_VIEW, MdsPermissions.FLOW_VIEW,
                MdsPermissions.EQC_VIEW, MdsPermissions.TOOL_GROUP_VIEW,
                MdsPermissions.EQUIPMENT_VIEW, MdsPermissions.CAPABILITY_VIEW,
                MdsPermissions.BANK_VIEW,
                MdsPermissions.CARRIER_VIEW, MdsPermissions.RETICLE_VIEW,
                MdsPermissions.INSP_VIEW, MdsPermissions.CONS_VIEW,
                MdsPermissions.STATE_MODEL_VIEW, MdsPermissions.NAMING_RULE_VIEW,
                MdsPermissions.REASON_VIEW,
                PermissionCodes.DICT_LIST);
        List<RoleDef> r = new ArrayList<>();
        r.add(new RoleDef(ROLE_ADMIN, "MDS 管理员", true, allPerms));
        r.add(new RoleDef(ROLE_OPERATOR, "MDS 操作员", false, operator));
        r.add(new RoleDef(ROLE_VIEWER, "MDS 只读", false, viewer));
        return r;
    }

    // ================================================================ 译文

    /**
     * 译文定义（双语成对，便于人工校对时对照）。
     *
     * @param code 键（与菜单 i18n_code / 前端 {@code t(key)} 对齐）
     * @param zh   简体中文（<b>强制兜底，必填</b>）
     * @param en   英文
     */
    public record TextDef(String code, String zh, String en) {
    }

    /** MDS 控制台文案（菜单名 / 分组名 / 按钮动作 / 外壳文案）。 */
    public static List<TextDef> texts() {
        List<TextDef> t = new ArrayList<>();
        // —— 菜单与分组 ——
        t.add(new TextDef("mds.nav.dashboard", "概览", "Overview"));
        t.add(new TextDef("mds.nav.group.site", "场地主数据", "Site master data"));
        t.add(new TextDef("mds.nav.topology", "位置拓扑", "Locations"));
        t.add(new TextDef("mds.nav.group.edc", "数采管理", "Data collection"));
        t.add(new TextDef("mds.nav.edcParamSets", "参数集", "Parameter sets"));
        t.add(new TextDef("mds.nav.edcReportTpls", "报告模板", "Report templates"));
        t.add(new TextDef("mds.nav.edcEquipMaps", "设备接口映射", "Equipment mappings"));
        t.add(new TextDef("mds.nav.edcPlans", "EDC 方案", "EDC plans"));
        t.add(new TextDef("mds.nav.edcParamSets.action.create", "参数集·新建", "Parameter set · create"));
        t.add(new TextDef("mds.nav.edcParamSets.action.import", "参数集·导入", "Parameter set · import"));
        t.add(new TextDef("mds.nav.edcReportTpls.action.create", "报告模板·新建", "Report template · create"));
        t.add(new TextDef("mds.nav.edcReportTpls.action.import", "报告模板·导入", "Report template · import"));
        t.add(new TextDef("mds.nav.edcEquipMaps.action.create", "设备映射·新建", "Equipment mapping · create"));
        t.add(new TextDef("mds.nav.edcEquipMaps.action.import", "设备映射·导入", "Equipment mapping · import"));
        t.add(new TextDef("mds.nav.edcPlans.action.create", "EDC 方案·新建", "EDC plan · create"));
        t.add(new TextDef("mds.nav.edcPlans.action.import", "EDC 方案·导入", "EDC plan · import"));
        // —— 按钮动作（授权界面回显用；单菜单五层，显式命名）——
        t.add(new TextDef("mds.nav.topology.action.site.create", "厂区·新建", "Site · create"));
        t.add(new TextDef("mds.nav.topology.action.site.update", "厂区·编辑", "Site · update"));
        t.add(new TextDef("mds.nav.topology.action.site.delete", "厂区·删除", "Site · delete"));
        t.add(new TextDef("mds.nav.topology.action.fab.create", "Fab·新建", "Fab · create"));
        t.add(new TextDef("mds.nav.topology.action.fab.update", "Fab·编辑", "Fab · update"));
        t.add(new TextDef("mds.nav.topology.action.fab.delete", "Fab·删除", "Fab · delete"));
        t.add(new TextDef("mds.nav.topology.action.area.create", "区域·新建", "Area · create"));
        t.add(new TextDef("mds.nav.topology.action.area.update", "区域·编辑", "Area · update"));
        t.add(new TextDef("mds.nav.topology.action.area.delete", "区域·删除", "Area · delete"));
        t.add(new TextDef("mds.nav.topology.action.bay.create", "Bay·新建", "Bay · create"));
        t.add(new TextDef("mds.nav.topology.action.bay.update", "Bay·编辑", "Bay · update"));
        t.add(new TextDef("mds.nav.topology.action.bay.delete", "Bay·删除", "Bay · delete"));
        t.add(new TextDef("mds.nav.topology.action.stage.create", "Stage·新建", "Stage · create"));
        t.add(new TextDef("mds.nav.topology.action.stage.update", "Stage·编辑", "Stage · update"));
        t.add(new TextDef("mds.nav.topology.action.stage.delete", "Stage·删除", "Stage · delete"));
        // —— 产品管理 ——
        t.add(new TextDef("mds.nav.group.product", "产品管理", "Products"));
        t.add(new TextDef("mds.nav.group.system", "系统配置", "System settings"));
        t.add(new TextDef("mds.nav.group.shift", "班组排班", "Shift Ops"));
        t.add(new TextDef("mds.nav.shiftDefs", "班次与班制", "Shifts & patterns"));
        t.add(new TextDef("mds.nav.shiftDefs.action.create", "班次·新建", "Shift · create"));
        t.add(new TextDef("mds.nav.shiftDefs.action.save", "班次·维护", "Shift · save"));
        t.add(new TextDef("mds.nav.shiftDefs.action.delete", "班次·删除", "Shift · delete"));
        t.add(new TextDef("mds.nav.shiftDefs.action.control", "班次·卡控", "Shift · control"));
        t.add(new TextDef("mds.nav.shiftSwaps", "换班申请", "Shift swaps"));
        t.add(new TextDef("mds.nav.shiftSwaps.action.apply", "换班·申请", "Swap · apply"));
        t.add(new TextDef("mds.nav.shiftSwaps.action.confirm", "换班·确认", "Swap · confirm"));
        t.add(new TextDef("mds.nav.shiftSwaps.action.cancel", "换班·取消", "Swap · cancel"));
        t.add(new TextDef("mds.nav.schedule", "排班安排", "Schedule"));
        t.add(new TextDef("mds.nav.schedule.action.save", "排班·维护", "Schedule · save"));
        t.add(new TextDef("mds.nav.shiftLogs", "交接班记录", "Shift logs"));
        t.add(new TextDef("mds.nav.shiftLogs.action.save", "交接班·提交", "Shift log · save"));
        t.add(new TextDef("mds.nav.shiftLogs.action.ack", "交接班·签收", "Shift log · ack"));
        t.add(new TextDef("mds.nav.technology", "制程技术", "Technologies"));
        t.add(new TextDef("mds.nav.technology.action.create", "制程技术·新建", "Technology · create"));
        t.add(new TextDef("mds.nav.technology.action.update", "制程技术·编辑", "Technology · update"));
        t.add(new TextDef("mds.nav.technology.action.delete", "制程技术·删除", "Technology · delete"));
        t.add(new TextDef("mds.nav.products", "产品", "Products"));
        t.add(new TextDef("mds.nav.products.action.create", "产品·新建", "Product · create"));
        t.add(new TextDef("mds.nav.products.action.update", "产品·编辑", "Product · update"));
        t.add(new TextDef("mds.nav.products.action.delete", "产品·删除", "Product · delete"));
        t.add(new TextDef("mds.nav.flows", "制造流程", "Flows"));
        t.add(new TextDef("mds.nav.flows.action.create", "制造流程·新建", "Flow · create"));
        t.add(new TextDef("mds.nav.flows.action.save", "制造流程·维护", "Flow · save"));
        t.add(new TextDef("mds.nav.flows.action.publish", "制造流程·发布", "Flow · publish"));
        t.add(new TextDef("mds.nav.flows.action.delete", "制造流程·删除", "Flow · delete"));
        t.add(new TextDef("mds.nav.flowRules", "流程规则", "Flow rules"));
        t.add(new TextDef("mds.nav.recipeResolve", "配方解算", "Recipe resolution"));
        t.add(new TextDef("mds.nav.flowRules.action.create", "流程规则·新建", "Flow rule · create"));
        t.add(new TextDef("mds.nav.flowRules.action.save", "流程规则·维护", "Flow rule · save"));
        t.add(new TextDef("mds.nav.flowRules.action.delete", "流程规则·删除", "Flow rule · delete"));
        t.add(new TextDef("mds.nav.operations", "工序库", "Operations"));
        t.add(new TextDef("mds.nav.operations.action.create", "工序·新建", "Operation · create"));
        t.add(new TextDef("mds.nav.operations.action.update", "工序·编辑", "Operation · update"));
        t.add(new TextDef("mds.nav.operations.action.delete", "工序·删除", "Operation · delete"));
        // —— 设备管理 ——
        t.add(new TextDef("mds.nav.group.equipment", "设备管理", "Equipment"));
        t.add(new TextDef("mds.nav.equipments", "机台台账", "Equipment"));
        t.add(new TextDef("mds.nav.transportEquipments", "搬运设备", "Transport equipment"));
        t.add(new TextDef("mds.nav.equipments.action.create", "机台·新建", "Equipment · create"));
        t.add(new TextDef("mds.nav.equipments.action.update", "机台·编辑", "Equipment · update"));
        t.add(new TextDef("mds.nav.equipments.action.delete", "机台·删除", "Equipment · delete"));
        t.add(new TextDef("mds.nav.equipmentClasses", "机型模板", "Equipment classes"));
        t.add(new TextDef("mds.nav.equipmentClasses.action.create", "机型·新建", "Equipment class · create"));
        t.add(new TextDef("mds.nav.equipmentClasses.action.update", "机型·编辑", "Equipment class · update"));
        t.add(new TextDef("mds.nav.equipmentClasses.action.delete", "机型·删除", "Equipment class · delete"));
        t.add(new TextDef("mds.nav.equipmentClasses.action.derive", "机型·派生机台", "Equipment class · derive equipment"));
        t.add(new TextDef("mds.nav.toolGroups", "机台组", "Tool groups"));
        t.add(new TextDef("mds.nav.toolGroups.action.create", "机台组·新建", "Tool group · create"));
        t.add(new TextDef("mds.nav.toolGroups.action.update", "机台组·编辑", "Tool group · update"));
        t.add(new TextDef("mds.nav.toolGroups.action.delete", "机台组·删除", "Tool group · delete"));
        t.add(new TextDef("mds.nav.capabilities", "设备能力", "Capabilities"));
        t.add(new TextDef("mds.nav.capabilities.action.create", "设备能力·新建", "Capability · create"));
        t.add(new TextDef("mds.nav.capabilities.action.update", "设备能力·编辑", "Capability · update"));
        t.add(new TextDef("mds.nav.capabilities.action.delete", "设备能力·删除", "Capability · delete"));
        t.add(new TextDef("mds.nav.banks", "存储区 Bank", "Stocker banks"));
        t.add(new TextDef("mds.nav.banks.action.create", "存储区·新建", "Stocker bank · create"));
        t.add(new TextDef("mds.nav.banks.action.update", "存储区·编辑", "Stocker bank · update"));
        t.add(new TextDef("mds.nav.banks.action.delete", "存储区·删除", "Stocker bank · delete"));
        t.add(new TextDef("mds.nav.carriers", "载具台账", "Carriers"));
        t.add(new TextDef("mds.nav.carriers.action.create", "载具·新建", "Carrier · create"));
        t.add(new TextDef("mds.nav.carriers.action.update", "载具·编辑", "Carrier · update"));
        t.add(new TextDef("mds.nav.carriers.action.delete", "载具·删除", "Carrier · delete"));
        t.add(new TextDef("mds.nav.reticles", "光罩管理", "Reticles"));
        t.add(new TextDef("mds.nav.reticles.action.create", "光罩·新建", "Reticle · create"));
        t.add(new TextDef("mds.nav.reticles.action.update", "光罩·编辑", "Reticle · update"));
        t.add(new TextDef("mds.nav.reticles.action.delete", "光罩·删除", "Reticle · delete"));
        t.add(new TextDef("mds.nav.group.quality", "质量与耗材", "Quality & consumables"));
        t.add(new TextDef("mds.nav.inspectionStds", "检验标准", "Inspection standards"));
        t.add(new TextDef("mds.nav.inspectionStds.action.create", "检验标准·新建", "Inspection std · create"));
        t.add(new TextDef("mds.nav.inspectionStds.action.update", "检验标准·编辑", "Inspection std · update"));
        t.add(new TextDef("mds.nav.inspectionStds.action.delete", "检验标准·删除", "Inspection std · delete"));
        t.add(new TextDef("mds.nav.consumables", "耗材台账", "Consumables"));
        t.add(new TextDef("mds.nav.consumables.action.create", "耗材·新建", "Consumable · create"));
        t.add(new TextDef("mds.nav.consumables.action.update", "耗材·编辑", "Consumable · update"));
        t.add(new TextDef("mds.nav.consumables.action.delete", "耗材·删除", "Consumable · delete"));
        t.add(new TextDef("mds.nav.stateModels", "状态模型", "State models"));
        t.add(new TextDef("mds.nav.stateModels.action.create", "状态模型·新建", "State model · create"));
        t.add(new TextDef("mds.nav.stateModels.action.save", "状态模型·维护", "State model · save"));
        t.add(new TextDef("mds.nav.stateModels.action.delete", "状态模型·删除", "State model · delete"));
        t.add(new TextDef("mds.nav.namingRules", "编码规则", "Naming rules"));
        t.add(new TextDef("mds.nav.namingRules.action.create", "编码规则·新建", "Naming rule · create"));
        t.add(new TextDef("mds.nav.namingRules.action.save", "编码规则·维护", "Naming rule · save"));
        t.add(new TextDef("mds.nav.namingRules.action.delete", "编码规则·删除", "Naming rule · delete"));
        // —— 原因码 ——
        t.add(new TextDef("mds.nav.reasonCodes", "原因码", "Reason codes"));
        t.add(new TextDef("mds.nav.reasonCodes.action.create", "原因码·新建", "Reason code · create"));
        t.add(new TextDef("mds.nav.reasonCodes.action.save", "原因码·维护", "Reason code · save"));
        t.add(new TextDef("mds.nav.reasonCodes.action.delete", "原因码·删除", "Reason code · delete"));
        t.add(new TextDef("mds.nav.menus", "菜单管理", "Menus"));
        t.add(new TextDef("mds.nav.menus.action.save", "菜单·维护", "Menu · save"));
        t.add(new TextDef("mds.nav.menus.action.delete", "菜单·删除", "Menu · delete"));
        t.add(new TextDef("mds.nav.menus.action.grant", "菜单·授权", "Menu · grant"));
        t.add(new TextDef("mds.nav.permissions", "权限管理", "Permissions"));
        t.add(new TextDef("mds.nav.permissions.action.save", "权限·维护", "Permission · save"));
        t.add(new TextDef("mds.nav.permissions.action.delete", "权限·删除", "Permission · delete"));
        t.add(new TextDef("mds.nav.rolesAdmin", "角色管理", "Roles"));
        t.add(new TextDef("mds.nav.rolesAdmin.action.save", "角色·维护", "Role · save"));
        t.add(new TextDef("mds.nav.rolesAdmin.action.delete", "角色·删除", "Role · delete"));
        t.add(new TextDef("mds.nav.rolesAdmin.action.grant", "角色·授权", "Role · grant"));
        t.add(new TextDef("mds.nav.i18n", "多语言", "Languages & i18n"));
        t.add(new TextDef("mds.nav.i18n.action.save", "多语言·维护", "i18n · save"));
        t.add(new TextDef("mds.nav.i18n.action.delete", "多语言·删除", "i18n · delete"));
        t.add(new TextDef("mds.nav.i18n.action.locale", "多语言·语言目录", "i18n · locales"));
        t.add(new TextDef("mds.nav.orgs", "组织管理", "Organization"));
        t.add(new TextDef("mds.nav.users", "人员管理", "Users"));
        // —— 数据字典 ——
        t.add(new TextDef("mds.nav.dicts", "数据字典", "Data dictionaries"));
        t.add(new TextDef("mds.nav.dicts.action.save", "数据字典·维护", "Dictionary · save"));
        t.add(new TextDef("mds.nav.dicts.action.delete", "数据字典·删除", "Dictionary · delete"));
        // —— 外壳 ——
        t.add(new TextDef("mds.shell.brandSub", "主数据服务", "Master Data Service"));
        t.add(new TextDef("mds.shell.tagline", "定义向下、实绩向上", "Definitions down, actuals up"));
        t.add(new TextDef("mds.shell.envDev", "开发环境", "Development"));
        return t;
    }

    /** 简体中文译文包（key → 中文）。 */
    public static Map<String, String> zhMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (TextDef t : texts()) {
            m.put(t.code(), t.zh());
        }
        return m;
    }

    /** 英文译文包（key → 英文）。 */
    public static Map<String, String> enMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (TextDef t : texts()) {
            m.put(t.code(), t.en());
        }
        return m;
    }
}
