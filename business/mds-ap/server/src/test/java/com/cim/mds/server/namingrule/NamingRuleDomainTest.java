package com.cim.mds.server.namingrule;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 编码规则集成测试（段列表模型）：有序段列表（LITERAL/ENUM/DATE/SEQ）+ 段级序号参数 +
 * 每段字符长度声明。覆盖：对象白名单与段校验（空段/变量越界/SEQ 缺长度拒绝）、逐段试算
 * （缺变量占位 + 派生正则）、强守门 apply 全链路（留空自动逐段渲染占号、手填匹配派生正则
 * 放行不占号、不匹配 400/2000、停用放行）、单条启用守门 409/2003、ENUM 段值域守门
 * （自定义 values）、BY_PARENT 分桶互不串号、BY_DATE 按天重置流水、计数器只读查询、
 * 规则复制（默认停用不带计数器）、删除规则连带计数器。
 *
 * <p>用 TECHNOLOGY（无变量，最简）/ PRODUCT（tech 父变量）/ FAB（site 变量 + ENUM 段）
 * 验证；权限 {@code mds:namingRule:*}，认证关闭直通。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-naming-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NamingRuleDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String techRuleId;
    private static final String TODAY = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    /** 段工厂：LITERAL / ENUM / DATE / SEQ。 */
    private static Map<String, Object> lit(String text) {
        return Map.of("type", "LITERAL", "text", text);
    }

    private static Map<String, Object> enu(String var, List<String> values) {
        return Map.of("type", "ENUM", "var", var, "values", values);
    }

    private static Map<String, Object> date(String pattern) {
        return Map.of("type", "DATE", "pattern", pattern);
    }

    private static Map<String, Object> var(String var) {
        return Map.of("type", "VAR", "var", var);
    }

    private static Map<String, Object> seq(int length, long start, String scope) {
        return scope == null
                ? Map.of("type", "SEQ", "length", length, "start", start)
                : Map.of("type", "SEQ", "length", length, "start", start, "scope", scope);
    }

    private String createRule(String objectType, String name, List<Map<String, Object>> segments,
                              boolean enabled) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/naming-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", objectType,
                                "ruleName", name,
                                "segments", segments,
                                "enabled", enabled))))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus())
                .as("createRule response: %s", body).isEqualTo(200);
        return om.readTree(body).path("data").path("id").asText();
    }

    private String createTechnology(String techCode, String techName) throws Exception {
        Map<String, String> body = techCode == null
                ? Map.of("techName", techName)
                : Map.of("techCode", techCode, "techName", techName);
        MvcResult result = mvc.perform(post("/api/v1/mds/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- cases

    @Test
    @Order(1)
    void objectTypesAndSegmentValidation() throws Exception {
        // 十四对象白名单可见（V12 起 FLOW 接入）
        mvc.perform(get("/api/v1/mds/naming-rules/object-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(14));

        // 空段列表 → 400
        mvc.perform(post("/api/v1/mds/naming-rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("objectType", "TECHNOLOGY"))))
                .andExpect(status().isBadRequest());
        // 未知段类型 → 400
        mvc.perform(post("/api/v1/mds/naming-rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", "TECHNOLOGY",
                                "segments", List.of(Map.of("type", "MAGIC"))))))
                .andExpect(status().isBadRequest());
        // ENUM 变量不在对象白名单（EQUIPMENT 无 tech）→ 400
        mvc.perform(post("/api/v1/mds/naming-rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", "EQUIPMENT",
                                "segments", List.of(enu("tech", List.of("T1")), seq(2, 1, null))))))
                .andExpect(status().isBadRequest());
        // SEQ 缺长度 → 400
        mvc.perform(post("/api/v1/mds/naming-rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", "TECHNOLOGY",
                                "segments", List.of(Map.of("type", "SEQ", "start", 1))))))
                .andExpect(status().isBadRequest());

        // 逐段试算：LITERAL + ENUM(缺变量占位) + DATE + SEQ(起始值定宽)
        MvcResult result = mvc.perform(post("/api/v1/mds/naming-rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", "EQUIPMENT",
                                "segments", List.of(lit("EQ-"), enu("fab", List.of("F1", "F2")),
                                        date("yyyyMMdd"), seq(2, 7, null))))))
                .andExpect(status().isOk())
                .andReturn();
        var data = om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(data.path("example").asText()).isEqualTo("EQ-<FAB>" + TODAY + "07");
        assertThat(data.path("missingVars").toString()).contains("fab");
        assertThat(data.path("regex").asText()).contains("\\d{8}").contains("\\d{2}");
        // 逐段明细：ENUM 段标记 missing
        assertThat(data.path("segmentResults").get(1).path("missing").asBoolean()).isTrue();
        // 补上 ctx 后缺变量消失
        MvcResult ok = mvc.perform(post("/api/v1/mds/naming-rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", "EQUIPMENT",
                                "segments", List.of(enu("fab", List.of("F1"))),
                                "ctx", Map.of("fab", "F1")))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(om.readTree(ok.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("example").asText()).isEqualTo("F1");
    }

    @Test
    @Order(2)
    void createRuleAndSingleActiveGuard() throws Exception {
        // TECHNOLOGY：[SEQ(2,1,GLOBAL)] 启用
        techRuleId = createRule("TECHNOLOGY", "制程编码·全局流水",
                List.of(seq(2, 1, "GLOBAL")), true);
        // 同对象第二条启用规则 → 409（active_marker 唯一索引口径）
        mvc.perform(post("/api/v1/mds/naming-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", "TECHNOLOGY",
                                "ruleName", "制程编码·备用",
                                "segments", List.of(seq(3, 1, "GLOBAL")),
                                "enabled", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2003));
        // 停用规则可并存
        createRule("TECHNOLOGY", "制程编码·停用备胎", List.of(seq(3, 1, "GLOBAL")), false);
    }

    @Test
    @Order(3)
    void autoGenerateAndSequence() throws Exception {
        // 留空 + 启用规则 → 自动占号（段 length=2，start=1）
        assertThat(om.readTree(createTechnology(null, "CMOS")).
                path("data").path("techCode").asText()).isEqualTo("01");
        assertThat(om.readTree(createTechnology(null, "BCD")).
                path("data").path("techCode").asText()).isEqualTo("02");
        // 手填匹配派生正则（\d{2} 整串）→ 原样放行且不占号
        assertThat(om.readTree(createTechnology("42", "BiCMOS")).
                path("data").path("techCode").asText()).isEqualTo("42");
        // 下一号仍是 03（42 未占号）
        assertThat(om.readTree(createTechnology(null, "SOI")).
                path("data").path("techCode").asText()).isEqualTo("03");
        // 手填不匹配（含非数字）→ 400
        mvc.perform(post("/api/v1/mds/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("techCode", "AB", "techName", "Bad"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(4)
    void disableFallsBackToManual() throws Exception {
        // 停用 → 留空被原有必填拒绝（400），手填任意值放行
        mvc.perform(put("/api/v1/mds/naming-rules/" + techRuleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "ruleName", "制程编码·全局流水",
                                "segments", List.of(seq(2, 1, "GLOBAL")),
                                "enabled", false))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/mds/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("techName", "NoRule"))))
                .andExpect(status().isBadRequest());
        assertThat(om.readTree(createTechnology("FREE-1", "Manual")).
                path("data").path("techCode").asText()).isEqualTo("FREE-1");
        // 重新启用（此时无其他启用规则，放行）
        mvc.perform(put("/api/v1/mds/naming-rules/" + techRuleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "ruleName", "制程编码·全局流水",
                                "segments", List.of(seq(2, 1, "GLOBAL")),
                                "enabled", true))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void byParentBucketIsolation() throws Exception {
        // PRODUCT：[VAR(tech), SEQ(3,1,BY_PARENT)]，变量段直通父制程码、流水按父分桶
        createRule("PRODUCT", "产品编码·按制程分桶", List.of(var("tech"), seq(3, 1, "BY_PARENT")), true);
        // 先建两个父制程（TECHNOLOGY 规则自动占号：04 / 05）
        String techA = om.readTree(createTechnology(null, "ParentA"))
                .path("data").path("techCode").asText();
        String techB = om.readTree(createTechnology(null, "ParentB"))
                .path("data").path("techCode").asText();
        // 各自从 001 起，互不串号
        assertThat(om.readTree(mvc.perform(post("/api/v1/mds/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of(
                                        "productName", "P-A", "techCode", techA, "productType", "PROD"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("productCode").asText()).isEqualTo(techA + "001");
        assertThat(om.readTree(mvc.perform(post("/api/v1/mds/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of(
                                        "productName", "P-B", "techCode", techA, "productType", "PROD"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("productCode").asText()).isEqualTo(techA + "002");
        assertThat(om.readTree(mvc.perform(post("/api/v1/mds/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of(
                                        "productName", "P-C", "techCode", techB, "productType", "PROD"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("productCode").asText()).isEqualTo(techB + "001");
    }

    @Test
    @Order(6)
    void enumSegmentDomainGuard() throws Exception {
        // FAB：[ENUM(var=site, values=[VS1]), SEQ(2,1,GLOBAL)] 启用
        createRule("FAB", "Fab编码·枚举+流水",
                List.of(enu("site", List.of("VS1")), seq(2, 1, "GLOBAL")), true);
        mvc.perform(post("/api/v1/mds/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("siteCode", "VS1", "siteName", "验证厂区"))))
                .andExpect(status().isOk());
        // 枚举值域内 → 自动编码 VS101 / VS102
        assertThat(om.readTree(mvc.perform(post("/api/v1/mds/fabs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of(
                                        "siteCode", "VS1", "fabName", "验证FabA"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("fabCode").asText()).isEqualTo("VS101");
        assertThat(om.readTree(mvc.perform(post("/api/v1/mds/fabs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of(
                                        "siteCode", "VS1", "fabName", "验证FabB"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("fabCode").asText()).isEqualTo("VS102");
        // 枚举值域外（site=VS2）→ 渲染 400
        mvc.perform(post("/api/v1/mds/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("siteCode", "VS2", "siteName", "越界厂区"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/mds/fabs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("siteCode", "VS2", "fabName", "越界Fab"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(7)
    void byDateResetCopyCountersAndDelete() throws Exception {
        // CAPABILITY：[DATE(yyyyMMdd), SEQ(3,1,BY_DATE)] —— 同一天流水连续，跨天回 001
        String capRuleId = createRule("CAPABILITY", "能力编码·按日重置",
                List.of(date("yyyyMMdd"), seq(3, 1, "BY_DATE")), true);
        assertThat(om.readTree(mvc.perform(post("/api/v1/mds/capabilities")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of("capabilityName", "TMP1"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("capabilityCode").asText()).isEqualTo(TODAY + "001");
        assertThat(om.readTree(mvc.perform(post("/api/v1/mds/capabilities")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(Map.of("capabilityName", "TMP2"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("capabilityCode").asText()).isEqualTo(TODAY + "002");

        // 计数器只读查询：一桶（D:当天），当前值 3
        MvcResult counters = mvc.perform(get("/api/v1/mds/naming-rules/" + capRuleId + "/counters"))
                .andExpect(status().isOk())
                .andReturn();
        var rows = om.readTree(counters.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("scopeKey").asText()).isEqualTo("D:" + TODAY);
        assertThat(rows.get(0).path("currentValue").asLong()).isEqualTo(3);

        // 复制：默认停用、名称加·副本、段列表一致、不带计数器
        MvcResult copied = mvc.perform(post("/api/v1/mds/naming-rules/" + capRuleId + "/copy"))
                .andExpect(status().isOk())
                .andReturn();
        var copyData = om.readTree(copied.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(copyData.path("ruleName").asText()).isEqualTo("能力编码·按日重置·副本");
        assertThat(copyData.path("enabled").asBoolean()).isFalse();
        assertThat(copyData.path("segments")).hasSize(2);

        // 删除规则 → 列表不再含该 id（副本名含原名子串，故按 id 判定）
        mvc.perform(delete("/api/v1/mds/naming-rules/" + capRuleId))
                .andExpect(status().isOk());
        MvcResult list = mvc.perform(get("/api/v1/mds/naming-rules"))
                .andExpect(status().isOk())
                .andReturn();
        var ids = om.readTree(list.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").findValuesAsText("id");
        assertThat(ids).doesNotContain(capRuleId);
    }
}
