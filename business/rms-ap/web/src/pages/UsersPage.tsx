// 人员管理（RMS 接线（只读））：页面实现在共享包 @cim/admin，数据面在本系统目录代理 /rms/admin/directory/**
// readOnly=true：无新建/启停/重置口令/解锁/删除——业务系统不允许维护人员主数据。。
import { useCallback } from 'react';
import { AdminProvider, UsersPage as SharedUsersPage } from '@cim/admin';
import { httpClient } from '@/lib/api';
import { usePermissions } from '@/lib/usePermission';

export default function UsersPage() {
  const codes = usePermissions();
  const hasAll = useCallback((cs: string[]) => cs.every((c) => codes.includes(c)), [codes]);
  return (
    <AdminProvider http={httpClient} hasAll={hasAll}>
      <SharedUsersPage readOnly usersPath="/rms/admin/directory/users" orgsPath="/rms/admin/directory/orgs" />
    </AdminProvider>
  );
}
