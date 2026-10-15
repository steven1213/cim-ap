// 单点登录（SSO）协作：与 IdP（IAM，端口 8081）的两处交互。
//
// 本 ap **不是** IdP，只做 RP（Relying Party）：
//   1. 未登录 → 整页跳转 IAM `/login?app=RMS&redirect=...`（登录页由 IAM 后端直出）；
//   2. 登出    → 调 IAM `POST /api/v1/logout` 完成**服务端吊销**（令牌进黑名单 + 清 SSO 会话 Cookie）。
//
// ⚠️ 登出必须回 IdP，不能在本地「清一下 localStorage 就算登出」——那样 accessToken/refreshToken
// 在有效期内仍然可用，属于典型的生产缺陷（业界惯例：RP-initiated logout）。
//
// 跨域要求：IAM 侧 `cim.iam.web.allowed-origins` 必须包含本 ap 控制台源，且其 CORS 已开
// `allow-credentials`（见 IamSecurityConfig），否则 `credentials: 'include'` 会被浏览器拦掉。

/** IdP（IAM）基址：优先 `VITE_IAM_LOGIN_BASE`，回退 `VITE_IAM_BASE`，最终 8081。 */
export const IAM_BASE: string =
  import.meta.env.VITE_IAM_LOGIN_BASE || import.meta.env.VITE_IAM_BASE || 'http://localhost:8081';

/** 跳转 IAM 统一登录页（登录成功后由 IAM 回跳 `{origin}/sso?token=...&refreshToken=...`）。 */
export function ssoLoginRedirect(app = 'RMS'): void {
  const redirect = encodeURIComponent(`${window.location.origin}/sso`);
  window.location.href = `${IAM_BASE}/login?app=${app}&redirect=${redirect}`;
}

/**
 * 请求 IdP 吊销当前会话（RP-initiated logout）。
 *
 * <p>同时带上 accessToken（供 IAM 取 `jti`/`ver` 做黑名单或版本踢出）与 refreshToken，
 * 并以 `credentials: 'include'` 让 IAM 能清掉 SSO 会话 Cookie，从而真正结束单点登录。</p>
 *
 * @returns 是否吊销成功（网络/配置失败返回 false，调用方应仍清本地态并提示）
 */
export async function idpLogout(accessToken: string | null, refreshToken: string | null): Promise<boolean> {
  try {
    const resp = await fetch(`${IAM_BASE}/api/v1/logout`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify({ refreshToken }),
      credentials: 'include',
    });
    return resp.ok;
  } catch {
    // 网络不可达 / CORS 未放行：不阻断本地登出，但调用方需知道服务端未确认吊销
    return false;
  }
}
