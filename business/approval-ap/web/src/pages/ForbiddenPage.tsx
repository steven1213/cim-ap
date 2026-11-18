import { useTranslation } from 'react-i18next';

/**
 * 403 兜底页：路由级权限守卫（RequirePerm）未授权时就地渲染（URL 不变，
 * 权限补上后刷新即达原页）。权威鉴权在后端 `@PreAuthorize`，本页只是前端体验。
 */
export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <div className="forbidden">
      <h1>403</h1>
      <p>{t('common.forbidden')}</p>
    </div>
  );
}
