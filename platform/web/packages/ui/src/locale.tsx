// 组件文案注入（不依赖 @cim/i18n）。
//
// 铁律：@cim/ui 不得 import @cim/data 或 @cim/i18n —— 组件保持纯展示。
// 但组件又必须能翻译（列标题、按钮、空态…），所以这里定义一个**注入接口**：
// 应用在根部用自己那套 i18n 的 `t` 包一层 <UiLocaleProvider>，
// 组件内部只调 useUiT()，依赖方向依旧是「应用 → 组件」，组件不知道 i18n 存在。
//
// 为什么不能直接在组件里写死中文：切语言时组件不重渲染（文案被缓存进 columns 数组），
// 表头会停在旧语言 —— 这是最容易漏的一类缺陷。

import { createContext, useContext, useMemo, type ReactNode } from 'react';

/** 翻译函数签名。缺键时应原样返回 key（便于开发态一眼看出漏翻）。 */
export type UiTranslate = (key: string, vars?: Record<string, unknown>) => string;

/**
 * 「这个键在应用里到底有没有译文」。
 *
 * <p>⚠️ 必须由应用注入，不能靠比对「返回值是否等于 key」来判断——各 ap 的 i18n 有各自的
 * 缺键兜底（RMS 会把 `ui.confirm.cancel` humanize 成 `Cancel`），于是 `t()` 永远不返回 key，
 * 组件里 `label(key, '取消')` 的中文兜底就永远不生效：界面上出现「确定 = 删除 / 取消 = Cancel」
 * 这种中英混排。这个缺陷是 2026-11-11 用探针在真实页面里抓到的，只靠读代码看不出来。</p>
 */
export type UiHasKey = (key: string) => boolean;

export interface UiLocaleValue {
  t: UiTranslate;
  locale: string;
  hasKey?: UiHasKey;
}

/** 未挂 Provider 时的兜底：原样返回 key。上线不会白屏，缺配置也能看出问题。 */
const FALLBACK: UiLocaleValue = {
  t: (key) => key,
  locale: 'zh-CN',
};

const UiLocaleContext = createContext<UiLocaleValue>(FALLBACK);

export function UiLocaleProvider({
  t,
  locale,
  hasKey,
  children,
}: {
  t: UiTranslate;
  locale?: string;
  /** 应用侧「键是否存在」的判断（通常是 `i18n.exists`）。不传则退回比对标量 key 的旧行为。 */
  hasKey?: UiHasKey;
  children: ReactNode;
}) {
  // locale 必须进依赖：i18next 的 t 在某些配置下引用稳定，仅靠 t 不会触发重渲染
  const value = useMemo<UiLocaleValue>(
    () => ({ t, locale: locale ?? 'zh-CN', hasKey }),
    [t, locale, hasKey],
  );
  return <UiLocaleContext.Provider value={value}>{children}</UiLocaleContext.Provider>;
}

/** 取翻译函数。 */
export function useUiT(): UiTranslate {
  return useContext(UiLocaleContext).t;
}

/** 取当前语言标识（如 zh-CN / en-US），供 antd 语言包等使用。 */
export function useUiLocale(): UiLocaleValue {
  return useContext(UiLocaleContext);
}

/**
 * 带兜底的翻译：命中则用译文，未命中则回落到 `fallback`。
 * 用于枚举值这类「后端只回 DRAFT、界面要显示草稿」的场景，也用于组件内置按钮文案。
 *
 * <p>「是否命中」优先用应用注入的 `hasKey`（权威判断）。没注入时才退回旧办法
 * ——比对标量 key，这招只在应用的缺键兜底「原样返回 key」时才有效。</p>
 */
export function useUiLabel(): (key: string, fallback: string) => string {
  const { t, hasKey } = useContext(UiLocaleContext);
  return useMemo(
    () => (key: string, fallback: string) => {
      if (hasKey) return hasKey(key) ? t(key) : fallback;
      const s = t(key);
      return s === key || s === undefined || s === null || s === '' ? fallback : s;
    },
    [t, hasKey],
  );
}
