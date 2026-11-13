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
 * 设备域集成测试：类型/设备/能力 CRUD、kind 规则（MATERIAL 绑 FAB/AREA/BAY、其余绑 STAGE）、
 * kind/class 从属守门、位置必须真实存在、能力关联全量替换、删除守门（被引用的类型/能力不可删）。
 *
 * <p>权限 {@code mds:equipmentType:* / mds:equipment:* / mds:capability:*}，认证关闭直通；
 * 位置引用 V4 演示种子（seed-stage-01 等，测试库同样执行 Flyway）。</p>
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

    private static String typeId;
    private static String eqpId;
    private static String capId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void createTypeEquipmentCapability() throws Exception {
        // 类型（TEST 大类 + PROBER 细类——测试机角色按业务定，此处类型走 TEST）
        String typeJson = create("/api/v1/mds/equipment-types", Map.of(
                "typeCode", "ET-TESTP", "typeName", "测试机", "kindCode", "TEST",
                "equipClassCode", "TESTER"));
        typeId = om.readTree(typeJson).path("data").path("id").asText();

        // 能力
        String capJson = create("/api/v1/mds/capabilities", Map.of(
                "capabilityCode", "CAP-TESTX", "capabilityName", "测试能力 X"));
        capId = om.readTree(capJson).path("data").path("id").asText();

        // 设备（TEST → 必须绑 STAGE；位置用 V4 种子的 seed-stage-01）
        String eqpJson = create("/api/v1/mds/equipments", Map.of(
                "equipmentCode", "EQP-TEST-01", "equipmentName", "测试机 #1",
                "typeCode", "ET-TESTP", "locationType", "STAGE", "locationId", "seed-stage-01"));
        eqpId = om.readTree(eqpJson).path("data").path("id").asText();
        assertThat(eqpId).isNotBlank();

        // 页面视图：process 视图可见，amhs 视图不可见（种子搬运设备在 amhs 视图）
        mvc.perform(get("/api/v1/mds/equipments").param("view", "process").param("keyword", "EQP-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].typeKind").value("TEST"));
        mvc.perform(get("/api/v1/mds/equipments").param("view", "amhs").param("keyword", "EQP-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(2)
    void kindRulesEnforced() throws Exception {
        // TEST 类型绑 BAY → 拒绝（非搬运设备必须绑 STAGE）
        mvc.perform(post("/api/v1/mds/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-BAD-01", "equipmentName", "错误绑定",
                                "typeCode", "ET-TESTP", "locationType", "BAY", "locationId", "seed-bay-01"))))
                .andExpect(status().isBadRequest());

        // 位置不存在 → 404
        mvc.perform(post("/api/v1/mds/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-BAD-02", "equipmentName", "位置不存在",
                                "typeCode", "ET-TESTP", "locationType", "STAGE", "locationId", "no-such-stage"))))
                .andExpect(status().isNotFound());

        // 未知 kind → 400
        mvc.perform(post("/api/v1/mds/equipment-types").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "typeCode", "ET-BAD", "typeName", "坏大类", "kindCode", "VEHICLE"))))
                .andExpect(status().isBadRequest());

        // 细类不从属大类（MATERIAL 大类挂 PROCESS 细类）→ 400
        mvc.perform(post("/api/v1/mds/equipment-types").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "typeCode", "ET-BAD2", "typeName", "坏组合",
                                "kindCode", "MATERIAL", "equipClassCode", "ETCH"))))
                .andExpect(status().isBadRequest());

        // 细类合法组合（MATERIAL + OHT）→ 通过；且 MATERIAL 设备绑 STAGE → 拒绝
        String mJson = create("/api/v1/mds/equipment-types", Map.of(
                "typeCode", "ET-AGV-T", "typeName", "测试 AGV", "kindCode", "MATERIAL",
                "equipClassCode", "OHT"));
        String mTypeId = om.readTree(mJson).path("data").path("id").asText();
        mvc.perform(post("/api/v1/mds/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-BAD-03", "equipmentName", "搬运绑台位",
                                "typeCode", "ET-AGV-T", "locationType", "STAGE", "locationId", "seed-stage-01"))))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/mds/equipment-types/" + mTypeId))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void capabilityAssociationReplace() throws Exception {
        // 全量替换：先挂 CAP-TESTX + 种子 CAP-E84，再替换成只剩 CAP-E84
        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of("CAP-TESTX", "CAP-E84"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of("CAP-E84"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(get("/api/v1/mds/equipments/" + eqpId + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("CAP-E84"));

        // 不存在的能力码 → 404
        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of("CAP-NOPE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void unitCrudAndGuards() throws Exception {
        // 工艺机台挂子单元（E120：腔=MODULE，端口/对准器=SUBSYSTEM）
        create("/api/v1/mds/equipments/" + eqpId + "/units", Map.of(
                "unitCode", "LP1", "unitName", "测试机端口1", "unitClass", "SUBSYSTEM",
                "unitType", "LOAD_PORT", "portNo", 1, "capacity", 25));
        String pm1Json = create("/api/v1/mds/equipments/" + eqpId + "/units", Map.of(
                "unitCode", "PM1", "unitName", "测试工艺腔1", "unitClass", "MODULE",
                "unitType", "PROCESS_CHAMBER"));
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

        // 列表 + 更新（unitCode 建后不可改；stateCode 走字典校验，测试库无字典种子故不传）
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
    @Order(5)
    void deleteGuards() throws Exception {
        // 能力被设备关联（CAP-E84 挂在 EQP-TEST-01 与种子设备上）→ 拒删；先解关联
        String capDelUrl = "/api/v1/mds/capabilities/" + capId;
        mvc.perform(put("/api/v1/mds/equipments/" + eqpId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of())))
                .andExpect(status().isOk());
        // 种子设备 EQP-OHT-01 关联 CAP-E84，所以 seed-cap-02 也不能删；这里只验证自建能力可删
        mvc.perform(delete(capDelUrl))
                .andExpect(status().isOk());

        // 类型被设备引用 → 拒删
        mvc.perform(delete("/api/v1/mds/equipment-types/" + typeId))
                .andExpect(status().isBadRequest());

        // 设备可删；子单元随设备物理级联（仓储直查：物理行清零，软删残留都不允许）
        mvc.perform(delete("/api/v1/mds/equipments/" + eqpId))
                .andExpect(status().isOk());
        assertThat(unitRepository.countByEquipmentId(eqpId)).isZero();

        // 删完类型守门解除、类型可删
        mvc.perform(delete("/api/v1/mds/equipment-types/" + typeId))
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
