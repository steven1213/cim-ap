import { useEffect, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { AppRegistration, AssignmentDto } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { ADMISSION_GRANT, ADMISSION_REVOKE } from '@/lib/permCodes';
import { IconAlert, IconCheckCircle, IconInbox, IconLink } from '@/components/Icons';

/**
 * 准入授权：以「用户」为主线，查看并编辑其可进入的 ap 与角色组。
 *
 * <p>任何授予 / 撤销都会 bump 该用户令牌版本（后端自动），其存量令牌立即失效、需重新登录 —
 * 这正是「准入变更即时生效」的落点。写操作按钮按 `iam:admission:grant|revoke` 权限显隐。</p>
 */
export default function AdmissionsPage() {
  const [apps, setApps] = useState<AppRegistration[]>([]);
  const [userId, setUserId] = useState('admin');
  const [assignments, setAssignments] = useState<AssignmentDto[] | null>(null);
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<AppRegistration[]>('/apps').then(setApps).catch((e: any) => setErr(e?.msg || '加载应用失败'));
    // 默认即以 admin 查询一次，进入页面立即可见准入矩阵（符合常用使用习惯）
    void query();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  async function query(e?: FormEvent) {
    e?.preventDefault();
    if (!userId.trim()) return;
    setErr('');
    setAssignments(null);
    try {
      const list = await api.get<AssignmentDto[]>(`/apps/users/${encodeURIComponent(userId.trim())}/assignments`);
      setAssignments(list);
      const map: Record<string, string> = {};
      for (const a of list) map[a.appCode] = a.roles.join(', ');
      setEdits(map);
    } catch (e: any) {
      setErr(e?.msg || '查询失败');
    }
  }

  function roleOf(code: string): AssignmentDto | undefined {
    return assignments?.find((a) => a.appCode === code);
  }

  async function grant(appCode: string) {
    setErr('');
    setBusy(true);
    try {
      const roles = (edits[appCode] ?? '')
        .split(',')
        .map((r) => r.trim())
        .filter(Boolean);
      await api.post(`/apps/${encodeURIComponent(appCode)}/users`, { userId: userId.trim(), roles });
      flash(`已授予 ${userId} 进入 ${appCode}（角色：${roles.join(', ') || '无'}）`);
      await query();
    } catch (e: any) {
      setErr(e?.msg || '授予失败');
    } finally {
      setBusy(false);
    }
  }

  async function revoke(appCode: string) {
    setErr('');
    try {
      await api.del(`/apps/${encodeURIComponent(appCode)}/users/${encodeURIComponent(userId.trim())}`);
      flash(`已撤销 ${userId} 在 ${appCode} 的准入`);
      await query();
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

      <Panel title="按用户授权" sub="输入用户 ID 查看其准入矩阵，逐条授予 / 撤销">
        <form className="row" onSubmit={query}>
          <div className="field">
            <label className="field-label" htmlFor="au">
              用户 ID
            </label>
            <input
              id="au"
              className="mono"
              placeholder="admin"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
            />
          </div>
          <button type="submit" disabled={!userId.trim()}>
            查询
          </button>
        </form>

        {assignments && (
          <div className="table-wrap" style={{ marginTop: 14 }}>
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 150 }}>接入码</th>
                  <th style={{ width: 170 }}>应用名称</th>
                  <th style={{ width: 90 }}>准入</th>
                  <th>角色组（逗号分隔，可编辑）</th>
                  <th style={{ width: 170 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {apps.map((a) => {
                  const cur = roleOf(a.appCode);
                  const admitted = !!cur;
                  return (
                    <tr key={a.appCode}>
                      <td className="mono">{a.appCode}</td>
                      <td>{a.appName}</td>
                      <td>
                        <span className="status">
                          <i className={'led ' + (admitted ? 'ok' : '')} />
                          <b>{admitted ? '已准入' : '未准入'}</b>
                        </span>
                      </td>
                      <td>
                        <input
                          className="mono"
                          placeholder="ADMIN, OPERATOR"
                          value={edits[a.appCode] ?? ''}
                          onChange={(e) => setEdits((m) => ({ ...m, [a.appCode]: e.target.value }))}
                        />
                      </td>
                      <td>
                        <div className="cell-actions">
                          <Perms code={ADMISSION_GRANT}>
                            <button className="btn-sm" disabled={busy} onClick={() => grant(a.appCode)}>
                              <IconLink width={13} height={13} />
                              {admitted ? '更新' : '授予'}
                            </button>
                          </Perms>
                          {admitted && (
                            <Perms code={ADMISSION_REVOKE}>
                              <button className="btn-danger btn-sm" onClick={() => revoke(a.appCode)}>
                                撤销
                              </button>
                            </Perms>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {!assignments && (
          <div className="empty" style={{ marginTop: 14 }}>
            <IconInbox width={22} height={22} />
            <b>输入用户 ID 后点击「查询」</b>
            <span>将列出全部已注册应用及其准入 / 角色状态</span>
          </div>
        )}
      </Panel>

      <Panel title="变更影响" sub="准入变更即时生效">
        <ul className="note-list">
          <li>授予 / 更新 / 撤销准入都会 bump 该用户的令牌版本，其存量令牌立即失效（需重新登录）。</li>
          <li>禁用某应用后，其下所有已分配用户的准入一并失效（登录时 apps claim 不再包含该接入码）。</li>
          <li>角色组为 ap 内粗粒度分组（如 ADMIN / OPERATOR），业务系统的菜单/按钮权限由业务 ap 自行控制。</li>
        </ul>
      </Panel>
    </div>
  );
}
