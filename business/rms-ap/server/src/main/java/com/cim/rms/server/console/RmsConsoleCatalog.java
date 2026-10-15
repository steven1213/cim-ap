package com.cim.rms.server.console;

import com.cim.rms.server.security.RmsPermissions;
import com.cim.system.menu.MenuType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RMS 控制台目录（菜单树 / 权限码 / 角色 / 译文）——种子的<b>单一事实源</b>。
 *
 * <p>把「控制台长什么样、有哪些页面、要什么权限、各语言怎么显示」集中在此，由
 * {@link RmsConsoleSeedService} 幂等写入本 ap 的库；改菜单/权限/文案只改本文件，<b>不再改前端代码</b>。
 * 与 IAM 控制台（{@code IamConsoleCatalog}）同构，是「配置化控制面」在 RMS 的落地。</p>
 *
 * <p>与 {@code cim-system} 约定一致：菜单节点只存 {@code i18n_code}，<b>不存中文字面量</b>；
 * 文案在 {@code sys_i18n} 里按语言给（i18n_code 命名空间 {@code rms.*}，与 RMS 前端内置包对齐）。</p>
 */
public final class RmsConsoleCatalog {

    private RmsConsoleCatalog() {}

    // ================================================================ 菜单树

    /**
     * 菜单定义。
     *
     * @param key       逻辑键（供父子引用与幂等定位；不落库）
     * @param parentKey 父节点逻辑键（{@code null} 为顶级）
     * @param type      {@code DIR} 目录 / {@code MENU} 菜单 / {@code BUTTON} 按钮
     * @param path      前端路由（{@code DIR}/{@code BUTTON} 为 {@code null}）
     * @param component 前端组件标识（供动态路由映射；{@code DIR}/{@code BUTTON} 可为 {@code null}）
     * @param i18nCode  名称 i18n 键（<b>幂等自然键</b>，全库唯一）
     * @param icon      图标标识（前端图标注册表的键）
     * @param permCode  关联权限码（{@code BUTTON} 必填；{@code MENU} 可空表示仅可见性控制）
     * @param sortNo    同级排序
     */
    public record MenuDef(String key, String parentKey, MenuType type, String path, String component,
                          String i18nCode, String icon, String permCode, int sortNo) {
    }

    /** RMS 控制台菜单树（父先于子；顺序即种子写入顺序）。 */
    public static List<MenuDef> menus() {
        List<MenuDef> m = new ArrayList<>();
        // —— 概览（不绑权限：登录后的落脚页）——
        m.add(menu("overview", null, "/", "Dashboard", "rms.nav.dashboard", "gauge", null, 10));
        // —— 配方管理 ——
        m.add(dir("grp.recipe", "rms.nav.group.recipe", 20));
        m.add(menu("recipes", "grp.recipe", "/recipes", "RecipeLibrary", "rms.nav.recipes",
                "sliders", RmsPermissions.RECIPE_VIEW, 10));
        m.add(menu("specs", "grp.recipe", "/specs", "Spec", "rms.nav.specs",
                "checklist", RmsPermissions.SPEC_VIEW, 20));
        m.add(menu("spec-templates", "grp.recipe", "/spec-templates", "SpecTemplate", "rms.nav.specTemplates",
                "copy", RmsPermissions.SPEC_VIEW, 30));
        m.add(menu("body-templates", "grp.recipe", "/body-templates", "BodyTemplate", "rms.nav.bodyTemplates",
                "file", RmsPermissions.BODY_TEMPLATE_VIEW, 40));
        // —— 设备管理 ——
        m.add(dir("grp.device", "rms.nav.group.device", 30));
        m.add(menu("device-types", "grp.device", "/device-types", "DeviceType", "rms.nav.deviceTypes",
                "apps", RmsPermissions.DEVICE_TYPE_VIEW, 10));
        m.add(menu("device-areas", "grp.device", "/device-areas", "DeviceArea", "rms.nav.deviceAreas",
                "tree", RmsPermissions.DEVICE_AREA_VIEW, 20));
        m.add(menu("devices", "grp.device", "/devices", "Device", "rms.nav.devices",
                "monitor", RmsPermissions.DEVICE_VIEW, 30));
        m.add(menu("ecsv", "grp.device", "/ecsv", "EcSv", "rms.nav.ecsv",
                "activity", RmsPermissions.ECSV_VIEW, 40));
        // —— 管控（Bypass / 资格矩阵 / Golden / 设备授权）——
        m.add(dir("grp.governance", "rms.nav.group.governance", 35));
        m.add(menu("bypasses", "grp.governance", "/bypasses", "Bypass", "rms.nav.bypasses",
                "shield-off", RmsPermissions.BYPASS_VIEW, 10));
        m.add(menu("qualifications", "grp.governance", "/qualifications", "Qualification", "rms.nav.qualifications",
                "badge", RmsPermissions.QUALIFICATION_VIEW, 20));
        m.add(menu("golden", "grp.governance", "/golden", "Golden", "rms.nav.golden",
                "star", RmsPermissions.GOLDEN_VIEW, 30));
        m.add(menu("device-auth", "grp.governance", "/device-auth", "DeviceAuth", "rms.nav.deviceAuth",
                "key", RmsPermissions.DEVICE_AUTH_VIEW, 40));
        m.add(menu("experiments", "grp.governance", "/experiments", "Experiment", "rms.nav.experiments",
                "flask", RmsPermissions.EXPERIMENT_VIEW, 50));
        // —— 高级（签核 / 比对 / 审计 / 集成）——
        m.add(dir("grp.advanced", "rms.nav.group.advanced", 40));
        m.add(menu("signoff", "grp.advanced", "/signoff", "Signoff", "rms.nav.signoff",
                "shield", RmsPermissions.SIGNOFF_VIEW, 10));
        m.add(menu("compare", "grp.advanced", "/compare", "Compare", "rms.nav.compare",
                "refresh", RmsPermissions.COMPARE_VIEW, 20));
        m.add(menu("audit", "grp.advanced", "/audit", "Audit", "rms.nav.audit",
                "inbox", RmsPermissions.AUDIT_VIEW, 30));
        m.add(menu("integration", "grp.advanced", "/integration", "Integration", "rms.nav.integration",
                "plug", RmsPermissions.INTEGRATION_VIEW, 40));
        return m;
    }

    private static MenuDef menu(String key, String parentKey, String path, String component,
                               String i18nCode, String icon, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.MENU, path, component, i18nCode, icon, permCode, sortNo);
    }

    private static MenuDef dir(String key, String i18nCode, int sortNo) {
        return new MenuDef(key, null, MenuType.DIR, null, null, i18nCode, null, null, sortNo);
    }

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
        // 签核流
        p.add(new PermDef(RmsPermissions.SIGNOFF_VIEW, "签核查看"));
        p.add(new PermDef(RmsPermissions.SIGNOFF_APPROVE, "签核审批"));
        // 比对
        p.add(new PermDef(RmsPermissions.COMPARE_VIEW, "比对查看"));
        // 审计
        p.add(new PermDef(RmsPermissions.AUDIT_VIEW, "审计查看"));
        // 参数规格 / 模板
        p.add(new PermDef(RmsPermissions.SPEC_VIEW, "参数规格查看"));
        p.add(new PermDef(RmsPermissions.SPEC_MANAGE, "参数规格维护"));
        // 设备授权
        p.add(new PermDef(RmsPermissions.DEVICE_AUTH_VIEW, "设备授权查看"));
        p.add(new PermDef(RmsPermissions.DEVICE_AUTH_MANAGE, "设备授权维护"));
        // Bypass
        p.add(new PermDef(RmsPermissions.BYPASS_VIEW, "Bypass 查看"));
        p.add(new PermDef(RmsPermissions.BYPASS_MANAGE, "Bypass 维护"));
        // 资格矩阵
        p.add(new PermDef(RmsPermissions.QUALIFICATION_VIEW, "资格矩阵查看"));
        p.add(new PermDef(RmsPermissions.QUALIFICATION_MANAGE, "资格矩阵维护"));
        // Golden 机差
        p.add(new PermDef(RmsPermissions.GOLDEN_VIEW, "Golden 查看"));
        p.add(new PermDef(RmsPermissions.GOLDEN_MANAGE, "Golden 维护"));
        // EC/SV
        p.add(new PermDef(RmsPermissions.ECSV_VIEW, "EC/SV 查看"));
        p.add(new PermDef(RmsPermissions.ECSV_MANAGE, "EC/SV 维护"));
        // Body IP 保护
        p.add(new PermDef(RmsPermissions.BODY_VIEW, "Body 明文查看"));
        p.add(new PermDef(RmsPermissions.BODY_EXPORT, "Body 导出"));
        p.add(new PermDef(RmsPermissions.BODY_TEMPLATE_VIEW, "Body 模板查看"));
        p.add(new PermDef(RmsPermissions.BODY_TEMPLATE_MANAGE, "Body 模板维护"));
        p.add(new PermDef(RmsPermissions.EXPERIMENT_VIEW, "实验通道查看"));
        p.add(new PermDef(RmsPermissions.EXPERIMENT_MANAGE, "实验通道维护"));
        // 报表 / 集成
        p.add(new PermDef(RmsPermissions.REPORT_VIEW, "报表查看"));
        p.add(new PermDef(RmsPermissions.INTEGRATION_VIEW, "集成监控查看"));
        p.add(new PermDef(RmsPermissions.INTEGRATION_OPERATE, "集成操作（解除 Hold 等）"));
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
        // 操作员：查看/新建/编辑/激活/版本 + 签核审批 + 各治理域维护，但不含任何 delete 与 Body 导出
        List<String> operator = List.of(
                RmsPermissions.RECIPE_VIEW, RmsPermissions.RECIPE_CREATE, RmsPermissions.RECIPE_UPDATE,
                RmsPermissions.RECIPE_ACTIVATE, RmsPermissions.RECIPE_VERSION,
                RmsPermissions.DEVICE_TYPE_VIEW, RmsPermissions.DEVICE_TYPE_CREATE, RmsPermissions.DEVICE_TYPE_UPDATE,
                RmsPermissions.DEVICE_AREA_VIEW, RmsPermissions.DEVICE_AREA_CREATE, RmsPermissions.DEVICE_AREA_UPDATE,
                RmsPermissions.DEVICE_VIEW, RmsPermissions.DEVICE_CREATE, RmsPermissions.DEVICE_UPDATE,
                RmsPermissions.SIGNOFF_VIEW, RmsPermissions.SIGNOFF_APPROVE,
                RmsPermissions.COMPARE_VIEW, RmsPermissions.AUDIT_VIEW,
                RmsPermissions.SPEC_VIEW, RmsPermissions.SPEC_MANAGE,
                RmsPermissions.DEVICE_AUTH_VIEW, RmsPermissions.DEVICE_AUTH_MANAGE,
                RmsPermissions.BYPASS_VIEW, RmsPermissions.BYPASS_MANAGE,
                RmsPermissions.QUALIFICATION_VIEW, RmsPermissions.QUALIFICATION_MANAGE,
                RmsPermissions.GOLDEN_VIEW, RmsPermissions.GOLDEN_MANAGE,
                RmsPermissions.ECSV_VIEW, RmsPermissions.ECSV_MANAGE,
                RmsPermissions.BODY_VIEW,
                RmsPermissions.BODY_TEMPLATE_VIEW, RmsPermissions.BODY_TEMPLATE_MANAGE,
                RmsPermissions.EXPERIMENT_VIEW, RmsPermissions.EXPERIMENT_MANAGE,
                RmsPermissions.REPORT_VIEW, RmsPermissions.INTEGRATION_VIEW, RmsPermissions.INTEGRATION_OPERATE);
        // 只读：各域 view + 配方版本 + 签核/比对/审计查看
        List<String> viewer = List.of(
                RmsPermissions.RECIPE_VIEW, RmsPermissions.RECIPE_VERSION,
                RmsPermissions.DEVICE_TYPE_VIEW, RmsPermissions.DEVICE_AREA_VIEW, RmsPermissions.DEVICE_VIEW,
                RmsPermissions.SIGNOFF_VIEW, RmsPermissions.COMPARE_VIEW, RmsPermissions.AUDIT_VIEW,
                RmsPermissions.SPEC_VIEW, RmsPermissions.DEVICE_AUTH_VIEW, RmsPermissions.BYPASS_VIEW,
                RmsPermissions.QUALIFICATION_VIEW, RmsPermissions.GOLDEN_VIEW, RmsPermissions.ECSV_VIEW,
                RmsPermissions.BODY_TEMPLATE_VIEW, RmsPermissions.EXPERIMENT_VIEW,
                RmsPermissions.REPORT_VIEW, RmsPermissions.INTEGRATION_VIEW);
        List<RoleDef> r = new ArrayList<>();
        r.add(new RoleDef(ROLE_ADMIN, "RMS 管理员", true, allPerms));
        r.add(new RoleDef(ROLE_OPERATOR, "RMS 操作员", false, operator));
        r.add(new RoleDef(ROLE_VIEWER, "RMS 只读", false, viewer));
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

    /** RMS 控制台文案（菜单名 / 分组名 / 外壳文案）。 */
    public static List<TextDef> texts() {
        List<TextDef> t = new ArrayList<>();
        // —— 菜单与分组 ——
        t.add(new TextDef("rms.nav.dashboard", "概览", "Overview"));
        t.add(new TextDef("rms.nav.group.recipe", "配方管理", "Recipe"));
        t.add(new TextDef("rms.nav.recipes", "配方库", "Recipes"));
        t.add(new TextDef("rms.nav.specs", "参数规格", "Param Specs"));
        t.add(new TextDef("rms.nav.specTemplates", "规格模板", "Spec Templates"));
        t.add(new TextDef("rms.nav.bodyTemplates", "Body 模板", "Body Templates"));
        t.add(new TextDef("rms.nav.experiments", "实验通道", "Experiments"));
        t.add(new TextDef("rms.nav.group.device", "设备管理", "Devices"));
        t.add(new TextDef("rms.nav.deviceTypes", "设备类型", "Device Types"));
        t.add(new TextDef("rms.nav.deviceAreas", "设备区域", "Device Areas"));
        t.add(new TextDef("rms.nav.devices", "设备台账", "Devices"));
        t.add(new TextDef("rms.nav.ecsv", "EC/SV 规格", "EC/SV Specs"));
        t.add(new TextDef("rms.nav.group.governance", "管控中心", "Governance"));
        t.add(new TextDef("rms.nav.bypasses", "Bypass 豁免", "Bypass"));
        t.add(new TextDef("rms.nav.qualifications", "资格矩阵", "Qualification"));
        t.add(new TextDef("rms.nav.golden", "Golden 机差", "Golden"));
        t.add(new TextDef("rms.nav.deviceAuth", "设备授权", "Device Auth"));
        t.add(new TextDef("rms.nav.group.advanced", "高级", "Advanced"));
        t.add(new TextDef("rms.nav.signoff", "签核流", "Sign-off"));
        t.add(new TextDef("rms.nav.compare", "在线比对", "Compare"));
        t.add(new TextDef("rms.nav.audit", "审计日志", "Audit Log"));
        t.add(new TextDef("rms.nav.integration", "集成监控", "Integration"));
        // —— 签核 / 比对 / 审计 页面（与前端内置包 rms.signoff.* / rms.compare.* / rms.audit.* 对齐）——
        t.add(new TextDef("rms.signoff.title", "签核流", "Sign-off"));
        t.add(new TextDef("rms.signoff.pending", "待审批", "Pending"));
        t.add(new TextDef("rms.signoff.approved", "已通过", "Approved"));
        t.add(new TextDef("rms.signoff.rejected", "已拒绝", "Rejected"));
        t.add(new TextDef("rms.signoff.approve", "通过", "Approve"));
        t.add(new TextDef("rms.signoff.reject", "拒绝", "Reject"));
        t.add(new TextDef("rms.signoff.submit", "发起签核", "Submit"));
        t.add(new TextDef("rms.compare.title", "在线比对", "Compare"));
        t.add(new TextDef("rms.compare.identical", "一致", "Identical"));
        t.add(new TextDef("rms.compare.diff", "存在差异", "Differences"));
        t.add(new TextDef("rms.audit.title", "审计日志", "Audit Log"));
        t.add(new TextDef("rms.audit.type", "动作", "Action"));
        t.add(new TextDef("rms.audit.actor", "操作人", "Operator"));
        t.add(new TextDef("rms.audit.target", "对象", "Target"));
        t.add(new TextDef("rms.audit.detail", "明细", "Detail"));
        // —— 外壳文案（与前端内置包 rms.shell.* 对齐，便于将来后端拉译文无缝切换）——
        t.add(new TextDef("rms.shell.brandSub", "配方管理系统", "Recipe Management System"));
        t.add(new TextDef("rms.shell.tagline", "半导体制造 · 配方生命周期中枢", "Semiconductor · Recipe Lifecycle Hub"));
        t.add(new TextDef("rms.shell.crypto", "统一身份 · 端到端加密", "Unified identity · End-to-end encryption"));
        t.add(new TextDef("rms.shell.adminBadge", "管理员", "ADMIN"));
        t.add(new TextDef("rms.shell.adminHint", "您拥有本系统的管理权限", "You hold administrative privileges"));
        t.add(new TextDef("rms.shell.toggleNav", "折叠/展开导航", "Collapse/expand navigation"));
        t.add(new TextDef("rms.shell.toggleGroup", "折叠/展开分组", "Collapse/expand group"));
        t.add(new TextDef("rms.shell.theme.dark", "切换到深色", "Switch to dark"));
        t.add(new TextDef("rms.shell.theme.light", "切换到浅色", "Switch to light"));
        t.add(new TextDef("rms.shell.logout", "退出登录", "Sign out"));
        t.add(new TextDef("rms.shell.language", "语言", "Language"));
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
