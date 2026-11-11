// antd 主题适配层：把 @cim/theme 的 CSS 变量映射成 antd v5 theme token。
//
// 为什么这样做：设计令牌只有一份（platform/web/packages/theme/src/tokens.css），
// antd 不能另立一套色板。这里在运行时读 CSS 变量再喂给 ConfigProvider，
// 明暗主题与密度档切换时自动跟随（MutationObserver 监听 data-theme / data-cim-density）。
//
// 注意：不要把色值写死在这里 —— 写死就等于多了一份色板，暗色主题立刻失效。

import { ConfigProvider, theme as antdTheme, type ThemeConfig } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useUiLocale } from './locale';

const ANT_LOCALE: Record<string, typeof zhCN> = {
  'zh-CN': zhCN,
  'zh-TW': zhCN,
  'en-US': enUS,
};

function readVar(styles: CSSStyleDeclaration, name: string, fallback: string): string {
  const v = styles.getPropertyValue(name).trim();
  return v || fallback;
}

function readPx(styles: CSSStyleDeclaration, name: string, fallback: number): number {
  const raw = styles.getPropertyValue(name).trim();
  const n = Number.parseFloat(raw);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

/**
 * 跟随 documentElement 上的主题/密度属性变化，返回最新一次读取的序号。
 * 只在客户端有意义；SSR 场景下首帧为 0（不会读到脏值，因为 readVar 有兜底）。
 */
function useThemeTick(): number {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const target = document.documentElement;
    const obs = new MutationObserver(() => setTick((x) => x + 1));
    obs.observe(target, { attributes: true, attributeFilter: ['data-theme', 'data-cim-density'] });
    return () => obs.disconnect();
  }, []);
  return tick;
}

/** 由 CSS 变量生成 antd ThemeConfig。 */
export function useAntdTheme(): ThemeConfig {
  const tick = useThemeTick();
  return useMemo<ThemeConfig>(() => {
    const empty = {
      getPropertyValue: () => '',
    } as unknown as CSSStyleDeclaration;
    const styles = typeof document === 'undefined' ? empty : getComputedStyle(document.documentElement);
    const isDark =
      typeof document !== 'undefined' && document.documentElement.getAttribute('data-theme') === 'dark';

    const primary = readVar(styles, '--primary', '#1f5fe0');
    const panel = readVar(styles, '--panel', '#ffffff');
    const panel2 = readVar(styles, '--panel-2', '#f6f8fb');
    const bg = readVar(styles, '--bg', '#eef1f6');
    const line = readVar(styles, '--line', '#e0e5ee');
    const lineStrong = readVar(styles, '--line-strong', '#ccd4e2');
    const text = readVar(styles, '--text', '#0d1626');
    const text2 = readVar(styles, '--text-2', '#46536b');
    const muted = readVar(styles, '--muted', '#7d8899');
    const ok = readVar(styles, '--ok', '#0d8a55');
    const err = readVar(styles, '--err', '#cf2f24');
    const warn = readVar(styles, '--warn', '#a86a12');
    const info = readVar(styles, '--info', '#0f6f9e');
    const sans = readVar(styles, '--font-sans', 'system-ui, sans-serif');
    const mono = readVar(styles, '--font-mono', 'monospace');
    const fsBase = readPx(styles, '--fs-base', 13);
    const fsSm = readPx(styles, '--fs-sm', 12.5);
    const hit = readPx(styles, '--cim-hit', 28);
    const radiusSm = readPx(styles, '--radius-sm', 6);

    return {
      algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      token: {
        colorPrimary: primary,
        colorSuccess: ok,
        colorWarning: warn,
        colorError: err,
        colorInfo: info,
        colorBgLayout: bg,
        colorBgContainer: panel,
        colorBgElevated: panel,
        colorBgSpotlight: isDark ? panel2 : text,
        colorBorder: lineStrong,
        colorBorderSecondary: line,
        colorText: text,
        colorTextSecondary: text2,
        colorTextTertiary: muted,
        colorTextQuaternary: muted,
        colorFillAlter: panel2,
        colorFillSecondary: isDark ? panel2 : line,
        fontFamily: sans,
        fontFamilyCode: mono,
        fontSize: fsBase,
        fontSizeSM: fsSm,
        borderRadius: radiusSm,
        borderRadiusLG: radiusSm,
        borderRadiusSM: radiusSm - 2 > 0 ? radiusSm - 2 : radiusSm,
        controlHeight: hit,
        controlHeightSM: Math.max(20, hit - 4),
        controlHeightLG: hit + 8,
        wireframe: false,
      },
      components: {
        // 表格：对齐现有 `table.data` 的观感（表头浅底、行高紧凑、边框用 --line）
        Table: {
          headerBg: panel2,
          headerColor: text2,
          headerSplitColor: 'transparent',
          headerSortActiveBg: panel2,
          headerSortHoverBg: isDark ? panel : panel2,
          borderColor: line,
          rowHoverBg: panel2,
          bodySortBg: 'transparent',
          cellPaddingBlockSM: readPx(styles, '--cim-row-h', 34) > 40 ? 10 : 6,
          cellPaddingInlineSM: 10,
          cellFontSizeSM: fsSm,
          footerBg: panel2,
          stickyScrollBarBg: lineStrong,
          expandIconBg: panel,
        },
        Modal: {
          contentBg: panel,
          headerBg: panel,
          footerBg: panel,
          titleFontSize: fsBase + 1,
        },
        Card: { colorBgContainer: panel },
        Tooltip: { colorBgSpotlight: isDark ? panel2 : text },
        Input: { paddingBlock: 4 },
        Select: { optionSelectedBg: readVar(styles, '--primary-soft', '#e8effd') },
      },
    };
    // tick 是重算触发器，不在依赖里出现会漏掉主题切换
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick]);
}

/**
 * 应用根部包裹：antd ConfigProvider（主题 + 语言）。
 * 用法见 docs/platform/web/component-platform-design.md §7。
 */
export function CimProvider({ children }: { children: ReactNode }) {
  const { locale } = useUiLocale();
  const antdLocale = ANT_LOCALE[locale] ?? zhCN;
  const themeConfig = useAntdTheme();
  return (
    <ConfigProvider theme={themeConfig} locale={antdLocale}>
      {children}
    </ConfigProvider>
  );
}
