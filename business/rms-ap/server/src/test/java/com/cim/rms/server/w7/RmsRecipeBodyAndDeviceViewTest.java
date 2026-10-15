package com.cim.rms.server.w7;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.integration.DeviceRecipeViewService;
import com.cim.rms.server.integration.ToolRecipeMapService;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionStatus;
import com.cim.rms.server.recipe.body.ParsedRecipe;
import com.cim.rms.server.recipe.body.RecipeBodyParseService;
import com.cim.rms.server.recipe.RecipeValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cim.spring.support.web.BizException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W7 集成测试：Body 解析（JSON_STRUCTURED / TEXT / 失败不阻断）、良构与可执行校验、
 * 受状态约束编辑（仅 DRAFT + 乐观锁）、设备视角聚合（四来源 + 孤儿）、区域树嵌套、腔体 CRUD 与引用守卫。
 *
 * <p>对应 W7 评估报告（Recipe-Model-Evaluation.md）确定的四项实施：
 * 设备视角导航 / Body 解析 + 参数视图 / 腔体维度 / 良构可执行校验。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w7-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsRecipeBodyAndDeviceViewTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    DeviceAdminService deviceAdminService;
    @Autowired
    RecipeService recipeService;
    @Autowired
    RecipeBodyParseService parseService;
    @Autowired
    RecipeValidationService validationService;
    @Autowired
    ToolRecipeMapService mapService;
    @Autowired
    DeviceRecipeViewService viewService;

    private static String typeId;
    private static String rootAreaId;
    private static String subAreaId;
    private static String deviceId;
    private static String chamberA;
    private static String recipeA;
    private static String recipeB;
    private static String versionA1;

    static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static final String JSON_BODY = """
            {
              "meta": {"process": "ETCH", "author": "w7-test"},
              "params": {
                "CHAMBER.TEMP": {"value": "70", "unit": "C", "min": "60", "max": "80"},
                "CHAMBER.PRESSURE": {"value": "2.5", "unit": "Torr"}
              },
              "steps": [
                {"no": 1, "name": "升温", "params": ["CHAMBER.TEMP", "CHAMBER.PRESSURE"]}
              ],
              "subRecipes": [
                {"name": "DEP", "ref": "RCP-CVD-SUB", "stepNo": 1}
              ]
            }""";

    static final String TEXT_BODY = """
            @model = W7-TEST
            [STEP 升温]
            CHAMBER.TEMP = 70 | C
            CHAMBER.PRESSURE = 2.5 | Torr
            """;

    @Test
    @Order(1)
    void setupTypeAreaDeviceChamber() throws Exception {
        typeId = deviceAdminService.createType("T-W7", "刻蚀机型 W7", "W7 测试机型", null, null).id();

        // 区域树：根 + 子区域（嵌套）
        rootAreaId = deviceAdminService.createArea("FAB-W7", "W7 测试厂区", null, 1, null).id();
        subAreaId = deviceAdminService.createArea("ST-W7", "W7 刻蚀子区", rootAreaId, 1, null).id();
        var tree = deviceAdminService.areaTree();
        assertThat(tree).isNotEmpty();
        var root = tree.stream().filter(n -> "FAB-W7".equals(n.code())).findFirst().orElseThrow();
        assertThat(root.children()).extracting(DeviceAdminService.AreaNode::code).containsExactly("ST-W7");

        deviceId = deviceAdminService.createDevice("D-W7", "刻蚀机 W7", typeId, subAreaId, null, null).id();

        // 腔体：HTTP 层建（同时验证端点）
        mvc.perform(post("/api/v1/rms/device-chambers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new ChamberReq(deviceId, "CH-A", "沉积腔 A", 1, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("CH-A"))
                .andExpect(jsonPath("$.data.deviceId").value(deviceId));
        mvc.perform(post("/api/v1/rms/device-chambers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new ChamberReq(deviceId, "CH-B", "沉积腔 B", 2, null))))
                .andExpect(status().isOk());
        chamberA = deviceAdminService.listChambers(deviceId).stream()
                .filter(c -> "CH-A".equals(c.code())).findFirst().orElseThrow().id();
        assertThat(deviceAdminService.listChambers(deviceId)).hasSize(2);
    }

    @Test
    @Order(2)
    void parseJsonStructured() {
        recipeA = recipeService.create("RCP-W7-A", "W7 配方 A", typeId, subAreaId, null,
                false, "W7 解析测试", "JSON_STRUCTURED", b64(JSON_BODY), null).id();
        var versions = recipeService.versionsOf(recipeA);
        assertThat(versions).hasSize(1);
        versionA1 = versions.get(0).getId();
        assertThat(versions.get(0).getParamSnapshot()).isNotBlank();

        ParsedRecipe p = parseService.parseOutcome(com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, b64(JSON_BODY)).parsed();
        assertThat(p).isNotNull();
        assertThat(p.format()).isEqualTo("JSON_STRUCTURED");
        assertThat(p.meta()).containsEntry("process", "ETCH");
        assertThat(p.params()).extracting(ParsedRecipe.Param::key)
                .containsExactlyInAnyOrder("CHAMBER.TEMP", "CHAMBER.PRESSURE");
        assertThat(p.steps()).hasSize(1);
        assertThat(p.steps().get(0).name()).isEqualTo("升温");
        assertThat(p.subRecipes()).hasSize(1);
        assertThat(p.subRecipes().get(0).ref()).isEqualTo("RCP-CVD-SUB");
        // 快照可序列化为扁平键值
        assertThat(parseService.snapshotJson(com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, b64(JSON_BODY)))
                .contains("CHAMBER.TEMP");
    }

    @Test
    @Order(3)
    void parseTextKeyValueAndSteps() {
        RecipeVersion v = recipeService.newVersion(recipeA, com.cim.rms.server.recipe.BodyFormat.TEXT,
                b64(TEXT_BODY), null, null, "TEXT 形态版本");
        ParsedRecipe p = parseService.parseOutcome(com.cim.rms.server.recipe.BodyFormat.TEXT, b64(TEXT_BODY)).parsed();
        assertThat(p).isNotNull();
        assertThat(p.meta()).containsEntry("model", "W7-TEST");
        assertThat(p.params()).extracting(ParsedRecipe.Param::key)
                .contains("CHAMBER.TEMP", "CHAMBER.PRESSURE");
        assertThat(p.params().get(0).unit()).isEqualTo("C");
        assertThat(p.steps()).hasSize(1);
        assertThat(p.steps().get(0).name()).isEqualTo("升温");
        assertThat(p.steps().get(0).params()).contains("CHAMBER.TEMP", "CHAMBER.PRESSURE");
        // 详情端点可解析该版本
        var view = recipeService.parsed(recipeA, v.getId());
        assertThat(view.ok()).isTrue();
    }

    @Test
    @Order(4)
    void parseFailureDoesNotThrow() {
        // 坏 JSON / 坏 TEXT 均返回 error 而不抛异常（解析不阻断业务流）
        var badJson = parseService.parseOutcome(com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, b64("not-a-json{{{"));
        assertThat(badJson.ok()).isFalse();
        assertThat(badJson.error()).isNotBlank();

        // TEXT 宽容：无法识别的行只记 warning，不抛错
        var badText = parseService.parseOutcome(com.cim.rms.server.recipe.BodyFormat.TEXT, b64("garbage-line-without-separator"));
        assertThat(badText.ok()).isTrue();
        assertThat(badText.parsed().params()).isEmpty();
        assertThat(badText.parsed().warnings()).isNotEmpty();
    }

    @Test
    @Order(5)
    void validationWellFormedAndExecutable() throws Exception {
        // 良构 + 无规格越界 → executable
        var ok = validationService.validate(recipeA, versionA1);
        assertThat(ok.wellFormed()).isTrue();
        assertThat(ok.executable()).isTrue();
        assertThat(ok.errors()).isEmpty();
        assertThat(ok.parsed()).isNotNull();

        // 非良构：坏 JSON 版本 → PARSE_FAIL error，executable=false
        RecipeVersion badV = recipeService.newVersion(recipeA, com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED,
                b64("{{{broken"), null, null, "坏 body 版本");
        var bad = validationService.validate(recipeA, badV.getId());
        assertThat(bad.wellFormed()).isFalse();
        assertThat(bad.executable()).isFalse();
        assertThat(bad.errors()).anyMatch(e -> e.startsWith("PARSE_FAIL"));

        // HTTP 端点可达
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/rms/recipes/" + recipeA + "/versions/" + versionA1 + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wellFormed").value(true));
    }

    @Test
    @Order(6)
    void editBodyDraftOnlyWithOptimisticLock() throws Exception {
        // 注意取目标版本（v1）的 hash——versionsOf 按 versionNo 倒序，get(0) 是最新版本
        String oldHash = recipeService.versionsOf(recipeA).stream()
                .filter(v -> versionA1.equals(v.getId())).findFirst().orElseThrow().getBodyHash();

        // 错误旧 hash → 乐观锁拒绝（先比对库中旧值，再覆盖——W7 修复的顺序 bug 回归点）
        mvc.perform(put("/api/v1/rms/recipes/" + recipeA + "/versions/" + versionA1 + "/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new BodyReq("TEXT", b64("CHAMBER.TEMP=99"),
                                "deadbeef", "并发冲突测试"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        // 正确旧 hash + DRAFT → 成功，hash 重算
        RecipeVersion edited = recipeService.updateVersionBody(recipeA, versionA1,
                com.cim.rms.server.recipe.BodyFormat.TEXT, b64("CHAMBER.TEMP=88"), oldHash, "在线编辑");
        assertThat(edited.getBodyHash()).isNotEqualTo(oldHash);

        // 激活后不可再编辑（仅 DRAFT 可改）
        recipeService.activate(recipeA, versionA1);
        assertThatThrownBy(() -> recipeService.updateVersionBody(recipeA, versionA1,
                null, b64("CHAMBER.TEMP=1"), null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅 DRAFT 可改");
        mvc.perform(put("/api/v1/rms/recipes/" + recipeA + "/versions/" + versionA1 + "/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new BodyReq("TEXT", b64("CHAMBER.TEMP=1"), null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(7)
    void deviceRecipeViewAggregatesSourcesAndOrphan() throws Exception {
        // P1 → 配方 A（机型适用 + PPID 绑定 + 腔体）→ 三来源
        var p1 = mapService.register(deviceId, typeId, "P1", recipeA, chamberA);
        assertThat(p1.chamberCode()).isEqualTo("CH-A");
        // P2 → 配方 A（无腔体）
        mapService.register(deviceId, typeId, "P2", recipeA, null);
        // P3 → 配方 B（随后软删配方 B → 机台上残留 PPID 绑定 → 孤儿）
        recipeB = recipeService.create("RCP-W7-B", "W7 配方 B", typeId, subAreaId, null,
                false, null, "TEXT", b64("K=V"), null).id();
        mapService.register(deviceId, typeId, "P3", recipeB, null);
        recipeService.delete(recipeB);

        DeviceRecipeViewService.DeviceRecipeView view = viewService.view(deviceId);
        assertThat(view.deviceId()).isEqualTo(deviceId);
        assertThat(view.deviceTypeName()).isEqualTo("刻蚀机型 W7");
        // 配方 A 一行（P1/P2 映射按配方折叠，行取首个 PPID）+ 孤儿 P3（映射指向已删配方）= 2 行
        assertThat(view.total()).isEqualTo(2);
        assertThat(view.orphan()).isEqualTo(1);
        var rowA = view.rows().stream().filter(r -> "RCP-W7-A".equals(r.code())).findFirst().orElseThrow();
        assertThat(rowA.sources()).contains("TYPE_APPLICABLE", "PPID_MAPPED");
        assertThat(rowA.activeVersionNo()).isEqualTo(1);
        assertThat(rowA.versionStatus()).isEqualTo(RecipeVersionStatus.ACTIVE.name());
        assertThat(rowA.ppidOnTool()).isEqualTo("P1");
        assertThat(rowA.chamberCode()).isEqualTo("CH-A");
        // 孤儿行：配方已删，code 为空
        var rowOrphan = view.rows().stream().filter(r -> r.recipeId() == null).findFirst().orElseThrow();
        assertThat(rowOrphan.ppidOnTool()).isEqualTo("P3");
        assertThat(rowOrphan.sources()).contains(DeviceRecipeViewService.SRC_ON_TOOL);

        // HTTP 端点可达
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/rms/devices/" + deviceId + "/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.orphan").value(1));
    }

    @Test
    @Order(8)
    void recipeToolsReverseLookup() throws Exception {
        // 配方视角反查（W7 体验整合）：recipeA 有 P1（绑 CH-A）+ P2 两条映射
        var usages = mapService.usagesOf(recipeA);
        assertThat(usages).hasSize(2);
        var u1 = usages.stream().filter(u -> "P1".equals(u.ppidOnTool())).findFirst().orElseThrow();
        assertThat(u1.deviceCode()).isEqualTo("D-W7");
        assertThat(u1.chamberCode()).isEqualTo("CH-A");
        var u2 = usages.stream().filter(u -> "P2".equals(u.ppidOnTool())).findFirst().orElseThrow();
        assertThat(u2.chamberCode()).isNull();
        assertThat(usages).allMatch(u -> u.qualificationStatus() == null);

        // 已删配方反查 → DATA_NOT_FOUND（不臆造）
        assertThatThrownBy(() -> mapService.usagesOf(recipeB))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("配方不存在");

        // HTTP 端点可达
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/rms/recipes/" + recipeA + "/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(9)
    void chamberDeleteGuardedByPpidReference() throws Exception {
        // CH-A 仍被 P1 的 PPID 映射引用 → 删除被拒
        assertThatThrownBy(() -> deviceAdminService.deleteChamber(chamberA))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仍被");
        mvc.perform(delete("/api/v1/rms/device-chambers/" + chamberA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        // CH-B 无引用 → 可删
        String chamberB = deviceAdminService.listChambers(deviceId).stream()
                .filter(c -> "CH-B".equals(c.code())).findFirst().orElseThrow().id();
        mvc.perform(delete("/api/v1/rms/device-chambers/" + chamberB))
                .andExpect(status().isOk());
        assertThat(deviceAdminService.listChambers(deviceId)).hasSize(1);
    }

    record ChamberReq(String deviceId, String code, String name, Integer sortNo, String description) {
    }

    record BodyReq(String bodyFormat, String bodyBase64, String expectedBodyHash, String changeSummary) {
    }
}
