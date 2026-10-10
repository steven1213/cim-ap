import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { changeLanguage, currentLang, loadLocales } from '@/lib/i18n';

/** 语言切换器（顶栏共用）。切换不刷新页面，写 localStorage + 重灌译文包。 */
export default function LanguageSwitcher({ className = '' }: { className?: string }) {
  const { t } = useTranslation();
  const [locales, setLocales] = useState<{ code: string; name: string }[]>([]);
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

  const known = locales.some((l) => l.code === lang);
  return (
    <select
      className={'lang-select ' + className}
      value={lang}
      disabled={busy}
      onChange={(e) => onChange(e.target.value)}
      title={t('rms.shell.language')}
      aria-label={t('rms.shell.language')}
    >
      {!known && <option value={lang}>{lang}</option>}
      {locales.map((l) => (
        <option key={l.code} value={l.code}>
          {l.name || l.code}
        </option>
      ))}
    </select>
  );
}
