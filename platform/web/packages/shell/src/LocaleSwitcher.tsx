// 语言切换器（顶栏与登录页共用）。
//
// <p><b>为什么在 shell 而不在 ui</b>：它只出现在外壳的右上角与登录页两处，
// 属于「控制台壳」的一部分；样式 `.lang-select` 在 `@cim/ui/shell.css`，
// shell 已经依赖 ui，放这里不会多引一层。</p>
//
// <p><b>为什么要拆成「受控组件 + hook」</b>：三个 ap 的差别不在界面而在数据来源——
// IAM 的语言目录来自后端 `/api/i18n/locales`（新增语种不用改前端），
// RMS / approval 是内置两种。原先是三份各写一遍，
// 结果 approval 那份漏了 busy 态、漏了 `className`、`title` 还硬编码成 "Language"
// （切中文后仍是英文 tooltip）。所以把「界面」与「取目录 + 切语言」拆开：
// 组件只画 `<select>`，hook 收注入的运行时。</p>
//
// <p><b>文案收字符串而不是 key</b>：与 `types.ts` 的约定一致——外壳的 props
// 在 Layout 每次 render 时重建（Layout 订阅了 i18n），切语言必然重算；
// 反过来让外壳认识三个 ap 的键空间才是不合理的那一侧。</p>

import { useEffect, useRef, useState } from 'react';

/** 语言目录项（与 `@cim/i18n` 的 `LocaleInfo` 结构兼容，只取渲染需要的两个字段）。 */
export interface ShellLocale {
  code: string;
  name: string;
}

/**
 * 语言运行时。
 *
 * <p>三个 ap 的 `lib/i18n.ts` 都恰好导出这三个同名函数（`@cim/i18n` 的薄适配层），
 * 所以接口形状是现成的、不需要应用再包一层适配器。</p>
 */
export interface ShellLocaleRuntime {
  /** 当前语言（读 localStorage 后的生效值，不是 i18next 的 `language`）。 */
  currentLang(): string;
  /** 切换语言：重灌译文包 + 通知应用（写 `Accept-Language` 等）。 */
  changeLanguage(lang: string): Promise<void>;
  /** 拉语言目录。 */
  loadLocales(): Promise<ShellLocale[]>;
}

export interface LocaleSwitcherProps {
  locales: ShellLocale[];
  value: string;
  onChange: (code: string) => void | Promise<void>;
  /** 已翻译的 title / aria-label。 */
  label: string;
  /** 切换中：禁用下拉，避免连点导致译文包与 UI 语言错位。 */
  busy?: boolean;
  className?: string;
}

/**
 * 语言下拉。
 *
 * <p>`value` 不在 `locales` 里时补一个占位项——目录被改过或刚清过缓存时，
 * 不补会让下拉显示为空白，用户以为语言丢了。</p>
 */
export function LocaleSwitcher({
  locales,
  value,
  onChange,
  label,
  busy = false,
  className,
}: LocaleSwitcherProps) {
  const known = locales.some((l) => l.code === value);
  return (
    <select
      className={className ? `lang-select ${className}` : 'lang-select'}
      value={value}
      disabled={busy}
      onChange={(e) => void onChange(e.target.value)}
      title={label}
      aria-label={label}
    >
      {!known && <option value={value}>{value}</option>}
      {locales.map((l) => (
        <option key={l.code} value={l.code}>
          {l.name || l.code}
        </option>
      ))}
    </select>
  );
}

export interface UseLocaleSwitcherResult {
  locales: ShellLocale[];
  lang: string;
  busy: boolean;
  change: (code: string) => Promise<void>;
}

/**
 * 语言切换的数据侧：拉目录、维持当前语言、切换时置 busy。
 *
 * <p>⚠️ `runtime` 通常是调用点内联的对象字面量，**每次 render 都是新引用**。
 * 直接把它挂进 `useEffect` 依赖会「拉目录 → setState → 重 render → 再拉」自激，
 * 所以用 ref 跟进最新引用、effect 只跑一次。</p>
 */
export function useLocaleSwitcher(runtime: ShellLocaleRuntime): UseLocaleSwitcherResult {
  const [locales, setLocales] = useState<ShellLocale[]>([]);
  const [lang, setLang] = useState<string>(() => runtime.currentLang());
  const [busy, setBusy] = useState(false);

  const runtimeRef = useRef(runtime);
  runtimeRef.current = runtime;

  useEffect(() => {
    let alive = true;
    runtimeRef
      .current
      .loadLocales()
      .then((list) => {
        if (alive) setLocales(list);
      })
      .catch(() => {
        // 目录拉不到就只显示当前语言一项：切换器退化为「只读」，但不至于白屏。
        // （IAM 的适配层内部已有内置兜底，这里只是不让 rejection 冒到控制台。）
      });
    return () => {
      alive = false;
    };
  }, []);

  async function change(next: string): Promise<void> {
    if (next === lang) return;
    setBusy(true);
    try {
      await runtimeRef.current.changeLanguage(next);
      setLang(next);
    } finally {
      setBusy(false);
    }
  }

  return { locales, lang, busy, change };
}
