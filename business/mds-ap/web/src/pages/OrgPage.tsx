// 组织管理（MDS 接线，只读）：页面实现在共享包 @cim/admin；组织主数据在 IAM，
// 数据面在本系统目录代理 /mds/admin/directory/**（identity-directory.md 方案 A）。
// readOnly=true：无新增/编辑/删除控件——业务系统不允许维护组织主数据。
import { useCallback } from 'react';
import { AdminProvider, OrgPage as SharedOrgPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { usePermissions } from '@/lib/usePermission';

export default function OrgPage() {
  const codes = usePermissions();
  const hasAll = useCallback((cs: string[]) => cs.every((c) => codes.includes(c)), [codes]);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedOrgPage readOnly orgPath="/mds/admin/directory/orgs" usersPath="/mds/admin/directory/users" />
    </AdminProvider>
  );
}
