/**
 * `@cim/i18n` —— 平台文案运行时。
 *
 * <p>三件不同的事不要混（见 `docs/platform/web/component-platform-design.md` §6.1）：
 * **UI 文案**走 i18n 包；**枚举字典**走 `sys_dict`（`@cim/ui` 的 `useDict` / `EnumTag`）；
 * **缺键**走上报。</p>
 *
 * <pre>{@code
 * // 应用侧只剩一层薄适配（app/src/lib/i18n.ts）
 * const cim = createI18n({
 *   app: 'rms',
 *   builtin: { 'zh-CN': zhCN, 'en-US': enUS },
 *   onLanguageChange: (lang) => setAcceptLanguage(lang),
 * });
 * export const bootstrapI18n = () => cim.bootstrap();
 * }</pre>
 */
export { createI18n } from './createI18n';
export { createBundleCache, createLangPreference } from './cache';
export { humanize, markMissingKey } from './humanize';
export { createMissingReporter } from './missing';
export type { MissingReporterOptions } from './missing';
export type {
  BundlePayload,
  CimI18n,
  CreateI18nOptions,
  LocaleInfo,
  RemoteSource,
} from './types';

/** 底层 i18next 单例——应用里 `import i18next from 'i18next'` 与它必须是同一个对象 */
export { default as i18nextInstance } from 'i18next';
