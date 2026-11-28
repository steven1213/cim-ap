package com.cim.mds.server.consumable;

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
 * 耗材（Consumable）集成测试（V34）：CRUD（全局唯一 409 / 存放设备存在 404 /
 * 字典校验 400 / warnPercent 范围 400）、更新（编码建后不可改）、按类别过滤、删除。
 *
 * <p>权限 {@code mds:consumable:*}，认证关闭直通；存放设备挂 V9 种子 Stocker
 * （seed-eqp-03）。consType/unit/lifeType 传值走字典校验，测试库无字典种子 →
 * 传任何值都 400（顺带验证守门存在）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-cons-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsumableDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String consAId;
    private static String consBId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void consumableCrudAndGuards() throws Exception {
        // 与 V34 种子撞码 → 409
        mvc.perform(post("/api/v1/mds/consumables").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "consCode", "CM-SL-001", "consName", "撞码耗材"))))
                .andExpect(status().isConflict());

        // 存放设备不存在 → 404
        mvc.perform(post("/api/v1/mds/consumables").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "consCode", "CM-TX", "consName", "坏设备",
                                "homeEquipmentId", "no-such-eqp"))))
                .andExpect(status().isNotFound());

        // consType 非法（测试库无字典种子）→ 400
        mvc.perform(post("/api/v1/mds/consumables").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "consCode", "CM-T0", "consName", "坏类别",
                                "consType", "NOT_IN_DICT"))))
                .andExpect(status().isBadRequest());

        // warnPercent 越界（0 不在 (0,100]）→ 400
        mvc.perform(post("/api/v1/mds/consumables").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "consCode", "CM-TW", "consName", "坏阈值", "warnPercent", 0))))
                .andExpect(status().isBadRequest());

        // 正常创建（不传字典字段）
        consAId = create(Map.of("consCode", "CM-T01", "consName", "测试研磨液",
                "specModel", "SL-TEST", "stockQty", 8.5, "minQty", 4,
                "homeEquipmentId", "seed-eqp-03", "warnPercent", 30));
        consBId = create(Map.of("consCode", "CM-T02", "consName", "测试抛光垫",
                "lifeType", "CYCLE", "lifeLimit", 300, "lifeUsed", 150));

        // 更新（consCode 建后不可改）
        mvc.perform(put("/api/v1/mds/consumables/" + consBId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "consCode", "CM-HACKED", "consName", "测试抛光垫·改",
                                "lifeUsed", 200, "warnPercent", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consCode").value("CM-T02"))
                .andExpect(jsonPath("$.data.consName").value("测试抛光垫·改"))
                .andExpect(jsonPath("$.data.lifeUsed").value(200));

        // 按存放设备过滤列表（种子 CM-SL-001/CM-PD-001 + 测试 CM-T01 同挂一号库）
        mvc.perform(get("/api/v1/mds/consumables").param("equipmentId", "seed-eqp-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].homeEquipmentCode").value("EQP-STK-001"))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @Order(2)
    void consumableDelete() throws Exception {
        // 无下游引用 → 直删
        mvc.perform(delete("/api/v1/mds/consumables/" + consAId))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/consumables/" + consBId))
                .andExpect(status().isOk());
        // 删除后列表剩种子：V34 两条 + ConsBomDemoSeed 的 CM-GA-001（种子 Runner 在测试上下文同样幂等执行）
        mvc.perform(get("/api/v1/mds/consumables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    private String create(Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/consumables")
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
