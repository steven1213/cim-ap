package com.cim.rms.server.recipe;

import com.cim.spring.support.web.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 配方生命周期集成测试（W1 切片）：建档→新版本→激活（单生效约束）→另存为→
 * 通配符查询→删除保护（Req 1/4/5/6/13/14/42）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-recipe-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        // 关闭 cim 认证链后，Spring Boot 缺省安全链会顶上（401/403）→ 一并排除
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecipeLifecycleTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    RecipeService recipeService;

    private static String typeId;
    private static String recipeId;
    private static String v1Id;
    private static String v2Id;

    private static final String BODY_V2 =
            Base64.getEncoder().encodeToString("RECIPE-BODY-v2\nstep=etch\n".getBytes(StandardCharsets.UTF_8));

    @Test
    @Order(1)
    void createRecipeWithInitialDraftVersion() throws Exception {
        MvcResult typeRes = mvc.perform(post("/api/v1/rms/device-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ETF300\",\"name\":\"机型 ETF300\"}"))
                .andExpect(status().isOk())
                .andReturn();
        typeId = om.readTree(typeRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        MvcResult res = mvc.perform(post("/api/v1/rms/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new CreateReq("REC-ETCH-STD", "标准刻蚀配方",
                                typeId, null, true, "W1 测试"))))
                .andExpect(status().isOk())
                .andReturn();
        recipeId = om.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        // 详情应含初始 v1（DRAFT、无 Body）
        MvcResult detail = mvc.perform(get("/api/v1/rms/recipes/" + recipeId))
                .andExpect(status().isOk())
                .andReturn();
        var node = om.readTree(detail.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(node.path("data").path("recipe").path("golden").asBoolean()).isTrue();
        assertThat(node.path("data").path("versions").size()).isEqualTo(1);
        assertThat(node.path("data").path("versions").get(0).path("status").asText()).isEqualTo("DRAFT");
        v1Id = node.path("data").path("versions").get(0).path("id").asText();
    }

    @Test
    @Order(2)
    void newVersionRecordsBodyHashAndLineage() throws Exception {
        String expectedHash = sha256(BODY_V2);
        MvcResult res = mvc.perform(post("/api/v1/rms/recipes/" + recipeId + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new NewVersionReq(BodyFormat.TEXT, BODY_V2,
                                expectedHash, "{\"temp\":350}", "上调温度步进"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains(expectedHash);
        v2Id = om.readTree(body).path("data").path("id").asText();
    }

    @Test
    @Order(3)
    void bodyHashMismatchRejected() throws Exception {
        // 中文文案断言走服务层（响应侧经 i18n，只回业务码——iam 同款口径）
        assertThatThrownBy(() -> recipeService.newVersion(recipeId, BodyFormat.TEXT, BODY_V2,
                "deadbeef", null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BodyHash 不匹配");
        mvc.perform(post("/api/v1/rms/recipes/" + recipeId + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new NewVersionReq(BodyFormat.TEXT, BODY_V2,
                                "deadbeef", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(4)
    void activateThenSingleActiveConstraint() throws Exception {
        // 激活 v2
        mvc.perform(post("/api/v1/rms/recipes/" + recipeId + "/versions/" + v2Id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        // 激活 v1 → v2 自动转 OBSOLETE（同配方至多一个生效，Req 14）
        mvc.perform(post("/api/v1/rms/recipes/" + recipeId + "/versions/" + v1Id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        MvcResult detail = mvc.perform(get("/api/v1/rms/recipes/" + recipeId + "/versions"))
                .andExpect(status().isOk())
                .andReturn();
        var versions = om.readTree(detail.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        long activeCount = 0;
        for (var v : versions) {
            if ("ACTIVE".equals(v.path("status").asText())) {
                activeCount++;
                assertThat(v.path("versionNo").asInt()).isEqualTo(1);
            }
        }
        assertThat(activeCount).isEqualTo(1);
        // 已生效版本不能重复激活（服务层文案断言 + HTTP 层 400/业务码）
        assertThatThrownBy(() -> recipeService.activate(recipeId, v1Id))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅 DRAFT 版本可激活");
        mvc.perform(post("/api/v1/rms/recipes/" + recipeId + "/versions/" + v1Id + "/activate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(5)
    void activeVersionBlocksDelete() throws Exception {
        assertThatThrownBy(() -> recipeService.delete(recipeId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("配方存在生效版本");
        mvc.perform(delete("/api/v1/rms/recipes/" + recipeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(6)
    void copyAsCarriesBodyAndLineage() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/rms/recipes/" + recipeId + "/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new CopyReq("REC-ETCH-COPY", "刻蚀副本"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("REC-ETCH-COPY"))
                .andExpect(jsonPath("$.data.golden").value(false))
                .andReturn();
        String copyId = om.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        // 副本 v1 携带源最新版本内容与血缘
        MvcResult detail = mvc.perform(get("/api/v1/rms/recipes/" + copyId))
                .andExpect(status().isOk())
                .andReturn();
        var v1 = om.readTree(detail.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("versions").get(0);
        assertThat(v1.path("bodyBase64").asText()).isEqualTo(BODY_V2);
        assertThat(v1.path("sourceVersionId").asText()).isNotBlank();
        assertThat(v1.path("changeSummary").asText()).contains("另存为自 REC-ETCH-STD");
    }

    @Test
    @Order(7)
    void wildcardQueryWithEscaping() throws Exception {
        mvc.perform(get("/api/v1/rms/recipes").param("keyword", "*ETCH*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        // ? 单字符通配：REC-ETCH-STD / REC-ETCH-COPY 均为 STD/COPY 结尾——用精确段验证
        mvc.perform(get("/api/v1/rms/recipes").param("keyword", "REC-ETCH-ST?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("REC-ETCH-STD"));
        // LIKE 转义：字面 % 不作通配符
        mvc.perform(get("/api/v1/rms/recipes").param("keyword", "REC%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(8)
    void draftOnlyRecipeDeletable() throws Exception {
        // 副本无 ACTIVE 版本 → 可删；删除后列表与详情不可见
        MvcResult listRes = mvc.perform(get("/api/v1/rms/recipes").param("keyword", "REC-ETCH-COPY"))
                .andExpect(status().isOk())
                .andReturn();
        String copyId = om.readTree(listRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").get(0).path("id").asText();
        mvc.perform(delete("/api/v1/rms/recipes/" + copyId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/rms/recipes").param("keyword", "*COPY*"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(9)
    void updateRecipeMeta() throws Exception {
        mvc.perform(put("/api/v1/rms/recipes/" + recipeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new UpdateReq("标准刻蚀配方 v2", null, null,
                                false, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("标准刻蚀配方 v2"))
                .andExpect(jsonPath("$.data.golden").value(false));
    }

    private static String sha256(String bodyBase64) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Base64.getDecoder().decode(bodyBase64));
        return java.util.HexFormat.of().formatHex(digest);
    }

    record CreateReq(String code, String name, String deviceTypeId, String areaId,
                     Boolean golden, String description) {
    }

    record UpdateReq(String name, String deviceTypeId, String areaId,
                     Boolean golden, String description) {
    }

    record CopyReq(String newCode, String newName) {
    }

    record NewVersionReq(BodyFormat bodyFormat, String bodyBase64, String expectedBodyHash,
                         String paramSnapshot, String changeSummary) {
    }
}
