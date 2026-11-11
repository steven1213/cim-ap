import { useTranslation } from 'react-i18next';
import { LocaleSwitcher, useLocaleSwitcher } from '@cim/shell';
import { changeLanguage, currentLang, loadLocales } from '@/lib/i18n';

/**
 * 语言切换器（顶栏共用）。
 *
 * <p>界面与通用逻辑在 `@cim/shell`；RMS 的差别只有一处：语言目录是**内置两种**
 * （`lib/i18n.ts` 的 `locales`），不走后端。切换不刷新页面，写 localStorage + 重灌译文包。</p>
 */
export default function LanguageSwitcher({ className = '' }: { className?: string }) {
  const { t } = useTranslation();
  const { locales, lang, busy, change } = useLocaleSwitcher({
    currentLang,
    changeLanguage,
    loadLocales,
  });
  return (
    <LocaleSwitcher
      locales={locales}
      value={lang}
      onChange={change}
      busy={busy}
      className={className}
      label={t('rms.shell.language')}
    />
  );
}
