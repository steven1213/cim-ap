import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { changeLanguage, currentLang, loadLocales } from '@/lib/i18n';

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const [locales, setLocales] = useState<{ code: string; name: string }[]>([]);

  useEffect(() => {
    loadLocales().then(setLocales);
  }, []);

  return (
    <select
      className="lang-select"
      value={currentLang()}
      onChange={(e) => changeLanguage(e.target.value)}
      title="Language"
      aria-label="Language"
    >
      {locales.map((l) => (
        <option key={l.code} value={l.code}>
          {l.name}
        </option>
      ))}
      {locales.length === 0 && (
        <option value={i18n.language}>{i18n.language}</option>
      )}
    </select>
  );
}
