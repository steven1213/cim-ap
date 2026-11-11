// 多语言运行时 —— 应用侧薄适配层。
//
// 运行时骨架（整包缓存防闪白、四级兜底、切语言不刷新、缺键上报）都在 `@cim/i18n`；
// 本文件只声明属于本应用的三件事：
//   1. 译文从哪来：**后端拉取**（`GET /api/i18n/messages`，改文案不发版）；
//   2. 语言目录从哪来：`GET /api/i18n/locales`（拉不到回退内置两种，登录页永远可用）；
//   3. 缺键往哪报：`POST /api/i18n/missing`（生成待翻译工单）。
//
// 与 RMS / approval 的差别仅在「来源」：那两个应用是内置包，本应用是后端驱动。
// 这正是 `@cim/i18n` 用 `remote` 选项而不是写死来源的原因。
import { createI18n } from '@cim/i18n';
import type { LocaleInfo } from '@cim/i18n';
import { rootGet, rootPost, setAcceptLanguage } from '@/lib/api';
import type { I18nBundle, SysLocaleDto } from '@/types';

/** 默认兜底语言（与后端 `cim.i18n.default-locale` 一致）。 */
export const DEFAULT_LANG = 'zh-CN';

/** 后端语言目录拉取失败时的保底选项。 */
export const FALLBACK_LOCALES: LocaleInfo[] = [
  { code: 'zh-CN', name: '简体中文', isDefault: true },
  { code: 'en-US', name: 'English' },
];

const cim = createI18n({
  app: 'iam',
  defaultLng: DEFAULT_LANG,
  // 本应用没有内置译文包（文案全在后端），故 builtin 留空——
  // 四级兜底的第三级在此退化为空集，第四级 humanize 顶上。
  locales: FALLBACK_LOCALES,
  onLanguageChange: setAcceptLanguage,
  dev: import.meta.env.DEV,
  remote: {
    async loadBundle({ lang, version }) {
      const params: Record<string, unknown> = { lang };
      if (version) params.v = version;
      const bundle = await rootGet<I18nBundle>('/api/i18n/messages', params);
      return { version: bundle.version, lang: bundle.lang, messages: bundle.messages ?? {} };
    },
    async loadLocales() {
      const list = await rootGet<SysLocaleDto[]>('/api/i18n/locales');
      // 后端 name 可空（未填中文名），这里落成「自称缺失时用 code」，避免下拉出现空行
      return (list ?? []).map((l) => ({
        code: l.code,
        name: l.name ?? l.code,
        isDefault: l.isDefault,
        sortNo: l.sortNo,
        status: l.status,
      }));
    },
    reportMissing: (keys, lang) => rootPost<number>('/api/i18n/missing', { keys, lang }),
  },
});

export const currentLang = (): string => cim.currentLang();
export const changeLanguage = (lang: string, force = false): Promise<void> =>
  cim.changeLanguage(lang, { force });
export const bootstrapI18n = (): Promise<string> => cim.bootstrap();
export const loadLocales = () => cim.loadLocales();
export const loadedLanguages = (): string[] => cim.loadedLanguages();

export const i18next = cim.instance;
export default cim.instance;
