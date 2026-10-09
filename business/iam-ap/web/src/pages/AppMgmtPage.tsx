import { useEffect, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { AppRegistration } from '@/types';
import Panel from '@/components/Panel';
import { IconAlert, IconApps, IconCheckCircle, IconInbox, IconRefresh } from '@/components/Icons';

/** 应用注册与准入管理（需 iam-ap:ADMIN）。 */
export default function AppMgmtPage() {
  const [apps, setApps] = useState<AppRegistration[]>([]);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);

  const [appCode, setAppCode] = useState('');
  const [appName, setAppName] = useState('');
  const [sortNo, setSortNo] = useState(0);

  const [assignApp, setAssignApp] = useState('');
  const [assignUser, setAssignUser] = useState('');
  const [assignRoles, setAssignRoles] = useState('');

  const [queryUser, setQueryUser] = useState('');
  const [userApps, setUserApps] = useState<string[] | null>(null);

  async function loadApps() {
    setLoading(true);
    setErr('');
    try {
      setApps(await api.get<AppRegistration[]>('/apps'));
    } catch (e: any) {
      setErr(e?.msg || '加载应用列表失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadApps();
  }, []);

  async function onRegister(e: FormEvent) {
    e.preventDefault();
    setMsg('');
    setErr('');
    setBusy(true);
    try {
      await api.post<AppRegistration>('/apps', { appCode, appName, sortNo: Number(sortNo) });
      setMsg(`已注册应用 ${appCode}`);
      setAppCode('');
      setAppName('');
      setSortNo(0);
      await loadApps();
    } catch (e: any) {
      setErr(e?.msg || '注册失败');
    } finally {
      setBusy(false);
    }
  }

  async function onAssign(e: FormEvent) {
    e.preventDefault();
    setMsg('');
    setErr('');
    setBusy(true);
    try {
      const roles = assignRoles
        .split(',')
        .map((r) => r.trim())
        .filter(Boolean);
      await api.post<void>(`/apps/${encodeURIComponent(assignApp)}/users`, { userId: assignUser, roles });
      setMsg(`已为用户 ${assignUser} 分配 ${assignApp}（角色：${roles.join(', ') || '无'}）`);
      setAssignUser('');
      setAssignRoles('');
    } catch (e: any) {
      setErr(e?.msg || '分配失败');
    } finally {
      setBusy(false);
    }
  }

  async function onRevoke(app: string, userId: string) {
    setMsg('');
    setErr('');
    try {
      await api.del<void>(`/apps/${encodeURIComponent(app)}/users/${encodeURIComponent(userId)}`);
      setMsg(`已撤销 ${userId} 在 ${app} 的准入`);
      if (queryUser === userId) setUserApps((prev) => (prev ? prev.filter((a) => a !== app) : prev));
    } catch (e: any) {
      setErr(e?.msg || '撤销失败');
    }
  }

  async function onQueryUser(e: FormEvent) {
    e.preventDefault();
    setUserApps(null);
    setErr('');
    try {
      setUserApps(await api.get<string[]>(`/apps/users/${encodeURIComponent(queryUser)}/apps`));
    } catch (e: any) {
      setErr(e?.msg || '查询失败');
    }
  }

  const enabled = apps.filter((a) => a.status === 'ENABLED').length;

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
        title="应用清单"
        sub="已注册的业务接入码及其准入状态"
        flush
        actions={
          <>
            <span className="tag">{apps.length} 条</span>
            <span className="tag ok">{enabled} 启用</span>
            <button className="btn-ghost btn-sm" onClick={loadApps} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? '刷新中' : '刷新'}
            </button>
          </>
        }
      >
        {apps.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>接入码</th>
                  <th>应用名称</th>
                  <th>状态</th>
                  <th className="num">排序</th>
                  <th>注册 ID</th>
                </tr>
              </thead>
              <tbody>
                {apps.map((a) => (
                  <tr key={a.id}>
                    <td className="mono">{a.appCode}</td>
                    <td>{a.appName}</td>
                    <td>
                      <span className="status">
                        <i className={'led ' + (a.status === 'ENABLED' ? 'ok' : 'err')} />
                        <b>{a.status === 'ENABLED' ? '启用' : '停用'}</b>
                      </span>
                    </td>
                    <td className="num">{a.sortNo}</td>
                    <td className="mono">{a.id}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconApps width={22} height={22} />
            <b>暂无应用</b>
            <span>在下方「注册应用」中添加第一个业务接入码</span>
          </div>
        )}
      </Panel>

      <div className="grid-2">
        <Panel title="注册应用" sub="接入码将写入令牌的 apps claim">
          <form onSubmit={onRegister}>
            <div className="form-grid">
              <div className="field">
                <label className="field-label" htmlFor="reg-code">
                  接入码
                </label>
                <input
                  id="reg-code"
                  className="mono"
                  placeholder="mds-ap"
                  value={appCode}
                  onChange={(e) => setAppCode(e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="reg-name">
                  应用名称
                </label>
                <input
                  id="reg-name"
                  placeholder="MDS 设备数据服务"
                  value={appName}
                  onChange={(e) => setAppName(e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="reg-sort">
                  排序号
                </label>
                <input
                  id="reg-sort"
                  type="number"
                  placeholder="0"
                  value={sortNo}
                  onChange={(e) => setSortNo(Number(e.target.value))}
                />
              </div>
            </div>
            <p className="hint">接入码全局唯一，注册后即纳入 IAM 的 ap 注册表，用于准入判定。</p>
            <button type="submit" className="btn-block" disabled={busy || !appCode || !appName} style={{ marginTop: 12 }}>
              注册应用
            </button>
          </form>
        </Panel>

        <Panel title="分配准入" sub="授予用户进入某应用的准入与角色组">
          <form onSubmit={onAssign}>
            <div className="field">
              <label className="field-label" htmlFor="as-app">
                目标应用
              </label>
              <select id="as-app" value={assignApp} onChange={(e) => setAssignApp(e.target.value)}>
                <option value="">选择应用…</option>
                {apps.map((a) => (
                  <option key={a.id} value={a.appCode}>
                    {a.appCode} · {a.appName}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label" htmlFor="as-user">
                用户 ID
              </label>
              <input
                id="as-user"
                className="mono"
                placeholder="admin"
                value={assignUser}
                onChange={(e) => setAssignUser(e.target.value)}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="as-roles">
                角色组
                <span className="opt">逗号分隔，可留空</span>
              </label>
              <input
                id="as-roles"
                className="mono"
                placeholder="ADMIN, OPERATOR"
                value={assignRoles}
                onChange={(e) => setAssignRoles(e.target.value)}
              />
            </div>
            <button
              type="submit"
              className="btn-block"
              disabled={busy || !assignApp || !assignUser}
              style={{ marginTop: 12 }}
            >
              分配准入
            </button>
          </form>
        </Panel>
      </div>

      <Panel title="用户准入查询" sub="按用户 ID 查询其已准入的应用，并支持逐条撤销">
        <form className="row" onSubmit={onQueryUser}>
          <div className="field">
            <label className="field-label" htmlFor="q-user">
              用户 ID
            </label>
            <input
              id="q-user"
              className="mono"
              placeholder="admin"
              value={queryUser}
              onChange={(e) => setQueryUser(e.target.value)}
            />
          </div>
          <button type="submit" disabled={!queryUser}>
            查询
          </button>
        </form>

        {userApps && (
          <div style={{ marginTop: 14 }}>
            {userApps.length ? (
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>接入码</th>
                      <th>准入状态</th>
                      <th style={{ width: 96 }} />
                    </tr>
                  </thead>
                  <tbody>
                    {userApps.map((app) => (
                      <tr key={app}>
                        <td className="mono">{app}</td>
                        <td>
                          <span className="status">
                            <i className="led ok" />
                            <b>已准入</b>
                          </span>
                        </td>
                        <td>
                          <div className="cell-actions">
                            <button className="btn-danger btn-sm" onClick={() => onRevoke(app, queryUser)}>
                              撤销准入
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="empty">
                <IconInbox width={22} height={22} />
                <b>用户 {queryUser} 暂无准入应用</b>
              </div>
            )}
          </div>
        )}
      </Panel>
    </div>
  );
}
