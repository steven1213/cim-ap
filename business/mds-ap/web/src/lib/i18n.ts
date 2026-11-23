// 多语言运行时 —— 应用侧薄适配层。
//
// 运行时骨架（整包缓存防闪白、四级兜底、切语言不刷新、缺键上报）都在 `@cim/i18n`；
// 本文件只声明两件属于本应用的事：
//   1. 译文从哪来：**内置包**（随前端发版，见 `lib/i18n/messages.ts`）打底 +
//      **远端包**（sys_i18n，`/api/i18n/messages`，逐键覆盖内置）——菜单名等
//      在库内维护的文案随远端包生效，内置包退为离线兜底；
//   2. 切语言时顺手改 `Accept-Language`（本包不认识 axios，也不该认识）。
import { createI18n } from '@cim/i18n';
import { adminEnUS, adminZhCN } from '@cim/admin';
import { httpClient, setAcceptLanguage } from '@/lib/api';
import { zhCN, enUS } from '@/lib/i18n/messages';

export const DEFAULT_LANG = 'zh-CN';

const cim = createI18n({
  app: 'mds',
  defaultLng: DEFAULT_LANG,
  // 内置包 = 本应用文案 + 系统配置六页共享文案（@cim/admin；远端 sys_i18n 可逐键覆盖）
  builtin: { 'zh-CN': { ...adminZhCN, ...zhCN }, 'en-US': { ...adminEnUS, ...enUS } },
  locales: [
    { code: 'zh-CN', name: '简体中文' },
    { code: 'en-US', name: 'English' },
  ],
  remote: {
    // `/api/i18n/messages` 是公开端点（登录页也要用，无 /api/v1 前缀），
    // 走平台通道 rootGet；版本一致时后端只回版本号、messages 为空包，
    // 运行时按「空包 = 保留缓存」处理。
    loadBundle: async ({ lang, version }) => {
      const payload = await httpClient.rootGet<{
        version: string;
        lang: string;
        messages: Record<string, string>;
      }>('/i18n/messages', { lang, ...(version ? { v: version } : {}) });
      if (!payload || !payload.messages) return null;
      return { version: payload.version, lang: payload.lang || lang, messages: payload.messages };
    },
    // 语言目录同走后端（GET /i18n/locales，公开端点）：i18n 管理页加语言，
    // 顶栏切换器自动出现，不随前端发版；拉取失败由运行时退回上面的内置目录
    loadLocales: async () => {
      const list = await httpClient.rootGet<Array<{ code: string; name: string; status?: string }>>(
        '/i18n/locales',
      );
      return (list ?? [])
        .filter((l) => l && l.code && l.status !== 'DISABLED')
        .map((l) => ({ code: l.code, name: l.name || l.code }));
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
