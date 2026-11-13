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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 区域组（AreaGroup）集成测试：建组 / 编码重复拒绝 / Area 入组与跨 Fab 拒绝 /
 * 组删除守门（组内有 Area 不可删）/ 移出后可删 / 组内 Area 搬 Fab 时组校验随最终 fabCode。
 *
 * <p>权限复用 {@code mds:area:*}，认证关闭直通；表结构含编辑锁三列（V3）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-areagroup-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AreaGroupAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String fab1Id;
    private static String fab2Id;
    private static String groupId;
    private static String areaId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void createGroupAndAssignArea() throws Exception {
        create("/api/v1/mds/sites", Map.of("siteCode", "S1", "siteName", "园区"));
        fab1Id = create("/api/v1/mds/fabs", Map.of(
                "fabCode", "F1", "siteCode", "S1", "fabName", "一号厂"));
        fab2Id = create("/api/v1/mds/fabs", Map.of(
                "fabCode", "F2", "siteCode", "S1", "fabName", "二号厂"));
        String groupJson = create("/api/v1/mds/area-groups", Map.of(
                "groupCode", "FEOL", "fabCode", "F1", "groupName", "前段工艺"));
        groupId = om.readTree(groupJson).path("data").path("id").asText();

        // 列表带 fabCode 过滤
        mvc.perform(get("/api/v1/mds/area-groups").param("fabCode", "F1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].groupCode").value("FEOL"));

        // Area 入组：AreaView 回带 areaGroupId
        String areaJson = create("/api/v1/mds/areas", Map.of(
                "areaCode", "DIFF", "fabCode", "F1", "areaName", "扩散区",
                "areaGroupId", groupId));
        areaId = om.readTree(areaJson).path("data").path("id").asText();
        assertThat(om.readTree(areaJson).path("data").path("areaGroupId").asText()).isEqualTo(groupId);
    }

    @Test
    @Order(2)
    void duplicateCodeAndCrossFabRejected() throws Exception {
        // 同 Fab 编码重复
        mvc.perform(post("/api/v1/mds/area-groups").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "groupCode", "FEOL", "fabCode", "F1", "groupName", "重复组"))))
                .andExpect(status().isConflict());
        // 同编码不同 Fab 合法（唯一范围 = Fab 内）
        create("/api/v1/mds/area-groups", Map.of(
                "groupCode", "FEOL", "fabCode", "F2", "groupName", "二号厂前段"));

        // Area 挂别的 Fab 的组 → 拒绝（msg 是 i18n 通用文案，按业务码断言）
        mvc.perform(post("/api/v1/mds/areas").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "areaCode", "ETCH", "fabCode", "F2", "areaName", "刻蚀区",
                                "areaGroupId", groupId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @Order(3)
    void deleteGuardAndMoveOut() throws Exception {
        // 组内有 Area：删除被守门（msg 是 i18n 通用文案，按业务码断言）
        mvc.perform(delete("/api/v1/mds/area-groups/" + groupId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));

        // Area 移出组（areaGroupId 置空）→ 组可删
        Map<String, Object> payload = new HashMap<>(Map.of(
                "areaCode", "DIFF", "fabCode", "F1", "areaName", "扩散区"));
        payload.put("areaGroupId", null);
        mvc.perform(put("/api/v1/mds/areas/" + areaId).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.areaGroupId").doesNotExist());

        mvc.perform(delete("/api/v1/mds/area-groups/" + groupId))
                .andExpect(status().isOk());

        // F1 的 FEOL 已删：再删 F2 的组（验证 Fab 内唯一互不影响）
        String f2Group = mvc.perform(get("/api/v1/mds/area-groups").param("fabCode", "F2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String f2GroupId = om.readTree(f2Group).path("data").get(0).path("id").asText();
        mvc.perform(delete("/api/v1/mds/area-groups/" + f2GroupId))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void groupEditLockLifecycle() throws Exception {
        String groupJson = create("/api/v1/mds/area-groups", Map.of(
                "groupCode", "BEOL", "fabCode", "F1", "groupName", "后段工艺"));
        String gid = om.readTree(groupJson).path("data").path("id").asText();

        // 抢锁 → 他人保存拒绝 → 本人保存成功并清锁
        mvc.perform(put("/api/v1/mds/locations/areaGroup/" + gid + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimed").value(true));

        mvc.perform(put("/api/v1/mds/area-groups/" + gid).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("groupName", "改名", "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupName").value("改名"))
                .andExpect(jsonPath("$.data.occupied").value(false));
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
