import { useEffect, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { AppRegistration, AppStatus } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { APP_CREATE, APP_UPDATE } from '@/lib/permCodes';
import {
  IconAlert,
  IconApps,
  IconCheckCircle,
  IconPower,
  IconRefresh,
} from '@/components/Icons';

/** 应用注册：登记业务接入码、维护状态（按钮按 `iam:app:*` 权限显隐）。准入分配见「准入授权」。 */
export default function AppMgmtPage() {
  const [apps, setApps] = useState<AppRegistration[]>([]);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);

  const [appCode, setAppCode] = useState('');
  const [appName, setAppName] = useState('');
  const [sortNo, setSortNo] = useState(0);

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

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  async function onRegister(e: FormEvent) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      await api.post<AppRegistration>('/apps', { appCode: appCode.trim(), appName, sortNo: Number(sortNo) });
      flash(`已注册应用 ${appCode}`);
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

  async function toggleStatus(a: AppRegistration) {
    setErr('');
    const next: AppStatus = a.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    if (next === 'DISABLED' && !window.confirm(`确认停用 ${a.appCode}？其下所有用户对该应用的准入将立即失效。`)) {
      return;
    }
    try {
      await api.put(`/apps/${encodeURIComponent(a.appCode)}`, { appName: a.appName, status: next });
      flash(`${a.appCode} 已${next === 'ENABLED' ? '启用' : '停用'}`);
      await loadApps();
    } catch (e: any) {
      setErr(e?.msg || '更新失败');
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
        sub="已注册的业务接入码；接入码即令牌 apps claim 的取值"
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
                  <th style={{ width: 100 }}>状态</th>
                  <th className="num" style={{ width: 80 }}>
                    排序
                  </th>
                  <th style={{ width: 120 }}>操作</th>
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
                    <td>
                      <Perms code={APP_UPDATE}>
                        <button className="btn-ghost btn-sm" onClick={() => toggleStatus(a)}>
                          <IconPower width={13} height={13} />
                          {a.status === 'ENABLED' ? '停用' : '启用'}
                        </button>
                      </Perms>
                    </td>
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

      <Perms code={APP_CREATE}>
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
          <button type="submit" className="btn-block" disabled={busy || !appCode.trim() || !appName} style={{ marginTop: 12 }}>
            注册应用
          </button>
          </form>
        </Panel>
      </Perms>

      <Panel title="接入指引" sub="业务 ap 如何对接 IAM 令牌">
        <ul className="note-list">
          <li>业务 ap 从 <code>/.well-known/jwks.json</code> 拉取公钥，本地验签（RS256），无需每请求回查 IAM。</li>
          <li>准入判定：令牌 <code>apps</code> claim 含本 ap 接入码即放行；否则 403。</li>
          <li>角色组：令牌 <code>roles</code> claim 携带本 ap 内的粗角色组，业务内部权限据此再细分。</li>
        </ul>
      </Panel>
    </div>
  );
}
