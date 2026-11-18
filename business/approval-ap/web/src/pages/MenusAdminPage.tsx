// 菜单管理（approval 接线）：页面实现在共享包 @cim/admin
import { useCallback } from 'react';
import { AdminProvider, MenusAdminPage as SharedPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { hasPerm } from '@/lib/usePermission';

export default function MenusAdminPage() {
  // dev 演示通道（无 IAM）在 hasPerm 内短路放行；sso 通道精确匹配
  const hasAll = useCallback((cs: string[]) => cs.every((c) => hasPerm(c)), []);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedPage />
    </AdminProvider>
  );
}
