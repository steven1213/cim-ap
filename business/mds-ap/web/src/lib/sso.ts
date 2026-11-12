// MDS 的 SSO 接线——实现全在 `@cim/shell` 的 `createSsoBinding`，本文件只剩一行。
//
// 本 ap **不是** IdP，只做 RP（Relying Party）：
//   1. 未登录 → 整页跳转 IAM `/login?app=MDS&redirect=...`（登录页由 IAM 后端直出）；
//   2. 登出    → 调 IAM `POST /api/v1/logout` 做服务端吊销（令牌进黑名单 + 清 SSO 会话 Cookie）。
//
// ⚠️ `app` 参数**只用于登录页文案与标识**，不是准入码。IAM 的登录页只消费 `redirect`；
//    令牌里的 `apps` claim 来自库内该用户的准入授权，准入比对的是
//    `cim.auth.admission.app-code`（= `mds-ap`），而 IAM 侧必须在
//    `cim.iam.auth.bootstrap.apps` 里登记同一个 code（否则准入一开就 403、登录后来回弹跳）。
import { createSsoBinding } from '@cim/shell';

/** MDS 的 SSO 绑定：登录地址、回跳地址、IdP 登出。 */
export const sso = createSsoBinding({ app: 'MDS' });
