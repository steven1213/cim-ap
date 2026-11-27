package com.cim.iam.server.directory;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.org.OrgNodeType;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 身份目录种子（启动时幂等写入组织树 + 演示用户）。
 *
 * <p><b>为什么需要</b>：H2 内存库重启即清数据，组织架构 / 用户档案 / 本地凭证全空——
 * 业务侧（MDS 部门下拉、排班选人、数据权限口径）直接无数据可选，每次重启都要手工补录。
 * 种子幂等（已存在即跳过），H2 每次重建、MySQL 只在首次落库，两种形态下重启都安全。</p>
 *
 * <p>种子内容（{@link OrgDef} / {@link UserDef}）：
 * 制造维度组织（厂区→车间→产线→工序→班组，数据权限与批量准入依据）+
 * 行政维度部门（DEPT，业务 ap 部门下拉的真源，identity-directory.md §3.1）+
 * 每部门一名演示用户（本地凭证 + 档案 + 组织归属，多归属演示见 prod01/pe01）。
 * 演示用户统一口令见 {@code cim.iam.directory.seed.user-password}（默认 CimUser#2026，仅创建时生效）。</p>
 *
 * <p><b>顺序</b>：{@code LOWEST_PRECEDENCE + 1}——在首管理员引导（登记接入码）与
 * 控制台种子之后执行：演示用户的 ap 准入依赖接入码已登记。失败仅告警不阻断启动，
 * 管理员可在控制台手工补建。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class DirectorySeedRunner implements ApplicationRunner {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrgNodeService orgNodeService;
    private final LocalCredentialService credentialService;
    private final ProfileService profileService;
    private final AppRegistrationService appRegistrationService;
    private final IamAuthProperties authProperties;
    private final DirectorySeedProperties properties;

    /** 组织节点种子定义（parents 先于 children；parentCode 为 null 即根；leader 可空 = 暂不指定）。 */
    private record OrgDef(String code, String name, OrgNodeType type, String parentCode, int sortNo,
                          String leader) {
    }

    /** 演示用户种子定义（secondaryOrg 非空即多归属演示）。 */
    private record UserDef(String username, String displayName, String employeeNo,
                           String jobTitle, String primaryOrg, String secondaryOrg) {
    }

    /**
     * 组织树种子：制造维度（AREA→WORKSHOP→LINE→PROCESS→TEAM）+ 行政维度（DEPT）。
     *
     * <p>DEPT 编码沿用原 {@code mds.owning_dept} 词表口径（PE/QC/EQP/PROD/MAT/PLAN/ENG），
     * 业务侧历史数据无需迁移；IT 为通用支撑部门。负责人 = 各部门演示用户
     * （Hold 到部门 → 部门负责人处理的链路闭环）。</p>
     */
    private static final List<OrgDef> ORGS = List.of(
            // —— 制造维度：6 英寸厂动线 ——
            new OrgDef("FAB1", "6英寸一号厂", OrgNodeType.AREA, null, 10, null),
            new OrgDef("WS1", "一号车间", OrgNodeType.WORKSHOP, "FAB1", 10, null),
            new OrgDef("L1", "一号产线", OrgNodeType.LINE, "WS1", 10, null),
            new OrgDef("PHOTO", "光刻工序", OrgNodeType.PROCESS, "L1", 10, "pe01"),
            new OrgDef("ETCH", "刻蚀工序", OrgNodeType.PROCESS, "L1", 20, "eqp01"),
            new OrgDef("QCP", "品保工序", OrgNodeType.PROCESS, "L1", 30, "qc01"),
            new OrgDef("T1", "运行一班", OrgNodeType.TEAM, "L1", 40, "prod01"),
            // —— 行政维度：部门真源（业务 ap 部门下拉数据源）——
            new OrgDef("PE", "工艺部（PE）", OrgNodeType.DEPT, null, 10, "pe01"),
            new OrgDef("QC", "质量部（QC）", OrgNodeType.DEPT, null, 20, "qc01"),
            new OrgDef("EQP", "设备部", OrgNodeType.DEPT, null, 30, "eqp01"),
            new OrgDef("PROD", "生产部", OrgNodeType.DEPT, null, 40, "prod01"),
            new OrgDef("MAT", "物料仓储部", OrgNodeType.DEPT, null, 50, "mat01"),
            new OrgDef("PLAN", "计划部", OrgNodeType.DEPT, null, 60, "plan01"),
            new OrgDef("ENG", "工程部", OrgNodeType.DEPT, null, 70, null),
            new OrgDef("IT", "信息技术部", OrgNodeType.DEPT, null, 80, null));

    /** 演示用户：每部门一名；pe01/prod01 多归属（跨线支援演示）。 */
    private static final List<UserDef> USERS = List.of(
            new UserDef("pe01", "张明", "E1001", "工艺工程师", "PE", "PHOTO"),
            new UserDef("qc01", "李娟", "E1002", "品质工程师", "QC", "QCP"),
            new UserDef("eqp01", "王强", "E1003", "设备工程师", "EQP", null),
            new UserDef("prod01", "赵磊", "E1004", "产线班组长", "PROD", "T1"),
            new UserDef("mat01", "孙婷", "E1005", "物料仓管员", "MAT", null),
            new UserDef("plan01", "周洁", "E1006", "生产计划员", "PLAN", null));

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("[iam-directory-seed] 身份目录种子已禁用（cim.iam.directory.seed.enabled=false）");
            return;
        }
        try {
            Map<String, String> orgIds = ensureOrgs();
            ensureUsers(orgIds);
            ensureLeaders();
        } catch (Exception e) {
            log.warn("[iam-directory-seed] 目录种子失败（可在控制台手工补建）：{}", e.getMessage(), e);
        }
    }

    /** 幂等建组织树（已存在即跳过），返回 code → id 映射供用户归属引用。 */
    private Map<String, String> ensureOrgs() {
        Map<String, String> ids = new HashMap<>();
        int created = 0;
        for (OrgDef def : ORGS) {
            var existing = orgNodeService.listAll().stream()
                    .filter(n -> def.code().equals(n.getCode()))
                    .findFirst();
            if (existing.isPresent()) {
                ids.put(def.code(), existing.get().getId());
                continue;
            }
            String parentId = def.parentCode() == null ? null : ids.get(def.parentCode());
            // 建树阶段不带 leader——负责人引用的用户可能尚未建（建树先于建用户）；
            // leader 由 ensureLeaders 在用户就位后统一补齐
            OrgNode n = orgNodeService.createManaged(parentId, def.code(), def.name(), def.type(), def.sortNo(), null);
            ids.put(def.code(), n.getId());
            created++;
        }
        if (created > 0) {
            log.info("[iam-directory-seed] 组织树种子完成：新建 {} 个节点（共 {} 个定义）", created, ORGS.size());
        }
        return ids;
    }

    /** 幂等补负责人（在用户就位后执行；已指定的不覆盖，兼容老库已建组织场景）。 */
    private void ensureLeaders() {
        int set = 0;
        for (OrgDef def : ORGS) {
            if (def.leader() == null || def.leader().isBlank()) {
                continue;
            }
            var node = orgNodeService.listAll().stream()
                    .filter(n -> def.code().equals(n.getCode()))
                    .findFirst()
                    .orElse(null);
            if (node == null || node.getLeaderUserId() != null) {
                continue;
            }
            orgNodeService.update(node.getId(), null, null, null, def.leader());
            set++;
        }
        if (set > 0) {
            log.info("[iam-directory-seed] 组织负责人补齐：{} 个节点（{}）",
                    set, ORGS.stream().filter(d -> d.leader() != null)
                            .map(d -> d.code() + "→" + d.leader())
                            .reduce((a, b) -> a + "、" + b).orElse(""));
        }
    }

    /** 幂等建演示用户：本地凭证 + 档案 + 组织归属 + ap 准入（接入码未登记的跳过并告警）。 */
    private void ensureUsers(Map<String, String> orgIds) {
        String rawPassword = properties.getUserPassword();
        int rounds = authProperties.getPassword().getRounds();
        int created = 0;
        for (UserDef def : USERS) {
            boolean isNew = credentialService.findByUsername(def.username()).isEmpty();
            if (isNew) {
                String clientSalt = randomHex(16);
                String clientHash = PasswordDerivation.derive(rawPassword, clientSalt, rounds);
                credentialService.registerLocalUser(def.username(), clientHash, clientSalt);
                profileService.createManaged(def.username(), def.displayName(), def.employeeNo(),
                        def.username() + "@cim.local", null, def.jobTitle());
                created++;
            }
            ensureOrgMembership(def, orgIds);
            ensureAdmission(def.username());
        }
        if (created > 0) {
            log.info("[iam-directory-seed] 演示用户种子完成：新建 {} 个（共 {} 个定义，统一初始口令见 user-password 配置）",
                    created, USERS.size());
        }
    }

    /** 组织归属幂等：已带主属 + 副属则不动（避免重启反复 bump 令牌强制下线）。 */
    private void ensureOrgMembership(UserDef def, Map<String, String> orgIds) {
        String primaryId = orgIds.get(def.primaryOrg());
        if (primaryId == null) {
            log.warn("[iam-directory-seed] 用户 {} 主属组织 {} 不存在，跳过归属", def.username(), def.primaryOrg());
            return;
        }
        String secondaryId = def.secondaryOrg() == null ? null : orgIds.get(def.secondaryOrg());
        Set<String> owned = profileService.orgIdsOfUser(def.username());
        if (owned.contains(primaryId) && (secondaryId == null || owned.contains(secondaryId))) {
            return;
        }
        List<ProfileService.OrgRef> refs = secondaryId == null
                ? List.of(new ProfileService.OrgRef(primaryId, true))
                : List.of(new ProfileService.OrgRef(primaryId, true), new ProfileService.OrgRef(secondaryId, false));
        profileService.setUserOrgs(def.username(), refs);
        log.info("[iam-directory-seed] 已设置 {} 组织归属：{}（主）{}",
                def.username(), def.primaryOrg(),
                def.secondaryOrg() == null ? "" : " + " + def.secondaryOrg() + "（副）");
    }

    /** ap 准入幂等：对 bootstrap 登记的全部接入码授予空角色准入（角色由管理员在控制台按需授予）。 */
    private void ensureAdmission(String username) {
        IamAuthProperties.Bootstrap bootstrap = authProperties.getBootstrap();
        if (bootstrap == null || bootstrap.getApps() == null) {
            return;
        }
        for (IamAuthProperties.BootstrapApp app : bootstrap.getApps()) {
            if (app.getCode() == null || app.getCode().isBlank()) {
                continue;
            }
            try {
                boolean admitted = appRegistrationService.listAssignmentsForUser(username).stream()
                        .anyMatch(a -> app.getCode().equals(a.getAppCode()));
                if (!admitted) {
                    appRegistrationService.assignUserToApp(username, app.getCode(), Set.of());
                    log.info("[iam-directory-seed] 已授予 {} 准入 {}（无角色，待控制台授予）", username, app.getCode());
                }
            } catch (Exception e) {
                log.warn("[iam-directory-seed] 用户 {} 准入 {} 失败：{}", username, app.getCode(), e.getMessage());
            }
        }
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
