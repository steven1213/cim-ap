package com.cim.mds.server.inspection;

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
 * 检验标准（InspectionStd）集成测试（V33）：头表 CRUD（全局唯一 409 / 字典校验 400）、
 * 明细全量替换（增/改/删一行提交、标准内编码重复 409）、删除级联清明细。
 *
 * <p>权限 {@code mds:inspection:*}，认证关闭直通。productGroup/samplingType/aqlLevel/
 * defectLevel 传值走字典校验，测试库无字典种子 → 传任何值都 400（顺带验证守门存在）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-insp-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InspectionDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String stdAId;
    private static String stdBId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void stdCrudAndGuards() throws Exception {
        // 与 V33 种子撞码 → 409
        mvc.perform(post("/api/v1/mds/inspection-stds").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "stdCode", "INS-CIS-01", "stdName", "撞码标准"))))
                .andExpect(status().isConflict());

        // samplingType 非法（测试库无字典种子）→ 400
        mvc.perform(post("/api/v1/mds/inspection-stds").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "stdCode", "INS-T0", "stdName", "坏抽样",
                                "samplingType", "NOT_IN_DICT"))))
                .andExpect(status().isBadRequest());

        // 正常创建（不传字典字段）
        stdAId = create(Map.of("stdCode", "INS-T01", "stdName", "测试检验标准", "sampleQty", 20));
        stdBId = create(Map.of("stdCode", "INS-T02", "stdName", "测试检验标准乙"));

        // 更新（stdCode 建后不可改）
        mvc.perform(put("/api/v1/mds/inspection-stds/" + stdAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "stdCode", "INS-HACKED", "stdName", "测试检验标准·改",
                                "sampleQty", 32))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stdCode").value("INS-T01"))
                .andExpect(jsonPath("$.data.stdName").value("测试检验标准·改"))
                .andExpect(jsonPath("$.data.sampleQty").value(32));
    }

    @Test
    @Order(2)
    void itemsReplaceAndGuards() throws Exception {
        // 全量替换：两行明细
        mvc.perform(put("/api/v1/mds/inspection-stds/" + stdAId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(
                                Map.of("itemCode", "CHK-T1", "itemName", "划伤", "spec", "不允许 > 2mm"),
                                Map.of("itemCode", "CHK-T2", "itemName", "颗粒", "spec", "≤ 30 颗/片")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 同标准内编码重复 → 409
        mvc.perform(put("/api/v1/mds/inspection-stds/" + stdAId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(
                                Map.of("itemCode", "CHK-D1", "itemName", "行一"),
                                Map.of("itemCode", "CHK-D1", "itemName", "行二")))))
                .andExpect(status().isConflict());

        // 缺 itemCode → 400
        mvc.perform(put("/api/v1/mds/inspection-stds/" + stdAId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(
                                Map.of("itemName", "无编码行")))))
                .andExpect(status().isBadRequest());

        // 重新全量替换：单行（验证先删后插 + 明细数回显）
        mvc.perform(put("/api/v1/mds/inspection-stds/" + stdAId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(
                                Map.of("itemCode", "CHK-T9", "itemName", "唯一行")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/mds/inspection-stds/" + stdAId + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itemCode").value("CHK-T9"));

        // B 标准明细不影响 A（按 std_id 隔离）
        mvc.perform(put("/api/v1/mds/inspection-stds/" + stdBId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(
                                Map.of("itemCode", "CHK-T9", "itemName", "B 标准同码行")))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void deleteCascadesItems() throws Exception {
        // 删除 A 标准 → 级联清明细（明细端点 404）
        mvc.perform(delete("/api/v1/mds/inspection-stds/" + stdAId))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/mds/inspection-stds/" + stdAId + "/items"))
                .andExpect(status().isNotFound());

        // 删除 B 标准
        mvc.perform(delete("/api/v1/mds/inspection-stds/" + stdBId))
                .andExpect(status().isOk());
    }

    private String create(Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/inspection-stds")
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
