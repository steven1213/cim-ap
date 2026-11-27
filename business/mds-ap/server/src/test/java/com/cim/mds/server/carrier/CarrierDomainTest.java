package com.cim.mds.server.carrier;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 载具（Carrier）集成测试（V31）：CRUD（全局唯一 409 / 归属设备存在 404 / 字典校验 400）、
 * 更新（编码建后不可改）、按状态过滤、删除（无下游引用，直删）。
 *
 * <p>权限 {@code mds:carrier:*}，认证关闭直通；归属设备挂 V9 种子 Stocker
 * （seed-eqp-03 一号 / seed-eqp-06 二号）。carrierType/status 传值走字典校验，
 * 测试库无字典种子 → 传任何值都 400（顺带验证守门存在），正常创建不传。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-carrier-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CarrierDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String carrierAId;
    private static String carrierBId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void carrierCrudAndGuards() throws Exception {
        // 与 V31 种子撞码 → 409
        mvc.perform(post("/api/v1/mds/carriers").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "carrierCode", "C-0001", "carrierName", "撞码载具"))))
                .andExpect(status().isConflict());

        // 归属设备不存在 → 404
        mvc.perform(post("/api/v1/mds/carriers").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "carrierCode", "C-TX", "carrierName", "坏设备",
                                "homeEquipmentId", "no-such-eqp"))))
                .andExpect(status().isNotFound());

        // status 非法（测试库无字典种子 → 任何值都不在字典内）→ 400
        mvc.perform(post("/api/v1/mds/carriers").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "carrierCode", "C-T0", "carrierName", "坏状态",
                                "status", "NOT_IN_DICT"))))
                .andExpect(status().isBadRequest());

        // 正常创建（不传字典字段，测试零字典依赖）
        carrierAId = create(Map.of("carrierCode", "C-T01", "carrierName", "测试花篮",
                "homeEquipmentId", "seed-eqp-03", "waferQty", 25));
        carrierBId = create(Map.of("carrierCode", "RC-T01", "carrierName", "测试光罩盒",
                "homeEquipmentId", "seed-eqp-06", "currentLotId", "LOT-T-001"));

        // 更新（carrierCode 建后不可改：请求里带也不生效）
        mvc.perform(put("/api/v1/mds/carriers/" + carrierAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "carrierCode", "C-HACKED", "carrierName", "测试花篮·改",
                                "currentLotId", "LOT-T-002", "waferQty", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carrierCode").value("C-T01"))
                .andExpect(jsonPath("$.data.carrierName").value("测试花篮·改"))
                .andExpect(jsonPath("$.data.currentLotId").value("LOT-T-002"));

        // 按归属设备过滤列表
        mvc.perform(get("/api/v1/mds/carriers").param("equipmentId", "seed-eqp-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].homeEquipmentCode").value("EQP-STK-002"));
    }

    @Test
    @Order(2)
    void carrierDelete() throws Exception {
        // 无下游引用 → 直删
        mvc.perform(delete("/api/v1/mds/carriers/" + carrierBId))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/carriers/" + carrierAId))
                .andExpect(status().isOk());
        // 删除后再查列表，种子 3 条仍在（软删不回显）
        mvc.perform(get("/api/v1/mds/carriers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    private String create(Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/carriers")
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
