// 多语言（approval 接线，数据面在 /approval/admin/i18n/**（ApprovalI18nAdminController））：页面实现在共享包 @cim/admin
import { useCallback } from 'react';
import { AdminProvider, I18nAdminPage as SharedPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { changeLanguage, currentLang } from '@/lib/i18n';
import { hasPerm } from '@/lib/usePermission';

export default function I18nAdminPage() {
  // dev 演示通道（无 IAM）在 hasPerm 内短路放行；sso 通道精确匹配
  const hasAll = useCallback((cs: string[]) => cs.every((c) => hasPerm(c)), []);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedPage basePath="/approval/admin/i18n" reloadTranslations={() => void changeLanguage(currentLang(), { force: true })} />
    </AdminProvider>
  );
}
