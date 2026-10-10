import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { AdminUserRow, OrgNode } from '@/types';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import { fmtDateTime, orgTypeLabel, originLabel } from '@/lib/format';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import {
  USER_CREATE,
  USER_DELETE,
  USER_ORGS,
  USER_RESET_PWD,
  USER_STATUS,
  USER_UNLOCK,
  USER_UPDATE,
} from '@/lib/permCodes';
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
 * 用户与档案。
 *
 * <p>把「本地凭证账号」与「用户档案」合并成统一视图：AD 同步来的员工（无本地凭证）也会出现，
 * 每行标注档案来源与组织归属。口令相关操作沿用与登录一致的两层派生——浏览器内先做第一层 PBKDF2，
 * 仅上传 clientHash + 随机盐，明文口令不出浏览器。</p>
 */

type PanelMode = null | 'create' | 'reset' | 'profile' | 'orgs';

export default function UsersPage() {
  const { t } = useTranslation();
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
      setErr(e?.msg || t('iam.users.errLoad'));
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
      flash(t('iam.users.msgCreated', { name: nu }));
      setNu('');
      setNp('');
      setNd('');
      setNe('');
      setNj('');
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.users.errCreate'));
    } finally {
      setBusy(false);
    }
  }

  async function onToggleStatus(r: AdminUserRow) {
    setErr('');
    try {
      await api.put(`/admin/users/${encodeURIComponent(r.userId)}/status`, { enabled: !r.enabled });
      flash(`${r.username} ` + (r.enabled ? t('iam.users.msgDisabled') : t('iam.users.msgEnabled')));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.users.errOp'));
    }
  }

  async function onUnlock(r: AdminUserRow) {
    setErr('');
    try {
      const cleared = await api.post<boolean>(`/admin/users/${encodeURIComponent(r.userId)}/unlock`);
      flash(cleared ? t('iam.users.msgUnlocked', { user: r.username }) : t('iam.users.msgNoLock', { user: r.username }));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.users.errUnlock'));
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
      flash(t('iam.users.msgResetDone', { id: target.userId }));
      setRp('');
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.users.errReset'));
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
      flash(t('iam.users.msgProfileSaved'));
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.users.errSaveProfile'));
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
      flash(t('iam.users.msgOrgsSaved'));
      closePanel();
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.users.errSaveOrgs'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(r: AdminUserRow) {
    if (r.hasCredential) {
      if (!window.confirm(t('iam.users.confirmDelete', { name: r.username }))) return;
    } else {
      if (!window.confirm(t('iam.users.confirmAdDelete', { name: r.username }))) return;
      return;
    }
    setErr('');
    try {
      await api.del(`/admin/users/${encodeURIComponent(r.userId)}`);
      flash(t('iam.users.msgDeleted', { name: r.username }));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.users.errDelete'));
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
        title={t('iam.users.title')}
        sub={t('iam.users.sub')}
        flush
        actions={
          <>
            <span className="tag">{t('iam.users.count', { n: rows.length })}</span>
            <span className="tag ok">{t('iam.users.enabled', { n: enabled })}</span>
            {adCount > 0 && <span className="tag info">{t('iam.users.adSynced', { n: adCount })}</span>}
            {locked > 0 && <span className="tag err">{t('iam.users.locked', { n: locked })}</span>}
            <input
              className="select-sm"
              style={{ width: 168 }}
              placeholder={t('iam.users.searchPH')}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.users.refreshing') : t('iam.users.refresh')}
            </button>
            <Perms code={USER_CREATE}>
              <button
                className="btn-sm"
                onClick={() => {
                  setPanel('create');
                  setTarget(null);
                }}
              >
                <IconPlus width={13} height={13} />
                {t('iam.users.newAccount')}
              </button>
            </Perms>
          </>
        }
      >
        {filtered.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('iam.users.th.user')}</th>
                  <th style={{ width: 92 }}>{t('iam.users.th.empNo')}</th>
                  <th style={{ width: 92 }}>{t('iam.users.th.source')}</th>
                  <th>{t('iam.users.th.org')}</th>
                  <th style={{ width: 130 }}>{t('iam.users.th.status')}</th>
                  <th style={{ width: 76 }}>{t('iam.users.th.admission')}</th>
                  <th style={{ width: 320 }}>{t('iam.users.th.actions')}</th>
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
                        {!r.hasCredential && <span className="tag info">{t('iam.users.tagProfileOnly')}</span>}
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
                        {!r.orgNames.length && <span className="dim">{t('iam.users.noOrg')}</span>}
                      </div>
                    </td>
                    <td>
                      <span className="status">
                        <i className={'led ' + (r.enabled ? 'ok' : 'err')} />
                        <b>{r.enabled ? t('iam.users.statusEnabled') : t('iam.users.statusDisabled')}</b>
                      </span>
                      {r.locked && (
                        <span className="tag err" style={{ marginLeft: 6 }} title={t('iam.lockouts.th.until') + ' ' + fmtDateTime(r.lockedUntil)}>
                           {t('iam.users.tagLocked')}
                        </span>
                      )}
                      {r.status === 'INACTIVE' && <span className="tag warn" style={{ marginLeft: 6 }}>{t('iam.users.tagProfileDisabled')}</span>}
                    </td>
                    <td className="num">{r.apps?.length ?? 0}</td>
                    <td>
                      <div className="cell-actions">
                        <Perms code={USER_UPDATE}>
                          <button className="btn-ghost btn-sm" onClick={() => openProfile(r)}>
                            <IconEdit width={13} height={13} />
                            {t('iam.users.btnProfile')}
                          </button>
                        </Perms>
                        <Perms code={USER_ORGS}>
                          <button className="btn-ghost btn-sm" onClick={() => openOrgs(r)}>
                            <IconTree width={13} height={13} />
                            {t('iam.users.btnOrgs')}
                          </button>
                        </Perms>
                        <Perms code={USER_STATUS}>
                          <button className="btn-ghost btn-sm" onClick={() => onToggleStatus(r)}>
                            {r.enabled ? <IconBan width={13} height={13} /> : <IconPower width={13} height={13} />}
                            {r.enabled ? t('iam.users.btnDisable') : t('iam.users.btnEnable')}
                          </button>
                        </Perms>
                        {r.hasCredential && (
                          <Perms code={USER_RESET_PWD}>
                            <button
                              className="btn-ghost btn-sm"
                              onClick={() => {
                                setTarget(r);
                                setRp('');
                                setPanel('reset');
                              }}
                            >
                              <IconKey width={13} height={13} />
                              {t('iam.users.btnReset')}
                            </button>
                          </Perms>
                        )}
                        {r.locked && (
                          <Perms code={USER_UNLOCK}>
                            <button className="btn-ghost btn-sm" onClick={() => onUnlock(r)}>
                              <IconKey width={13} height={13} />
                              {t('iam.users.btnUnlock')}
                            </button>
                          </Perms>
                        )}
                        <Perms code={USER_DELETE}>
                          <button className="btn-danger btn-sm" onClick={() => onDelete(r)} disabled={!r.hasCredential}>
                            <IconTrash width={13} height={13} />
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
            <IconUsers width={22} height={22} />
            <b>{rows.length ? t('iam.users.emptyMatch') : t('iam.users.emptyNone')}</b>
            <span>{rows.length ? t('iam.users.emptyHintMatch') : t('iam.users.emptyHintNone')}</span>
          </div>
        )}
      </Panel>

      {panel === 'create' && (
        <Panel title={t('iam.users.createTitle')} sub={t('iam.users.createSub')}>
          <form onSubmit={onCreate}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="nu">
                  {t('iam.users.f.username')}
                </label>
                <input id="nu" className="mono" placeholder="operator01" value={nu} onChange={(e) => setNu(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="np">
                  {t('iam.users.f.password')}
                </label>
                <input id="np" type="password" value={np} onChange={(e) => setNp(e.target.value)} autoComplete="new-password" />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nd">
                  {t('iam.users.f.name')}
                </label>
                <input id="nd" placeholder={t('iam.users.phName')} value={nd} onChange={(e) => setNd(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="ne">
                  {t('iam.users.f.empNo')}
                </label>
                <input id="ne" className="mono" placeholder="1001" value={ne} onChange={(e) => setNe(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nj">
                  {t('iam.users.f.job')}
                </label>
                <input id="nj" placeholder={t('iam.users.phJob')} value={nj} onChange={(e) => setNj(e.target.value)} />
              </div>
              <button type="submit" disabled={busy || !nu.trim() || !np}>
                {busy ? t('iam.users.creating') : t('iam.users.createSubmit')}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                {t('iam.users.cancel')}
              </button>
            </div>
          </form>
          <p className="hint">{t('iam.users.createHint')}</p>
        </Panel>
      )}

      {panel === 'reset' && target && (
        <Panel title={t('iam.users.resetTitle', { id: target.userId })} sub={t('iam.users.resetSub')}>
          <form onSubmit={onReset}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="rp">
                  {t('iam.users.resetNew')}
                </label>
                <input id="rp" type="password" value={rp} onChange={(e) => setRp(e.target.value)} autoComplete="new-password" />
              </div>
              <button type="submit" disabled={busy || !rp}>
                {busy ? t('iam.users.submitting') : t('iam.users.resetSubmit')}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                {t('iam.users.cancel')}
              </button>
            </div>
          </form>
        </Panel>
      )}

      {panel === 'profile' && target && (
        <Panel
          title={t('iam.users.profileTitle', { id: target.userId })}
          sub={
            target.source === 'AD_SYNCED'
              ? t('iam.users.profileSubAd')
              : t('iam.users.profileSubIam')
          }
        >
          <form onSubmit={onSaveProfile}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="pdn">
                  {t('iam.users.f.name')}
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
                  {t('iam.users.f.email')}
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
                  {t('iam.users.f.mobile')}
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
                  {t('iam.users.f.job')}
                </label>
                <input id="pjt" value={edJob} onChange={(e) => setEdJob(e.target.value)} placeholder={t('iam.users.phJob')} />
              </div>
              <button type="submit" disabled={busy}>
                {busy ? t('iam.users.saving') : t('iam.users.save')}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                {t('iam.users.cancel')}
              </button>
            </div>
          </form>
          <p className="hint">
            {t('iam.users.profileHint', { no: target.employeeNo ?? '—', src: originLabel(target.source) })}
          </p>
        </Panel>
      )}

      {panel === 'orgs' && target && (
        <Panel
          title={t('iam.users.orgsTitle', { id: target.userId })}
          sub={t('iam.users.orgsSub')}
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
              {!orgOptions.length && <span className="dim">{t('iam.users.orgsNone')}</span>}
            </div>
            <div className="row" style={{ marginTop: 12 }}>
              <div className="field">
                <label className="field-label" htmlFor="prm">
                  {t('iam.users.primaryOrg')}
                </label>
                <select id="prm" value={orgPrimary} onChange={(e) => setOrgPrimary(e.target.value)}>
                  <option value="">{t('iam.users.primaryNone')}</option>
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
                {busy ? t('iam.users.saving') : t('iam.users.saveOrgs')}
              </button>
              <button type="button" className="btn-ghost" onClick={closePanel}>
                {t('iam.users.cancel')}
              </button>
            </div>
          </form>
          <p className="hint">{t('iam.users.orgsHint')}</p>
        </Panel>
      )}

      <Panel title={t('iam.users.usageTitle')} sub={t('iam.users.usageSub')}>
        <ul className="note-list">
          <li>{t('iam.users.usageOnboard')}</li>
          <li>{t('iam.users.usageTransfer')}</li>
          <li>{t('iam.users.usageOffboard')}</li>
          <li>{t('iam.users.usagePwd')}</li>
        </ul>
      </Panel>
    </div>
  );
}
