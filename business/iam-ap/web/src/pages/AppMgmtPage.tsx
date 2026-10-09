import { useEffect, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { AppRegistration } from '@/types';

/** 应用注册与准入管理（需 iam-ap:ADMIN）。 */
export default function AppMgmtPage() {
  const [apps, setApps] = useState<AppRegistration[]>([]);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const [appCode, setAppCode] = useState('');
  const [appName, setAppName] = useState('');
  const [sortNo, setSortNo] = useState(0);

  const [assignApp, setAssignApp] = useState('');
  const [assignUser, setAssignUser] = useState('');
  const [assignRoles, setAssignRoles] = useState('');

  const [queryUser, setQueryUser] = useState('');
  const [userApps, setUserApps] = useState<string[] | null>(null);

  async function loadApps() {
    setErr('');
    try {
      setApps(await api.get<AppRegistration[]>('/apps'));
    } catch (e: any) {
      setErr(e?.msg || '加载应用列表失败');
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
      const roles = assignRoles.split(',').map((r) => r.trim()).filter(Boolean);
      await api.post<void>(`/apps/${encodeURIComponent(assignApp)}/users`, { userId: assignUser, roles });
      setMsg(`已为用户 ${assignUser} 分配 ${assignApp}（角色：${roles.join(', ')}）`);
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

  return (
    <div className="card">
      <h2>应用与准入管理</h2>
      {err && <div className="err">{err}</div>}
      {msg && <div className="ok">{msg}</div>}

      <section>
        <h3>应用列表</h3>
        <table>
          <thead>
            <tr>
              <th>接入码</th>
              <th>名称</th>
              <th>状态</th>
              <th>排序</th>
            </tr>
          </thead>
          <tbody>
            {apps.map((a) => (
              <tr key={a.id}>
                <td>{a.appCode}</td>
                <td>{a.appName}</td>
                <td>{a.status}</td>
                <td>{a.sortNo}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h3>注册新应用</h3>
        <form className="row" onSubmit={onRegister}>
          <input placeholder="接入码(如 mds-ap)" value={appCode} onChange={(e) => setAppCode(e.target.value)} />
          <input placeholder="名称" value={appName} onChange={(e) => setAppName(e.target.value)} />
          <input
            type="number"
            placeholder="排序"
            value={sortNo}
            onChange={(e) => setSortNo(Number(e.target.value))}
          />
          <button disabled={busy || !appCode || !appName}>注册</button>
        </form>
      </section>

      <section>
        <h3>分配用户到应用</h3>
        <form className="row" onSubmit={onAssign}>
          <select value={assignApp} onChange={(e) => setAssignApp(e.target.value)}>
            <option value="">选择应用…</option>
            {apps.map((a) => (
              <option key={a.id} value={a.appCode}>
                {a.appCode}
              </option>
            ))}
          </select>
          <input placeholder="用户ID" value={assignUser} onChange={(e) => setAssignUser(e.target.value)} />
          <input
            placeholder="角色(逗号分隔,如 ADMIN)"
            value={assignRoles}
            onChange={(e) => setAssignRoles(e.target.value)}
          />
          <button disabled={busy || !assignApp || !assignUser}>分配</button>
        </form>
      </section>

      <section>
        <h3>查询用户已准入的应用</h3>
        <form className="row" onSubmit={onQueryUser}>
          <input placeholder="用户ID" value={queryUser} onChange={(e) => setQueryUser(e.target.value)} />
          <button disabled={!queryUser}>查询</button>
        </form>
        {userApps && (
          <div>
            <p>
              用户 <b>{queryUser}</b> 已准入 {userApps.length} 个应用：
            </p>
            <ul>
              {userApps.map((app) => (
                <li key={app}>
                  {app}{' '}
                  <button onClick={() => onRevoke(app, queryUser)}>撤销</button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>
    </div>
  );
}
