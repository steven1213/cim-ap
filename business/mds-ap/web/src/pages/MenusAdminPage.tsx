// 菜单管理（MDS 接线）：页面实现在共享包 @cim/admin，这里注入 MDS 的 http 与权限判定。
// MDS 的入口码即平台 sys:* 码（种子直接授予），共享页用缺省码即可，无需覆盖。
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
