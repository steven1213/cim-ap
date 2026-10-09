package com.cim.iam.server.token;

import com.cim.auth.token.TokenClaims;
import com.cim.auth.token.TokenVersionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * IAM 自有令牌版本判定（验证端，in-JVM 直连版本表）。
 *
 * <p>与 cim-auth-starter 提供的 {@code IamTokenVersionChecker}（HTTP 回查 IAM 版本端点、带 TTL 缓存、
 * fail-open）不同，本类供 IAM 自身验证其签发的 admin 令牌时使用：版本存储与验证同进程同库，
 * 直接比对 {@link TokenVersionService#currentVersion(String)} 与令牌 {@code ver} claim，
 * 无 HTTP 往返、无 TTL 滞后，改密 / 踢人后即刻生效（design.md §8.1(f) / §8 边界表）。</p>
 *
 * <p>判定语义：</p>
 * <ul>
 *   <li>令牌无 {@code ver} claim（历史旧令牌）→ 放行（向后兼容）；</li>
 *   <li>令牌无 {@code uid} → 放行（无法定位版本，不阻断）；</li>
 *   <li>令牌 {@code ver} == 当前版本 → 放行；</li>
 *   <li>令牌 {@code ver} &lt; 当前版本（改密 / 踢人已 bump）→ 拒绝（返回 false → 过滤器 401，
 *       旧会话被强制失效，前端引导重新登录换取携带新版本的新令牌）；</li>
 *   <li>比对期间版本存储异常 → 版本机制属安全控制且存储为本地 DB（与凭证同源），<b>失败闭环</b>
 *       （返回 false → 401）并记录错误；不以「IAM 抖动」为由放行陈旧令牌。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class LocalTokenVersionChecker implements TokenVersionChecker {

    private final TokenVersionService tokenVersionService;

    @Override
    public boolean isAcceptable(TokenClaims claims) {
        Long tokenVer = claims.version();
        if (tokenVer == null) {
            return true; // 旧令牌无 ver：向后兼容放行
        }
        String uid = claims.userId();
        if (uid == null) {
            return true; // 无法定位用户：不阻断
        }
        try {
            long current = tokenVersionService.currentVersion(uid);
            boolean ok = tokenVer.longValue() == current;
            if (!ok) {
                log.info("[token-version] uid={} 令牌 ver={} ≠ 当前 ver={}，判定失效", uid, tokenVer, current);
            }
            return ok;
        } catch (Exception e) {
            log.error("[token-version] 比对用户 {} 令牌版本失败（失败闭环，拒绝访问）: {}", uid, e.getMessage());
            return false;
        }
    }
}
