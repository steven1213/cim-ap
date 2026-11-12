// RMS 的 SSO 接线——实现全在 `@cim/shell` 的 `createSsoBinding`。
//
// 本 ap **不是** IdP，只做 RP（Relying Party）：
//   1. 未登录 → 整页跳转 IAM `/login?app=RMS&redirect=...`（登录页由 IAM 后端直出）；
//   2. 登出    → 调 IAM `POST /api/v1/logout` 做服务端吊销（令牌进黑名单 + 清 SSO 会话 Cookie）。
//
// ⚠️ 登出必须回 IdP：只在本地清 localStorage 的话，accessToken/refreshToken 在有效期内
//    仍然可用——用户以为退出了，实际上没有。跨域要求 IAM 侧 `cim.iam.web.allowed-origins`
//    含本 console 源且 CORS 开 `allow-credentials`，否则清 SSO Cookie 那步会被浏览器拦掉。
import { createSsoBinding } from '@cim/shell';

/** RMS 的 SSO 绑定：登录地址、回跳地址、IdP 登出。 */
export const sso = createSsoBinding({ app: 'RMS' });
