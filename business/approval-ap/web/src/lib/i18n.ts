// 多语言运行时 —— 应用侧薄适配层。
//
// 运行时骨架（整包缓存防闪白、四级兜底、切语言不刷新）都在 `@cim/i18n`；
// 本文件只声明两件属于本应用的事：
//   1. 译文从哪来：**内置包**（随前端发版，见 `lib/i18n/messages.ts`）；
//   2. 本应用没有 `setAcceptLanguage`（api 层不带语言头），故不接 onLanguageChange。
import { createI18n } from '@cim/i18n';
import { zhCN, enUS } from '@/lib/i18n/messages';

export const DEFAULT_LANG = 'zh-CN';

const cim = createI18n({
  app: 'approval',
  defaultLng: DEFAULT_LANG,
  builtin: { 'zh-CN': zhCN, 'en-US': enUS },
  locales: [
    { code: 'zh-CN', name: '简体中文' },
    { code: 'en-US', name: 'English' },
  ],
  dev: import.meta.env.DEV,
});

export const currentLang = (): string => cim.currentLang();
export const changeLanguage = (lang: string): Promise<void> => cim.changeLanguage(lang);
export const bootstrapI18n = (): Promise<string> => cim.bootstrap();
export const loadLocales = () => cim.loadLocales();

export const i18next = cim.instance;
export default cim.instance;
