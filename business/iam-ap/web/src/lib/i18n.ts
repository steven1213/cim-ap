// 多语言运行时（i18next + react-i18next）。
//
// 设计要点（见 docs/business/iam-ap/server/console-menu-perm-i18n.md §5.6 / §7.3）：
//
// 1. **文案运行时从后端拉取**，不在前端打包中文 JSON——改文案不发版。
//    端点 `GET /api/i18n/messages?lang=&v=`（平台 `cim-i18n-starter` 提供的公开端点）。
// 2. **中文兜底有两层**：服务端 `frontendBundle` 已把「默认语言(zh-CN)」合并进目标语言包，
//    故 en-US 缺译的键也返回中文；前端再加 `fallbackLng: 'zh-CN'` 与 `parseMissingKeyHandler`
//    ——四级兜底全未命中时也**绝不渲染裸 key**。
// 3. **本地缓存的是「整包」而非仅版本号**：端点约定「`v` 与当前版本一致则只回版本、消息体为空」，
//    若前端只缓存版本号，刷新页面后就会拿到空包、界面全空。故按语言把译文包整体落
//    `localStorage`，启动时**先**用缓存同步渲染（避免闪烁），再请求比对版本。
// 4. 语言切换**不刷新页面**：写 localStorage + 更新 `Accept-Language` 请求头 + 重拉 bundle。
import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import { rootGet, rootPost, setAcceptLanguage } from '@/lib/api';
import type { I18nBundle, SysLocaleDto } from '@/types';

/** 默认兜底语言（与后端 `cim.i18n.default-locale` 一致）。 */
export const DEFAULT_LANG = 'zh-CN';

/** 后端语言目录拉取失败时的保底选项。 */
export const FALLBACK_LOCALES: SysLocaleDto[] = [
  { id: 'zh-CN', code: 'zh-CN', name: '简体中文', isDefault: true, sortNo: 0, status: 'ENABLED' },
  { id: 'en-US', code: 'en-US', name: 'English', isDefault: false, sortNo: 10, status: 'ENABLED' },
];

const LANG_KEY = 'iam-lang';
const VERSION_KEY = 'iam-i18n-version';
const BUNDLE_KEY_PREFIX = 'iam-i18n-bundle:';

/** 已成功灌入 i18next 的语言（进程内）。 */
const loadedInMemory = new Set<string>();

// ------------------------------------------------------------------ 缺 key 上报

const missingKeys = new Set<string>();
let missingTimer: number | undefined;

/**
 * 把一个缺失键加入上报队列（去重 + 延迟批量提交）。
 *
 * <p>「缺 key」= 四级兜底链全未命中（含服务端兜底后仍无值），属**真缺口**，
 * 应生成待翻译工单；同一键在一次会话内只上报一次。</p>
 */
function queueMissingKey(key: string): void {
  if (!key || !key.includes('.')) return;
  if (missingKeys.has(key)) return;
  missingKeys.add(key);
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.warn(`[i18n] 缺失译文键：${key}`);
  }
  if (missingTimer !== undefined) return;
  missingTimer = window.setTimeout(() => {
    missingTimer = undefined;
    const keys = Array.from(missingKeys);
    missingKeys.clear();
    if (keys.length === 0) return;
    rootPost<number>('/api/i18n/missing', { keys, lang: i18next.language }).catch(() => {
      // 上报失败不影响渲染：重新入队，等下一次触发
      keys.forEach((k) => missingKeys.add(k));
    });
  }, 3000);
}

/** `apache.menu.orgs` → `Orgs`：兜底可读文案（绝不显示裸 key）。 */
function humanize(key: string): string {
  const tail = key.includes('.') ? key.slice(key.lastIndexOf('.') + 1) : key;
  const spaced = tail
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim();
  if (!spaced) return key;
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

// ------------------------------------------------------------------ 初始化

let initialized = false;

/** 初始化 i18next（幂等）。 */
function ensureInit(): void {
  if (initialized) return;
  i18next.use(initReactI18next).init({
    // 以「上次选择的语言」起步（而非默认语言），避免刷新瞬间回落到中文再跳变
    lng: currentLang(),
    fallbackLng: DEFAULT_LANG,
    defaultNS: 'translation',
    ns: ['translation'],
    resources: {},
    // 同步初始化：资源为空对象、无异步加载，initImmediate=false 可让 init 立即完成，
    // 从而保证首次渲染时 `isInitialized` 已为 true（否则 react-i18next 可能挂起等待）。
    initImmediate: false,
    react: {
      // 不用 Suspense：文案是「先渲染缓存、后台刷新」的模式，挂起会导致白屏/闪烁
      useSuspense: false,
      bindI18n: 'languageChanged loaded',
      bindI18nStore: 'added removed',
    },
    // 缺 key：渲染兜底文案 + 上报待翻译
    saveMissing: true,
    parseMissingKeyHandler: (key: string) => humanize(key),
    missingKeyHandler: (_lngs: readonly string[], _ns: string, key: string) => queueMissingKey(key),
    interpolation: { escapeValue: false },
    returnNull: false,
  });
  initialized = true;
}

function readCachedBundle(lang: string): Record<string, string> | null {
  try {
    const raw = localStorage.getItem(BUNDLE_KEY_PREFIX + lang);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Record<string, string>;
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

function writeCachedBundle(lang: string, messages: Record<string, string>): void {
  try {
    localStorage.setItem(BUNDLE_KEY_PREFIX + lang, JSON.stringify(messages));
  } catch {
    // 超出配额等：缓存失败不影响本次会话（内存里已有）
  }
}

/**
 * 拉取并灌注某语言的译文包。
 *
 * <p>若本地已有该语言缓存，则带上 `v` 版本号；服务端返回空包（版本未变）时直接用缓存，
 * 返回非空包时覆盖缓存。</p>
 */
async function loadBundle(lang: string, force: boolean): Promise<void> {
  const cached = force ? null : readCachedBundle(lang);
  const params: Record<string, unknown> = { lang };
  if (cached) {
    const v = localStorage.getItem(VERSION_KEY);
    if (v) params.v = v;
  }
  const bundle = await rootGet<I18nBundle>('/api/i18n/messages', params);
  localStorage.setItem(VERSION_KEY, bundle.version);
  const messages = bundle.messages ?? {};
  if (Object.keys(messages).length === 0) {
    // 服务端版本未变：用本地缓存灌入
    if (cached) {
      i18next.addResourceBundle(bundle.lang || lang, 'translation', cached, true, true);
      loadedInMemory.add(bundle.lang || lang);
    }
    return;
  }
  i18next.addResourceBundle(bundle.lang || lang, 'translation', messages, true, true);
  loadedInMemory.add(bundle.lang || lang);
  writeCachedBundle(bundle.lang || lang, messages);
}

/** 当前语言（localStorage 优先，其次浏览器语言，最后默认）。 */
export function currentLang(): string {
  return localStorage.getItem(LANG_KEY) || DEFAULT_LANG;
}

/**
 * 切换语言（不刷新页面）。
 *
 * @param lang  目标语言（BCP-47）
 * @param force 强制重拉（忽略本地缓存版本），语言目录变更后使用
 */
export async function changeLanguage(lang: string, force = false): Promise<void> {
  ensureInit();
  setAcceptLanguage(lang);
  try {
    await loadBundle(lang, force);
  } catch {
    // 译文拉取失败：退回本地缓存（若有），至少不显示裸 key
    const cached = readCachedBundle(lang);
    if (cached) {
      i18next.addResourceBundle(lang, 'translation', cached, true, true);
    }
  }
  if (i18next.language !== lang) {
    await i18next.changeLanguage(lang);
  } else {
    // 同一语言但资源刚更新：手动触发重渲染
    i18next.emit('languageChanged', lang);
  }
  localStorage.setItem(LANG_KEY, lang);
}

/**
 * 启动引导：先用本地缓存同步渲染（防闪白），再异步拉最新包。
 *
 * @return 生效语言
 */
export async function bootstrapI18n(): Promise<string> {
  ensureInit();
  const lang = currentLang();
  setAcceptLanguage(lang);
  const cached = readCachedBundle(lang);
  if (cached) {
    i18next.addResourceBundle(lang, 'translation', cached, true, true);
    loadedInMemory.add(lang);
    if (i18next.language !== lang) {
      await i18next.changeLanguage(lang);
    }
  }
  // 异步比对版本（不阻塞首屏）
  changeLanguage(lang).catch(() => {});
  return lang;
}

/** 语言目录（公开端点；失败回退内置两种）。 */
export async function loadLocales(): Promise<SysLocaleDto[]> {
  try {
    const list = await rootGet<SysLocaleDto[]>('/api/i18n/locales');
    const enabled = (list ?? []).filter((l) => l.status !== 'DISABLED');
    return enabled.length > 0 ? enabled : FALLBACK_LOCALES;
  } catch {
    return FALLBACK_LOCALES;
  }
}

/** 已灌入内存的语言（调试用）。 */
export function loadedLanguages(): string[] {
  return Array.from(loadedInMemory);
}

export { i18next };
export default i18next;
