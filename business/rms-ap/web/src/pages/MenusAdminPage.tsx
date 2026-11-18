// 菜单管理（RMS 接线）：页面实现在共享包 @cim/admin。
import { useCallback } from 'react';
import { AdminProvider, MenusAdminPage as SharedMenusAdminPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { usePermissions } from '@/lib/usePermission';

export default function MenusAdminPage() {
  const codes = usePermissions();
  const hasAll = useCallback((cs: string[]) => cs.every((c) => codes.includes(c)), [codes]);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedMenusAdminPage />
    </AdminProvider>
  );
}
