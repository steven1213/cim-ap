import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { AppRegistration, OrgGrantRow, OrgNode } from '@/types';
import { orgTypeLabel, originLabel } from '@/lib/format';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { GRANT_GRANT, GRANT_REVOKE } from '@/lib/permCodes';
import {
  IconAlert,
  IconCheckCircle,
  IconOrgGrant,
  IconPlus,
  IconRefresh,
  IconShield,
  IconTrash,
} from '@/components/Icons';

/**
 * 组织授权（组织 × 应用）。
 *
 * <p>「给某个组织授予某 ap + 粗角色组」，IAM 在签发令牌时按用户归属（含祖先链）<b>运行时展开</b>到人，
 * 不落派生行——组织一改，受影响用户令牌版本 bump，重签即带新准入。
 * 个人授予与组织授予取<b>并集</b>（个人只能追加、不能扣减）。写操作按 `iam:grant:*` 显隐。</p>
 */
export default function OrgGrantsPage() {
  const { t } = useTranslation();
  const [grants, setGrants] = useState<OrgGrantRow[]>([]);
  const [nodes, setNodes] = useState<OrgNode[]>([]);
  const [apps, setApps] = useState<AppRegistration[]>([]);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  const [orgId, setOrgId] = useState('');
  const [appCode, setAppCode] = useState('');
  const [roles, setRoles] = useState('');
  const [includeChildren, setIncludeChildren] = useState(true);
  const [affected, setAffected] = useState<number | null>(null);

  const orgOptions = useMemo(
    () =>
      nodes
        .slice()
        .sort((a, b) => a.path.localeCompare(b.path))
        .map((n) => ({
          id: n.id,
          label: `${'·'.repeat(Math.max(0, n.path.split('/').filter(Boolean).length - 1))} ${n.name} (${n.code})`,
          source: n.source,
          nodeType: n.nodeType,
        })),
    [nodes],
  );

  async function load() {
    setLoading(true);
    setErr('');
    try {
      const [g, o, a] = await Promise.all([
        api.get<OrgGrantRow[]>('/admin/org-grants'),
        api.get<OrgNode[]>('/admin/orgs'),
        api.get<AppRegistration[]>('/apps'),
      ]);
      setGrants(g);
      setNodes(o);
      setApps(a);
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgGrants.errLoad'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    if (!orgId) {
      setAffected(null);
      return;
    }
    api
      .get<string[]>(`/admin/orgs/${encodeURIComponent(orgId)}/users`)
      .then((list) => setAffected(list.length))
      .catch(() => setAffected(null));
  }, [orgId]);

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  async function onGrant(e: FormEvent) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      await api.post('/admin/org-grants', {
        orgId,
        appCode,
        roles: roles
          .split(',')
          .map((r) => r.trim())
          .filter(Boolean),
        includeChildren,
      });
      flash(t('iam.orgGrants.msgGranted', { app: appCode }));
      setRoles('');
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgGrants.errGrant'));
    } finally {
      setBusy(false);
    }
  }

  async function onRevoke(g: OrgGrantRow) {
    if (!window.confirm(t('iam.orgGrants.confirmRevoke', { org: g.orgName ?? g.orgId, app: g.appCode }))) return;
    setErr('');
    try {
      await api.del(`/admin/org-grants/${encodeURIComponent(g.orgId)}/${encodeURIComponent(g.appCode)}`);
      flash(t('iam.orgGrants.msgRevoked'));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgGrants.errRevoke'));
    }
  }

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}
      {msg && (
        <div className="alert ok">
          <IconCheckCircle width={15} height={15} />
          <span>{msg}</span>
        </div>
      )}

      <Panel
        title={t('iam.orgGrants.title')}
        sub={t('iam.orgGrants.sub')}
        flush
        actions={
          <>
            <span className="tag">{t('iam.orgGrants.count', { n: grants.length })}</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.orgGrants.refreshing') : t('iam.orgGrants.refresh')}
            </button>
          </>
        }
      >
        {grants.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('iam.orgGrants.th.org')}</th>
                  <th>{t('iam.orgGrants.th.code')}</th>
                  <th>{t('iam.orgGrants.th.appCode')}</th>
                  <th>{t('iam.orgGrants.th.roles')}</th>
                  <th style={{ width: 120 }}>{t('iam.orgGrants.th.children')}</th>
                  <th style={{ width: 110 }}>{t('iam.orgGrants.th.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {grants.map((g) => (
                  <tr key={`${g.orgId}:${g.appCode}`}>
                    <td>{g.orgName ?? '—'}</td>
                    <td className="mono dim">{g.orgCode ?? g.orgId}</td>
                    <td>
                      <span className="tag pri mono">{g.appCode}</span>
                    </td>
                    <td>
                      <div className="tag-list">
                        {g.roles.length ? (
                          g.roles.map((r) => (
                            <span key={r} className="role-chip">
                              {r}
                            </span>
                          ))
                        ) : (
                          <span className="dim">{t('iam.orgGrants.onlyAdmission')}</span>
                        )}
                      </div>
                    </td>
                    <td>{g.includeChildren ? <span className="tag ok">{t('iam.orgGrants.yes')}</span> : <span className="tag">{t('iam.orgGrants.no')}</span>}</td>
                    <td>
                      <div className="cell-actions">
                        <Perms code={GRANT_REVOKE}>
                          <button className="btn-danger btn-sm" onClick={() => onRevoke(g)}>
                            <IconTrash width={13} height={13} />
                            {t('iam.orgGrants.revoke')}
                          </button>
                        </Perms>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconOrgGrant width={22} height={22} />
            <b>{t('iam.orgGrants.emptyTitle')}</b>
            <span>{t('iam.orgGrants.emptyHint')}</span>
          </div>
        )}
      </Panel>

      <Perms code={GRANT_GRANT}>
        <Panel title={t('iam.orgGrants.grantTitle')} sub={t('iam.orgGrants.grantSub')}>
          <form onSubmit={onGrant}>
          <div className="row">
            <div className="field">
              <label className="field-label" htmlFor="go">
                {t('iam.orgGrants.f.org')}
              </label>
              <select id="go" value={orgId} onChange={(e) => setOrgId(e.target.value)}>
                <option value="">{t('iam.orgGrants.selectOrg')}</option>
                {orgOptions.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                    {o.source === 'AD_SYNCED' ? '［AD］' : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label" htmlFor="ga">
                {t('iam.orgGrants.f.appCode')}
              </label>
              <select id="ga" value={appCode} onChange={(e) => setAppCode(e.target.value)}>
                <option value="">{t('iam.orgGrants.selectApp')}</option>
                {apps.map((a) => (
                  <option key={a.appCode} value={a.appCode}>
                    {a.appCode}
                    {a.appName ? ` · ${a.appName}` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label" htmlFor="gr">
                {t('iam.orgGrants.f.roles')}
              </label>
              <input
                id="gr"
                className="mono"
                placeholder="OPERATOR,SHIFT_LEAD"
                value={roles}
                onChange={(e) => setRoles(e.target.value)}
              />
            </div>
            <label className="check">
              <input
                type="checkbox"
                checked={includeChildren}
                onChange={(e) => setIncludeChildren(e.target.checked)}
              />
              <span>{t('iam.orgGrants.includeChildren')}</span>
            </label>
            <button type="submit" disabled={busy || !orgId || !appCode}>
              <IconPlus width={13} height={13} />
              {busy ? t('iam.orgGrants.granting') : t('iam.orgGrants.grant')}
            </button>
          </div>
        </form>
        <p className="hint">
          {t('iam.orgGrants.hint')}
          {affected != null && (
            <>
              {' '}
              {t('iam.orgGrants.affected', { n: affected })}
            </>
          )}
        </p>
        </Panel>
      </Perms>

      <Panel title={t('iam.orgGrants.divTitle')} sub={t('iam.orgGrants.divSub')}>
        <ul className="note-list">
          <li>{t('iam.orgGrants.divPersonal')}</li>
          <li>{t('iam.orgGrants.divOrg')}</li>
          <li>{t('iam.orgGrants.divUnion')}</li>
          <li>{t('iam.orgGrants.divAd')}</li>
          <li>
            {t('iam.orgGrants.typeRef')}
            <span className="mono">{orgTypeLabel('AREA')}</span> →
            <span className="mono">{orgTypeLabel('WORKSHOP')}</span> →
            <span className="mono">{orgTypeLabel('LINE')}</span> →
            <span className="mono">{orgTypeLabel('PROCESS')}</span> →
            <span className="mono">{orgTypeLabel('TEAM')}</span>
            {t('iam.orgGrants.localMaintain', { src: originLabel('IAM_MANAGED') })}
          </li>
        </ul>
        <div className="tag-list" style={{ marginTop: 8 }}>
          <span className="tag info">
            <IconShield width={12} height={12} />
            {t('iam.orgGrants.divIam')}
          </span>
        </div>
      </Panel>
    </div>
  );
}
