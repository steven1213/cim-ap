import { useEffect, useMemo, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { AdminUserRow, OrgNode } from '@/types';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import { fmtDateTime, orgTypeLabel, originLabel } from '@/lib/format';
import Panel from '@/components/Panel';
import {
  IconAlert,
  IconBan,
  IconCheckCircle,
  IconEdit,
  IconKey,
  IconPlus,
  IconPower,
  IconRefresh,
  IconTree,
  IconTrash,
  IconUsers,
} from '@/components/Icons';

/**
 * 用户与档案（需 iam-ap:ADMIN）。
 *
 * <p>把「本地凭证账号」与「用户档案」合并成统一视图：AD 同步来的员工（无本地凭证）也会出现，
 * 每行标注档案来源与组织归属。口令相关操作沿用与登录一致的两层派生——浏览器内先做第一层 PBKDF2，
 * 仅上传 clientHash + 随机盐，明文口令不出浏览器。</p>
 */

type PanelMode = null | 'create' | 'reset' | 'profile' | 'orgs';

export default function UsersPage() {
  const [rows, setRows] = useState<AdminUserRow[]>([]);
  const [orgs, setOrgs] = useState<OrgNode[]>([]);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [panel, setPanel] = useState<PanelMode>(null);
  const [target, setTarget] = useState<AdminUserRow | null>(null);
  const [keyword, setKeyword] = useState('');

  // 新建
  const [nu, setNu] = useState('');
  const [np, setNp] = useState('');
  const [nd, setNd] = useState('');
  const [ne, setNe] = useState('');
  const [nj, setNj] = useState('');

  // 重置口令
  const [rp, setRp] = useState('');

  // 编辑档案
  const [edName, setEdName] = useState('');
  const [edEmail, setEdEmail] = useState('');
  const [edMobile, setEdMobile] = useState('');
  const [edJob, setEdJob] = useState('');

  // 归属
  const [orgSel, setOrgSel] = useState<Record<string, boolean>>({});
  const [orgPrimary, setOrgPrimary] = useState('');

  async function load() {
    setLoading(true);
    setErr('');
    try {
      const [us, os] = await Promise.all([
        api.get<AdminUserRow[]>('/admin/users'),
        api.get<OrgNode[]>('/admin/orgs'),
      ]);
      setRows(us);
      setOrgs(os);
    } catch (e: any) {
      setErr(e?.msg || '加载用户列表失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  function closePanel() {
    setPanel(null);
    setTarget(null);
  }

  const filtered = useMemo(() => {
    const k = keyword.trim().toLowerCase();
    if (!k) return rows;
    return rows.filter(
      (r) =>
        r.username.toLowerCase().includes(k) ||
        (r.displayName ?? '').toLowerCase().includes(k) ||
        (r.employeeNo ?? '').toLowerCase().includes(k) ||
        (r.jobTitle ?? '').toLowerCase().includes(k) ||
        r.orgNames.some((o) => o.toLowerCase().includes(k)),
    );
  }, [rows, keyword]);

  const orgOptions = useMemo(
    () =>
      orgs
        .slice()
        .sort((a, b) => a.path.localeCompare(b.path))
        .map((n) => ({
          id: n.id,
          label: `${'·'.repeat(Math.max(0, n.path.split('/').filter(Boolean).length - 1))} ${n.name} · ${orgTypeLabel(n.nodeType)}${
            n.source === 'AD_SYNCED' ? '［AD］' : ''
          }`,
        })),
    [orgs],
  );

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      const clientSalt = randomClientSalt();
      const credential = await deriveClientHash(np, clientSalt);
      await api.post('/admin/users', {
        username: nu.trim(),
        credential,
        clientSalt,
        displayName: nd.trim() || null,
        employeeNo: ne.trim() || null,
        jobTitle: nj.trim() || null,
      });
      flash(`已创建账号 ${nu}`);
      setNu('');
      setNp('');
      setNd('');
      setNe('');
      setNj('');
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || '创建失败');
    } finally {
      setBusy(false);
    }
  }

  async function onToggleStatus(r: AdminUserRow) {
    setErr('');
    try {
      await api.put(`/admin/users/${encodeURIComponent(r.userId)}/status`, { enabled: !r.enabled });
      flash(`${r.username} 已${r.enabled ? '禁用（已强制下线）' : '启用'}`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '操作失败');
    }
  }

  async function onUnlock(r: AdminUserRow) {
    setErr('');
    try {
      const cleared = await api.post<boolean>(`/admin/users/${encodeURIComponent(r.userId)}/unlock`);
      flash(cleared ? `${r.username} 已解锁` : `${r.username} 无锁定记录`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '解锁失败');
    }
  }

  async function onReset(e: FormEvent) {
    e.preventDefault();
    if (!target) return;
    setErr('');
    setBusy(true);
    try {
      const clientSalt = randomClientSalt();
      const credential = await deriveClientHash(rp, clientSalt);
      await api.put(`/admin/users/${encodeURIComponent(target.userId)}/password`, { credential, clientSalt });
      flash(`已重置 ${target.userId} 的口令（该用户已强制下线）`);
      setRp('');
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || '重置失败');
    } finally {
      setBusy(false);
    }
  }

  async function onSaveProfile(e: FormEvent) {
    e.preventDefault();
    if (!target) return;
    setErr('');
    setBusy(true);
    try {
      await api.put(`/admin/profiles/${encodeURIComponent(target.userId)}`, {
        displayName: edName.trim() || null,
        email: edEmail.trim() || null,
        mobile: edMobile.trim() || null,
        jobTitle: edJob.trim() || null,
      });
      flash('已保存档案');
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || '保存失败');
    } finally {
      setBusy(false);
    }
  }

  async function onSaveOrgs(e: FormEvent) {
    e.preventDefault();
    if (!target) return;
    setErr('');
    setBusy(true);
    try {
      const list = Object.entries(orgSel)
        .filter(([, v]) => v)
        .map(([id]) => ({ orgId: id, primary: id === orgPrimary }));
      await api.put(`/admin/users/${encodeURIComponent(target.userId)}/orgs`, { orgs: list });
      flash('已保存组织归属（该用户已强制下线，重登后生效）');
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || '保存归属失败');
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(r: AdminUserRow) {
    if (r.hasCredential) {
      if (!window.confirm(`确认删除账号 ${r.username}？该操作不可恢复，其存量令牌将立即失效。`)) return;
    } else {
      if (!window.confirm(`${r.username} 是 AD 同步用户（无本地凭证），IAM 侧不提供删除。请在企业目录中处理。`)) return;
      return;
    }
    setErr('');
    try {
      await api.del(`/admin/users/${encodeURIComponent(r.userId)}`);
      flash(`已删除账号 ${r.username}`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '删除失败');
    }
  }

  function openProfile(r: AdminUserRow) {
    setTarget(r);
    setEdName(r.displayName ?? '');
    setEdEmail('');
    setEdMobile('');
    setEdJob(r.jobTitle ?? '');
    setPanel('profile');
  }

  async function openOrgs(r: AdminUserRow) {
    setTarget(r);
    setPanel('orgs');
    try {
      const cur = await api.get<{ orgId: string; primary: boolean }[]>(
        `/admin/users/${encodeURIComponent(r.userId)}/orgs`,
      );
      const sel: Record<string, boolean> = {};
      let primary = '';
      cur.forEach((o) => {
        sel[o.orgId] = true;
        if (o.primary) primary = o.orgId;
      });
      setOrgSel(sel);
      setOrgPrimary(primary || cur[0]?.orgId || '');
    } catch {
      setOrgSel({});
      setOrgPrimary('');
    }
  }

  const enabled = rows.filter((r) => r.enabled).length;
  const locked = rows.filter((r) => r.locked).length;
  const adCount = rows.filter((r) => r.source === 'AD_SYNCED').length;

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
        title="用户与档案"
        sub="本地凭证账号 ∪ 用户档案（AD 同步来的员工无本地凭证，改密在目录侧）"
        flush
        actions={
          <>
            <span className="tag">{rows.length} 人</span>
            <span className="tag ok">{enabled} 启用</span>
            {adCount > 0 && <span className="tag info">{adCount} AD 同步</span>}
            {locked > 0 && <span className="tag err">{locked} 锁定</span>}
            <input
              className="select-sm"
              style={{ width: 168 }}
              placeholder="搜索 姓名/工号/组织"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? '刷新中' : '刷新'}
            </button>
            <button
              className="btn-sm"
              onClick={() => {
                setPanel('create');
                setTarget(null);
              }}
            >
              <IconPlus width={13} height={13} />
              新建账号
            </button>
          </>
        }
      >
        {filtered.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>用户</th>
                  <th style={{ width: 92 }}>工号</th>
                  <th style={{ width: 92 }}>来源</th>
                  <th>组织</th>
                  <th style={{ width: 130 }}>状态</th>
                  <th style={{ width: 76 }}>准入</th>
                  <th style={{ width: 320 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((r) => (
                  <tr key={r.userId}>
                    <td>
                      <div className="user-cell">
                        <span className="mono">{r.username}</span>
                        {r.displayName && <span className="dim">{r.displayName}</span>}
                        {r.jobTitle && <span className="tag">{r.jobTitle}</span>}
                        {!r.hasCredential && <span className="tag info">仅档案</span>}
                      </div>
                    </td>
                    <td className="mono dim">{r.employeeNo ?? '—'}</td>
                    <td>
                      <span className={'tag ' + (r.source === 'AD_SYNCED' ? 'info' : r.source === 'IAM_MANAGED' ? 'pri' : '')}>
                        {originLabel(r.source)}
                      </span>
                    </td>
                    <td>
                      <div className="tag-list">
                        {r.orgNames.slice(0, 3).map((o, i) => (
                          <span key={i} className="tag" title={o}>
                            {o.length > 26 ? `${o.slice(-18)}…` : o}
                          </span>
                        ))}
                        {r.orgNames.length > 3 && <span className="dim">+{r.orgNames.length - 3}</span>}
                        {!r.orgNames.length && <span className="dim">未归属</span>}
                      </div>
                    </td>
                    <td>
                      <span className="status">
                        <i className={'led ' + (r.enabled ? 'ok' : 'err')} />
                        <b>{r.enabled ? '启用' : '禁用'}</b>
                      </span>
                      {r.locked && (
                        <span className="tag err" style={{ marginLeft: 6 }} title={`锁定至 ${fmtDateTime(r.lockedUntil)}`}>
                          锁定
                        </span>
                      )}
                      {r.status === 'INACTIVE' && <span className="tag warn" style={{ marginLeft: 6 }}>档案停用</span>}
                    </td>
                    <td className="num">{r.apps?.length ?? 0}</td>
                    <td>
                      <div className="cell-actions">
                        <button className="btn-ghost btn-sm" onClick={() => openProfile(r)}>
                          <IconEdit width={13} height={13} />
                          档案
                        </button>
                        <button className="btn-ghost btn-sm" onClick={() => openOrgs(r)}>
                          <IconTree width={13} height={13} />
                          归属
                        </button>
                        <button className="btn-ghost btn-sm" onClick={() => onToggleStatus(r)}>
                          {r.enabled ? <IconBan width={13} height={13} /> : <IconPower width={13} height={13} />}
                          {r.enabled ? '禁用' : '启用'}
                        </button>
                        {r.hasCredential && (
                          <button
                            className="btn-ghost btn-sm"
                            onClick={() => {
                              setTarget(r);
                              setRp('');
                              setPanel('reset');
                            }}
                          >
                            <IconKey width={13} height={13} />
                            重置
                          </button>
                        )}
                        {r.locked && (
                          <button className="btn-ghost btn-sm" onClick={() => onUnlock(r)}>
                            <IconKey width={13} height={13} />
                            解锁
                          </button>
                        )}
                        <button className="btn-danger btn-sm" onClick={() => onDelete(r)} disabled={!r.hasCredential}>
                          <IconTrash width={13} height={13} />
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
            <IconUsers width={22} height={22} />
            <b>{rows.length ? '无匹配用户' : '暂无用户'}</b>
            <span>{rows.length ? '换个关键词试试' : '点击右上「新建账号」创建第一个账号'}</span>
          </div>
        )}
      </Panel>

      {panel === 'create' && (
        <Panel title="新建账号" sub="口令在浏览器内完成第一层 PBKDF2 派生后上传；档案字段可留空后续补充">
          <form onSubmit={onCreate}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="nu">
                  用户名
                </label>
                <input id="nu" className="mono" placeholder="operator01" value={nu} onChange={(e) => setNu(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="np">
                  初始口令
                </label>
                <input id="np" type="password" value={np} onChange={(e) => setNp(e.target.value)} autoComplete="new-password" />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nd">
                  姓名
                </label>
                <input id="nd" placeholder="张三" value={nd} onChange={(e) => setNd(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="ne">
                  工号
                </label>
                <input id="ne" className="mono" placeholder="1001" value={ne} onChange={(e) => setNe(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nj">
                  岗位
                </label>
                <input id="nj" placeholder="蚀刻操作员" value={nj} onChange={(e) => setNj(e.target.value)} />
              </div>
              <button type="submit" disabled={busy || !nu.trim() || !np}>
                {busy ? '创建中…' : '创建'}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                取消
              </button>
            </div>
          </form>
          <p className="hint">
            新建账号默认无任何准入。建议：在「组织架构」把该用户挂到工序 → 由「组织授权」自动获得准入；
            或到「准入授权」单独授予。
          </p>
        </Panel>
      )}

      {panel === 'reset' && target && (
        <Panel title={`重置口令 · ${target.userId}`} sub="重置成功后该用户所有会话立即失效，需以新口令重登">
          <form onSubmit={onReset}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="rp">
                  新口令
                </label>
                <input id="rp" type="password" value={rp} onChange={(e) => setRp(e.target.value)} autoComplete="new-password" />
              </div>
              <button type="submit" disabled={busy || !rp}>
                {busy ? '提交中…' : '确认重置'}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                取消
              </button>
            </div>
          </form>
        </Panel>
      )}

      {panel === 'profile' && target && (
        <Panel
          title={`档案 · ${target.userId}`}
          sub={
            target.source === 'AD_SYNCED'
              ? 'AD 同步档案：姓名/邮箱/手机只读，仅岗位可在 IAM 侧维护'
              : 'IAM 自建档案：可自由维护'
          }
        >
          <form onSubmit={onSaveProfile}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="pdn">
                  姓名
                </label>
                <input
                  id="pdn"
                  value={edName}
                  onChange={(e) => setEdName(e.target.value)}
                  disabled={target.source === 'AD_SYNCED'}
                />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="pem">
                  邮箱
                </label>
                <input
                  id="pem"
                  value={edEmail}
                  onChange={(e) => setEdEmail(e.target.value)}
                  disabled={target.source === 'AD_SYNCED'}
                  placeholder="zhangsan@corp.com"
                />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="pmb">
                  手机
                </label>
                <input
                  id="pmb"
                  value={edMobile}
                  onChange={(e) => setEdMobile(e.target.value)}
                  disabled={target.source === 'AD_SYNCED'}
                />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="pjt">
                  岗位
                </label>
                <input id="pjt" value={edJob} onChange={(e) => setEdJob(e.target.value)} placeholder="蚀刻操作员" />
              </div>
              <button type="submit" disabled={busy}>
                {busy ? '保存中…' : '保存'}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                取消
              </button>
            </div>
          </form>
          <p className="hint">
            工号 <span className="mono">{target.employeeNo ?? '—'}</span>、来源 {originLabel(target.source)}。
            岗位由 IAM 维护（AD 常无此维度），可用于按岗位批量授权。
          </p>
        </Panel>
      )}

      {panel === 'orgs' && target && (
        <Panel
          title={`组织归属 · ${target.userId}`}
          sub="支持多归属（多能工 / 跨线支援）；保存后该用户会话失效，重登即按新归属获得准入"
        >
          <form onSubmit={onSaveOrgs}>
            <div className="org-pick">
              {orgOptions.map((o) => (
                <label key={o.id} className="check">
                  <input
                    type="checkbox"
                    checked={!!orgSel[o.id]}
                    onChange={(e) => {
                      setOrgSel((prev) => ({ ...prev, [o.id]: e.target.checked }));
                      if (e.target.checked && !orgPrimary) setOrgPrimary(o.id);
                      if (!e.target.checked && orgPrimary === o.id) setOrgPrimary('');
                    }}
                  />
                  <span>{o.label}</span>
                </label>
              ))}
              {!orgOptions.length && <span className="dim">尚未建立组织，请先到「组织架构」创建</span>}
            </div>
            <div className="row" style={{ marginTop: 12 }}>
              <div className="field">
                <label className="field-label" htmlFor="prm">
                  主属组织
                </label>
                <select id="prm" value={orgPrimary} onChange={(e) => setOrgPrimary(e.target.value)}>
                  <option value="">（不指定）</option>
                  {orgOptions
                    .filter((o) => orgSel[o.id])
                    .map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.label}
                      </option>
                    ))}
                </select>
              </div>
              <button type="submit" disabled={busy}>
                {busy ? '保存中…' : '保存归属'}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                取消
              </button>
            </div>
          </form>
          <p className="hint">
            主属组织用于展示与默认数据权限范围。归属变更会 bump 该用户令牌版本 → 旧令牌 401 → 重登即带新准入。
          </p>
        </Panel>
      )}

      <Panel title="使用说明" sub="身份生命周期">
        <ul className="note-list">
          <li>
            <b>入职</b>：建账号（或由 AD 同步自动建档案）→ 在「组织架构」挂到工序 → 组织授权自动生效；例外再走「准入授权」。
          </li>
          <li>
            <b>转岗</b>：调整组织归属即可——组织授权随归属自动增减（个人授予需手动处理）。
          </li>
          <li>
            <b>停用/离职</b>：禁用账号或把档案置为停用，会自动 bump 令牌版本 → 所有存量令牌立即失效。
          </li>
          <li>
            <b>口令遗忘</b>：重置口令（自动强制下线）；AD 账号请在企业目录侧改密，IAM 不代管。
          </li>
        </ul>
      </Panel>
    </div>
  );
}
