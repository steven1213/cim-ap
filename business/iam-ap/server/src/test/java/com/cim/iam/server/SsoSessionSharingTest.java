package com.cim.iam.server;

import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.SsoProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证「单点登录会话共享」（修复：在 IAM 登录一次 → 访问其它已接入 ap 如 RMS 直接进入，不再重复登录）。
 *
 * <p><b>缺陷根因</b>：各 ap 的 SPA 把令牌存在<b>各自源</b>的 localStorage（5171 与 5174 是不同源），
 * 因此在 IAM 登录不会让 RMS 自动获得令牌，RMS 未登录时仍会被引导回登录页重新输入口令。</p>
 *
 * <p><b>修复机制</b>：IAM 后端（8081）登录成功时种下一枚<b>同站共享</b>的 SSO 会话 Cookie（host-only，
 * 与端口无关，跨 localhost 各端口共享）；任一 ap 中继到 8081 的 {@code /login} 时，若此 Cookie 有效，
 * 后端直接为该 ap 签发令牌并 302 回跳，不再渲染登录表单。</p>
 *
 * <p><b>本测试验证</b>：
 * ① 登录后响应带 {@code iam_sso} Cookie；
 * ② 携带该 Cookie 访问 {@code GET /login?redirect=RMS回跳地址} → 返回 302 且 Location 携带
 *    {@code token}&amp;{@code refreshToken}，且令牌 {@code apps} 含 {@code rms-ap}（证明 RMS 可直接进入）；
 * ③ 无 Cookie / 伪造 Cookie 访问 {@code /login} → 仍渲染登录表单（200），不会误放行也不会死循环。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-sso;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456",
        "cim.iam.auth.bootstrap.apps[0].code=rms-ap",
        "cim.iam.auth.bootstrap.apps[0].name=RMS",
        "cim.iam.auth.bootstrap.apps[0].roles=ADMIN"
})
class SsoSessionSharingTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RsaKeyService rsaKeyService;
    @Autowired
    private SsoProperties ssoProperties;

    private static final int ROUNDS = 100_000;
    private static final String RMS_CALLBACK = "http://localhost:5174/sso/callback";

    @Test
    void ssoSessionCookie_autoIssuesTokenForRms_andRedirectsWithoutForm() throws Exception {
        // 0) rms-ap 的登记 + 首管理员准入由 {@code cim.iam.auth.bootstrap.apps} 在 BootstrapAdminRunner
        //    中完成（与运行态一致）；故本测试无需手工调注册/授权接口。使后续 SSO 签发的令牌 apps 含 rms-ap，
        //    复现「在 IAM 登录一次 → 访问 RMS 直接进入」的真实场景。

        // 1) 真实登录 admin → 响应须带 SSO 会话 Cookie
        String salt = fetchSalt("admin");
        MvcResult loginResult = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "admin",
                                "credential", PasswordDerivation.derive("Admin@123456", salt, ROUNDS),
                                "clientSalt", salt))))
                .andExpect(status().isOk())
                .andReturn();
        String loginToken = readData(loginResult.getResponse().getContentAsString(), "token");
        assertThat(loginToken).isNotBlank();

        Cookie ssoCookie = null;
        for (Cookie c : loginResult.getResponse().getCookies()) {
            if (ssoProperties.getCookieName().equals(c.getName())) {
                ssoCookie = c;
                break;
            }
        }
        assertThat(ssoCookie)
                .as("登录成功必须在响应上种下 SSO 会话 Cookie")
                .isNotNull();
        assertThat(ssoCookie.getName()).isEqualTo(ssoProperties.getCookieName());

        // 2) 携带有效 SSO Cookie 访问中继登录页 → 必须 302 且 Location 带 token/refreshToken
        MvcResult relay = mvc.perform(get("/login")
                        .cookie(ssoCookie)
                        .param("redirect", RMS_CALLBACK))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = relay.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        assertThat(location).startsWith(RMS_CALLBACK);
        assertThat(location).contains("token=");
        assertThat(location).contains("refreshToken=");

        // 3) 解析回跳令牌：必须是 IAM 签发的有效 JWT，且 apps 含 rms-ap（RMS 准入通过）
        String token = extractQueryParam(location, "token");
        assertThat(token).isNotBlank();
        Jws<Claims> jws = Jwts.parser()
                .verifyWith((RSAPublicKey) rsaKeyService.getPublicKey()).build()
                .parseSignedClaims(token);
        Claims c = jws.getPayload();
        assertThat(c.get("uid")).isEqualTo("admin");
        @SuppressWarnings("unchecked")
        List<String> appsList = (List<String>) c.get("apps");
        Set<String> apps = new LinkedHashSet<>(appsList);
        assertThat(apps).contains("rms-ap");
        assertThat(((Number) c.get("ver")).longValue()).isGreaterThanOrEqualTo(1L);

        // 4) 无 Cookie 访问 /login（从未在 IAM 登录过）→ 必须回到登录表单（200），不会误放行
        mvc.perform(get("/login").param("redirect", RMS_CALLBACK))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String noCookieBody = mvc.perform(get("/login").param("redirect", RMS_CALLBACK))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(noCookieBody).contains("id=\"f\""); // 登录表单已渲染

        // 5) 伪造/失效 SSO Cookie → 同样回到登录表单，不泄漏、不循环
        mvc.perform(get("/login")
                        .cookie(new Cookie(ssoProperties.getCookieName(), "forged.invalid.token"))
                        .param("redirect", RMS_CALLBACK))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String forgedBody = mvc.perform(get("/login")
                        .cookie(new Cookie(ssoProperties.getCookieName(), "forged.invalid.token"))
                        .param("redirect", RMS_CALLBACK))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(forgedBody).contains("id=\"f\"");
    }

    private String fetchSalt(String username) throws Exception {
        String resp = mvc.perform(get("/api/v1/login/salt").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readData(resp, "clientSalt");
    }

    private String readData(String response, String field) throws Exception {
        JsonNode node = objectMapper.readTree(response).path("data").path(field);
        return node.isMissingNode() ? null : node.asText();
    }

    private String extractQueryParam(String url, String name) throws Exception {
        int idx = url.indexOf('?');
        String query = idx >= 0 ? url.substring(idx + 1) : url;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
