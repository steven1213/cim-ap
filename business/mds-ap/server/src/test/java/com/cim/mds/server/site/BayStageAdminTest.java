package com.cim.mds.server.site;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.auth.token.TokenClaims;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bay / Stage 集成测试：五层建链、下游引用保护（V1 版 deleteArea 零保护的回归）、
 * 停用父层不可挂新子层、搬迁守门、编辑锁全生命周期（抢锁 / 他人拒绝 / 本人保存清锁）。
 *
 * <p>编辑锁「他人 vs 本人」的区分靠测试线程手动塞 {@link CimUserPrincipal}：
 * 认证关闭时 {@link EditLockSupport#currentUser()} 回落 {@code anonymous}，
 * 令牌态则取 username——MockMvc 同线程执行，thread-local 直接生效。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-bay-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BayStageAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String siteId;
    private static String fabId;
    private static String areaId;
    private static String bayId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void buildChainSiteFabAreaBayStage() throws Exception {
        siteId = create("/api/v1/mds/sites",
                Map.of("siteCode", "CD-SITE", "siteName", "成都园区"));
        fabId = create("/api/v1/mds/fabs",
                Map.of("fabCode", "F1", "siteCode", "CD-SITE", "fabName", "6 英寸一号厂", "amhsType", "OHT"));
        areaId = create("/api/v1/mds/areas",
                Map.of("areaCode", "ETCH", "fabCode", "F1", "areaName", "刻蚀区", "areaType", "Etch"));
        String bayJson = mvc.perform(post("/api/v1/mds/bays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "bayCode", "B01", "fabCode", "F1", "areaCode", "ETCH", "bayName", "B01 湾"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        bayId = om.readTree(bayJson).path("data").path("id").asText();
        // 联表名派生：areaName 来自直接父层
        assertThat(om.readTree(bayJson).path("data").path("areaName").asText()).isEqualTo("刻蚀区");

        create("/api/v1/mds/stages",
                Map.of("stageCode", "S01", "fabCode", "F1", "bayCode", "B01", "stageName", "刻蚀机位 1"));
    }

    @Test
    @Order(2)
    void duplicateBayCodeWithinFabRejected() throws Exception {
        mvc.perform(post("/api/v1/mds/bays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "bayCode", "B01", "fabCode", "F1", "areaCode", "ETCH", "bayName", "重复湾"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2003));
    }

    @Test
    @Order(3)
    void deleteAreaBlockedWhileBayExists() throws Exception {
        // V1 版此处直接删成功（deleteArea 零保护），V2 起被 Bay 挡下
        mvc.perform(delete("/api/v1/mds/areas/" + areaId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @Order(4)
    void reparentAreaWithChildrenRejected() throws Exception {
        create("/api/v1/mds/fabs",
                Map.of("fabCode", "F2", "siteCode", "CD-SITE", "fabName", "6 英寸二号厂"));
        mvc.perform(put("/api/v1/mds/areas/" + areaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "areaCode", "ETCH", "fabCode", "F2", "areaName", "刻蚀区"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @Order(5)
    void createChildUnderDisabledFabRejected() throws Exception {
        // 停用 F2 后仍可改属性，但不可挂新 Area（V1 只查存在不查启用的回归）
        mvc.perform(put("/api/v1/mds/fabs/" + fabIdOf("F2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "fabCode", "F2", "siteCode", "CD-SITE", "fabName", "6 英寸二号厂",
                                "enabled", false))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/mds/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "areaCode", "PHOTO", "fabCode", "F2", "areaName", "光刻区"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @Order(6)
    void lockClaimAndState() throws Exception {
        // 无认证态 → currentUser=anonymous；抢锁成功回显持有人
        mvc.perform(put("/api/v1/mds/locations/area/" + areaId + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimed").value(true))
                .andExpect(jsonPath("$.data.occupiedBy").value("anonymous"));

        mvc.perform(get("/api/v1/mds/locations/area/" + areaId + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occupied").value(true))
                .andExpect(jsonPath("$.data.occupiedBy").value("anonymous"));
    }

    @Test
    @Order(7)
    void updateRejectedWhileLockedByOther() throws Exception {
        asUser("alice");
        mvc.perform(put("/api/v1/mds/areas/" + areaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "areaCode", "ETCH", "fabCode", "F1", "areaName", "别人改不动"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
        // 删除同样被锁挡下
        mvc.perform(delete("/api/v1/mds/areas/" + areaId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @Order(8)
    void holderSavesAndLockCleared() throws Exception {
        // 持有人（anonymous）保存 → 锁与数据同一事务清除
        mvc.perform(put("/api/v1/mds/areas/" + areaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "areaCode", "ETCH", "fabCode", "F1", "areaName", "刻蚀一区"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/mds/locations/area/" + areaId + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occupied").value(false))
                .andExpect(jsonPath("$.data.occupiedBy").isEmpty());

        // 释放别人的锁：按 occupied_by 条件释放，动不了不存在的锁也不报错
        asUser("bob");
        mvc.perform(delete("/api/v1/mds/locations/area/" + areaId + "/lock"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    void deleteChainLeafToRoot() throws Exception {
        asUser(null); // 回到无认证态（anonymous 与持有人一致才可删被锁记录）
        MvcResult stages = mvc.perform(get("/api/v1/mds/stages").param("fabCode", "F1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        String stageId = om.readTree(stages.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path(0).path("id").asText();

        mvc.perform(delete("/api/v1/mds/stages/" + stageId)).andExpect(status().isOk());
        // Bay 下已无 Stage 可删；Area 下仍有 Bay，删除仍被挡
        mvc.perform(delete("/api/v1/mds/bays/" + bayId)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mds/areas/" + areaId)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 帮手

    private String create(String url, Map<String, Object> payload) throws Exception {
        MvcResult result = mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return om.readTree(result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("id").asText();
    }

    private String fabIdOf(String fabCode) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/mds/fabs").param("keyword", fabCode))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path(0).path("id").asText();
    }

    /** 在测试线程塞令牌态主体（MockMvc 同线程，service 的 currentUser() 即返回该 username）。 */
    private static void asUser(String username) {
        if (username == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        TokenClaims claims = new TokenClaims("u-" + username, username,
                Set.of("mds-ap"), Set.of("MDS_OPERATOR"), "default", "jti-" + username,
                Set.of("mds:area:view", "mds:area:update"), 1L, Instant.now().plusSeconds(600), Map.of());
        CimUserPrincipal principal = new CimUserPrincipal(claims, claims.authorities());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
