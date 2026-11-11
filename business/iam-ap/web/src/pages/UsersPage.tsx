import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimInput,
  CimSelect,
  CimTag,
  DataTable,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/api';
import type { AdminUserRow, OrgNode } from '@/types';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import { fmtDateTime, orgTypeLabel, originLabel } from '@/lib/format';
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
import { IconBan, IconEdit, IconKey, IconPlus, IconPower, IconTree, IconTrash } from '@/components/Icons';

type PanelMode = null | 'create' | 'reset' | 'profile' | 'orgs';

interface Bundle {
  users: AdminUserRow[];
  orgs: OrgNode[];
}

/**
 * 用户与档案。
 *
 * <p>把「本地凭证账号」与「用户档案」合并成统一视图：AD 同步来的员工（无本地凭证）也会出现，
 * 每行标注档案来源与组织归属。口令相关操作沿用与登录一致的两层派生——浏览器内先做第一层 PBKDF2，
 * 仅上传 clientHash + 随机盐，明文口令不出浏览器。</p>
 */
export default function UsersPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [panel, setPanel] = useState<PanelMode>(null);
  const [target, setTarget] = useState<AdminUserRow | null>(null);
  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [toggling, setToggling] = useState<string | null>(null);

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

  const bundleRes = useResource<Bundle>(async ({ signal }) => {
    const [users, orgs] = await Promise.all([
      api.get<AdminUserRow[]>('/admin/users', undefined, { signal }),
      api.get<OrgNode[]>('/admin/orgs', undefined, { signal }),
    ]);
    return { users, orgs };
  });

  const rows = bundleRes.data?.users ?? [];
  const orgs = bundleRes.data?.orgs ?? [];

  /** 打开「归属」面板才取该用户当前的组织（抽屉未打开不发请求）。 */
  const curOrgsRes = useResource<{ orgId: string; primary: boolean }[]>(
    ({ signal }) =>
      api.get<{ orgId: string; primary: boolean }[]>(
        `/admin/users/${encodeURIComponent(target?.userId ?? '')}/orgs`,
        undefined,
        { signal },
      ),
    { deps: [target?.userId, panel], enabled: panel === 'orgs' && !!target },
  );

  // 服务端回包 → 本地勾选态。这是「外部数据落到可编辑副本」，只在回包变化时做一次
  useEffect(() => {
    if (panel !== 'orgs' || !curOrgsRes.data) return;
    const sel: Record<string, boolean> = {};
    let primary = '';
    for (const o of curOrgsRes.data) {
      sel[o.orgId] = true;
      if (o.primary) primary = o.orgId;
    }
    setOrgSel(sel);
    setOrgPrimary(primary || curOrgsRes.data[0]?.orgId || '');
  }, [curOrgsRes.data, panel]);

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
          label: `${'·'.repeat(Math.max(0, n.path.split('/').filter(Boolean).length - 1))} ${n.name} · ${orgTypeLabel(
            n.nodeType,
          )}${n.source === 'AD_SYNCED' ? '［AD］' : ''}`,
        })),
    [orgs],
  );

  async function onCreate() {
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
      toast.success(t('iam.users.msgCreated', { name: nu.trim() }));
      setNu('');
      setNp('');
      setNd('');
      setNe('');
      setNj('');
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.users.errCreate'));
    } finally {
      setBusy(false);
    }
  }

  async function onToggleStatus(r: AdminUserRow) {
    setToggling(r.userId);
    try {
      await api.put(`/admin/users/${encodeURIComponent(r.userId)}/status`, { enabled: !r.enabled });
      toast.success(`${r.username} ` + (r.enabled ? t('iam.users.msgDisabled') : t('iam.users.msgEnabled')));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.users.errOp'));
    } finally {
      setToggling(null);
    }
  }

  async function onUnlock(r: AdminUserRow) {
    try {
      const cleared = await api.post<boolean>(`/admin/users/${encodeURIComponent(r.userId)}/unlock`);
      toast.success(
        cleared ? t('iam.users.msgUnlocked', { user: r.username }) : t('iam.users.msgNoLock', { user: r.username }),
      );
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.users.errUnlock'));
    }
  }

  async function onReset() {
    if (!target) return;
    setBusy(true);
    try {
      const clientSalt = randomClientSalt();
      const credential = await deriveClientHash(rp, clientSalt);
      await api.put(`/admin/users/${encodeURIComponent(target.userId)}/password`, { credential, clientSalt });
      toast.success(t('iam.users.msgResetDone', { id: target.userId }));
      setRp('');
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.users.errReset'));
    } finally {
      setBusy(false);
    }
  }

  async function onSaveProfile() {
    if (!target) return;
    setBusy(true);
    try {
      await api.put(`/admin/profiles/${encodeURIComponent(target.userId)}`, {
        displayName: edName.trim() || null,
        email: edEmail.trim() || null,
        mobile: edMobile.trim() || null,
        jobTitle: edJob.trim() || null,
      });
      toast.success(t('iam.users.msgProfileSaved'));
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.users.errSaveProfile'));
    } finally {
      setBusy(false);
    }
  }

  async function onSaveOrgs() {
    if (!target) return;
    setBusy(true);
    try {
      const list = Object.entries(orgSel)
        .filter(([, v]) => v)
        .map(([id]) => ({ orgId: id, primary: id === orgPrimary }));
      await api.put(`/admin/users/${encodeURIComponent(target.userId)}/orgs`, { orgs: list });
      toast.success(t('iam.users.msgOrgsSaved'));
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.users.errSaveOrgs'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(r: AdminUserRow) {
    const ok = await confirm({
      title: t('iam.common.delete'),
      content: t('iam.users.confirmDelete', { name: r.username }),
      danger: true,
      okKey: 'iam.common.delete',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    try {
      await api.del(`/admin/users/${encodeURIComponent(r.userId)}`);
      toast.success(t('iam.users.msgDeleted', { name: r.username }));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.users.errDelete'));
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

  function openOrgs(r: AdminUserRow) {
    setTarget(r);
    setPanel('orgs');
  }

  const enabled = rows.filter((r) => r.enabled).length;
  const locked = rows.filter((r) => r.locked).length;
  const adCount = rows.filter((r) => r.source === 'AD_SYNCED').length;

  const columns = useMemo<Array<DataTableColumn<AdminUserRow>>>(
    () => [
      {
        key: 'username',
        titleKey: 'iam.users.th.user',
        render: (r) => (
          <div className="user-cell">
            <span className="cim-mono">{r.username}</span>
            {r.displayName && <span className="dim">{r.displayName}</span>}
            {r.jobTitle && <span className="tag">{r.jobTitle}</span>}
            {!r.hasCredential && (
              <CimTag size="small" tone="run">
                {t('iam.users.tagProfileOnly')}
              </CimTag>
            )}
          </div>
        ),
      },
      {
        key: 'employeeNo',
        titleKey: 'iam.users.th.empNo',
        width: 100,
        mono: true,
        render: (r) => <span className="dim">{r.employeeNo ?? '—'}</span>,
      },
      {
        key: 'source',
        titleKey: 'iam.users.th.source',
        width: 100,
        render: (r) => (
          <CimTag size="small" tone={r.source === 'AD_SYNCED' ? 'run' : r.source === 'IAM_MANAGED' ? 'idle' : 'idle'}>
            {originLabel(r.source)}
          </CimTag>
        ),
      },
      {
        key: 'orgNames',
        titleKey: 'iam.users.th.org',
        render: (r) =>
          r.orgNames.length ? (
            <div className="tag-list">
              {r.orgNames.slice(0, 3).map((o, i) => (
                <span key={i} title={o}>
                  <CimTag size="small">{o.length > 26 ? `${o.slice(-18)}…` : o}</CimTag>
                </span>
              ))}
              {r.orgNames.length > 3 && <span className="dim">+{r.orgNames.length - 3}</span>}
            </div>
          ) : (
            <span className="dim">{t('iam.users.noOrg')}</span>
          ),
      },
      {
        key: 'status',
        titleKey: 'iam.users.th.status',
        width: 170,
        render: (r) => (
          <>
            <CimTag size="small" tone={r.enabled ? 'ok' : 'err'}>
              {r.enabled ? t('iam.users.statusEnabled') : t('iam.users.statusDisabled')}
            </CimTag>
            {r.locked && (
              <span title={t('iam.lockouts.th.until') + ' ' + fmtDateTime(r.lockedUntil)} style={{ marginLeft: 6 }}>
                <CimTag size="small" tone="warn">
                  {t('iam.users.tagLocked')}
                </CimTag>
              </span>
            )}
            {r.status === 'INACTIVE' && (
              <span style={{ marginLeft: 6 }}>
                <CimTag size="small" tone="warn">
                  {t('iam.users.tagProfileDisabled')}
                </CimTag>
              </span>
            )}
          </>
        ),
      },
      {
        key: 'apps',
        titleKey: 'iam.users.th.admission',
        width: 80,
        align: 'right',
        render: (r) => r.apps?.length ?? 0,
      },
      {
        key: 'actions',
        titleKey: 'iam.users.th.actions',
        width: 330,
        render: (r) => (
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
              <button
                className="btn-ghost btn-sm"
                disabled={toggling === r.userId}
                onClick={() => void onToggleStatus(r)}
              >
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
                <button className="btn-ghost btn-sm" onClick={() => void onUnlock(r)}>
                  <IconKey width={13} height={13} />
                  {t('iam.users.btnUnlock')}
                </button>
              </Perms>
            )}
            <Perms code={USER_DELETE}>
              <button
                className="btn-danger btn-sm"
                onClick={() => void onDelete(r)}
                disabled={!r.hasCredential}
              >
                <IconTrash width={13} height={13} />
              </button>
            </Perms>
          </div>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, toggling],
  );

  return (
    <Page>
      <Panel
        title={t('iam.users.title')}
        sub={t('iam.users.sub')}
        actions={
          <>
            <CimTag>{t('iam.users.count', { n: rows.length })}</CimTag>
            <CimTag tone="ok">{t('iam.users.enabled', { n: enabled })}</CimTag>
            {adCount > 0 && <CimTag tone="run">{t('iam.users.adSynced', { n: adCount })}</CimTag>}
            {locked > 0 && <CimTag tone="err">{t('iam.users.locked', { n: locked })}</CimTag>}
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
        <DataTable<AdminUserRow>
          flat
          columns={columns}
          rows={filtered}
          rowKey={(r) => r.userId}
          loading={bundleRes.loading}
          error={bundleRes.error?.message}
          onRetry={bundleRes.refresh}
          onRefresh={bundleRes.refresh}
          settingsKey="iam.users"
          scrollX="max-content"
          search={{
            value: keyword,
            onChange: setKeyword,
            placeholderKey: 'iam.users.searchPH',
            width: 180,
          }}
          emptyTitleKey={rows.length ? 'iam.users.emptyMatch' : 'iam.users.emptyNone'}
          emptyHintKey={rows.length ? 'iam.users.emptyHintMatch' : 'iam.users.emptyHintNone'}
        />
      </Panel>

      {panel === 'create' && (
        <Panel title={t('iam.users.createTitle')} sub={t('iam.users.createSub')}>
          <div className="row">
            <div className="field">
              <label className="field-label" htmlFor="nu">
                {t('iam.users.f.username')}
              </label>
              <CimInput id="nu" mono placeholder="operator01" value={nu} onChange={setNu} width={180} />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="np">
                {t('iam.users.f.password')}
              </label>
              <CimInput
                id="np"
                password
                autoComplete="new-password"
                value={np}
                onChange={setNp}
                width={200}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="nd">
                {t('iam.users.f.name')}
              </label>
              <CimInput id="nd" placeholder={t('iam.users.phName')} value={nd} onChange={setNd} width={160} />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="ne">
                {t('iam.users.f.empNo')}
              </label>
              <CimInput id="ne" mono placeholder="1001" value={ne} onChange={setNe} width={130} />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="nj">
                {t('iam.users.f.job')}
              </label>
              <CimInput id="nj" placeholder={t('iam.users.phJob')} value={nj} onChange={setNj} width={160} />
            </div>
            <button type="button" disabled={busy || !nu.trim() || !np} onClick={() => void onCreate()}>
              {busy ? t('iam.users.creating') : t('iam.users.createSubmit')}
            </button>
            <button type="button" className="btn-ghost" onClick={closePanel}>
              {t('iam.users.cancel')}
            </button>
          </div>
          <p className="hint">{t('iam.users.createHint')}</p>
        </Panel>
      )}

      {panel === 'reset' && target && (
        <Panel title={t('iam.users.resetTitle', { id: target.userId })} sub={t('iam.users.resetSub')}>
          <div className="row">
            <div className="field">
              <label className="field-label" htmlFor="rp">
                {t('iam.users.resetNew')}
              </label>
              <CimInput
                id="rp"
                password
                autoComplete="new-password"
                value={rp}
                onChange={setRp}
                width={220}
                onEnter={() => void onReset()}
              />
            </div>
            <button type="button" disabled={busy || !rp} onClick={() => void onReset()}>
              {busy ? t('iam.users.submitting') : t('iam.users.resetSubmit')}
            </button>
            <button type="button" className="btn-ghost" onClick={closePanel}>
              {t('iam.users.cancel')}
            </button>
          </div>
        </Panel>
      )}

      {panel === 'profile' && target && (
        <Panel
          title={t('iam.users.profileTitle', { id: target.userId })}
          sub={target.source === 'AD_SYNCED' ? t('iam.users.profileSubAd') : t('iam.users.profileSubIam')}
        >
          <div className="row">
            <div className="field">
              <label className="field-label" htmlFor="pdn">
                {t('iam.users.f.name')}
              </label>
              <CimInput
                id="pdn"
                value={edName}
                onChange={setEdName}
                disabled={target.source === 'AD_SYNCED'}
                width={160}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="pem">
                {t('iam.users.f.email')}
              </label>
              <CimInput
                id="pem"
                value={edEmail}
                onChange={setEdEmail}
                disabled={target.source === 'AD_SYNCED'}
                placeholder="zhangsan@corp.com"
                width={220}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="pmb">
                {t('iam.users.f.mobile')}
              </label>
              <CimInput
                id="pmb"
                value={edMobile}
                onChange={setEdMobile}
                disabled={target.source === 'AD_SYNCED'}
                width={160}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="pjt">
                {t('iam.users.f.job')}
              </label>
              <CimInput
                id="pjt"
                value={edJob}
                onChange={setEdJob}
                placeholder={t('iam.users.phJob')}
                width={160}
              />
            </div>
            <button type="button" disabled={busy} onClick={() => void onSaveProfile()}>
              {busy ? t('iam.users.saving') : t('iam.users.save')}
            </button>
            <button type="button" className="btn-ghost" onClick={closePanel}>
              {t('iam.users.cancel')}
            </button>
          </div>
          <p className="hint">
            {t('iam.users.profileHint', { no: target.employeeNo ?? '—', src: originLabel(target.source) })}
          </p>
        </Panel>
      )}

      {panel === 'orgs' && target && (
        <Panel title={t('iam.users.orgsTitle', { id: target.userId })} sub={t('iam.users.orgsSub')}>
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
              <CimSelect
                id="prm"
                value={orgPrimary}
                onChange={(v) => setOrgPrimary(typeof v === 'string' ? v : '')}
                options={[
                  { value: '', label: t('iam.users.primaryNone') },
                  ...orgOptions.filter((o) => orgSel[o.id]).map((o) => ({ value: o.id, label: o.label })),
                ]}
                width={280}
              />
            </div>
            <button type="button" disabled={busy} onClick={() => void onSaveOrgs()}>
              {busy ? t('iam.users.saving') : t('iam.users.saveOrgs')}
            </button>
            <button type="button" className="btn-ghost" onClick={closePanel}>
              {t('iam.users.cancel')}
            </button>
          </div>
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
    </Page>
  );
}
