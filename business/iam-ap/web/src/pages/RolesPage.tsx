import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import { CimTag, DataTable, Page, Panel, type DataTableColumn } from '@cim/ui';
import * as api from '@/lib/api';
import type { AppRoleGroup } from '@/types';

/** 角色组视图（路由级 `RequirePerm`，需 `iam:role:list`）：按接入码聚合实际在用的粗角色组与人数分布。 */
export default function RolesPage() {
  const { t } = useTranslation();

  const listRes = useResource<AppRoleGroup[]>(({ signal }) => api.get<AppRoleGroup[]>('/admin/roles', undefined, { signal }));
  const groups = listRes.data ?? [];
  const totalRoles = groups.reduce((s, g) => s + g.roles.length, 0);

  const columns = useMemo<Array<DataTableColumn<AppRoleGroup>>>(
    () => [
      { key: 'appCode', titleKey: 'iam.rolesView.th.appCode', width: 170, mono: true, render: (g) => g.appCode },
      { key: 'appName', titleKey: 'iam.rolesView.th.appName', width: 220, render: (g) => g.appName },
      {
        key: 'roles',
        titleKey: 'iam.rolesView.th.roles',
        render: (g) =>
          g.roles.length ? (
            <div className="tag-list">
              {g.roles.map((r) => (
                // CimTag 不收 title，人数提示包一层 span
                <span key={r.role} title={t('iam.rolesView.userCount', { n: r.userCount })}>
                  <CimTag tone="run" size="small">
                    {r.role}
                    <b className="tag-count">{r.userCount}</b>
                  </CimTag>
                </span>
              ))}
            </div>
          ) : (
            <span className="dim">{t('iam.rolesView.noRole')}</span>
          ),
      },
    ],
    [t],
  );

  return (
    <Page>
      <Panel
        title={t('iam.rolesView.title')}
        sub={t('iam.rolesView.sub')}
        actions={
          <>
            <CimTag>{t('iam.rolesView.countApps', { n: groups.length })}</CimTag>
            <CimTag tone="run">{t('iam.rolesView.countRoles', { n: totalRoles })}</CimTag>
          </>
        }
      >
        <DataTable<AppRoleGroup>
          flat
          columns={columns}
          rows={groups}
          rowKey={(g) => g.appCode}
          loading={listRes.loading}
          error={listRes.error?.message}
          onRetry={listRes.refresh}
          onRefresh={listRes.refresh}
          settingsKey="iam.rolesView"
          emptyTitleKey="iam.rolesView.emptyTitle"
          emptyHintKey="iam.rolesView.emptyHint"
        />
      </Panel>

      <Panel title={t('iam.rolesView.aboutTitle')} sub={t('iam.rolesView.aboutSub')}>
        <ul className="note-list">
          <li>{t('iam.rolesView.about1')}</li>
          <li>{t('iam.rolesView.about2')}</li>
          <li>{t('iam.rolesView.about3')}</li>
        </ul>
      </Panel>
    </Page>
  );
}
