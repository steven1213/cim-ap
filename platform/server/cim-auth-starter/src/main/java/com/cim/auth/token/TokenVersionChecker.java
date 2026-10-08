package com.cim.auth.token;

/**
 * 令牌版本失效判定（design.md §8.1(f) / plan.md T6.5 的<b>验证侧</b>）。
 *
 * <p><b>为什么需要它</b>：无状态令牌一旦签发便天然「不死」到过期为止。当权限/角色被收回、
 * 账号被强制下线时，需要用一个「版本号」把已签发的旧令牌<b>立即作废</b>——IAM 在关键管理动作
 * （改权限、改密、踢人）后把该用户的令牌版本 +1，令牌里的 {@code ver} claim 便与新版本不再匹配，
 * 旧令牌随即失效（→ 401，前端重新登录换取携带新版本的新令牌）。</p>
 *
 * <p><b>职责边界（关键）</b>：本接口是 platform 侧的<b>验证端</b>——只回答「这个令牌的版本
 * 现在还可接受吗」。版本号的<b>存储、递增与下发</b>归 {@code business/iam-ap}（认证中心持
 * 版本表 / 黑名单，见设计 §8 边界表）。这与 {@code LocalAuthorityLoader}（接口在 platform、
 * 实现在业务域模块）是同一种「接缝」手法：platform 定契约，落地方提供实现。</p>
 *
 * <p><b>为什么不在本 starter 里内置版本存储</b>：验证侧不每请求回查 IAM（§8.1(a)），
 * 而版本存储的正确实现取决于 IAM 的部署形态（Redis 版本计数 / DB 版本列 / 本地缓存 + TTL 刷新）。
 * 因此 platform 只给出<b>默认放行</b>实现（{@link #acceptAll()}），真实实现由接入方以 Bean 覆盖
 * （自动装配处声明了 {@code @ConditionalOnMissingBean}）。</p>
 *
 * <p><b>与「权限即时生效」的关系</b>：本 ap 的业务权限由 {@code DbLocalAuthorityLoader}
 * <b>每请求从库解析</b>（不加缓存），故权限/角色变更本就在下一次请求即时生效；版本机制用于
 * 「主动作废已签发令牌」（如改密、强制下线、或将来把权限改为按令牌版本缓存时的缓存失效信号）。</p>
 */
public interface TokenVersionChecker {

    /**
     * 令牌版本是否仍可接受。
     *
     * @param claims 已验证（签名/有效期通过）的令牌声明；{@link TokenClaims#version()} 为 {@code ver} claim
     * @return {@code true} 放行；{@code false} 视为已失效（过滤器映射为 401）
     */
    boolean isAcceptable(TokenClaims claims);

    /**
     * 默认实现：不校验版本，一律放行——保持「未接入 IAM 版本表」时的既有行为不变。
     *
     * <p>注意这<b>不是</b>「不安全」：签名与有效期仍由 {@link JwtVerifier} 强校验，
     * 本方法只放弃「主动提前作废」这一层能力。</p>
     */
    static TokenVersionChecker acceptAll() {
        return claims -> true;
    }
}
