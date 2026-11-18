// 多语言运行时 —— 应用侧薄适配层。
//
// 运行时骨架（整包缓存防闪白、四级兜底、切语言不刷新）都在 `@cim/i18n`；
// 本文件只声明两件属于本应用的事：
//   1. 译文从哪来：**内置包**（随前端发版，见 `lib/i18n/messages.ts`）打底 +
//      **远端包**（sys_i18n，`/api/v1/i18n/messages` 公开端点）逐键覆盖；
//   2. 本应用没有 `setAcceptLanguage`（api 层不带语言头），故不接 onLanguageChange。
import axios from 'axios';
import { createI18n } from '@cim/i18n';
import { adminZhCN, adminEnUS } from '@cim/admin';
import { zhCN, enUS } from '@/lib/i18n/messages';

export const DEFAULT_LANG = 'zh-CN';

const cim = createI18n({
  app: 'approval',
  defaultLng: DEFAULT_LANG,
  // 内置包 = 本应用文案 + 系统配置六页共享文案（@cim/admin 优先，远端 sys_i18n 可逐键覆盖）
  builtin: {
    'zh-CN': { ...zhCN, ...adminZhCN },
    'en-US': { ...enUS, ...adminEnUS },
  },
  locales: [
    { code: 'zh-CN', name: '简体中文' },
    { code: 'en-US', name: 'English' },
  ],
  remote: {
    // 公开端点（登录页也要用），裸 axios 直取、不走业务 client（避免 401 拦截器误清态）。
    loadBundle: async ({ lang, version }) => {
      const r = await axios.get('/api/v1/i18n/messages', {
        params: version ? { lang, v: version } : { lang },
      });
      const body = r.data as { code?: number; data?: { version: string; lang: string; messages: Record<string, string> } };
      const payload = body && body.code === 0 ? body.data : undefined;
      if (!payload || !payload.messages) return null;
      return { version: payload.version, lang: payload.lang || lang, messages: payload.messages };
    },
  },
  dev: import.meta.env.DEV,
});

export const currentLang = (): string => cim.currentLang();
export const changeLanguage = (lang: string, opts?: { force?: boolean }): Promise<void> =>
  cim.changeLanguage(lang, opts);
export const bootstrapI18n = (): Promise<string> => cim.bootstrap();
export const loadLocales = () => cim.loadLocales();

export const i18next = cim.instance;
export default cim.instance;
