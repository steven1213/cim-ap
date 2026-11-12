// SSO 中继页（`/login` 的实体）——三个业务 ap 共用的那一份。
//
// 本页**不是登录表单**。表单在 IAM（`8081/login`，由 IAM 后端直出的静态页），全仓只有那一处。
// 这里只做两件事：未登录时整页中继过去；把 IAM 的拒绝原因讲清楚。
//
// 为什么值得收进外壳：页面里三条判断都是踩出来的，抄第四遍必然漏掉其中一条。
//
//   1. **准入失败（`?error=admission`）必须停住**：此时 IAM 侧会话仍在，若继续自动中继，
//      IAM 会拿同一枚令牌再弹回 `/sso`，两端无限往返（表现为「页面一直在跳」）。
//   2. **换账号前先 `idpLogout`**：准入没开通时重试必然拿到同一枚令牌、同一个 403，
//      唯一有意义的动作是换账号；而 IAM 的 SSO 会话还挂着，不先清就秒登回原账号。
//   3. **已登录要直接进首页**：否则用户手敲 `/login` 会被中继回 IAM 再弹回来，
//      一次多余的整页往返。
//
// ⚠️ 依赖方向：外壳认识 `@cim/ui`（呈现）与 react-router（导航），但**不认识任何 store**——
//    令牌怎么读、会话怎么写、跳转怎么发，全部由应用注入。

import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CimButton, CimInput, InlineAlert } from '@cim/ui';
import { SsoRelay } from './sso';

/** 本页需要的全部文案（由应用按自己的 i18n 键注入，包内零中文）。 */
export interface SsoLoginPageLabels {
  /** 错误标题（`?error=` 存在时的主文案）。 */
  title: string;
  /** 跳转中主文案。 */
  redirecting: string;
  /** `?error=admission`：准入未开通。 */
  admissionDenied: string;
  /** 其余错误：SSO 失败。 */
  ssoFailed: string;
  /** 「换账号」按钮。 */
  switchAccount: string;
  /** DEV 预览区标题，如「DEV 预览（粘贴 IAM 签发的 JWT）」。 */
  devPreview: string;
  /** DEV 预览区按钮，如「以本地令牌进入」。 */
  devApply: string;
}

export interface SsoLoginPageProps {
  /** 品牌方块字（如 `RM` / `AP` / `MD`）。 */
  mark: string;
  /** 品牌名，如 `CIM RMS`。 */
  brand: string;
  /** 品牌副标题（通常传应用自己的 `brandSub` 文案）。 */
  brandSub?: string;
  /** 读当前 access token（应用注入，如 `() => useAuthStore.getState().token`）。 */
  getToken: () => string | null;
  /** 写会话（dev 粘贴令牌用）。 */
  setSession: (token: string, refreshToken: string) => void;
  /** 发起 SSO 跳转（`ssoBinding.redirect`）。 */
  redirect: () => void;
  /** 换账号前吊销 IdP 会话（`() => ssoBinding.idpLogout(null, null)`）。 */
  idpLogout: () => Promise<boolean>;
  labels: SsoLoginPageLabels;
  /** 已登录 / 粘贴令牌后去哪，默认 `/`。 */
  homePath?: string;
}

/** `VITE_SSO_AUTO_REDIRECT=false` 可关掉自动中继（排查问题时用，免去改代码）。 */
function autoRelayEnabled(): boolean {
  const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {};
  return (env.VITE_SSO_AUTO_REDIRECT ?? 'true') !== 'false';
}

function isDevBuild(): boolean {
  const env = (import.meta as unknown as { env?: Record<string, boolean | undefined> }).env;
  return env?.DEV === true;
}

export function SsoLoginPage(props: SsoLoginPageProps) {
  const { mark, brand, brandSub, labels, homePath = '/' } = props;
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const error = params.get('error');
  const [devToken, setDevToken] = useState('');
  const [switching, setSwitching] = useState(false);

  // 注入的函数多半是内联箭头（每次渲染都换引用），放进 effect 依赖会让自动中继反复触发
  // ——`location.assign` 连发几次，表现是「登录页闪两下才走」。用 latest-ref 取最新的那个。
  const latest = useRef(props);
  latest.current = props;

  // `error` 存在时不中继：见文件头第 1 条，继续跳就是两端无限往返。
  const shouldRelay = autoRelayEnabled() && !error;

  useEffect(() => {
    if (latest.current.getToken()) {
      navigate(homePath, { replace: true });
      return;
    }
    if (shouldRelay) latest.current.redirect();
  }, [navigate, homePath, shouldRelay]);

  function applyDevToken() {
    const token = devToken.trim();
    if (!token) return;
    // 开发态：直接以传入的 JWT 同时作为 access/refresh（仅本地预览用）
    latest.current.setSession(token, token);
    navigate(homePath, { replace: true });
  }

  async function switchAccount() {
    setSwitching(true);
    // 准入失败时令牌已被 BootGate 清掉，这里没有可吊销的令牌；
    // 该请求唯一的作用是让 IAM 清掉 SSO 会话 Cookie（不带 credentials 就会秒登回原账号）。
    await latest.current.idpLogout();
    latest.current.redirect();
  }

  return (
    <SsoRelay
      mark={mark}
      brand={brand}
      brandSub={brandSub}
      label={error ? labels.title : labels.redirecting}
      busy={!error}
    >
      {error && (
        <>
          <InlineAlert tone="err">
            {error === 'admission' ? labels.admissionDenied : labels.ssoFailed}
          </InlineAlert>
          {error === 'admission' && (
            <CimButton block disabled={switching} onClick={switchAccount}>
              {labels.switchAccount}
            </CimButton>
          )}
        </>
      )}

      {isDevBuild() && (
        <div className="sso-relay-dev">
          <div className="sso-relay-dev-cap">{labels.devPreview}</div>
          <CimInput rows={3} placeholder="eyJ..." value={devToken} onChange={setDevToken} />
          <CimButton block onClick={applyDevToken}>
            {labels.devApply}
          </CimButton>
        </div>
      )}
    </SsoRelay>
  );
}
