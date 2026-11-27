package com.cim.mds.server.reticle;

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
 * 光罩（Reticle）集成测试（V32）：CRUD（全局唯一 409 / 存放设备存在 404 / 字典校验 400）、
 * 工序 reticleRef 引用闭环（不存在 404、存在可挂、被引用拒删）、删除守门解除后可删。
 *
 * <p>权限 {@code mds:reticle:*}，认证关闭直通；存放设备挂 V9 种子 Stocker
 * （seed-eqp-03）；工序守门走 OperationAdminService.requireReticle（create/update/copy
 * 三处接线）。status 传值走字典校验，测试库无字典种子 → 传任何值都 400。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-reticle-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReticleDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String reticleId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void reticleCrudAndGuards() throws Exception {
        // 与 V32 种子撞码 → 409
        mvc.perform(post("/api/v1/mds/reticles").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "reticleCode", "RT-LT-001", "reticleName", "撞码光罩"))))
                .andExpect(status().isConflict());

        // 存放设备不存在 → 404
        mvc.perform(post("/api/v1/mds/reticles").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "reticleCode", "RT-TX", "reticleName", "坏设备",
                                "homeEquipmentId", "no-such-eqp"))))
                .andExpect(status().isNotFound());

        // status 非法（测试库无字典种子）→ 400
        mvc.perform(post("/api/v1/mds/reticles").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "reticleCode", "RT-T0", "reticleName", "坏状态",
                                "status", "NOT_IN_DICT"))))
                .andExpect(status().isBadRequest());

        // 正常创建
        reticleId = create(Map.of("reticleCode", "RT-T01", "reticleName", "测试光罩",
                "layer", "LT", "homeEquipmentId", "seed-eqp-03",
                "usageCount", 10, "usageLimit", 5000));

        // 更新（reticleCode 建后不可改）
        mvc.perform(put("/api/v1/mds/reticles/" + reticleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "reticleCode", "RT-HACKED", "reticleName", "测试光罩·改",
                                "layer", "VT", "usageCount", 20, "usageLimit", 6000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reticleCode").value("RT-T01"))
                .andExpect(jsonPath("$.data.reticleName").value("测试光罩·改"))
                .andExpect(jsonPath("$.data.usageLimit").value(6000));
    }

    @Test
    @Order(2)
    void operationReticleRefClosure() throws Exception {
        // 工序挂不存在的光罩 → 404（引用闭环写入侧校验）
        mvc.perform(post("/api/v1/mds/operations").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "opCode", "OPE-RTX", "opName", "坏引用工序", "opType", "PROCESS",
                                "reticleRef", "RT-NOPE"))))
                .andExpect(status().isNotFound());

        // 工序挂存在的光罩 → 200
        String opId = createOp("OPE-RT01", "RT-T01");

        // 光罩被工序引用 → 拒删（2004 → 400）
        mvc.perform(delete("/api/v1/mds/reticles/" + reticleId))
                .andExpect(status().isBadRequest());

        // 种子光罩未引用 → 可删（反向守门只拦真实引用）
        mvc.perform(delete("/api/v1/mds/reticles/"
                + findIdByCode("RT-VT-001")))
                .andExpect(status().isOk());

        // 删工序后守门解除 → 光罩可删
        mvc.perform(delete("/api/v1/mds/operations/" + opId))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/reticles/" + reticleId))
                .andExpect(status().isOk());
    }

    private String create(Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/reticles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = om.readTree(json).path("data").path("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }

    private String createOp(String opCode, String reticleRef) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "opCode", opCode, "opName", "光罩引用工序",
                                "opType", "PROCESS", "reticleRef", reticleRef))))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = om.readTree(json).path("data").path("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }

    private String findIdByCode(String code) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/mds/reticles").param("keyword", code))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = om.readTree(json).path("data").path(0).path("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }
}
