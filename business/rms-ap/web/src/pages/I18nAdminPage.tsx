// 多语言（RMS 接线，数据面在 /rms/admin/i18n/**（RmsI18nAdminController））：页面实现在共享包 @cim/admin。
import { changeLanguage, currentLang } from '@/lib/i18n';
import { useCallback } from 'react';
import { AdminProvider, I18nAdminPage as SharedI18nAdminPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { usePermissions } from '@/lib/usePermission';

export default function I18nAdminPage() {
  const codes = usePermissions();
  const hasAll = useCallback((cs: string[]) => cs.every((c) => codes.includes(c)), [codes]);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedI18nAdminPage basePath="/rms/admin/i18n" reloadTranslations={() => void changeLanguage(currentLang(), true)} />
    </AdminProvider>
  );
}
