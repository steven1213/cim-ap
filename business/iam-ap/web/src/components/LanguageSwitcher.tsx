import { useTranslation } from 'react-i18next';
import { LocaleSwitcher, useLocaleSwitcher } from '@cim/shell';
import { changeLanguage, currentLang, loadLocales } from '@/lib/i18n';

/**
 * 语言切换器（顶栏与登录页共用）。
 *
 * <p>界面与「拉目录 / 切语言」的通用逻辑都在 `@cim/shell`，本文件只声明属于
 * IAM 的两件事：语言目录来自后端（`/api/i18n/locales`，语种入库后新增不需改前端，
 * 拉不到时适配层回退内置 zh-CN / en-US，登录页永远可用）、tooltip 用哪个文案键。</p>
 *
 * <p>切换**不刷新页面**：写 localStorage + 更新 `Accept-Language` + 重拉译文包
 * （见 `lib/i18n.ts`）。</p>
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
      label={t('iam.shell.language')}
    />
  );
}
