// SSO 回跳接收页（`/sso` 或 IAM 自己的 `/sso-callback`）——四个 ap 共用的那一份。
//
// IAM 认证完成后把 `token` / `refreshToken` 经查询参数带回本页，此处写入 authStore 并跳首页；
// 没有 token 就回登录页重新发起。逻辑只有六行，但四个 ap 各写了一份，且**路径不同**
// （IAM 是 `/sso-callback`，其余是 `/sso`），所以路径由应用注入。
//
// ⚠️ 为什么值得收：这六行决定了「登录成功后能不能进得去」。任何一份漏掉 `refreshToken`
//    的兜底（`refreshToken || token`），刷新时就会因为没有 refreshToken 被踢出登录。

import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { SsoRelay } from './sso';

export interface SsoCallbackPageProps {
  /** 品牌方块字（如 `RM` / `MD`）。 */
  mark: string;
  /** 品牌名，如 `CIM RMS`。 */
  brand: string;
  /** 中转文案，如「正在进入…」。 */
  label: string;
  /** 写会话（应用注入 `authStore.setSession`）。 */
  setSession: (token: string, refreshToken: string) => void;
  /** 登入后去哪，默认 `/`。 */
  homePath?: string;
  /** 拿不到 token 时回哪，默认 `/login`。 */
  loginPath?: string;
}

export function SsoCallbackPage(props: SsoCallbackPageProps) {
  const { mark, brand, label, setSession, homePath = '/', loginPath = '/login' } = props;
  const navigate = useNavigate();
  const [params] = useSearchParams();

  useEffect(() => {
    const token = params.get('token');
    const refreshToken = params.get('refreshToken');
    if (token) {
      // 没有 refreshToken 时用 access 顶替：后端刷新接口会拒绝，但至少不会立刻掉登录态
      setSession(token, refreshToken || token);
      navigate(homePath, { replace: true });
    } else {
      navigate(loginPath, { replace: true });
    }
    // 只在挂载时跑一次：`params` 每次渲染都是新对象，进依赖会无限重定向
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return <SsoRelay mark={mark} brand={brand} label={label} />;
}
