// 多语言运行时（i18next + react-i18next）—— W2 阶段为本地内置包版本。
//
// 设计要点（与 IAM 同源的 API 契约，便于后续切换为后端驱动）：
// 1. 启动时先用本地缓存（localStorage）同步渲染防闪白，再异步比对版本拉最新包。
// 2. 缺 key 兜底：humanize(key) 生成可读文案，**绝不渲染裸 key**。
// 3. 后续接入平台 cim-i18n-starter 时，把本文件的「资源来源」换成
//    `get /api/i18n/messages` 即可（键结构平移为 sys_i18n 行）。
import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import { setAcceptLanguage } from '@/lib/api';
import { zhCN, enUS } from '@/lib/i18n/messages';

export const DEFAULT_LANG = 'zh-CN';

const LANG_KEY = 'rms-lang';
const VERSION_KEY = 'rms-i18n-version';
const BUNDLE_KEY_PREFIX = 'rms-i18n-bundle:';

const BUNDLES: Record<string, Record<string, string>> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};

/** 已成功灌入 i18next 的语言（进程内）。 */
const loadedInMemory = new Set<string>();

function humanize(key: string): string {
  const tail = key.includes('.') ? key.slice(key.lastIndexOf('.') + 1) : key;
  const spaced = tail
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim();
  if (!spaced) return key;
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

let initialized = false;

function ensureInit(): void {
  if (initialized) return;
  i18next.use(initReactI18next).init({
    lng: currentLang(),
    fallbackLng: DEFAULT_LANG,
    defaultNS: 'translation',
    ns: ['translation'],
    resources: {
      'zh-CN': { translation: zhCN },
      'en-US': { translation: enUS },
    },
    initImmediate: false,
    react: { useSuspense: false, bindI18n: 'languageChanged loaded' },
    saveMissing: false,
    parseMissingKeyHandler: (key: string) => humanize(key),
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
    /* ignore quota errors */
  }
}

async function loadBundle(lang: string): Promise<void> {
  const messages = BUNDLES[lang] ?? BUNDLES[DEFAULT_LANG];
  i18next.addResourceBundle(lang, 'translation', messages, true, true);
  loadedInMemory.add(lang);
  writeCachedBundle(lang, messages);
  localStorage.setItem(VERSION_KEY, '1');
}

export function currentLang(): string {
  return localStorage.getItem(LANG_KEY) || DEFAULT_LANG;
}

export async function changeLanguage(lang: string): Promise<void> {
  ensureInit();
  setAcceptLanguage(lang);
  await loadBundle(lang);
  if (i18next.language !== lang) {
    await i18next.changeLanguage(lang);
  } else {
    i18next.emit('languageChanged', lang);
  }
  localStorage.setItem(LANG_KEY, lang);
}

export async function bootstrapI18n(): Promise<string> {
  ensureInit();
  const lang = currentLang();
  setAcceptLanguage(lang);
  // 先用本地缓存同步渲染（防闪白）；本地无缓存则用内置包
  const cached = readCachedBundle(lang);
  if (cached) {
    i18next.addResourceBundle(lang, 'translation', cached, true, true);
    loadedInMemory.add(lang);
  }
  await loadBundle(lang).catch(() => {});
  return lang;
}

/** 语言目录（W2 内置两种；接入 cim-i18n 后改为拉取 /api/i18n/locales）。 */
export async function loadLocales(): Promise<{ code: string; name: string }[]> {
  return [
    { code: 'zh-CN', name: '简体中文' },
    { code: 'en-US', name: 'English' },
  ];
}

export { i18next };
export default i18next;
