import { useEffect, useMemo, useState, type FormEvent } from 'react';
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
      setErr(e?.msg || '加载组织授权失败');
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
      flash(`已授予 ${appCode}（受影响用户令牌已失效，需重登生效）`);
      setRoles('');
      await load();
    } catch (e: any) {
      setErr(e?.msg || '授予失败');
    } finally {
      setBusy(false);
    }
  }

  async function onRevoke(g: OrgGrantRow) {
    if (!window.confirm(`确认撤销「${g.orgName ?? g.orgId}」的 ${g.appCode} 授予？`)) return;
    setErr('');
    try {
      await api.del(`/admin/org-grants/${encodeURIComponent(g.orgId)}/${encodeURIComponent(g.appCode)}`);
      flash('已撤销组织授予');
      await load();
    } catch (e: any) {
      setErr(e?.msg || '撤销失败');
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
        title="组织授予清单"
        sub="组织 → ap → 粗角色组；签发令牌时按用户归属（含祖先链）实时展开"
        flush
        actions={
          <>
            <span className="tag">{grants.length} 条</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? '刷新中' : '刷新'}
            </button>
          </>
        }
      >
        {grants.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>组织</th>
                  <th>编码</th>
                  <th>接入码</th>
                  <th>角色组</th>
                  <th style={{ width: 120 }}>覆盖子组织</th>
                  <th style={{ width: 110 }}>操作</th>
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
                          <span className="dim">（仅准入，无角色）</span>
                        )}
                      </div>
                    </td>
                    <td>{g.includeChildren ? <span className="tag ok">是</span> : <span className="tag">否</span>}</td>
                    <td>
                      <div className="cell-actions">
                        <Perms code={GRANT_REVOKE}>
                          <button className="btn-danger btn-sm" onClick={() => onRevoke(g)}>
                            <IconTrash width={13} height={13} />
                            撤销
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
            <b>暂无组织授予</b>
            <span>在下方按组织批量授予准入</span>
          </div>
        )}
      </Panel>

      <Perms code={GRANT_GRANT}>
        <Panel title="授予组织准入" sub="先把组织树建对并挂人，再给组织授权——人员进出组织自动继承/失去准入">
          <form onSubmit={onGrant}>
          <div className="row">
            <div className="field">
              <label className="field-label" htmlFor="go">
                组织
              </label>
              <select id="go" value={orgId} onChange={(e) => setOrgId(e.target.value)}>
                <option value="">请选择组织</option>
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
                接入码
              </label>
              <select id="ga" value={appCode} onChange={(e) => setAppCode(e.target.value)}>
                <option value="">请选择应用</option>
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
                角色组（逗号分隔）
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
              <span>覆盖子组织</span>
            </label>
            <button type="submit" disabled={busy || !orgId || !appCode}>
              <IconPlus width={13} height={13} />
              {busy ? '授予中…' : '授予'}
            </button>
          </div>
        </form>
        <p className="hint">
          勾选「覆盖子组织」时，授予作用于该组织<b>及其全部后代</b>；不勾选则仅作用于本组织直属人员。
          {affected != null && (
            <>
              {' '}
              当前所选组织（含子组织）共 <b>{affected}</b> 人，授予后其令牌版本将被 bump、需重新登录。
            </>
          )}
        </p>
        </Panel>
      </Perms>

      <Panel title="与「准入授权」的分工" sub="个人 vs 组织">
        <ul className="note-list">
          <li>
            <b>个人准入（准入授权页）</b>：给某个用户单独开某 ap 的准入与角色组，用于例外与临时授权。
          </li>
          <li>
            <b>组织授予（本页）</b>：给组织整体开准入，组织内人员（含子组织）自动生效——<b>入职即通、转岗即变、离职即断</b>。
          </li>
          <li>
            <b>两者取并集</b>：个人授予只能追加、不能扣减。要收回权限请撤销组织授予或把该用户移出组织。
          </li>
          <li>
            <b>AD 组织</b>（标记［AD］）由同步器维护，可作为授权目标；但 IAM 侧不能改其名称与层级。
          </li>
          <li>
            组织类型参考：<span className="mono">{orgTypeLabel('AREA')}</span> →
            <span className="mono">{orgTypeLabel('WORKSHOP')}</span> →
            <span className="mono">{orgTypeLabel('LINE')}</span> →
            <span className="mono">{orgTypeLabel('PROCESS')}</span> →
            <span className="mono">{orgTypeLabel('TEAM')}</span>；来源
            <span className="mono"> {originLabel('IAM_MANAGED')}</span> 可本地维护。
          </li>
        </ul>
        <div className="tag-list" style={{ marginTop: 8 }}>
          <span className="tag info">
            <IconShield width={12} height={12} />
            IAM 只管准入，不介入业务系统内部权限
          </span>
        </div>
      </Panel>
    </div>
  );
}
