package com.cim.mds.server.site;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.hamcrest.Matchers;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 产品（Product）集成测试：创建（含 tech 引用守门）/ 全局编码重复拒绝 /
 * 非法 usageType 拒绝 / NPW 细分守门（缺 npwType、非 NPW 带 npwType、NPW 创建成功）/
 * 编辑锁生命周期（抢锁→保存清锁）/ 删除。
 *
 * <p>权限 {@code mds:product:*}，认证关闭直通；锁 type=product（V6 起入白名单）。
 * 注意：Flyway 种子进测试库，断言用独特关键字、不依赖库内总量。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-product-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String productId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private String create(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return om.readTree(json).path("data").path("id").asText();
    }

    @Test
    @Order(10)
    void createWithTechReferenceAndTypeGuard() throws Exception {
        // tech 引用守门：引用不存在的制程技术 → DATA_NOT_FOUND(2001)
        mvc.perform(post("/api/v1/mds/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"PROD-X","productName":"X","techCode":"TECH-NOPE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2001));

        // 非法 usageType → PARAM_INVALID(2000)
        mvc.perform(post("/api/v1/mds/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"PROD-X","productName":"X","usageType":"BOND"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        // NPW 必须带 npwType → PARAM_INVALID(2000)
        mvc.perform(post("/api/v1/mds/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"NPW-X","productName":"缺细分","usageType":"NPW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        // 非 NPW 带了 npwType → PARAM_INVALID(2000)
        mvc.perform(post("/api/v1/mds/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"PROD-Y","productName":"多余细分","usageType":"PRODUCTION","npwType":"DUMMY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        productId = create("""
                {"productCode":"PROD-TEST-01","productName":"集成测试产品",
                 "techCode":"TECH-01","usageType":"PRODUCTION","productGroup":"CIS",
                 "substrateType":"EPI","owner":"tester","enabled":true}
                """);
        assertThat(productId).isNotBlank();

        // 字典值守门：productGroup/substrateType 必须在 mds.product_group / mds.substrate_type 启用项内
        mvc.perform(post("/api/v1/mds/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"PROD-BAD","productName":"坏字典值","productGroup":"NO_SUCH_GROUP"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        mvc.perform(get("/api/v1/mds/products").param("keyword", "PROD-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].techCode").value("TECH-01"))
                .andExpect(jsonPath("$.data[0].usageType").value("PRODUCTION"))
                .andExpect(jsonPath("$.data[0].npwType").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].productGroup").value("CIS"))
                .andExpect(jsonPath("$.data[0].substrateType").value("EPI"));
    }

    @Test
    @Order(15)
    void npwProductRequiresSubtypeAndSaves() throws Exception {
        // NPW + 合法细分 → 创建成功，npwType 落库（V26 定稿口径）
        String npwId = create("""
                {"productCode":"NPW-TEST-01","productName":"控挡片",
                 "usageType":"NPW","npwType":"MONITOR","enabled":true}
                """);
        assertThat(npwId).isNotBlank();
        mvc.perform(get("/api/v1/mds/products").param("keyword", "NPW-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].usageType").value("NPW"))
                .andExpect(jsonPath("$.data[0].npwType").value("MONITOR"));
        mvc.perform(delete("/api/v1/mds/products/" + npwId)).andExpect(status().isOk());
    }

    @Test
    @Order(20)
    void duplicateCodeRejected() throws Exception {
        mvc.perform(post("/api/v1/mds/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"PROD-TEST-01","productName":"重复编码"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2003));
    }

    @Test
    @Order(30)
    void editLockLifecycle() throws Exception {
        // 抢锁
        mvc.perform(put("/api/v1/mds/locations/product/" + productId + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimed").value(true));

        // 抢锁后保存 → 锁清（同事务）
        mvc.perform(put("/api/v1/mds/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"集成测试产品·改","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occupied").value(false))
                .andExpect(jsonPath("$.data.productName").value("集成测试产品·改"));
    }

    @Test
    @Order(40)
    void deleteThenInvisible() throws Exception {
        mvc.perform(delete("/api/v1/mds/products/" + productId))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/mds/products").param("keyword", "PROD-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(50)
    void technologyDeleteGuardedByReference() throws Exception {
        // 自建技术 + 引用它的产品：删除技术被守门；解除引用后可删
        // （不能用种子 TECH-02——种子产品 seed-prod-02 也引用它，守门恒拦）
        MvcResult techRes = mvc.perform(post("/api/v1/mds/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"techCode":"TECH-GUARD","techName":"守门测试技术"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String techId = om.readTree(techRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        assertThat(techId).isNotBlank();

        String refId = create("""
                {"productCode":"PROD-TEST-REF","productName":"引用守门产品",
                 "techCode":"TECH-GUARD","enabled":true}
                """);
        assertThat(refId).isNotBlank();

        // 被引用 → OPERATION_NOT_ALLOWED(2004)
        mvc.perform(delete("/api/v1/mds/technologies/" + techId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));

        // 清理引用产品后可删
        mvc.perform(delete("/api/v1/mds/products/" + refId)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/technologies/" + techId))
                .andExpect(status().isOk());
    }
}
