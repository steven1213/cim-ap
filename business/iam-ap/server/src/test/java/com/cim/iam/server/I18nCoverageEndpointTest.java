package com.cim.iam.server;

import com.cim.i18n.model.I18nScope;
import com.cim.i18n.model.SysI18n;
import com.cim.i18n.service.I18nService;
import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.IamAuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 翻译覆盖率端点：把「英文界面显示中文」这条**无信号**的故障变成可看的数字。
 *
 * <p>同一个键在两条链上的表现必须能区分开，这正是本测试的核心断言：</p>
 * <ul>
 *   <li>{@code GET /api/i18n/messages?lang=en-US} —— <b>合并后</b>的整包，键「命中中文」，
 *       前端与 i18next 都不会报缺键；</li>
 *   <li>{@code GET /api/v1/admin/i18n/coverage} —— 按<b>原始值</b>比，看得见这条缺译。</li>
 * </ul>
 *
 * <p>字段名（{@code locale/base/total/translated/missing/truncated}）由断言锁住：
 * 前端按这些名字取值，名字对不上会静默显示成 `—`。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-i18n-coverage;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456"
})
class I18nCoverageEndpointTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private I18nService i18nService;
    @Autowired
    private IamAuthProperties authProps;

    @Test
    void englishCoverageSeesKeysThatTheMergedBundleHides() throws Exception {
        // 只译了中文的键（模拟「新加文案忘了翻英文」）
        SysI18n onlyZh = new SysI18n();
        onlyZh.setLocaleCode("zh-CN");
        onlyZh.setCode("iam.test.coverage.probe");
        onlyZh.setContent("仅中文");
        onlyZh.setScope(I18nScope.USER);
        i18nService.saveMessage(onlyZh);

        // ① 前端整包：键是「命中」的——英文包里出现中文，没有任何错误信号
        String bundle = mvc.perform(get("/api/i18n/messages").param("lang", "en-US"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode bundleNode = objectMapper.readTree(bundle).path("data").path("messages");
        assertThat(bundleNode.path("iam.test.coverage.probe").asText()).isEqualTo("仅中文");

        // ② 覆盖率：同一条缺译，这里看得见
        String resp = mvc.perform(get("/api/v1/admin/i18n/coverage")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(resp).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(2);

        JsonNode zh = byLocale(data, "zh-CN");
        JsonNode en = byLocale(data, "en-US");

        // 默认语言恒为自己的基准
        assertThat(zh.path("base").asBoolean()).isTrue();
        assertThat(zh.path("translated").asInt()).isEqualTo(zh.path("total").asInt());
        assertThat(zh.path("total").asInt()).isGreaterThan(0);

        // 英文：这条键列在 missing 里，且 分子 + 缺译 = 分母（不截断时自洽）
        assertThat(en.path("base").asBoolean()).isFalse();
        assertThat(textsOf(en.path("missing"))).contains("iam.test.coverage.probe");
        assertThat(en.path("truncated").asBoolean()).isFalse();
        assertThat(en.path("translated").asInt() + en.path("missing").size())
                .isEqualTo(en.path("total").asInt());
    }

    // ---------- helpers ----------

    private static JsonNode byLocale(JsonNode data, String locale) {
        for (JsonNode node : data) {
            if (locale.equals(node.path("locale").asText())) {
                return node;
            }
        }
        throw new AssertionError("覆盖率结果里没有语言：" + locale);
    }

    private static List<String> textsOf(JsonNode array) {
        List<String> keys = new ArrayList<>();
        array.forEach(n -> keys.add(n.asText()));
        return keys;
    }

    private String adminToken() throws Exception {
        String salt = fetchSalt("admin");
        String clientHash = PasswordDerivation.derive("Admin@123456", salt, rounds());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "admin", "credential", clientHash, "clientSalt", salt));
        String resp = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("token").asText();
    }

    private String fetchSalt(String username) throws Exception {
        String resp = mvc.perform(get("/api/v1/login/salt").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("clientSalt").asText();
    }

    private int rounds() {
        return authProps.getPassword().getRounds();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
