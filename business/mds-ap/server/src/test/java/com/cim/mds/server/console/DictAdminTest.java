package com.cim.mds.server.console;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据字典（平台 sys_dict/sys_dict_item）集成测试：字典 CRUD / 明细 CRUD /
 * 系统级守门（is_system=true 拒删，标记可设置可取消——取消后即可删）。
 *
 * <p>端点在 cim-system（{@code /sys/dicts/**}），随宿主 mds-ap 暴露；权限
 * {@code sys:dict:*}，认证关闭直通。种子字典（mds.wafer_type 等）也在测试库内，
 * 断言一律用自建数据、不依赖库内总量。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-dict-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DictAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String sysDictId;
    private static String freeDictId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private String body(int expect, String method, String url, Map<String, Object> payload) throws Exception {
        var req = switch (method) {
            case "post" -> post(url).contentType(MediaType.APPLICATION_JSON);
            case "put" -> put(url).contentType(MediaType.APPLICATION_JSON);
            case "delete" -> delete(url);
            default -> get(url);
        };
        if (payload != null) {
            req = req.content(om.writeValueAsString(payload));
        }
        var res = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        return res.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    void createDicts() throws Exception {
        String json = body(200, "post", "/sys/dicts", Map.of(
                "code", "t.dict.sys", "name", "系统级字典", "isSystem", true, "status", "ENABLED"));
        sysDictId = om.readTree(json).path("data").path("id").asText();
        assertThat(sysDictId).isNotBlank();

        json = body(200, "post", "/sys/dicts", Map.of(
                "code", "t.dict.free", "name", "普通字典", "isSystem", false, "status", "ENABLED"));
        freeDictId = om.readTree(json).path("data").path("id").asText();

        // 编码全局唯一
        body(409, "post", "/sys/dicts", Map.of(
                "code", "t.dict.sys", "name", "重复", "status", "ENABLED"));
    }

    @Test
    @Order(2)
    void itemCrud() throws Exception {
        String itemJson = body(200, "post", "/sys/dicts/" + sysDictId + "/items", Map.of(
                "itemKey", "A", "itemValue", "Alpha", "sortNo", 10, "status", "ENABLED"));
        String itemId = om.readTree(itemJson).path("data").path("id").asText();

        // 字典内 itemKey 唯一
        body(409, "post", "/sys/dicts/" + sysDictId + "/items", Map.of(
                "itemKey", "A", "itemValue", "重复", "status", "ENABLED"));

        // 改键 + 改显示值
        body(200, "put", "/sys/dicts/" + sysDictId + "/items/" + itemId, Map.of(
                "itemKey", "A2", "itemValue", "Alpha2", "sortNo", 20, "status", "ENABLED"))
                .contains("A2");

        // 全量明细（管理口径，含停用）能看到改后的值
        String list = body(200, "get", "/sys/dicts/" + sysDictId + "/items", null);
        JsonNode items = om.readTree(list).path("data");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("itemKey").asText()).isEqualTo("A2");
    }

    @Test
    @Order(3)
    void systemGuards() throws Exception {
        // 系统级明细拒删
        String lockJson = body(200, "post", "/sys/dicts/" + sysDictId + "/items", Map.of(
                "itemKey", "LOCK", "itemValue", "锁定值", "isSystem", true, "status", "ENABLED"));
        String lockId = om.readTree(lockJson).path("data").path("id").asText();
        body(400, "delete", "/sys/dicts/" + sysDictId + "/items/" + lockId, null);

        // 普通明细可删
        String tmpJson = body(200, "post", "/sys/dicts/" + sysDictId + "/items", Map.of(
                "itemKey", "TMP", "itemValue", "临时", "status", "ENABLED"));
        String tmpId = om.readTree(tmpJson).path("data").path("id").asText();
        body(200, "delete", "/sys/dicts/" + sysDictId + "/items/" + tmpId, null);

        // 系统级字典拒删
        body(400, "delete", "/sys/dicts/" + sysDictId, null);

        // 标记可设置：把系统级取消后即可删（守门只认当前标记值）
        body(200, "put", "/sys/dicts", Map.of(
                "id", sysDictId, "code", "t.dict.sys", "name", "系统级字典",
                "isSystem", false, "status", "ENABLED"));
        body(200, "delete", "/sys/dicts/" + sysDictId, null);

        // 普通字典直接删：明细随字典级联软删
        body(200, "delete", "/sys/dicts/" + freeDictId, null);
        String list = body(200, "get", "/sys/dicts/" + freeDictId + "/items", null);
        assertThat(om.readTree(list).path("data")).isEmpty();
    }
}
