package com.cim.mds.server.site;

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
 * 场地主数据集成测试：Site / Fab / Area 的 CRUD、编码唯一、引用保护、软删后重建。
 *
 * <p>响应体中文断言用 {@code getContentAsString(UTF_8)}——MockMvc 缺省 ISO-8859-1
 * 会把中文变乱码，仅 ASCII 断言暴露不了该问题。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-site-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SiteAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String siteId;
    private static String fabId;
    private static String areaId;

    @Test
    @Order(1)
    void createSite() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "siteCode", "CD-SITE", "siteName", "成都园区", "address", "四川省成都市"));
        String json = mvc.perform(post("/api/v1/mds/sites")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.siteCode").value("CD-SITE"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(json).contains("成都园区"); // UTF-8 未破损
        siteId = om.readTree(json).path("data").path("id").asText();
        assertThat(siteId).isNotBlank();
    }

    @Test
    @Order(2)
    void duplicateSiteCodeRejected() throws Exception {
        String body = om.writeValueAsString(Map.of("siteCode", "CD-SITE", "siteName", "重复基地"));
        mvc.perform(post("/api/v1/mds/sites")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2003));
    }

    @Test
    @Order(3)
    void createFabUnderSite() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "fabCode", "F1", "siteCode", "CD-SITE", "fabName", "6 英寸一号厂",
                "fabType", "6寸", "amhsType", "OHT"));
        String json = mvc.perform(post("/api/v1/mds/fabs")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("成都园区"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        fabId = om.readTree(json).path("data").path("id").asText();
    }

    @Test
    @Order(4)
    void fabWithUnknownSiteRejected() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "fabCode", "F9", "siteCode", "NO-SUCH-SITE", "fabName", "悬空 Fab"));
        mvc.perform(post("/api/v1/mds/fabs")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @Order(5)
    void siteDeleteBlockedWhileFabExists() throws Exception {
        mvc.perform(delete("/api/v1/mds/sites/" + siteId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @Order(6)
    void createAndUpdateArea() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "areaCode", "ETCH", "fabCode", "F1", "areaName", "刻蚀区", "areaType", "Etch"));
        String json = mvc.perform(post("/api/v1/mds/areas")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fabName").value("6 英寸一号厂"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        areaId = om.readTree(json).path("data").path("id").asText();

        String patch = om.writeValueAsString(Map.of(
                "areaCode", "ETCH", "fabCode", "F1", "areaName", "刻蚀一区", "areaType", "Etch"));
        mvc.perform(put("/api/v1/mds/areas/" + areaId)
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.areaName").value("刻蚀一区"));
    }

    @Test
    @Order(7)
    void listFiltersByParentAndKeyword() throws Exception {
        mvc.perform(get("/api/v1/mds/areas").param("fabCode", "F1").param("keyword", "刻蚀"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(get("/api/v1/mds/areas").param("fabCode", "NO-SUCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(8)
    void deleteChainThenRecreateSameCode() throws Exception {
        mvc.perform(delete("/api/v1/mds/areas/" + areaId)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/fabs/" + fabId)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/sites/" + siteId)).andExpect(status().isOk());

        // 逻辑删除后同编码可重建：唯一键是 (code, deleted)，不是 code
        String body = om.writeValueAsString(Map.of("siteCode", "CD-SITE", "siteName", "成都园区（重建）"));
        mvc.perform(post("/api/v1/mds/sites")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("成都园区（重建）"));
    }

    @Test
    @Order(9)
    void areaCodeUniqueWithinFabOnly() throws Exception {
        // F1 已删，重建一个 Fab；不同 Fab 可有同名 Area
        mvc.perform(post("/api/v1/mds/fabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                        "fabCode", "F1", "siteCode", "CD-SITE", "fabName", "6 英寸一号厂"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/mds/fabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                        "fabCode", "F2", "siteCode", "CD-SITE", "fabName", "6 英寸二号厂"))))
                .andExpect(status().isOk());

        for (String fab : new String[]{"F1", "F2"}) {
            mvc.perform(post("/api/v1/mds/areas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of(
                                    "areaCode", "PHOTO", "fabCode", fab, "areaName", "光刻区"))))
                    .andExpect(status().isOk());
        }

        // 同一 Fab 内重复 → 冲突
        mvc.perform(post("/api/v1/mds/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "areaCode", "PHOTO", "fabCode", "F1", "areaName", "光刻区重复"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2003));
    }
}
