// 多语言（MDS 接线）：页面实现在共享包 @cim/admin，数据面在 /mds/admin/i18n/**（MdsI18nAdminController）。
// 「刷新译文」= 重拉当前语言整包（写库即缓存失效，刷新后界面立即换新译文）。
import { useCallback } from 'react';
import { AdminProvider, I18nAdminPage as SharedI18nAdminPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { usePermissions } from '@/lib/usePermission';
import { changeLanguage, currentLang } from '@/lib/i18n';

export default function I18nAdminPage() {
  const codes = usePermissions();
  const hasAll = useCallback((cs: string[]) => cs.every((c) => codes.includes(c)), [codes]);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedI18nAdminPage basePath="/mds/admin/i18n" reloadTranslations={() => void changeLanguage(currentLang(), true)} />
    </AdminProvider>
  );
}
