import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useResource } from '@cim/data';
import {
  CimSpin,
  CimTag,
  DataTable,
  EmptyState,
  InlineAlert,
  KpiCard,
  KpiGrid,
  Page,
  Panel,
  type DataTableColumn,
  CimButton,
} from '@cim/ui';
import type { StatusTone } from '@cim/theme';
import * as api from '@/lib/api';
import type { OverviewDto } from '@/types';
import { useAuthStore } from '@/store/authStore';
import { hasPermission } from '@/lib/permissions';
import { OVERVIEW_VIEW } from '@/lib/permCodes';
import { auditLabel, auditTone, fmtRelative, orgTypeLabel, originLabel } from '@/lib/format';
import {
  IconAlert,
  IconApps,
  IconList,
  IconLock,
  IconMonitor,
  IconShield,
  IconTree,
  IconUser,
  IconUsers,
} from '@/components/Icons';

/** `auditTone` 的 4 档 → 平台 5 档（info 落 run，道理同审计页）。 */
function toneOf(type: string, result?: string | null): StatusTone {
  const v = auditTone(type, result);
  return v === 'info' ? 'run' : v;
}

/**
 * 概览：
 * - 具备 `iam:overview:view` → 平台态势（用户/接入/会话/锁定 + 最近审计事件）；
 * - 否则 → 个人身份、准入矩阵与会话安全属性。
 *
 * <p>用**权限码**而非「角色名」分支：角色是可配置的（管理端能新建角色），
 * 判据必须落在权限码上，否则自定义角色拿不到管理视角、或越权看到管理视角。</p>
 */
export default function DashboardPage() {
  const permissions = useAuthStore((s) => s.permissions);
  const admin = hasPermission(permissions, OVERVIEW_VIEW);

  if (admin) return <AdminOverview />;
  return <PersonalOverview />;
}

function AdminOverview() {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const ovRes = useResource<OverviewDto>(({ signal }) => api.get<OverviewDto>('/admin/overview', undefined, { signal }));

  const eventColumns = useMemo<Array<DataTableColumn<OverviewDto['recentEvents'][number]>>>(
    () => [
      {
        key: 'createTime',
        titleKey: 'iam.dashboard.th.time',
        width: 150,
        mono: true,
        render: (e) => <span className="dim">{fmtRelative(e.createTime)}</span>,
      },
      {
        key: 'type',
        titleKey: 'iam.dashboard.th.type',
        width: 150,
        render: (e) => (
          <CimTag size="small" tone={toneOf(e.type, e.result)}>
            {auditLabel(e.type)}
          </CimTag>
        ),
      },
      {
        key: 'actor',
        titleKey: 'iam.dashboard.th.operator',
        width: 130,
        mono: true,
        ellipsis: true,
        render: (e) => e.actor ?? '—',
      },
      {
        key: 'subject',
        titleKey: 'iam.dashboard.th.target',
        width: 140,
        mono: true,
        ellipsis: true,
        render: (e) => e.subject ?? '—',
      },
      {
        key: 'detail',
        titleKey: 'iam.dashboard.th.detail',
        ellipsis: true,
        render: (e) => <span className="dim">{e.detail ?? '—'}</span>,
      },
    ],
    [],
  );

  if (ovRes.error) {
    return (
      <Page>
        <Panel title={t('iam.dashboard.platformTitle')}>
          <InlineAlert tone="err">{ovRes.error.message || t('iam.dashboard.errLoad')}</InlineAlert>
          <CimButton type="primary" size="small" style={{ marginTop: 12 }} onClick={ovRes.refresh}>
            {t('iam.common.refresh')}
          </CimButton>
        </Panel>
      </Page>
    );
  }

  const ov = ovRes.data;
  if (!ov) {
    return (
      <Page>
        <Panel title={t('iam.dashboard.platformTitle')}>
          <CimSpin block height={240} />
        </Panel>
      </Page>
    );
  }

  const failTone = ov.loginFailureCount > 0 ? 'warn' : 'ok';

  return (
    <Page>
      <KpiGrid>
        <KpiCard icon={<IconUsers width={16} height={16} />} label={t('iam.dashboard.kpiUser')} value={`${ov.enabledUserCount} / ${ov.userCount}`} sub={t('iam.dashboard.kpiUserSub', { n: ov.disabledUserCount })} />
        <KpiCard icon={<IconUser width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiProfile')} value={ov.profileCount} sub={t('iam.dashboard.kpiProfileSub', { n: ov.adProfileCount })} />
        <KpiCard icon={<IconTree width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiOrg')} value={ov.orgCount} sub={t('iam.dashboard.kpiOrgSub', { n: ov.orgManagedCount })} />
        <KpiCard icon={<IconApps width={16} height={16} />} tone="ok" label={t('iam.dashboard.kpiApp')} value={`${ov.enabledAppCount} / ${ov.appCount}`} sub={t('iam.dashboard.kpiAppSub')} />
        <KpiCard icon={<IconMonitor width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiSession')} value={ov.activeSessionCount} sub={t('iam.dashboard.kpiSessionSub')} />
        <KpiCard icon={<IconLock width={16} height={16} />} tone={ov.lockedCount > 0 ? 'warn' : 'ok'} label={t('iam.dashboard.kpiLock')} value={ov.lockedCount} sub={t('iam.dashboard.kpiLockSub')} />
        <KpiCard icon={<IconShield width={16} height={16} />} tone="ok" label={t('iam.dashboard.kpiLoginOk')} value={ov.loginSuccessCount} sub={t('iam.dashboard.kpiCumulative')} />
        <KpiCard icon={<IconAlert width={16} height={16} />} tone={failTone} label={t('iam.dashboard.kpiLoginFail')} value={ov.loginFailureCount} sub={t('iam.dashboard.kpiLoginFailSub')} />
      </KpiGrid>

      <Panel
        title={t('iam.dashboard.recentAudit')}
        actions={
          <CimButton size="small" onClick={() => navigate('/audit')}>
            <IconList width={13} height={13} />
            {t('iam.dashboard.viewAll')}
          </CimButton>
        }
      >
        <DataTable
          flat
          columns={eventColumns}
          rows={ov.recentEvents}
          rowKey={(e) => e.id}
          onRefresh={ovRes.refresh}
          emptyTitleKey="iam.dashboard.noAudit"
          scrollX="max-content"
        />
      </Panel>
    </Page>
  );
}

function PersonalOverview() {
  const user = useAuthStore((s) => s.user);
  const { t } = useTranslation();

  const orgs = user?.orgs ?? [];
  const roleEntries = Object.entries(user?.roles ?? {});
  const roleCount = roleEntries.reduce((sum, [, roles]) => sum + roles.length, 0);
  const apps = user?.apps ?? [];

  const orgColumns = useMemo<Array<DataTableColumn<(typeof orgs)[number]>>>(
    () => [
      {
        key: 'name',
        titleKey: 'iam.dashboard.th.org',
        render: (o) => (
          <>
            {o.name} <span className="cim-mono dim">{o.code}</span>
          </>
        ),
      },
      {
        key: 'nodeType',
        titleKey: 'iam.dashboard.th.type',
        width: 90,
        render: (o) => (
          <CimTag size="small">{orgTypeLabel(o.nodeType)}</CimTag>
        ),
      },
      {
        key: 'primary',
        titleKey: 'iam.dashboard.th.primary',
        width: 80,
        render: (o) => (o.primary ? <CimTag size="small" tone="ok">{t('iam.dashboard.yes')}</CimTag> : <span className="dim">—</span>),
      },
    ],
    [t],
  );

  const matrixColumns = useMemo<Array<DataTableColumn<{ app: string; roles: string[] }>>>(
    () => [
      { key: 'app', titleKey: 'iam.dashboard.th.appCode', mono: true, width: 200, render: (r) => r.app },
      {
        key: 'admitted',
        titleKey: 'iam.dashboard.th.admission',
        width: 130,
        render: () => (
          <CimTag size="small" tone="ok">
            {t('iam.dashboard.admitted')}
          </CimTag>
        ),
      },
      {
        key: 'roles',
        titleKey: 'iam.dashboard.th.roles',
        render: (r) =>
          r.roles.length ? (
            <div className="tag-list">
              {r.roles.map((x) => (
                <CimTag key={x} size="small" tone="run">
                  {x}
                </CimTag>
              ))}
            </div>
          ) : (
            <CimTag size="small">{t('iam.dashboard.noRole')}</CimTag>
          ),
      },
    ],
    [t],
  );

  if (!user) {
    return (
      <Page>
        <Panel title={t('iam.dashboard.profileTitle')}>
          <EmptyState titleKey="iam.dashboard.loadingUser" />
        </Panel>
      </Page>
    );
  }

  const matrixRows = roleEntries.map(([app, roles]) => ({ app, roles }));

  return (
    <Page>
      <KpiGrid>
        <KpiCard icon={<IconUser width={16} height={16} />} label={t('iam.dashboard.kpiUserId')} value={user.userId} />
        <KpiCard icon={<IconApps width={16} height={16} />} tone="ok" label={t('iam.dashboard.kpiApps')} value={apps.length} />
        <KpiCard icon={<IconShield width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiRoles')} value={roleCount} />
        <KpiCard icon={<IconTree width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiOrgs')} value={orgs.length} sub={orgs.filter((o) => o.primary).map((o) => o.name).join(', ') || undefined} />
      </KpiGrid>

      <div className="grid-2">
        <Panel title={t('iam.dashboard.identityTitle')}>
          <dl className="meta">
            <dt className="meta-k">{t('iam.dashboard.mk.userId')}</dt>
            <dd className="meta-v mono">{user.userId}</dd>
            <dt className="meta-k">{t('iam.dashboard.mk.username')}</dt>
            <dd className="meta-v">{user.username}</dd>
            <dt className="meta-k">{t('iam.dashboard.mk.name')}</dt>
            <dd className="meta-v">{user.displayName ?? '—'}</dd>
            <dt className="meta-k">{t('iam.dashboard.mk.empNo')}</dt>
            <dd className="meta-v mono">{user.employeeNo ?? '—'}</dd>
            <dt className="meta-k">{t('iam.dashboard.mk.job')}</dt>
            <dd className="meta-v">{user.jobTitle ?? '—'}</dd>
            <dt className="meta-k">{t('iam.dashboard.mk.source')}</dt>
            <dd className="meta-v">{originLabel(user.source)}</dd>
            <dt className="meta-k">{t('iam.dashboard.mk.tenant')}</dt>
            <dd className="meta-v mono">{user.tenantId ?? '—'}</dd>
          </dl>
        </Panel>

          <DataTable
            title={t('iam.dashboard.myOrgsTitle')}
            columns={orgColumns}
            rows={orgs}
            rowKey={(o) => o.orgId}
            emptyTitleKey="iam.dashboard.noOrgs"
            emptyHintKey="iam.dashboard.noOrgsHint"
            showCount={false}
          />
      </div>

      <Panel title={t('iam.dashboard.sessionTitle')}>
        <dl className="meta">
          <dt className="meta-k">{t('iam.dashboard.mk.authSrc')}</dt>
          <dd className="meta-v">{t('iam.dashboard.v.localCred')}</dd>
          <dt className="meta-k">{t('iam.dashboard.mk.signAlgo')}</dt>
          <dd className="meta-v mono">RS256 / JWT</dd>
          <dt className="meta-k">{t('iam.dashboard.mk.verify')}</dt>
          <dd className="meta-v">{t('iam.dashboard.v.jwks')}</dd>
          <dt className="meta-k">{t('iam.dashboard.mk.admission')}</dt>
          <dd className="meta-v mono">{t('iam.dashboard.v.appsClaim')}</dd>
          <dt className="meta-k">{t('iam.dashboard.mk.invalid')}</dt>
          <dd className="meta-v">{t('iam.dashboard.v.version')}</dd>
        </dl>
      </Panel>

      <Panel
        title={t('iam.dashboard.matrixTitle')}
        actions={<CimTag>{t('iam.dashboard.matrixCount', { n: apps.length })}</CimTag>}
      >
        <DataTable
          flat
          columns={matrixColumns}
          rows={matrixRows}
          rowKey={(r) => r.app}
          emptyTitleKey="iam.dashboard.noApp"
          emptyHintKey="iam.dashboard.noAppHint"
          showCount={false}
        />
      </Panel>
    </Page>
  );
}

