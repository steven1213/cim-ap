import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { changeLanguage, currentLang, DEFAULT_LANG, loadLocales } from '@/lib/i18n';
import type { SysLocaleDto } from '@/types';

/**
 * 语言切换器（顶栏与登录页共用）。
 *
 * <p>切换**不刷新页面**：写 localStorage + 更新 `Accept-Language` + 重拉译文包
 * （见 `lib/i18n.ts`）。语言列表来自公开端点 `/api/i18n/locales`——语言目录入库后
 * 新增语种无需改前端（拉不到时回退内置 zh-CN / en-US，保证登录页永远可用）。</p>
 */
export default function LanguageSwitcher({ className = '' }: { className?: string }) {
  const { t } = useTranslation();
  const [locales, setLocales] = useState<SysLocaleDto[]>([]);
  const [lang, setLang] = useState(currentLang());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    loadLocales().then((list) => {
      if (alive) setLocales(list);
    });
    return () => {
      alive = false;
    };
  }, []);

  async function onChange(next: string) {
    if (next === lang) return;
    setBusy(true);
    try {
      await changeLanguage(next);
      setLang(next);
    } finally {
      setBusy(false);
    }
  }

  // 已选语言不在目录里（例如目录被改过）时补一个占位项，避免下拉显示空白
  const options = locales.length > 0 ? locales : [];
  const known = options.some((l) => l.code === lang);

  return (
    <select
      className={'lang-select ' + className}
      value={lang}
      disabled={busy}
      onChange={(e) => onChange(e.target.value)}
      title={t('iam.shell.language')}
      aria-label={t('iam.shell.language')}
    >
      {!known && <option value={lang}>{lang || DEFAULT_LANG}</option>}
      {options.map((l) => (
        <option key={l.code} value={l.code}>
          {l.name || l.code}
        </option>
      ))}
    </select>
  );
}
