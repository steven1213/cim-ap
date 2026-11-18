// 角色管理（approval 接线）：页面实现在共享包 @cim/admin
import { useCallback } from 'react';
import { AdminProvider, RolesAdminPage as SharedPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { hasPerm } from '@/lib/usePermission';

export default function RolesAdminPage() {
  // dev 演示通道（无 IAM）在 hasPerm 内短路放行；sso 通道精确匹配
  const hasAll = useCallback((cs: string[]) => cs.every((c) => hasPerm(c)), []);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedPage />
    </AdminProvider>
  );
}
