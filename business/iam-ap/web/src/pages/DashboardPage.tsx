import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import * as api from '@/lib/api';
import type { OverviewDto } from '@/types';
import { useAuthStore } from '@/store/authStore';
import { hasPermission } from '@/lib/permissions';
import { OVERVIEW_VIEW } from '@/lib/permCodes';
import { auditLabel, auditTone, fmtRelative, orgTypeLabel, originLabel } from '@/lib/format';
import Panel from '@/components/Panel';
import {
  IconAlert,
  IconApps,
  IconGauge,
  IconInbox,
  IconList,
  IconLock,
  IconMonitor,
  IconShield,
  IconTree,
  IconUser,
  IconUsers,
} from '@/components/Icons';

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
  const [ov, setOv] = useState<OverviewDto | null>(null);
  const [err, setErr] = useState('');
  const navigate = useNavigate();
  const { t } = useTranslation();

  useEffect(() => {
    api
      .get<OverviewDto>('/admin/overview')
      .then(setOv)
      .catch((e: any) => setErr(e?.msg || t('iam.dashboard.errLoad')));
  }, []);

  if (err) {
    return (
      <div className="page">
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      </div>
    );
  }

  if (!ov) {
    return (
      <Panel title={t('iam.dashboard.platformTitle')}>
        <div className="empty">
          <IconGauge width={24} height={24} />
          <b>{t('iam.dashboard.loadingPlatform')}</b>
        </div>
      </Panel>
    );
  }

  const failTone = ov.loginFailureCount > 0 ? 'warn' : 'ok';

  return (
    <div className="page">
      <div className="kpi-grid">
        <Kpi icon={<IconUsers width={16} height={16} />} label={t('iam.dashboard.kpiUser')} value={`${ov.enabledUserCount} / ${ov.userCount}`} sub={t('iam.dashboard.kpiUserSub', { n: ov.disabledUserCount })} />
        <Kpi icon={<IconUser width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiProfile')} value={ov.profileCount} sub={t('iam.dashboard.kpiProfileSub', { n: ov.adProfileCount })} />
        <Kpi icon={<IconTree width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiOrg')} value={ov.orgCount} sub={t('iam.dashboard.kpiOrgSub', { n: ov.orgManagedCount })} />
        <Kpi icon={<IconApps width={16} height={16} />} tone="ok" label={t('iam.dashboard.kpiApp')} value={`${ov.enabledAppCount} / ${ov.appCount}`} sub={t('iam.dashboard.kpiAppSub')} />
        <Kpi icon={<IconMonitor width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiSession')} value={ov.activeSessionCount} sub={t('iam.dashboard.kpiSessionSub')} />
        <Kpi icon={<IconLock width={16} height={16} />} tone={ov.lockedCount > 0 ? 'warn' : 'ok'} label={t('iam.dashboard.kpiLock')} value={ov.lockedCount} sub={t('iam.dashboard.kpiLockSub')} />
        <Kpi icon={<IconShield width={16} height={16} />} tone="ok" label={t('iam.dashboard.kpiLoginOk')} value={ov.loginSuccessCount} sub={t('iam.dashboard.kpiCumulative')} />
        <Kpi icon={<IconAlert width={16} height={16} />} tone={failTone} label={t('iam.dashboard.kpiLoginFail')} value={ov.loginFailureCount} sub={t('iam.dashboard.kpiLoginFailSub')} />
      </div>

      <Panel
        title={t('iam.dashboard.recentAudit')}
        sub={t('iam.dashboard.recentAuditSub')}
        flush
        actions={
          <button className="btn-ghost btn-sm" onClick={() => navigate('/audit')}>
            <IconList width={13} height={13} />
            {t('iam.dashboard.viewAll')}
          </button>
        }
      >
        {ov.recentEvents.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 150 }}>{t('iam.dashboard.th.time')}</th>
                  <th style={{ width: 120 }}>{t('iam.dashboard.th.type')}</th>
                  <th style={{ width: 120 }}>{t('iam.dashboard.th.operator')}</th>
                  <th style={{ width: 130 }}>{t('iam.dashboard.th.target')}</th>
                  <th>{t('iam.dashboard.th.detail')}</th>
                </tr>
              </thead>
              <tbody>
                {ov.recentEvents.map((e) => (
                  <tr key={e.id}>
                    <td className="mono dim">{fmtRelative(e.createTime)}</td>
                    <td>
                      <span className={'tag ' + auditTone(e.type, e.result)}>{auditLabel(e.type)}</span>
                    </td>
                    <td className="mono">{e.actor ?? '—'}</td>
                    <td className="mono">{e.subject ?? '—'}</td>
                    <td className="dim">{e.detail ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconInbox width={22} height={22} />
            <b>{t('iam.dashboard.noAudit')}</b>
          </div>
        )}
      </Panel>
    </div>
  );
}

function PersonalOverview() {
  const user = useAuthStore((s) => s.user);
  const { t } = useTranslation();

  if (!user) {
    return (
      <Panel title={t('iam.dashboard.profileTitle')}>
        <div className="empty">
          <IconGauge width={24} height={24} />
          <b>{t('iam.dashboard.loadingUser')}</b>
        </div>
      </Panel>
    );
  }

  const apps = user.apps ?? [];
  const roleEntries = Object.entries(user.roles ?? {});
  const roleCount = roleEntries.reduce((sum, [, roles]) => sum + roles.length, 0);
  const orgs = user.orgs ?? [];

  return (
    <div className="page">
      <div className="kpi-grid">
        <Kpi icon={<IconUser width={16} height={16} />} label={t('iam.dashboard.kpiUserId')} value={user.userId} />
        <Kpi icon={<IconApps width={16} height={16} />} tone="ok" label={t('iam.dashboard.kpiApps')} value={apps.length} />
        <Kpi icon={<IconShield width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiRoles')} value={roleCount} />
        <Kpi icon={<IconTree width={16} height={16} />} tone="info" label={t('iam.dashboard.kpiOrgs')} value={orgs.length} sub={orgs.filter((o) => o.primary).map((o) => o.name).join(', ') || undefined} />
      </div>

      <div className="grid-2">
        <Panel title={t('iam.dashboard.identityTitle')} sub={t('iam.dashboard.identitySub')}>
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

        <Panel title={t('iam.dashboard.myOrgsTitle')} sub={t('iam.dashboard.myOrgsSub')}>
          {orgs.length ? (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('iam.dashboard.th.org')}</th>
                    <th style={{ width: 84 }}>{t('iam.dashboard.th.type')}</th>
                    <th style={{ width: 76 }}>{t('iam.dashboard.th.primary')}</th>
                  </tr>
                </thead>
                <tbody>
                  {orgs.map((o) => (
                    <tr key={o.orgId}>
                      <td>
                        {o.name} <span className="mono dim">{o.code}</span>
                      </td>
                      <td>
                        <span className="tag">{orgTypeLabel(o.nodeType)}</span>
                      </td>
                      <td>{o.primary ? <span className="tag ok">{t('iam.dashboard.yes')}</span> : <span className="dim">—</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="empty">
              <IconTree width={22} height={22} />
              <b>{t('iam.dashboard.noOrgs')}</b>
              <span>{t('iam.dashboard.noOrgsHint')}</span>
            </div>
          )}
        </Panel>
      </div>

        <Panel title={t('iam.dashboard.sessionTitle')} sub={t('iam.dashboard.sessionSub')}>
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
          sub={t('iam.dashboard.matrixSub')}
          flush
          actions={<span className="tag">{t('iam.dashboard.matrixCount', { n: apps.length })}</span>}
        >
        {roleEntries.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                    <th>{t('iam.dashboard.th.appCode')}</th>
                    <th>{t('iam.dashboard.th.admission')}</th>
                    <th>{t('iam.dashboard.th.roles')}</th>
                </tr>
              </thead>
              <tbody>
                {roleEntries.map(([app, roles]) => (
                  <tr key={app}>
                    <td className="mono">{app}</td>
                    <td>
                      <span className="status">
                        <i className="led ok" />
                        <b>{t('iam.dashboard.admitted')}</b>
                      </span>
                    </td>
                    <td>
                      {roles.length ? (
                        <div className="tag-list">
                          {roles.map((r) => (
                            <span key={r} className="tag pri">
                              {r}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="tag">{t('iam.dashboard.noRole')}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
              <IconInbox width={22} height={22} />
              <b>{t('iam.dashboard.noApp')}</b>
              <span>{t('iam.dashboard.noAppHint')}</span>
          </div>
        )}
      </Panel>
    </div>
  );
}

function Kpi({
  icon,
  label,
  value,
  sub,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  sub?: string;
  tone?: 'ok' | 'info' | 'warn';
}) {
  return (
    <div className={'kpi' + (tone ? ' ' + tone : '')}>
      <div className="kpi-ico">{icon}</div>
      <div className="kpi-main">
        <div className="kpi-label">{label}</div>
        <div className="kpi-val">{value}</div>
        {sub && <div className="kpi-sub">{sub}</div>}
      </div>
    </div>
  );
}
