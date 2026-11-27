package com.cim.mds.server.statemodel;

import com.cim.mds.server.console.MdsDictSeedService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 状态模型集成测试：模型注册（V10 种子 4 模型 + 转换规则）、矩阵勾选开关
 * （建/物理删 + 触发说明）、resolve 形态匹配（精确优先/兜底）、设备与子单元
 * 状态转换守门（非法跨跳拒绝，E30 口径）。
 *
 * <p>权限 {@code mds:stateModel:*}，认证关闭直通；测试库无字典种子（ApplicationRunner
 * 不跑），设备域守门用例前手动执行 {@link MdsDictSeedService#seed()} 补词表。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-statemodel-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StateModelDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    MdsDictSeedService dictSeedService;

    private static String equipmentId;
    private static String unitId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void seedDictsOnce() {
        // 幂等：首例执行时补齐字典词表（后续用例直接复用）
        dictSeedService.seed();
    }

    @Test
    @Order(1)
    void modelsAndTransitionsSeeded() throws Exception {
        // 4 个模型；EQUIPMENT 六态 15 条标准转换
        MvcResult result = mvc.perform(get("/api/v1/mds/state-models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(om.readTree(json).get("data").toString())
                .contains("EQUIPMENT").contains("UNIT_LOAD_PORT")
                .contains("UNIT_CHAMBER").contains("UNIT_GENERIC");

        mvc.perform(get("/api/v1/mds/state-models/EQUIPMENT/transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(15));
        mvc.perform(get("/api/v1/mds/state-models/UNIT_LOAD_PORT/transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));

        // 合法目标：SBY 出发可达 PRD/ENG/SDT/UDT/NST 五态
        mvc.perform(get("/api/v1/mds/state-models/EQUIPMENT/allowed").param("from", "SBY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));

        // resolve：形态精确匹配优先，未命中落兜底模型
        mvc.perform(get("/api/v1/mds/state-models/resolve")
                        .param("objectType", "EQUIPMENT_UNIT").param("unitForm", "LOAD_PORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelCode").value("UNIT_LOAD_PORT"));
        mvc.perform(get("/api/v1/mds/state-models/resolve")
                        .param("objectType", "EQUIPMENT_UNIT").param("unitForm", "ROBOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelCode").value("UNIT_GENERIC"));
        mvc.perform(get("/api/v1/mds/state-models/resolve").param("objectType", "NO_SUCH"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(2)
    void matrixToggleAndGuards() throws Exception {
        // 勾上非法跨跳 PRD→ENG → allowed 立即可达（矩阵开关即时生效）
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromState", "PRD", "toState", "ENG",
                                "allowed", true, "triggerNote", "临时放开（测试）"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.triggerNote").value("临时放开（测试）"));
        mvc.perform(get("/api/v1/mds/state-models/EQUIPMENT/allowed").param("from", "PRD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@ == 'ENG')]").exists());

        // 取消勾选 → 物理删，allowed 不再包含
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromState", "PRD", "toState", "ENG", "allowed", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/mds/state-models/EQUIPMENT/allowed").param("from", "PRD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@ == 'ENG')]").doesNotExist());

        // 守门：from==to / 状态不在模型状态集 / 模型不存在
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromState", "PRD", "toState", "PRD", "allowed", true))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromState", "NOT_A_STATE", "toState", "SBY", "allowed", true))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/mds/state-models/NO_SUCH/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromState", "PRD", "toState", "SBY", "allowed", true))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(3)
    void equipmentTransitionGuard() throws Exception {
        // 建设备（SBY 起步）→ 合法转换 SBY→PRD → 非法跨跳 PRD→ENG 拒绝（OPERATION_NOT_ALLOWED→400）
        equipmentId = create("/api/v1/mds/equipments", Map.of(
                "equipmentCode", "EQP-ST-001", "equipmentName", "状态守门测试机",
                "classCode", "ET-LITHO", "locationType", "STAGE", "locationId", "seed-stage-03",
                "stateCode", "SBY"));

        mvc.perform(put("/api/v1/mds/equipments/" + equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-ST-001", "equipmentName", "状态守门测试机",
                                "classCode", "ET-LITHO", "locationType", "STAGE", "locationId", "seed-stage-03",
                                "stateCode", "PRD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stateCode").value("PRD"));

        mvc.perform(put("/api/v1/mds/equipments/" + equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-ST-001", "equipmentName", "状态守门测试机",
                                "classCode", "ET-LITHO", "locationType", "STAGE", "locationId", "seed-stage-03",
                                "stateCode", "ENG"))))
                .andExpect(status().isBadRequest());

        // 状态不变的其他字段更新不受守门影响
        mvc.perform(put("/api/v1/mds/equipments/" + equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentCode", "EQP-ST-001", "equipmentName", "状态守门测试机·改",
                                "classCode", "ET-LITHO", "locationType", "STAGE", "locationId", "seed-stage-03",
                                "stateCode", "PRD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipmentName").value("状态守门测试机·改"));
    }

    @Test
    @Order(4)
    void unitTransitionGuard() throws Exception {
        // 子单元按形态解析模型：LOAD_PORT → UNIT_LOAD_PORT（TRANSFER_BLOCKED 枢纽）
        unitId = create("/api/v1/mds/equipments/" + equipmentId + "/units", Map.of(
                "unitCode", "LP01", "unitName", "端口 1",
                "unitClass", "SUBSYSTEM", "unitType", "LOAD_PORT",
                "stateCode", "READY_TO_LOAD"));

        // 合法：READY_TO_LOAD → TRANSFER_BLOCKED
        mvc.perform(put("/api/v1/mds/equipments/" + equipmentId + "/units/" + unitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "unitCode", "LP01", "unitName", "端口 1",
                                "unitClass", "SUBSYSTEM", "unitType", "LOAD_PORT",
                                "stateCode", "TRANSFER_BLOCKED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stateCode").value("TRANSFER_BLOCKED"));

        // 非法跨跳：TRANSFER_BLOCKED → IDLE（LOAD_PORT 模型 allowlist 无此转换，
        // IDLE 属 GENERIC/CHAMBER 状态）→ OPERATION_NOT_ALLOWED（400）
        mvc.perform(put("/api/v1/mds/equipments/" + equipmentId + "/units/" + unitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "unitCode", "LP01", "unitName", "端口 1",
                                "unitClass", "SUBSYSTEM", "unitType", "LOAD_PORT",
                                "stateCode", "IDLE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void seedTransitionRulesStillHoldAfterToggleCycle() throws Exception {
        // 回归：矩阵开关往返后种子规则不丢（toggle 只动目标行）
        mvc.perform(get("/api/v1/mds/state-models/UNIT_CHAMBER/transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7));
        mvc.perform(get("/api/v1/mds/state-models/UNIT_GENERIC/transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    @Order(6)
    void modelCrudAndGuards() throws Exception {
        // 新建：状态集须为挂载词表子集（PRD/SBY ∈ mds.equip_state）
        mvc.perform(post("/api/v1/mds/state-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "modelCode", "EQUIPMENT_MINI", "modelName", "两态测试模型",
                                "dictCode", "mds.equip_state", "objectType", "EQUIPMENT",
                                "stateCodes", java.util.List.of("PRD", "SBY")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelCode").value("EQUIPMENT_MINI"));

        // 编码重复 409；状态不在词表 400；状态集为空 400
        mvc.perform(post("/api/v1/mds/state-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "modelCode", "EQUIPMENT_MINI", "modelName", "重复",
                                "dictCode", "mds.equip_state", "objectType", "EQUIPMENT",
                                "stateCodes", java.util.List.of("PRD")))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/mds/state-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "modelCode", "EQUIPMENT_MINI2", "modelName", "脏状态",
                                "dictCode", "mds.equip_state", "objectType", "EQUIPMENT",
                                "stateCodes", java.util.List.of("NOT_IN_DICT")))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/mds/state-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "modelCode", "EQUIPMENT_MINI2", "modelName", "空状态",
                                "dictCode", "mds.equip_state", "objectType", "EQUIPMENT",
                                "stateCodes", java.util.List.of()))))
                .andExpect(status().isBadRequest());

        // 更新：只吃 modelName/unitForm/stateCodes；加状态 PRD/SBY/NST 合法
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT_MINI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "modelName", "两态测试模型·改",
                                "stateCodes", java.util.List.of("PRD", "SBY", "NST")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").value("两态测试模型·改"))
                .andExpect(jsonPath("$.data.states.length()").value(3));

        // 删除守门①：存在转换规则不可删（OPERATION_NOT_ALLOWED→400）
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT_MINI/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromState", "SBY", "toState", "PRD", "allowed", true))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/state-models/EQUIPMENT_MINI"))
                .andExpect(status().isBadRequest());

        // 删除守门②：收缩状态集时被转换引用的状态（SBY）不可移除
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT_MINI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "modelName", "两态测试模型·改",
                                "stateCodes", java.util.List.of("PRD", "NST")))))
                .andExpect(status().isBadRequest());

        // 清掉规则 → 删除成功 → 列表不再包含
        mvc.perform(put("/api/v1/mds/state-models/EQUIPMENT_MINI/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromState", "SBY", "toState", "PRD", "allowed", false))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/state-models/EQUIPMENT_MINI"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/mds/state-models"))
                .andExpect(jsonPath("$.data[?(@.modelCode == 'EQUIPMENT_MINI')]").doesNotExist());
    }

    private String create(String url, Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = om.readTree(json).path("data").path("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }
}
