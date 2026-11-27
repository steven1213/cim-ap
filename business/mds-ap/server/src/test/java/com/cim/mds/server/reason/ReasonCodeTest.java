package com.cim.mds.server.reason;

import com.cim.mds.server.admin.DirectoryClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 原因码（V16）集成测试：类型/码 CRUD 守门 + 树路径维护（挪树重算/防环）
 * + 主数据演进 + 启停与删除守门。
 *
 * <p>认证关闭直通；独立 H2 库名（各测类隔离）。业务码断言按 {@code $.code}
 * （BizCode→HTTP：2000→400、2001→404、2003→409、2004→400）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-reason-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReasonCodeTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    /** 部门目录 mock（V36 部门真源=IAM 组织架构；单测不依赖外部 IAM）。 */
    @MockBean
    DirectoryClient directoryClient;

    @BeforeEach
    void mockDeptDirectory() {
        when(directoryClient.deptOptions()).thenReturn(List.of(
                new DirectoryClient.DeptOption("EQP", "设备部", "/root/eqp/"),
                new DirectoryClient.DeptOption("PE", "工艺部（PE）", "/root/pe/")));
        // requireDeptCode 走真实实现（内部调上面 stub 的 deptOptions）；mock 类默认把该方法也替掉了
        when(directoryClient.requireDeptCode(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
    }

    private static String holdTypeKey;
    private static String rootId;
    private static String childId;
    private static String grandchildId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    private String getTypeKey(String typeKey) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/mds/reason-code-types"))
                .andExpect(status().isOk()).andReturn();
        for (var node : om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")) {
            if (typeKey.equals(node.path("typeKey").asText())) {
                return node.path("id").asText();
            }
        }
        return null;
    }

    /** 幂等确保类型存在（种子 Runner 在测试上下文同样会执行，8 类已入库）。 */
    private void ensureType(String typeKey) throws Exception {
        if (getTypeKey(typeKey) != null) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("typeKey", typeKey);
        m.put("typeName", "测试类型 " + typeKey);
        m.put("sortNo", 100);
        mvc.perform(post("/api/v1/mds/reason-code-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(m)))
                .andExpect(status().isOk());
    }

    private String body(String url) throws Exception {
        MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String createCode(String code, String typeKey, String parentId, String shortName)
            throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("reasonCode", code);
        m.put("typeKey", typeKey);
        m.put("shortName", shortName);
        m.put("owningDept", "EQP");
        m.put("parentId", parentId);
        m.put("enabled", true);
        MvcResult result = mvc.perform(post("/api/v1/mds/reason-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(m)))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String findIdByCode(String listJson, String code) throws Exception {
        for (var node : om.readTree(listJson).path("data")) {
            if (code.equals(node.path("reasonCode").asText())) {
                return node.path("id").asText();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- cases

    @Test
    @Order(1)
    void typeCrudAndGuards() throws Exception {
        // 幂等确保（种子已入库 HOLD/RELEASE）；重复 typeKey → 409
        ensureType("HOLD");
        ensureType("RELEASE");
        Map<String, Object> dup = new HashMap<>();
        dup.put("typeKey", "HOLD");
        dup.put("typeName", "重复");
        MvcResult dupResult = mvc.perform(post("/api/v1/mds/reason-code-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dup)))
                .andReturn();
        assertThat(dupResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":2003");
        holdTypeKey = "HOLD";
        assertThat(body("/api/v1/mds/reason-code-types")).contains("HOLD").contains("RELEASE");
    }

    @Test
    @Order(2)
    void createCodeTreeWithPath() throws Exception {
        // 根节点：level 1，path /HLD-001/
        String root = createCode("HLD-001", holdTypeKey, null, "设备故障");
        assertThat(root).contains("\"code\":0").contains("\"levelNo\":1")
                .contains("\"nodePath\":\"/HLD-001/\"");
        String list = body("/api/v1/mds/reason-codes");
        rootId = findIdByCode(list, "HLD-001");

        // 二级/三级：路径前缀逐层展开
        String child = createCode("HLD-001-MECH", holdTypeKey, rootId, "机械故障");
        assertThat(child).contains("\"levelNo\":2")
                .contains("\"nodePath\":\"/HLD-001/HLD-001-MECH/\"");
        childId = findIdByCode(body("/api/v1/mds/reason-codes"), "HLD-001-MECH");
        String grandchild = createCode("HLD-001-MECH-BRG", holdTypeKey, childId, "轴承磨损");
        assertThat(grandchild).contains("\"levelNo\":3")
                .contains("\"nodePath\":\"/HLD-001/HLD-001-MECH/HLD-001-MECH-BRG/\"");
        grandchildId = findIdByCode(body("/api/v1/mds/reason-codes"), "HLD-001-MECH-BRG");

        // 重复编码 → 409；未知类型 → 404；跨类型父节点 → 400
        assertThat(createCode("HLD-001", holdTypeKey, null, "重复")).contains("\"code\":2003");
        assertThat(createCode("HLD-XXX", "NO_TYPE", null, "未知类型")).contains("\"code\":2001");
        assertThat(createCode("RLS-001", "RELEASE", rootId, "跨类型挂树")).contains("\"code\":2000");
    }

    @Test
    @Order(3)
    void moveSubtreeAndCycleGuard() throws Exception {
        // 新根 R2，把 HLD-001 整棵树挂过去 → 子树路径/层级全部重算
        createCode("HLD-900", holdTypeKey, null, "新根");
        String r2Id = findIdByCode(body("/api/v1/mds/reason-codes"), "HLD-900");

        Map<String, Object> move = new HashMap<>();
        move.put("shortName", "设备故障");
        move.put("owningDept", "EQP");
        move.put("parentId", r2Id);
        move.put("enabled", true);
        mvc.perform(put("/api/v1/mds/reason-codes/" + rootId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(move)))
                .andExpect(status().isOk());
        String list = body("/api/v1/mds/reason-codes");
        for (var node : om.readTree(list).path("data")) {
            String code = node.path("reasonCode").asText();
            if ("HLD-001".equals(code)) {
                assertThat(node.path("nodePath").asText()).isEqualTo("/HLD-900/HLD-001/");
                assertThat(node.path("levelNo").asInt()).isEqualTo(2);
            }
            if ("HLD-001-MECH".equals(code)) {
                assertThat(node.path("nodePath").asText()).isEqualTo("/HLD-900/HLD-001/HLD-001-MECH/");
                assertThat(node.path("levelNo").asInt()).isEqualTo(3);
            }
            if ("HLD-001-MECH-BRG".equals(code)) {
                assertThat(node.path("levelNo").asInt()).isEqualTo(4);
            }
        }

        // 防环：把 HLD-900 挂到自己的后代 HLD-001 之下 → 400
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("parentId", rootId);
        cycle.put("enabled", true);
        MvcResult cycleResult = mvc.perform(put("/api/v1/mds/reason-codes/" + r2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(cycle)))
                .andReturn();
        assertThat(cycleResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":2000");
    }

    @Test
    @Order(4)
    void masterDataEvolution() throws Exception {
        // 主数据演进：改短名 + 挪部门 + 停用 + 挪树（父改回根下）
        Map<String, Object> evolve = new HashMap<>();
        evolve.put("shortName", "轴承磨损·新文案");
        evolve.put("owningDept", "PE");
        evolve.put("parentId", rootId);
        evolve.put("enabled", false);
        MvcResult evolveResult = mvc.perform(put("/api/v1/mds/reason-codes/" + grandchildId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(evolve)))
                .andExpect(status().isOk()).andReturn();
        // 挪树后码自己的当前路径已是新值
        assertThat(evolveResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"nodePath\":\"/HLD-900/HLD-001/HLD-001-MECH-BRG/\"")
                .contains("\"owningDept\":\"PE\"");
    }

    @Test
    @Order(5)
    void deleteGuards() throws Exception {
        // 有子节点不可删 → 2004
        MvcResult childDel = mvc.perform(delete("/api/v1/mds/reason-codes/" + rootId)).andReturn();
        assertThat(childDel.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":2004");
        // 类型下仍有码不可删 → 2004
        String holdTypeId = getTypeKey(holdTypeKey);
        MvcResult typeDel = mvc.perform(delete("/api/v1/mds/reason-code-types/" + holdTypeId)).andReturn();
        assertThat(typeDel.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":2004");
        // RELEASE 类型无码 → 可删；删后清单不含
        String releaseTypeId = getTypeKey("RELEASE");
        mvc.perform(delete("/api/v1/mds/reason-code-types/" + releaseTypeId)).andExpect(status().isOk());
        assertThat(body("/api/v1/mds/reason-code-types")).doesNotContain("RELEASE");
        // 叶子码（无子）可删：挪走子级后删自身验证软删通路
        Map<String, Object> reattach = new HashMap<>();
        reattach.put("shortName", "机械故障");
        reattach.put("owningDept", "EQP");
        reattach.put("parentId", rootId);
        reattach.put("enabled", true);
        mvc.perform(put("/api/v1/mds/reason-codes/" + childId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(reattach)))
                .andExpect(status().isOk());
        // 现在链条是 /HLD-900/HLD-001/{HLD-001-MECH, HLD-001-MECH-BRG}，都无子节点；
        // 删 HLD-001-MECH（无子）应成功
        mvc.perform(delete("/api/v1/mds/reason-codes/" + childId)).andExpect(status().isOk());
        assertThat(body("/api/v1/mds/reason-codes")).doesNotContain("HLD-001-MECH\"");
    }

    @Test
    @Order(7)
    void typeUpdateKeepsKeyImmutable() throws Exception {
        String holdTypeId = getTypeKey(holdTypeKey);
        Map<String, Object> req = new HashMap<>();
        req.put("typeKey", "SCRAP"); // 试图改键
        req.put("typeName", "挂起（改名后）");
        MvcResult result = mvc.perform(put("/api/v1/mds/reason-code-types/" + holdTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":2000");
        // 改名不涉键 → OK
        req.put("typeKey", holdTypeKey);
        mvc.perform(put("/api/v1/mds/reason-code-types/" + holdTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk());
        assertThat(body("/api/v1/mds/reason-code-types")).contains("挂起（改名后）");
    }
}
