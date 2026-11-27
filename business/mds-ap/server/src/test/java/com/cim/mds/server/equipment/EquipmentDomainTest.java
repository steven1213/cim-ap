package com.cim.mds.server.equipment;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备域集成测试（v2，2026-11-24）：机型模板/机台/能力 CRUD、kind 规则（MATERIAL 绑
 * FAB/AREA/BAY、其余绑 STAGE）、kind/class 从属守门、位置必须真实存在、能力关联全量替换、
 * 删除守门、模板默认值回填（派生语义）、运行控制模式字典校验、机台组同机型入组守门。
 *
 * <p>权限 {@code mds:equipmentClass:* / mds:toolGroup:* / mds:equipment:* / mds:capability:*}，
 * 认证关闭直通；位置引用 V4 演示种子（seed-stage-01 等，测试库同样执行 Flyway）；
 * 控制模式/生产模式走 MdsDictSeedService 种子（测试上下文同样执行）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-equipment-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EquipmentDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    CimEquipmentUnitRepository unitRepository;
    @Autowired
    CimToolGroupMemberRepository toolGroupMemberRepository;

    private static String classId;
    private static String eqpId;
    private static String capId;
    private static String groupId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void createClassEquipmentCapability() throws Exception {
        // 机型模板（TEST 大类 + TESTER 细类）+ 默认约束（派生回填的验证基准）
        String clsJson = create("/api/v1/mds/equipment-classes", Map.of(
                "classCode", "ET-TESTP", "className", "测试机", "kindCode", "TEST",
                "equipClassCode", "TESTER",
                "defaultWaferSizes", "150,200", "defaultBatchMin", 1, "defaultBatchMax", 2,
                "defaultStandardWph", 30));
        classId = om.readTree(clsJson).path("data").path("id").asText();

        // 能力
        String capJson = create("/api/v1/mds/capabilities", Map.of(
                "capabilityCode", "CAP-TESTX", "capabilityName", "测试能力 X"));
        capId = om.readTree(capJson).path("data").path("id").asText();

        // 机台（TEST → 必须绑 STAGE；位置用 V4 种子的 seed-stage-01）——
        // 约束字段不传，验证从模板默认值回填（派生语义）
        String eqpJson = create("/api/v1/mds/equipments", Map.of(
                "equipmentCode", "EQP-TEST-01", "equipmentName", "测试机 #1",
                "classCode", "ET-TESTP", "locationType", "STAGE", "locationId", "seed-stage-01",
                "ownerUser", "pe.demo", "controlMode", "ONLINE_LOCAL"));
        eqpId = om.readTree(eqpJson).path("data").path("id").asText();
        assertThat(eqpId).isNotBlank();

        // 派生回填断言：片径/批/产能来自模板；production_mode 空默认 PRODUCTION
        MvcResult eqpResult = mvc.perform(get("/api/v1/mds/equipments/" + eqpId))
                .andExpect(status().isOk())
                .andReturn();
        String body = eqpResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(om.readTree(body).path("data").path("waferSizes").asText()).isEqualTo("150,200");
        assertThat(om.readTree(body).path("data").path("batchMax").asInt()).isEqualTo(2);
        assertThat(om.readTree(body).path("data").path("standardWph").asDouble()).isEqualTo(30.0);
        assertThat(om.readTree(body).path("data").path("productionMode").asText()).isEqualTo("PRODUCTION");
        assertThat(om.readTree(body).path("data").path("ownerUser").asText()).isEqualTo("pe.demo");
        assertThat(om.readTree(body).path("data").path("kindCode").asText()).isEqualTo("TEST");

        // 页面视图：process 视图可见，amhs 视图不可见（种子搬运机台在 amhs 视图）
        mvc.perform(get("/api/v1/mds/equipments").param("view", "process").param("keyword", "EQP-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/mds/equipments").param("view", "amhs").param("keyword", "EQP-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // owner 过滤动线
        mvc.perform(get("/api/v1/mds/equipments").param("ownerUser", "pe.demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].equipmentCode").value("EQP-TEST-01"));
    }

    @Test
    @Order(2)
    void kindRulesEnforced() throws Exception {
        // TEST 机型绑 BAY → 拒绝（非搬运设备必须绑 STAGE）
        mvc.perform(post("/api/v1/mds/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-BAD-01", "equipmentName", "错误绑定",
                                "classCode", "ET-TESTP", "locationType", "BAY", "locationId", "seed-bay-01"))))
                .andExpect(status().isBadRequest());

        // 位置不存在 → 404
        mvc.perform(post("/api/v1/mds/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-BAD-02", "equipmentName", "位置不存在",
                                "classCode", "ET-TESTP", "locationType", "STAGE", "locationId", "no-such-stage"))))
                .andExpect(status().isNotFound());

        // 未知 kind → 400
        mvc.perform(post("/api/v1/mds/equipment-classes").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "classCode", "ET-BAD", "className", "坏大类", "kindCode", "VEHICLE"))))
                .andExpect(status().isBadRequest());

        // 细类不从属大类（MATERIAL 大类挂 PROCESS 细类）→ 400
        mvc.perform(post("/api/v1/mds/equipment-classes").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "classCode", "ET-BAD2", "className", "坏组合",
                                "kindCode", "MATERIAL", "equipClassCode", "ETCH"))))
                .andExpect(status().isBadRequest());

        // 控制模式不在字典内 → 400（mds.equip_control_mode 种子已跑）
        mvc.perform(post("/api/v1/mds/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-BAD-04", "equipmentName", "坏控制模式",
                                "classCode", "ET-TESTP", "locationType", "STAGE", "locationId", "seed-stage-01",
                                "controlMode", "WARP_DRIVE"))))
                .andExpect(status().isBadRequest());

        // 细类合法组合（MATERIAL + OHT）→ 通过；且 MATERIAL 机台绑 STAGE → 拒绝
        String mJson = create("/api/v1/mds/equipment-classes", Map.of(
                "classCode", "ET-AGV-T", "className", "测试 AGV", "kindCode", "MATERIAL",
                "equipClassCode", "OHT"));
        String mClassId = om.readTree(mJson).path("data").path("id").asText();
        mvc.perform(post("/api/v1/mds/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-BAD-03", "equipmentName", "搬运绑台位",
                                "classCode", "ET-AGV-T", "locationType", "STAGE", "locationId", "seed-stage-01"))))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/mds/equipment-classes/" + mClassId))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void toolGroupSameClassGuard() throws Exception {
        // 建组绑定 ET-TESTP，成员=同机型机台 → 通过
        String gJson = create("/api/v1/mds/tool-groups", Map.of(
                "groupCode", "TG-TEST", "groupName", "测试机台组", "classCode", "ET-TESTP"));
        groupId = om.readTree(gJson).path("data").path("id").asText();

        mvc.perform(post("/api/v1/mds/tool-groups/" + groupId + "/members/" + eqpId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].equipmentCode").value("EQP-TEST-01"));

        // 异机型机台（种子 ET-ETCH）入组 → 拒绝（同机型守门）
        mvc.perform(post("/api/v1/mds/tool-groups/" + groupId + "/members/seed-eqp-05"))
                .andExpect(status().isBadRequest());

        // 重复入组幂等（不报错不重复）
        mvc.perform(post("/api/v1/mds/tool-groups/" + groupId + "/members/" + eqpId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(1));

        // 移出成员
        mvc.perform(delete("/api/v1/mds/tool-groups/" + groupId + "/members/" + eqpId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(0));
    }

    @Test
    @Order(4)
    void capabilityAssociationReplace() throws Exception {
        // 全量替换：先挂 CAP-TESTX + 种子 CAP-E84，再替换成只剩 CAP-E84
        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of("capabilityCode", "CAP-TESTX"), Map.of("capabilityCode", "CAP-E84")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of("capabilityCode", "CAP-E84")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(get("/api/v1/mds/equipments/" + eqpId + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capabilityCode").value("CAP-E84"));

        // 不存在的能力码 → 404
        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of("capabilityCode", "CAP-NOPE")))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void unitCrudAndGuards() throws Exception {
        // 工艺机台挂子单元（E120：腔=MODULE，端口/对准器=SUBSYSTEM）+ v2 端口属性
        create("/api/v1/mds/equipments/" + eqpId + "/units", Map.of(
                "unitCode", "LP1", "unitName", "测试机端口1", "unitClass", "SUBSYSTEM",
                "unitType", "LOAD_PORT", "portNo", 1, "capacity", 25,
                "carrierCategory", "CASSETTE", "loadPurpose", "BOTH"));
        String pm1Json = create("/api/v1/mds/equipments/" + eqpId + "/units", Map.of(
                "unitCode", "PM1", "unitName", "测试工艺腔1", "unitClass", "MODULE",
                "unitType", "PROCESS_CHAMBER", "contaminationLevel", "2"));
        String pm1Id = om.readTree(pm1Json).path("data").path("id").asText();

        // 设备内重复编码 → 409
        mvc.perform(post("/api/v1/mds/equipments/" + eqpId + "/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "unitCode", "LP1", "unitName", "重复编码",
                                "unitClass", "SUBSYSTEM", "unitType", "LOAD_PORT"))))
                .andExpect(status().isConflict());

        // 非法 unitType / unitClass → 400
        mvc.perform(post("/api/v1/mds/equipments/" + eqpId + "/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "unitCode", "BAD1", "unitName", "坏类型",
                                "unitClass", "MODULE", "unitType", "TURBINE"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/mds/equipments/" + eqpId + "/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "unitCode", "BAD2", "unitName", "坏类",
                                "unitClass", "WIDGET", "unitType", "ROBOT"))))
                .andExpect(status().isBadRequest());

        // 列表 + 更新（unitCode 建后不可改；stateCode 走字典校验，此处不传）
        mvc.perform(get("/api/v1/mds/equipments/" + eqpId + "/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/units/" + pm1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "unitName", "测试工艺腔1-改", "unitClass", "MODULE",
                                "unitType", "PROCESS_CHAMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitName").value("测试工艺腔1-改"));
    }

    @Test
    @Order(6)
    void deleteGuards() throws Exception {
        // 能力被设备关联（CAP-E84 挂在 EQP-TEST-01 与种子设备上）→ 拒删；先解关联
        String capDelUrl = "/api/v1/mds/capabilities/" + capId;
        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of())))
                .andExpect(status().isOk());
        mvc.perform(delete(capDelUrl))
                .andExpect(status().isOk());

        // 机型被机台引用 → 拒删
        mvc.perform(delete("/api/v1/mds/equipment-classes/" + classId))
                .andExpect(status().isBadRequest());

        // 机台可删；子单元/组成员随设备物理级联；被候选池 EQUIP 指派引用时拒删
        mvc.perform(post("/api/v1/mds/tool-groups/" + groupId + "/members/" + eqpId))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/mds/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "opCode", "OPE-REF-PROBE", "opName", "引用探针", "opType", "PROCESS"))))
                .andExpect(status().isOk());
        String probe = mvc.perform(post("/api/v1/mds/operations/OPE-REF-PROBE/assigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "assignType", "EQUIP", "assignRef", "EQP-TEST-01",
                                "recipeRule", "FIXED", "recipeRef", "PPID-X", "priority", 100))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String probeAssignId = om.readTree(probe).path("data").path("id").asText();
        mvc.perform(delete("/api/v1/mds/equipments/" + eqpId))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/mds/assigns/" + probeAssignId))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/equipments/" + eqpId))
                .andExpect(status().isOk());
        assertThat(unitRepository.countByEquipmentId(eqpId)).isZero();
        assertThat(toolGroupMemberRepository.count()).isEqualTo(2); // 种子两条成员仍在，本机台的成员已物理删

        // 机台组不引用机型时机型可删：先删组（守门：组绑定 class 时拒删机型）
        mvc.perform(delete("/api/v1/mds/equipment-classes/" + classId))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/mds/tool-groups/" + groupId))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/equipment-classes/" + classId))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private String create(String url, Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
