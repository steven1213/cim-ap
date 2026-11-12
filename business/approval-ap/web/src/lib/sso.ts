// approval 的 SSO 接线——实现全在 `@cim/shell` 的 `createSsoBinding`。
//
// 本 ap **不是** IdP，只做 RP（Relying Party）：
//   1. 未登录 → 整页跳转 IAM `/login?app=APPROVAL&redirect=...`（登录页由 IAM 后端直出）；
//   2. 登出    → 调 IAM `POST /api/v1/logout` 做服务端吊销（令牌进黑名单 + 清 SSO 会话 Cookie）。
//
// ⚠️ 登出只在 `sso` 通道做：`dev` 通道没有令牌，`authStore.logout` 会跳过吊销直接清本地态。
import { createSsoBinding } from '@cim/shell';

/** approval 的 SSO 绑定：登录地址、回跳地址、IdP 登出。 */
export const sso = createSsoBinding({ app: 'APPROVAL' });
