// 组织管理（approval 接线（只读））：页面实现在共享包 @cim/admin，数据面在本系统目录代理 /approval/admin/directory/**（identity-directory.md 方案 A）
import { useCallback } from 'react';
import { AdminProvider, OrgPage as SharedPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { hasPerm } from '@/lib/usePermission';

export default function OrgPage() {
  // dev 演示通道（无 IAM）在 hasPerm 内短路放行；sso 通道精确匹配
  const hasAll = useCallback((cs: string[]) => cs.every((c) => hasPerm(c)), []);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedPage readOnly orgPath="/approval/admin/directory/orgs" usersPath="/approval/admin/directory/users" />
    </AdminProvider>
  );
}
