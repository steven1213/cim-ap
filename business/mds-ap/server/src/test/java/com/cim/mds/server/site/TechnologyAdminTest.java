package com.cim.mds.server.site;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 制程技术（Technology）集成测试：创建 / 全局编码重复拒绝 / 关键字搜索 /
 * 编辑锁生命周期（抢锁→保存清锁）/ 软删后列表不可见。
 *
 * <p>权限 {@code mds:tech:*}，认证关闭直通；锁 type=technology（V5 起入白名单）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-technology-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TechnologyAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String techId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void createAndList() throws Exception {
        String json = create("/api/v1/mds/technologies", Map.of(
                "techCode", "TECH-CMOS", "techName", "CMOS Logic", "techNode", "0.35um"));
        techId = om.readTree(json).path("data").path("id").asText();

        create("/api/v1/mds/technologies", Map.of(
                "techCode", "TECH-BCD", "techName", "BCD Power", "techNode", "0.5um"));

        // 关键字命中（编码模糊；V5 演示种子也在库内，用独特编码避免撞种子数据）
        mvc.perform(get("/api/v1/mds/technologies").param("keyword", "TECH-CMOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].techCode").value("TECH-CMOS"))
                .andExpect(jsonPath("$.data[0].techNode").value("0.35um"));
    }

    @Test
    @Order(2)
    void duplicateCodeRejected() throws Exception {
        // tech_code 全局唯一（无 Fab 维度）
        mvc.perform(post("/api/v1/mds/technologies").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "techCode", "TECH-CMOS", "techName", "重复技术"))))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void editLockLifecycle() throws Exception {
        // 抢锁（type=technology）→ 本人保存成功并清锁
        mvc.perform(put("/api/v1/mds/locations/technology/" + techId + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimed").value(true));

        mvc.perform(put("/api/v1/mds/technologies/" + techId).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "techName", "CMOS Logic 改名", "techNode", "0.35um", "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.techName").value("CMOS Logic 改名"))
                .andExpect(jsonPath("$.data.occupied").value(false));
    }

    @Test
    @Order(4)
    void deleteAndVanish() throws Exception {
        mvc.perform(delete("/api/v1/mds/technologies/" + techId))
                .andExpect(status().isOk());

        // 软删后不可见（种子 TECH-01/02 仍在库内，按独特关键字断言）
        mvc.perform(get("/api/v1/mds/technologies").param("keyword", "TECH-CMOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/mds/technologies").param("keyword", "TECH-BCD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
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
