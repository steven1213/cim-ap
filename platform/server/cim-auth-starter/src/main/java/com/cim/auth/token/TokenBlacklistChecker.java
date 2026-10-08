package com.cim.auth.token;

/**
 * 令牌黑名单（主动吊销 / 登出）判定（design.md §8.1(h) / plan.md 验证侧）。
 *
 * <p><b>为什么需要它</b>：无状态令牌天然「不死」到过期。{@link TokenVersionChecker} 通过
 * bump 用户版本可让该用户<b>所有</b>已签发令牌失效（改权限 / 改密 / 强制下线场景）；但「只杀
 * 当前这一条访问令牌」（典型如用户登出当前设备）需要更精细的机制——IAM 把该令牌的 {@code jti}
 * 写入黑名单，验证侧在版本判定之后、准入之前查询，命中即 401。</p>
 *
 * <p><b>职责边界（关键）</b>：本接口是 platform 侧的<b>验证端</b>——只回答「这条令牌现在是否
 * 已被拉黑」。黑名单的<b>存储与写入</b>归 {@code business/iam-ap}（见 design.md §8 边界表）。
 * 与 {@link TokenVersionChecker} 同属「接缝」手法：platform 定契约，落地方提供实现。</p>
 *
 * <p><b>与版本机制的关系</b>：两者互补。黑名单精细（单条令牌），版本粗放（用户级全量）。登出通常
 * 同时做「拉黑当前 jti + bump 用户版本」——前者即时杀当前会话，后者兜底杀该用户其余存量令牌。</p>
 *
 * <p><b>为什么不在本 starter 内置黑名单存储</b>：黑名单的正确实现取决于 IAM 部署形态
 * （Redis / DB / 本地缓存），且写入发生在 IAM 登录/登出链路，不属于验证侧职责。platform 只给出
 * 默认放行实现 {@link #acceptAll()}，真实实现由接入方以 Bean 覆盖。</p>
 */
public interface TokenBlacklistChecker {

    /**
     * 令牌是否仍可接受（未被拉黑）。
     *
     * @param claims 已验证（签名/有效期通过）的令牌声明；{@link TokenClaims#jti()} 为 {@code jti} claim
     * @return {@code true} 放行；{@code false} 视为已吊销（过滤器映射为 401）
     */
    boolean isAcceptable(TokenClaims claims);

    /**
     * 默认实现：不查黑名单，一律放行——保持「未接入 IAM 黑名单」时的既有行为不变。
     *
     * <p>注意这<b>不是</b>「不安全」：签名与有效期仍由 {@link JwtVerifier} 强校验，
     * 本方法只放弃「主动按 jti 吊销」这一层能力。</p>
     */
    static TokenBlacklistChecker acceptAll() {
        return claims -> true;
    }
}
