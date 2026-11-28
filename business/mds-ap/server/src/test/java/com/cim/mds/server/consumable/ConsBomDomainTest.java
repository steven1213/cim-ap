package com.cim.mds.server.consumable;

import com.cim.mds.server.flow.CimFlow;
import com.cim.mds.server.flow.CimFlowRepository;
import com.cim.mds.server.flow.CimOperation;
import com.cim.mds.server.flow.CimOperationRepository;
import com.cim.mds.server.flow.CimParamDef;
import com.cim.mds.server.flow.CimParamDefRepository;
import com.cim.mds.server.site.CimProduct;
import com.cim.mds.server.site.CimProductRepository;
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
 * 消耗品 BOM（ConsBom）集成测试（V38）：编码唯一 409 / 绑定引用 400 /
 * spec 守门（不存在 404、DURABLE/OBSOLETE 400）/ VARIABLE 参数键守门 400 /
 * 行全量替换、状态机（激活行空 400、非 DRAFT 编辑 400、激活→SUPERSEDED 自动画、
 * 版本号递增、ACTIVE/SUPERSEDED 不可删 400）。
 *
 * <p>权限 {@code mds:consBom:*}，认证关闭直通；绑定上下文引用（产品/流程/工序）
 * 与守门用规格/参数经 Repository 直建（不经 API 字典守门）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-consbom-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsBomDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    CimProductRepository productRepository;
    @Autowired
    CimFlowRepository flowRepository;
    @Autowired
    CimOperationRepository operationRepository;
    @Autowired
    CimParamDefRepository paramDefRepository;
    @Autowired
    CimConsumableRepository specRepository;

    private static String productId;
    private static String flowId;
    private static String operationId;
    private static String specOkId;
    private static String specDurableId;
    private static String specObsoleteId;
    private static String bomAId;
    private static String bomBId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void ensureRefs() {
        if (productId != null) {
            return;
        }
        CimProduct product = new CimProduct();
        product.setProductCode("CBOM-IT-PROD");
        product.setProductName("BOM 测试产品");
        product = productRepository.save(product);
        productId = product.getId();

        CimFlow flow = new CimFlow();
        flow.setFlowCode("CBOM-IT-FLW");
        flow.setFlowName("BOM 测试流程");
        flow = flowRepository.save(flow);
        flowId = flow.getId();

        CimOperation op = new CimOperation();
        op.setOpCode("CBOM-IT-OP");
        op.setOpName("BOM 测试工序");
        op.setOpType("PROCESS");
        op = operationRepository.save(op);
        operationId = op.getId();

        specOkId = newSpec("CM-IT-OK", CimConsumable.KIND_CONSUMABLE, CimConsumable.LC_ACTIVE);
        specDurableId = newSpec("CM-IT-DUR", CimConsumable.KIND_DURABLE, CimConsumable.LC_ACTIVE);
        specObsoleteId = newSpec("CM-IT-OBS", CimConsumable.KIND_CONSUMABLE, CimConsumable.LC_OBSOLETE);

        CimParamDef param = new CimParamDef();
        param.setAttrKey("CBOM_IT_PARAM");
        param.setAttrName("BOM 测试参数");
        param.setGroupCode("DC");
        param.setValueType(CimParamDef.VT_DECIMAL);
        param.setScopes("");
        paramDefRepository.save(param);
    }

    private String newSpec(String code, String kind, String lifecycle) {
        CimConsumable s = new CimConsumable();
        s.setConsCode(code);
        s.setConsName(code + " 名称");
        s.setUnit("EA");
        s.setLifeType("NONE");
        s.setMaterialKind(kind);
        s.setLifecycle(lifecycle);
        s.setLotControl(Boolean.FALSE);
        s.setEnabled(Boolean.TRUE);
        return specRepository.save(s).getId();
    }

    @Test
    @Order(1)
    void createGuards() throws Exception {
        ensureRefs();

        // 绑定引用不存在 → 400
        mvc.perform(post("/api/v1/mds/cons-boms").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "bomCode", "CBOM-IT-X1", "bomName", "坏引用",
                                "productId", "no-such", "flowId", flowId, "operationId", operationId))))
                .andExpect(status().isBadRequest());

        // spec 不存在 → 404
        bomAId = createBom("CBOM-IT-A", "主 BOM");
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of(
                                "specId", "no-such-spec", "consumeType", "FIXED",
                                "basis", "PER_LOT", "stdQty", 1)))))
                .andExpect(status().isNotFound());

        // spec DURABLE → 400（耐用品挡 BOM 误引用）
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of(
                                "specId", specDurableId, "consumeType", "FIXED",
                                "basis", "PER_LOT", "stdQty", 1)))))
                .andExpect(status().isBadRequest());

        // spec OBSOLETE → 400（淘汰不得被新 BOM 引用）
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of(
                                "specId", specObsoleteId, "consumeType", "FIXED",
                                "basis", "PER_LOT", "stdQty", 1)))))
                .andExpect(status().isBadRequest());

        // VARIABLE 参数键未注册 → 400；FIXED 带参数键 → 400
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of(
                                "specId", specOkId, "consumeType", "VARIABLE",
                                "basis", "PER_WAFER", "stdQty", 0,
                                "paramKey", "NO_SUCH_PARAM", "paramQtyPerUnit", 0.5)))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of(
                                "specId", specOkId, "consumeType", "FIXED",
                                "basis", "PER_LOT", "stdQty", 1, "paramKey", "CBOM_IT_PARAM")))))
                .andExpect(status().isBadRequest());

        // 撞码 → 409
        mvc.perform(post("/api/v1/mds/cons-boms").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "bomCode", "CBOM-IT-A", "bomName", "撞码",
                                "productId", productId, "flowId", flowId, "operationId", operationId))))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(2)
    void replaceLinesAndLifecycle() throws Exception {
        ensureRefs();

        // 行全量替换：FIXED + VARIABLE（参数已注册）成功；同头同 spec 查重
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(
                                Map.of("specId", specOkId, "consumeType", "FIXED",
                                        "basis", "PER_LOT", "stdQty", 2, "tolerancePct", 5),
                                Map.of("specId", specOkId, "consumeType", "VARIABLE",
                                        "basis", "PER_WAFER", "stdQty", 1,
                                        "paramKey", "CBOM_IT_PARAM", "paramQtyPerUnit", 0.02)))))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(
                                Map.of("specId", specOkId, "consumeType", "FIXED",
                                        "basis", "PER_LOT", "stdQty", 2, "tolerancePct", 5)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 空行激活 → 400
        String bomB = createBom("CBOM-IT-B", "空行 BOM");
        bomBId = bomB;
        mvc.perform(post("/api/v1/mds/cons-boms/" + bomBId + "/activate"))
                .andExpect(status().isBadRequest());

        // 非激活状态（还是 DRAFT？先激活 A 再试编辑 SUPERSEDED 前的状态机）
        mvc.perform(post("/api/v1/mds/cons-boms/" + bomAId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.sourceOpVersion").isNumber());

        // ACTIVE 编辑头 → 400；ACTIVE 删除 → 400
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "bomName", "改名", "productId", productId,
                                "flowId", flowId, "operationId", operationId))))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/mds/cons-boms/" + bomAId))
                .andExpect(status().isBadRequest());

        // BOM 行编辑在 ACTIVE 态 → 400
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomAId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of(
                                "specId", specOkId, "consumeType", "FIXED",
                                "basis", "PER_LOT", "stdQty", 9)))))
                .andExpect(status().isBadRequest());

        // B 挂行后激活 → A 自动画 SUPERSEDED、B 版本号 2
        mvc.perform(put("/api/v1/mds/cons-boms/" + bomBId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(Map.of(
                                "specId", specOkId, "consumeType", "FIXED",
                                "basis", "PER_RUN", "stdQty", 1)))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/mds/cons-boms/" + bomBId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.versionNo").value(2));

        mvc.perform(get("/api/v1/mds/cons-boms").param("keyword", "CBOM-IT-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUPERSEDED"));

        // SUPERSEDED 删除 → 400
        mvc.perform(delete("/api/v1/mds/cons-boms/" + bomAId))
                .andExpect(status().isBadRequest());

        // B 停用 → DISABLED，之后可删
        mvc.perform(post("/api/v1/mds/cons-boms/" + bomBId + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mvc.perform(delete("/api/v1/mds/cons-boms/" + bomBId))
                .andExpect(status().isOk());
    }

    private String createBom(String code, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/cons-boms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "bomCode", code, "bomName", name,
                                "productId", productId, "flowId", flowId,
                                "operationId", operationId))))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = om.readTree(json).path("data").path("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }
}
