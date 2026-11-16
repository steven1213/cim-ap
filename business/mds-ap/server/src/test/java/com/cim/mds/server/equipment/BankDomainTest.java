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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 存储区 Bank 集成测试：Bank CRUD（设备内唯一/字典校验/所属设备存在）、转运规则
 * （from≠to、唯一对 409、对端必须存在、跨 Stocker 允许）、删除守门（被规则引用不可删）。
 *
 * <p>权限 {@code mds:bank:*}，认证关闭直通；Bank 挂 V7/V9 演示种子设备
 * （seed-eqp-03 一号 Stocker / seed-eqp-06 二号 Stocker）。bankType 传值走字典校验，
 * 测试库无字典种子 → 传任何值都 400（顺带验证守门存在），正常创建不传 bankType。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-bank-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String bankAId;
    private static String bankBId;
    private static String bankCId;
    private static String ruleId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void bankCrudAndGuards() throws Exception {
        // 同设备重复编码 → 409（V9 种子已在 seed-eqp-03 上建了 BK-A01）
        mvc.perform(post("/api/v1/mds/stocker-banks").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentId", "seed-eqp-03", "bankCode", "BK-A01",
                                "bankName", "重复编码"))))
                .andExpect(status().isConflict());

        // 所属设备不存在 → 404
        mvc.perform(post("/api/v1/mds/stocker-banks").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentId", "no-such-eqp", "bankCode", "BK-X",
                                "bankName", "坏设备"))))
                .andExpect(status().isNotFound());

        // bankType 非法（测试库无字典种子 → 任何值都不在字典内）→ 400
        mvc.perform(post("/api/v1/mds/stocker-banks").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentId", "seed-eqp-03", "bankCode", "BK-T1",
                                "bankName", "坏类型", "bankType", "NOT_IN_DICT"))))
                .andExpect(status().isBadRequest());

        // 同编码挂到另一台 Stocker → 允许（唯一作用域 = 设备内）
        bankBId = create("/api/v1/mds/stocker-banks", Map.of(
                "equipmentId", "seed-eqp-06", "bankCode", "BK-A01",
                "bankName", "二号库·原料区", "capacity", 60));

        // 正常创建（不传 bankType，测试零字典依赖）
        bankAId = create("/api/v1/mds/stocker-banks", Map.of(
                "equipmentId", "seed-eqp-03", "bankCode", "BK-T01",
                "bankName", "测试存储区", "capacity", 25));
        bankCId = create("/api/v1/mds/stocker-banks", Map.of(
                "equipmentId", "seed-eqp-06", "bankCode", "BK-T02",
                "bankName", "测试存储区乙"));

        // 更新（bankCode 建后不可改：请求里带也不生效）
        mvc.perform(put("/api/v1/mds/stocker-banks/" + bankAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "equipmentId", "seed-eqp-03", "bankCode", "BK-HACKED",
                                "bankName", "测试存储区·改", "capacity", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("测试存储区·改"))
                .andExpect(jsonPath("$.data.capacity").value(30));

        // 按设备过滤列表
        mvc.perform(get("/api/v1/mds/stocker-banks").param("equipmentId", "seed-eqp-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].equipmentCode").value("EQP-STK-002"));
    }

    @Test
    @Order(2)
    void transferRuleCrudAndGuards() throws Exception {
        // from == to → 400
        mvc.perform(post("/api/v1/mds/bank-transfer-rules").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromBankId", bankAId, "toBankId", bankAId))))
                .andExpect(status().isBadRequest());

        // 对端 Bank 不存在 → 404
        mvc.perform(post("/api/v1/mds/bank-transfer-rules").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromBankId", bankAId, "toBankId", "no-such-bank"))))
                .andExpect(status().isNotFound());

        // 正常创建（跨 Stocker：A 在一号库，C 在二号库）
        ruleId = create("/api/v1/mds/bank-transfer-rules", Map.of(
                "fromBankId", bankAId, "toBankId", bankCId, "priority", 50));

        // 同 from→to 重复 → 409
        mvc.perform(post("/api/v1/mds/bank-transfer-rules").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fromBankId", bankAId, "toBankId", bankCId))))
                .andExpect(status().isConflict());

        // 按Bank查相关规则（A 作为 from 命中）
        mvc.perform(get("/api/v1/mds/bank-transfer-rules").param("bankId", bankAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].priority").value(50));

        // 更新（from/to 建后不可改，只改优先级/启用）
        mvc.perform(put("/api/v1/mds/bank-transfer-rules/" + ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("priority", 99, "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value(99))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @Order(3)
    void deleteGuards() throws Exception {
        // Bank 被转运规则引用 → 拒删
        mvc.perform(delete("/api/v1/mds/stocker-banks/" + bankAId))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/mds/stocker-banks/" + bankCId))
                .andExpect(status().isBadRequest());

        // 未被引用的 Bank 可删
        mvc.perform(delete("/api/v1/mds/stocker-banks/" + bankBId))
                .andExpect(status().isOk());

        // 删规则后 Bank 守门解除、可删
        mvc.perform(delete("/api/v1/mds/bank-transfer-rules/" + ruleId))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/stocker-banks/" + bankAId))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/stocker-banks/" + bankCId))
                .andExpect(status().isOk());
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
