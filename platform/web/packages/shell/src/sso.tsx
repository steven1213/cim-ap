// 统一登录（SSO）协作：登录地址拼装 + 中继页呈现。
//
// 收在这里的原因：`iam` 与 `rms` 各写了一份「${IAM}/login?app=&redirect=」的拼装，
// 又各写了一份整页居中的中继壳（IAM 用内联 style、RMS 用 `.login-screen` 卡片）。
// 两份拼装一旦分叉（少 encode 一个参数）就是「回跳地址被截断」这类难查的问题；
// 两份壳则是同一件事两种长相。
//
// ⚠️ 与 guards.tsx 同口径：**跳转时机由应用掌握**（登录态在各 ap 的 zustand store 里），
//    本模块只提供纯函数与呈现。组件不读 store、不判断是否已登录、不做 navigate。
import type { ReactNode } from 'react';

export interface SsoLoginUrlOptions {
  /** IdP 基址（应用从自己的 env 注入，如 `http://localhost:8081`）。 */
  iamBase: string;
  /** 应用接入码（IAM 侧 `apps.code`），如 `RMS` / `IAM`。 */
  app: string;
  /** 登录成功后的回跳地址（绝对 URL，各 ap 的回调页）。 */
  callbackUrl: string;
}

/** 拼 IAM 统一登录页地址。尾部斜杠容错，两个参数都做 encode。 */
export function ssoLoginUrl({ iamBase, app, callbackUrl }: SsoLoginUrlOptions): string {
  const base = iamBase.replace(/\/+$/, '');
  return `${base}/login?app=${encodeURIComponent(app)}&redirect=${encodeURIComponent(callbackUrl)}`;
}

/**
 * 整页跳转到 IAM 统一登录页。
 *
 * <p>用 `location.assign` 而非路由跳转：登录页在 IdP 源上，必须整页导航才能让 SSO 会话
 * Cookie 与回跳地址生效。</p>
 */
export function redirectToSso(options: SsoLoginUrlOptions): void {
  window.location.assign(ssoLoginUrl(options));
}

export interface SsoRelayProps {
  /** 品牌方块字（如 `IA` / `RM`），不传则不显示品牌区。 */
  mark?: string;
  /** 品牌名，如 `CIM RMS`。 */
  brand?: string;
  /** 品牌副标题（通常传应用自己的 brandSub 文案）。 */
  brandSub?: string;
  /** 主文案，如「正在跳转至统一登录…」。 */
  label: string;
  /** 次级说明，如「若长时间未跳转，请确认 IAM 服务可访问后重试。」 */
  hint?: string;
  /**
   * 是否显示进度条。等待/跳转中为 `true`（默认）；准入失败等**错误态必须传 `false`**——
   * 页面并没有在等任何东西，留着动画会让人以为还在加载。
   */
  busy?: boolean;
  /** 卡片内的附加内容：错误提示、重试按钮、DEV 预览区等。 */
  children?: ReactNode;
}/**
 * SSO 中继页外壳：整页居中的一张小卡片。
 *
 * <p><b>不用控制台侧栏那套 `.brand-title` / `.brand-sub`</b>：它们的文字色是为深色侧栏定的
 * （`#fff` / `--sidebar-fg`），放到白色卡片上直接看不见。本组件用自有的
 * `.sso-relay-*` 类名，配色显式声明，不依赖使用方的背景色。</p>
 */
export function SsoRelay({ mark, brand, brandSub, label, hint, busy = true, children }: SsoRelayProps) {
  return (
    <div className="sso-relay">
      <div className="sso-relay-card">
        {(mark || brand) && (
          <div className="sso-relay-brand">
            {mark && <div className="sso-relay-mark">{mark}</div>}
            {brand && (
              <div className="sso-relay-id">
                <div className="sso-relay-name">{brand}</div>
                {brandSub && <div className="sso-relay-sub">{brandSub}</div>}
              </div>
            )}
          </div>
        )}
        <p className="sso-relay-label" role="status" aria-live="polite">
          {label}
        </p>
        {busy && (
          <div className="sso-relay-bar" aria-hidden="true">
            <i />
          </div>
        )}
        {hint && <p className="sso-relay-hint">{hint}</p>}
        {children}
      </div>
    </div>
  );
}

// ─────────────────────────────── RP 绑定 ───────────────────────────────
//
// 上面三个是「怎么拼 URL / 怎么跳」，下面这个是「一个 ap 作为 RP 需要的那一整套」。
//
// 为什么还要这一层：`rms` / `approval` / `mds` 各写了一份 `lib/sso.ts`，里面除了
// `ssoLoginUrl`（已在上面的纯函数里）还有 `idpLogout`（RP-initiated logout）与
// `callbackUrl`——那部分逐字节相同却各留一份。`approval` 的注释甚至写着「已收进
// `@cim/shell`」，而实际上登出逻辑还在本地，**注释比代码乐观**。三份登出＝三个出错点，
// 而登出错了不会有人报障（令牌在有效期内仍可用，用户只是"以为"退出了）。

/** 本 ap 在 SSO 里的三个地址与一处吊销能力。 */
export interface SsoBinding {
  /** 写进 `/login?app=` 的接入码（**只用于登录页标识，不是准入码**）。 */
  app: string;
  /** IdP 基址。 */
  iamBase: string;
  /** 登录成功后的回跳地址（本 ap 的回调页）。 */
  callbackUrl(): string;
  /** IAM 统一登录页地址。 */
  loginUrl(app?: string): string;
  /** 整页跳转到 IAM 统一登录页。 */
  redirect(app?: string): void;
  /** 请求 IdP 吊销当前会话；返回值表示 IdP 是否确认（网络/CORS 失败为 `false`）。 */
  idpLogout(accessToken: string | null, refreshToken: string | null): Promise<boolean>;
}

export interface SsoBindingOptions {
  /** 应用接入码，如 `RMS` / `APPROVAL` / `MDS`。 */
  app: string;
  /** 回跳路径，默认 `/sso`（IAM 自己是 `/sso-callback`）。 */
  callbackPath?: string;
  /** IdP 基址；缺省按 `VITE_IAM_LOGIN_BASE` → `VITE_IAM_BASE` → `http://localhost:8081` 解析。 */
  iamBase?: string;
}

/**
 * 解析 IdP 基址。
 *
 * <p>`import.meta.env` 在包里按 `@cim/ui` 的既有写法取（不强依赖 vite/client 类型）：
 * 本包是**源码直引**，env 由引用它的应用在构建时静态替换，所以读到的就是各 ap 自己的配置。</p>
 */
export function resolveIamBase(): string {
  const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {};
  return env.VITE_IAM_LOGIN_BASE || env.VITE_IAM_BASE || 'http://localhost:8081';
}

/**
 * 建一个 ap 的 SSO 绑定。
 *
 * <pre>{@code
 * // app/src/lib/sso.ts —— 应用侧只剩这一行
 * export const sso = createSsoBinding({ app: 'MDS' });
 * }</pre>
 */
export function createSsoBinding(options: SsoBindingOptions): SsoBinding {
  const { app, callbackPath = '/sso', iamBase = resolveIamBase() } = options;

  const binding: SsoBinding = {
    app,
    iamBase,

    callbackUrl: () => `${window.location.origin}${callbackPath}`,

    loginUrl: (override) => ssoLoginUrl({ iamBase, app: override ?? app, callbackUrl: binding.callbackUrl() }),

    redirect: (override) => {
      window.location.assign(binding.loginUrl(override));
    },

    /**
     * 请求 IdP 吊销当前会话（RP-initiated logout）。
     *
     * <p>⚠️ 登出必须回 IdP，不能只在本地「清一下 localStorage 就算登出」——那样
     * accessToken/refreshToken 在有效期内仍然可用，属于典型的生产缺陷。</p>
     *
     * <p>同时带 accessToken（供 IAM 取 `jti`/`ver` 做黑名单或版本踢出）与 refreshToken，
     * 并以 `credentials: 'include'` 让 IAM 能清掉 SSO 会话 Cookie——不带这个凭据，
     * 服务端只会吊销令牌、SSO 会话还挂着，用户「换账号」时会被秒登回原账号。跨域要求
     * IAM 侧 `cim.iam.web.allowed-origins` 含本 console 源且 CORS 开 allow-credentials。</p>
     *
     * <p>失败（网络不可达 / CORS 未放行）返回 `false` 而不抛：本地登出不能被 IdP 阻塞，
     * 但调用方要知道服务端未确认吊销，否则「点了退出、令牌还有效」不会被任何人发现。</p>
     */
    async idpLogout(accessToken, refreshToken) {
      try {
        const resp = await fetch(`${iamBase}/api/v1/logout`, {
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
        return false;
      }
    },
  };

  return binding;
}
