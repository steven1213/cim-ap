// 多语言运行时 —— 应用侧薄适配层。
//
// 运行时骨架（整包缓存防闪白、四级兜底、切语言不刷新、缺键上报）都在 `@cim/i18n`；
// 本文件只声明两件属于本应用的事：
//   1. 译文从哪来：**内置包**（随前端发版，见 `lib/i18n/messages.ts`）；
//   2. 切语言时顺手改 `Accept-Language`（本包不认识 axios，也不该认识）。
//
// 后续若本库接上 `cim-i18n-starter` 并灌入 `sys_i18n` 种子，只需在这里加 `remote`：
// 远端包会以逐键覆盖的方式叠在内置包之上，内置包永远兜得住，不会出现空界面。
import { createI18n } from '@cim/i18n';
import { adminEnUS, adminZhCN } from '@cim/admin';
import { httpClient, setAcceptLanguage } from '@/lib/api';
import { zhCN, enUS } from '@/lib/i18n/messages';

export const DEFAULT_LANG = 'zh-CN';

const cim = createI18n({
  app: 'rms',
  defaultLng: DEFAULT_LANG,
  // 六页系统配置（@cim/admin）的文案随包内置（admin.* 命名空间），远端同键可覆盖
  builtin: { 'zh-CN': { ...zhCN, ...adminZhCN }, 'en-US': { ...enUS, ...adminEnUS } },
  locales: [
    { code: 'zh-CN', name: '简体中文' },
    { code: 'en-US', name: 'English' },
  ],
  remote: {
    // `/i18n/messages` 公开端点（平台已统一 /api/v1 前缀）；版本一致时后端只回版本号、
    // messages 为空包，运行时按「空包 = 保留缓存」处理。
    loadBundle: async ({ lang, version }) => {
      const payload = await httpClient.rootGet<{
        version: string;
        lang: string;
        messages: Record<string, string>;
      }>('/i18n/messages', { lang, ...(version ? { v: version } : {}) });
      if (!payload || !payload.messages) return null;
      return { version: payload.version, lang: payload.lang || lang, messages: payload.messages };
    },
  },
  onLanguageChange: setAcceptLanguage,
  dev: import.meta.env.DEV,
});

export const currentLang = (): string => cim.currentLang();
/** force=true：无视缓存版本号强制重拉整包（译文维护保存后刷新界面用）。 */
export const changeLanguage = (lang: string, force = false): Promise<void> =>
  cim.changeLanguage(lang, force ? { force } : undefined);
export const bootstrapI18n = (): Promise<string> => cim.bootstrap();
export const loadLocales = () => cim.loadLocales();

export const i18next = cim.instance;
export default cim.instance;
