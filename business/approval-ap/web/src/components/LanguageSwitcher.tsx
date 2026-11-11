import { useTranslation } from 'react-i18next';
import { LocaleSwitcher, useLocaleSwitcher } from '@cim/shell';
import { changeLanguage, currentLang, loadLocales } from '@/lib/i18n';

/**
 * 语言切换器（顶栏共用）。
 *
 * <p>界面与通用逻辑在 `@cim/shell`；approval 的差别只有一处：语言目录是**内置两种**，
 * 不走后端。原实现还缺三项通用能力——切换中不禁用（连点会让译文包与 UI 语言错位）、
 * 不支持 `className`、tooltip 硬编码 "Language"（切中文后仍是英文），补齐后与
 * IAM / RMS 完全同构。</p>
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
      label={t('shell.language')}
    />
  );
}
