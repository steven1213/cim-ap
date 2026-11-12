package com.cim.iam.server.sso;

import com.cim.iam.server.auth.RefreshTokenService;
import com.cim.iam.server.config.SsoProperties;
import com.cim.iam.server.token.TokenIssueRequest;
import com.cim.iam.server.token.TokenIssuerService;
import com.cim.spring.support.web.RawResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 统一登录入口（{@code GET /login}）：**所有客户端唯一的登录页**。
 *
 * <p><b>职责只有两件事</b>：① 认出「已经在 IAM 登录过」的会话，直接为请求方应用签发令牌并 302 回跳；
 *   ② 否则把静态登录页交出去。</p>
 *
 * <p><b>页面本体不在本类里</b>：HTML/CSS/JS 三件放在
 * {@code src/main/resources/static/login/}，随 jar 发布，Spring Boot 静态资源处理器直接托管
 * （{@code /login/login.css}、{@code /login/login.js}）。本类曾把整页内联在 Java 文本块里，
 * 代价是：改一个像素要重启 JVM、没有 lint/格式化、样式与文案各维护一份副本。拆出来之后
 * 这三件事都回到正常工具链，而「登录页跟着身份服务发布」这一点没变。</p>
 *
 * <p><b>为何是零依赖静态页，而不是 IAM 控制台 SPA 的一个路由</b>：登录页要在「未认证」状态可用。
 * 挂进控制台 SPA 就要过 {@code ProtectedRoute}/{@code BootGate}，未登录时会被再弹回登录页形成往返；
 * 且控制台打包失败或资源未就绪时，登录入口跟着一起失效——身份服务最不该有的单点。
 * 代价是它无法复用 {@code @cim/ui} 的组件外观（版式靠 {@code login.css} 与 {@code ui.css} 人工对齐，
 * 见 {@code docs/platform/web/component-adoption-audit.md} §8.3）。</p>
 *
 * <p><b>文案契约</b>：页面不硬编码文案，运行时拉同一后端的 i18n bundle
 * （{@code GET /api/i18n/messages?lang=}，公开端点），键名与控制台一致（{@code iam.login.*}）。
 * 因此改文案不发版。内置词典只留 zh-CN 兜底。</p>
 *
 * <p><b>登录契约</b>：{@code GET /api/v1/login/salt} 取客户端盐 → 浏览器内 PBKDF2WithHmacSHA256
 * 第一层派生（明文口令不出浏览器）→ {@code POST /api/v1/login} → 成功后整页跳
 * {@code redirect?token=...&refreshToken=...}，由接入 ap 的回调页落地。</p>
 *
 * <p><b>安全</b>：本端点无令牌即可访问（安全链 {@code anyRequest().permitAll()}，JWT 过滤器对无令牌
 * 请求直接放行），其依赖的 {@code /api/v1/login}、{@code /api/v1/login/salt}、
 * {@code /api/i18n/messages} 同为公开端点。页面同源调用，不涉及跨域。{@code crypto.subtle}
 * 要求安全上下文——localhost 视为安全上下文，生产须 HTTPS。回跳地址须在
 * {@code allowed-redirect-hosts} 白名单内，防开放重定向。</p>
 */
@RestController
@RequiredArgsConstructor
public class SsoLoginPageController {

    private final TokenIssuerService tokenIssuerService;
    private final RefreshTokenService refreshTokenService;
    private final SsoSessionService ssoSessionService;
    private final SsoProperties ssoProperties;

    /** 登录页本体（随 jar 发布）。缺了就是打包事故——登录全挂，故请求期显式报错。 */
    private static final Resource LOGIN_PAGE = new ClassPathResource("static/login/index.html");

    /**
     * 统一登录页。
     *
     * <p><b>SSO 会话感知（单点登录关键）</b>：若请求携带<b>有效</b>的 IAM SSO 会话 Cookie
     * （由 {@link SsoSessionService} 签发/校验，等价于「已在 IAM 登录过」），且带了合法的
     * {@code redirect}（回跳地址在白名单内），则<b>不再渲染表单</b>，而是直接为请求方应用
     * 签发令牌并 302 回跳。于是用户在 IAM 登录一次后，访问 RMS 等其它 ap 会「秒进」首页。</p>
     *
     * <p>{@code app} 与 {@code redirect} 查询参数原样留在 URL 上供页面脚本读取，本方法不消费它们
     * （除回跳判定外）——页面用 {@code location.search} 自己取。</p>
     */
    @RawResponse(reason = RawResponse.Reason.HTML,
            value = "统一登录页：交回静态页或 302 回跳业务 ap，不是数据接口")
    @GetMapping("/login")
    public ResponseEntity<Resource> loginPage(HttpServletRequest request,
                                              HttpServletResponse response,
                                              @RequestParam(required = false) String redirect) {
        Optional<SsoSessionService.SsoPrincipal> principal =
                ssoSessionService.validate(readCookie(request, ssoProperties.getCookieName()));

        if (principal.isPresent() && redirect != null && !redirect.isBlank() && isRedirectAllowed(redirect)) {
            SsoSessionService.SsoPrincipal p = principal.get();
            // 为请求方应用签发全新令牌（复用 IAM 通用签发，各 ap 后端自行按 apps claim 做准入）。
            String access = tokenIssuerService.issue(new TokenIssueRequest(p.userId(), p.username(), null));
            String refresh = refreshTokenService.issue(p.userId(), access);
            // 滑动续期：刷新 SSO 会话 Cookie，维持「一次登录、多 ap 复用」的窗口。
            ssoSessionService.attach(response, ssoSessionService.create(p.userId(), p.username(), p.version()));

            String sep = redirect.contains("?") ? "&" : "?";
            String target = redirect + sep
                    + "token=" + URLEncoder.encode(access, StandardCharsets.UTF_8)
                    + "&refreshToken=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
        }

        if (!LOGIN_PAGE.exists()) {
            throw new IllegalStateException(
                    "登录页静态资源缺失：classpath:static/login/index.html（检查 Maven resources 是否被打进 jar）");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // 登录页绝不能进任何缓存：缓存住的表单会让「已登录」用户看到上一个人的回跳地址。
                .cacheControl(CacheControl.noStore())
                .body(LOGIN_PAGE);
    }

    /** 读取指定名称的 Cookie 值（不存在返回 null）。 */
    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    /** 回跳地址必须在白名单主机内，避免开放重定向。相对路径（以 / 开头）视为安全。 */
    private boolean isRedirectAllowed(String redirect) {
        try {
            URI uri = URI.create(redirect);
            String host = uri.getHost();
            if (host == null) return true; // 相对路径
            return ssoProperties.getAllowedRedirectHosts().contains(host);
        } catch (Exception e) {
            return false;
        }
    }
}
