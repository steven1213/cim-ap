// 多语言运行时（i18next + react-i18next）—— 本地内置包版本。
// 与 rms-ap 同源 API：本地缓存防闪白、humanize 兜底、预留后端驱动切换。
import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import { zhCN, enUS } from '@/lib/i18n/messages';

export const DEFAULT_LANG = 'zh-CN';

const LANG_KEY = 'approval-lang';

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

export function currentLang(): string {
  return localStorage.getItem(LANG_KEY) || DEFAULT_LANG;
}

export async function changeLanguage(lang: string): Promise<void> {
  ensureInit();
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
  await i18next.changeLanguage(lang);
  return lang;
}

export async function loadLocales(): Promise<{ code: string; name: string }[]> {
  return [
    { code: 'zh-CN', name: '简体中文' },
    { code: 'en-US', name: 'English' },
  ];
}

export { i18next };
export default i18next;
