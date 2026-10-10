package com.cim.rms.server.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cim.spring.support.web.BizException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备管理集成测试（Req 46/47/48）：类型/区域/台账 CRUD + 引用保护 + 软删过滤。
 *
 * <p>响应体中文断言用 {@code getContentAsString(StandardCharsets.UTF_8)}——
 * MockMvc 缺省 ISO-8859-1 会把中文变乱码，仅 ASCII 断言暴露不了该问题。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-device-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        // 关闭 cim 认证链后，Spring Boot 缺省安全链会顶上（401/403）→ 一并排除
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeviceAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    DeviceAdminService deviceAdminService;

    private static String typeId;
    private static String areaId;
    private static String deviceId;

    @Test
    @Order(1)
    void createTypeAreaDevice() throws Exception {
        MvcResult typeRes = mvc.perform(post("/api/v1/rms/device-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new TypeReq("ETFD-300", "刻蚀机型 300", "刻蚀"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ETFD-300"))
                .andReturn();
        typeId = om.readTree(typeRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        MvcResult areaRes = mvc.perform(post("/api/v1/rms/device-areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new AreaReq("FAB1-ETC", "一厂刻蚀区", null, 1, null))))
                .andExpect(status().isOk())
                .andReturn();
        areaId = om.readTree(areaRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        MvcResult devRes = mvc.perform(post("/api/v1/rms/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new DeviceReq("ETCH-01", "刻蚀机 01", typeId, areaId,
                                null, "W1 测试机台"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andReturn();
        deviceId = om.readTree(devRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
    }

    @Test
    @Order(2)
    void duplicateCodeRejectedWithChineseMessage() throws Exception {
        // 中文文案断言走服务层（响应侧经 i18n，只回业务码与通用文案——iam 同款口径）
        assertThatThrownBy(() -> deviceAdminService.createType("ETFD-300", "重复机型", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("机型编码已存在");
        // HTTP 层：业务异常统一 400 + 业务码 2000（PARAM_INVALID）
        mvc.perform(post("/api/v1/rms/device-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new TypeReq("ETFD-300", "重复机型", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(3)
    void keywordSearchMatchesCodeAndName() throws Exception {
        mvc.perform(get("/api/v1/rms/devices").param("keyword", "*ETCH*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("ETCH-01"));
        mvc.perform(get("/api/v1/rms/devices").param("keyword", "*刻蚀*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @Order(4)
    void typeReferencedByDeviceCannotBeDeleted() throws Exception {
        assertThatThrownBy(() -> deviceAdminService.deleteType(typeId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("机型仍被设备引用");
        mvc.perform(delete("/api/v1/rms/device-types/" + typeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
    }

    @Test
    @Order(5)
    void areaWithDeviceCannotBeDeleted_thenDeviceDeletedUnlocks() throws Exception {
        assertThatThrownBy(() -> deviceAdminService.deleteArea(areaId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("区域仍被设备引用");
        mvc.perform(delete("/api/v1/rms/device-areas/" + areaId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        mvc.perform(delete("/api/v1/rms/devices/" + deviceId)).andExpect(status().isOk());
        // 软删后列表不可见
        mvc.perform(get("/api/v1/rms/devices"))
                .andExpect(jsonPath("$.data.length()").value(0));
        // 设备删除后区域可删
        mvc.perform(delete("/api/v1/rms/device-areas/" + areaId)).andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void updateDeviceStatusAndName() throws Exception {
        MvcResult devRes = mvc.perform(post("/api/v1/rms/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new DeviceReq("ETCH-02", "刻蚀机 02", typeId, null,
                                null, null))))
                .andExpect(status().isOk())
                .andReturn();
        String id = om.readTree(devRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        mvc.perform(put("/api/v1/rms/devices/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new DeviceReq(null, "刻蚀机 02A", null, null,
                                DeviceStatus.DISABLED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("刻蚀机 02A"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    record TypeReq(String code, String name, String description) {
    }

    record AreaReq(String code, String name, String parentId, Integer sortNo, String description) {
    }

    record DeviceReq(String code, String name, String deviceTypeId, String areaId,
                     DeviceStatus status, String description) {
    }
}
