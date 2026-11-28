package com.cim.mds.server.dynamicquery;

import java.util.List;
import java.util.Map;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 动态查询/视图引擎集成测试（V39）：三层验证（SELECT-only / 参数一致 / dry-run）、
 * ViewModel col_key 硬校验、运行时 meta/data 契约、删除引用守门。
 *
 * <p>SQL 直查 V34 种子表 cim_consumable（演示种子在测试上下文也会跑，但本测试
 * 自建查询不依赖演示数据行——dry-run 只验证可执行）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-dq-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicQueryDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String queryId;
    private static String viewModelId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static String body(Object o) throws Exception {
        return new ObjectMapper().writeValueAsString(o);
    }

    @Test
    @Order(1)
    void sqlValidationLayers() throws Exception {
        // ① 非 SELECT 拒绝（UPDATE 伪装）
        mvc.perform(post("/api/v1/mds/query-defs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("queryCode", "QRY-IT-UPD", "queryName", "x",
                                "sqlText", "UPDATE cim_consumable SET stock_qty = 0", "params", List.of()))))
                .andExpect(status().isBadRequest());

        // ① 多语句拒绝
        mvc.perform(post("/api/v1/mds/query-defs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("queryCode", "QRY-IT-MULTI", "queryName", "x",
                                "sqlText", "SELECT 1; DROP TABLE cim_consumable", "params", List.of()))))
                .andExpect(status().isBadRequest());

        // ① SELECT ... INTO 拒绝（H2 方言语法）
        mvc.perform(post("/api/v1/mds/query-defs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("queryCode", "QRY-IT-INTO", "queryName", "x",
                                "sqlText", "SELECT * INTO t_copy FROM cim_consumable", "params", List.of()))))
                .andExpect(status().isBadRequest());

        // ② 未声明参数被引用 → 拒绝
        mvc.perform(post("/api/v1/mds/query-defs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("queryCode", "QRY-IT-UNDEC", "queryName", "x",
                                "sqlText", "SELECT cons_code FROM cim_consumable WHERE deleted = FALSE AND (:p1 IS NULL OR cons_type = :p1)",
                                "params", List.of()))))
                .andExpect(status().isBadRequest());

        // ①③ 合法 SELECT → 通过（dry-run 真跑 cim_consumable）
        MvcResult ok = mvc.perform(post("/api/v1/mds/query-defs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "queryCode", "QRY-IT-A", "queryName", "测试查询A",
                                "sqlText", "SELECT cons_code, cons_name, cons_type, stock_qty, lifecycle FROM cim_consumable WHERE deleted = FALSE AND (:consType IS NULL OR cons_type = :consType)",
                                "pageSizeMax", 500, "enabled", true,
                                "params", List.of(Map.of("paramKey", "consType", "paramLabel", "类别",
                                        "paramType", "DICT", "required", false, "dictCode", "mds.consumable_type"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validatedAt").isNotEmpty())
                .andReturn();
        queryId = om.readTree(ok.getResponse().getContentAsString()).at("/data/id").asText();

        // 声明参数未使用 → 拒绝（update 走同一链路）
        mvc.perform(put("/api/v1/mds/query-defs/" + queryId).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("queryName", "测试查询A",
                                "sqlText", "SELECT cons_code FROM cim_consumable WHERE deleted = FALSE",
                                "params", List.of(Map.of("paramKey", "ghost", "paramLabel", "幽灵", "paramType", "STRING"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(2)
    void viewModelColKeyGuardAndRuntimeContract() throws Exception {
        // col_key 不在结果集 → 拒绝
        mvc.perform(post("/api/v1/mds/view-models").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("viewCode", "VM-IT-BAD", "viewName", "坏列视图",
                                "queryCode", "QRY-IT-A", "allowExport", true, "pageSize", 20, "enabled", true,
                                "cols", List.of(Map.of("colKey", "not_a_column", "formatType", "NONE"))))))
                .andExpect(status().isBadRequest());

        // 合法视图（3 列，含 DICT 格式化）→ 通过
        MvcResult ok = mvc.perform(post("/api/v1/mds/view-models").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("viewCode", "VM-IT-A", "viewName", "测试视图A",
                                "queryCode", "QRY-IT-A", "allowExport", true, "pageSize", 5, "enabled", true,
                                "cols", List.of(
                                        Map.of("colKey", "cons_code", "colTitle", "编码", "formatType", "NONE", "sortNo", 10),
                                        Map.of("colKey", "cons_type", "colTitle", "类别", "formatType", "DICT", "formatArg", "mds.consumable_type", "sortNo", 20),
                                        Map.of("colKey", "stock_qty", "colTitle", "库存", "align", "RIGHT", "formatType", "NUMBER", "sortNo", 30))))))
                .andExpect(status().isOk())
                .andReturn();
        viewModelId = om.readTree(ok.getResponse().getContentAsString()).at("/data/id").asText();

        // meta 契约
        mvc.perform(get("/api/v1/mds/view-models/VM-IT-A/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCode").value("VM-IT-A"))
                .andExpect(jsonPath("$.data.queryCode").value("QRY-IT-A"))
                .andExpect(jsonPath("$.data.allowExport").value(true))
                .andExpect(jsonPath("$.data.params[0].key").value("consType"))
                .andExpect(jsonPath("$.data.columns.length()").value(3));

        // data 契约（种子行存在与否不影响分页结构）
        mvc.perform(post("/api/v1/mds/view-models/VM-IT-A/data").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("params", Map.of("consType", ""), "page", 1, "size", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());

        // export 契约（allowExport=true）
        mvc.perform(get("/api/v1/mds/view-models/VM-IT-A/export"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void deleteGuardAndLifecycle() throws Exception {
        // 查询被 ViewModel 引用 → 删除拒绝
        mvc.perform(delete("/api/v1/mds/query-defs/" + queryId))
                .andExpect(status().isBadRequest());

        // 视图删除 → 引用解除
        mvc.perform(delete("/api/v1/mds/view-models/" + viewModelId))
                .andExpect(status().isOk());

        // 引用解除后查询可删
        mvc.perform(delete("/api/v1/mds/query-defs/" + queryId))
                .andExpect(status().isOk());
    }
}
