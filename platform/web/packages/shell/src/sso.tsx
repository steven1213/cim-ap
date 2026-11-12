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
}

/**
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
