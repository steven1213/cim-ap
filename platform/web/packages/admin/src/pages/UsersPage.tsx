// 人员与档案（共享，六页系统配置之一）。
//
// 与 OrgPage 同构：IAM 宿主全 CRUD（口令双层派生不出浏览器，见 ../crypto）；
// 业务系统 readOnly=true 走目录代理——表格只读（无操作列、无新建/启停/重置/
// 解锁/删除），补 email/mobile 两列（目录档案含联系方式）。
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimCheckbox,
  CimInput,
  CimSelect,
  CimTag,
  DataTable,
  InlineAlert,
  Page,
  Panel,
  CimField,
  useConfirm,
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import { useAdminHasAll, useAdminHttp } from '../context';
import { IAM_CREATE_PERMS, USER_PERMS, type AdminPermOverrides } from '../perms';
import type { AdminUserRow, OrgNode } from '../types';
import { deriveClientHash, randomClientSalt } from '../crypto';
import { fmtDateTime, orgTypeLabel, originLabel } from '../format';
import { IconBan, IconEdit, IconKey, IconPower, IconTree, IconTrash } from '@cim/ui';

type PanelMode = null | 'create' | 'reset' | 'profile' | 'orgs';

interface Bundle {
  users: AdminUserRow[];
  orgs: OrgNode[];
}

export interface UsersPageProps {
  /** 只读模式（业务系统）：无任何写操作。 */
  readOnly?: boolean;
  perms?: AdminPermOverrides;
  /** 人员端点前缀（缺省 /admin/users）。 */
  usersPath?: string;
  /** 组织端点前缀（缺省 /admin/orgs）。 */
  orgsPath?: string;
  /** 档案端点前缀（缺省 /admin/profiles）。 */
  profilesPath?: string;
}

/** 人员页（六页共享）。 */
export default function UsersPage({ readOnly = false, perms, usersPath = '/admin/users', orgsPath = '/admin/orgs', profilesPath = '/admin/profiles' }: UsersPageProps) {
  const { t } = useTranslation();
  const http = useAdminHttp();
  const hasAll = useAdminHasAll();
  const toast = useToast();
  const confirm = useConfirm();
  const p = { ...USER_PERMS, ...IAM_CREATE_PERMS, ...perms };
  const canCreate = !readOnly && hasAll(p.userCreate ?? ['iam:user:create']);
  const canUpdate = !readOnly && hasAll(p.save ?? []);
  const canOrgs = !readOnly && hasAll(p.member ?? []);
  const canStatus = !readOnly && hasAll(p.status ?? []);
  const canResetPwd = !readOnly && hasAll(p.resetPwd ?? []);
  const canUnlock = !readOnly && hasAll(p.unlock ?? []);
  const canRemove = !readOnly && hasAll(p.remove ?? []);

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
      http.get<AdminUserRow[]>(usersPath, undefined, { signal }),
      http.get<OrgNode[]>(orgsPath, undefined, { signal }),
    ]);
    return { users: users ?? [], orgs: orgs ?? [] };
  });

  const rows = bundleRes.data?.users ?? [];
  const orgs = bundleRes.data?.orgs ?? [];

  /** 打开「归属」面板才取该用户当前的组织（抽屉未打开不发请求）。 */
  const curOrgsRes = useResource<{ orgId: string; primary: boolean }[]>(
    async ({ signal }) =>
      (await http.get<{ orgId: string; primary: boolean }[]>(
        `${usersPath}/${encodeURIComponent(target?.userId ?? '')}/orgs`,
        undefined,
        { signal },
      )) ?? [],
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
      await http.post(usersPath, {
        username: nu.trim(),
        credential,
        clientSalt,
        displayName: nd.trim() || null,
        employeeNo: ne.trim() || null,
        jobTitle: nj.trim() || null,
      });
      toast.success(t('admin.users.msgCreated', { name: nu.trim() }));
      setNu('');
      setNp('');
      setNd('');
      setNe('');
      setNj('');
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.users.errCreate'));
    } finally {
      setBusy(false);
    }
  }

  async function onToggleStatus(r: AdminUserRow) {
    setToggling(r.userId);
    try {
      await http.put(`${usersPath}/${encodeURIComponent(r.userId)}/status`, { enabled: !r.enabled });
      toast.success(`${r.username} ` + (r.enabled ? t('admin.users.msgDisabled') : t('admin.users.msgEnabled')));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.users.errOp'));
    } finally {
      setToggling(null);
    }
  }

  async function onUnlock(r: AdminUserRow) {
    try {
      const cleared = await http.post<boolean>(`${usersPath}/${encodeURIComponent(r.userId)}/unlock`);
      toast.success(
        cleared ? t('admin.users.msgUnlocked', { user: r.username }) : t('admin.users.msgNoLock', { user: r.username }),
      );
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.users.errUnlock'));
    }
  }

  async function onReset() {
    if (!target) return;
    setBusy(true);
    try {
      const clientSalt = randomClientSalt();
      const credential = await deriveClientHash(rp, clientSalt);
      await http.put(`${usersPath}/${encodeURIComponent(target.userId)}/password`, { credential, clientSalt });
      toast.success(t('admin.users.msgResetDone', { id: target.userId }));
      setRp('');
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.users.errReset'));
    } finally {
      setBusy(false);
    }
  }

  async function onSaveProfile() {
    if (!target) return;
    setBusy(true);
    try {
      await http.put(`${profilesPath}/${encodeURIComponent(target.userId)}`, {
        displayName: edName.trim() || null,
        email: edEmail.trim() || null,
        mobile: edMobile.trim() || null,
        jobTitle: edJob.trim() || null,
      });
      toast.success(t('admin.users.msgProfileSaved'));
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.users.errSaveProfile'));
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
      await http.put(`${usersPath}/${encodeURIComponent(target.userId)}/orgs`, { orgs: list });
      toast.success(t('admin.users.msgOrgsSaved'));
      closePanel();
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.users.errSaveOrgs'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(r: AdminUserRow) {
    const ok = await confirm({
      title: t('admin.common.delete'),
      content: t('admin.users.confirmDelete', { name: r.username }),
      danger: true,
      okKey: 'admin.common.delete',
      cancelKey: 'admin.common.cancel',
    });
    if (!ok) return;
    try {
      await http.del(`${usersPath}/${encodeURIComponent(r.userId)}`);
      toast.success(t('admin.users.msgDeleted', { name: r.username }));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.users.errDelete'));
    }
  }

  function openProfile(r: AdminUserRow) {
    setTarget(r);
    setEdName(r.displayName ?? '');
    setEdEmail(r.email ?? '');
    setEdMobile(r.mobile ?? '');
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

  const columns = useMemo<Array<DataTableColumn<AdminUserRow>>>(() => {
    const cols: Array<DataTableColumn<AdminUserRow>> = [
      {
        key: 'username',
        titleKey: 'admin.users.th.user',
        render: (r) => (
          <div className="user-cell">
            <span className="cim-mono">{r.username}</span>
            {r.displayName && <span className="dim">{r.displayName}</span>}
            {r.jobTitle && <span className="tag">{r.jobTitle}</span>}
            {!readOnly && !r.hasCredential && (
              <CimTag size="small" tone="run">
                {t('admin.users.tagProfileOnly')}
              </CimTag>
            )}
          </div>
        ),
      },
      {
        key: 'employeeNo',
        titleKey: 'admin.users.th.empNo',
        width: 100,
        mono: true,
        render: (r) => <span className="dim">{r.employeeNo ?? '—'}</span>,
      },
      ...(readOnly
        ? [
            {
              key: 'email',
              titleKey: 'admin.users.th.email',
              width: 200,
              render: (r: AdminUserRow) => <span className="dim">{r.email ?? '—'}</span>,
            } as DataTableColumn<AdminUserRow>,
            {
              key: 'mobile',
              titleKey: 'admin.users.th.mobile',
              width: 130,
              render: (r: AdminUserRow) => <span className="dim">{r.mobile ?? '—'}</span>,
            } as DataTableColumn<AdminUserRow>,
          ]
        : []),
      {
        key: 'source',
        titleKey: 'admin.users.th.source',
        width: 100,
        render: (r) => (
          <CimTag size="small" tone={r.source === 'AD_SYNCED' ? 'run' : 'idle'}>
            {originLabel(r.source)}
          </CimTag>
        ),
      },
      {
        key: 'orgNames',
        titleKey: 'admin.users.th.org',
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
            <span className="dim">{t('admin.users.noOrg')}</span>
          ),
      },
      {
        key: 'status',
        titleKey: 'admin.users.th.status',
        width: 170,
        render: (r) => (
          <>
            <CimTag size="small" tone={r.enabled ? 'ok' : 'err'}>
              {r.enabled ? t('admin.users.statusEnabled') : t('admin.users.statusDisabled')}
            </CimTag>
            {r.locked && (
              <span title={t('admin.lockouts.th.until') + ' ' + fmtDateTime(r.lockedUntil)} style={{ marginLeft: 6 }}>
                <CimTag size="small" tone="warn">
                  {t('admin.users.tagLocked')}
                </CimTag>
              </span>
            )}
            {r.status === 'INACTIVE' && (
              <span style={{ marginLeft: 6 }}>
                <CimTag size="small" tone="warn">
                  {t('admin.users.tagProfileDisabled')}
                </CimTag>
              </span>
            )}
          </>
        ),
      },
    ];
    if (!readOnly) {
      cols.push({
        key: 'apps',
        titleKey: 'admin.users.th.admission',
        width: 80,
        align: 'right',
        render: (r) => r.apps?.length ?? 0,
      });
      cols.push({
        key: 'actions',
        titleKey: 'admin.users.th.actions',
        width: 330,
        render: (r) => (
          <div className="cell-actions">
            {canUpdate && (
              <CimButton size="small" onClick={() => openProfile(r)}>
                <IconEdit width={13} height={13} />
                {t('admin.users.btnProfile')}
              </CimButton>
            )}
            {canOrgs && (
              <CimButton size="small" onClick={() => openOrgs(r)}>
                <IconTree width={13} height={13} />
                {t('admin.users.btnOrgs')}
              </CimButton>
            )}
            {canStatus && (
              <CimButton
                size="small"
                disabled={toggling === r.userId}
                onClick={() => void onToggleStatus(r)}
              >
                {r.enabled ? <IconBan width={13} height={13} /> : <IconPower width={13} height={13} />}
                {r.enabled ? t('admin.users.btnDisable') : t('admin.users.btnEnable')}
              </CimButton>
            )}
            {r.hasCredential && canResetPwd && (
              <CimButton
                size="small"
                onClick={() => {
                  setTarget(r);
                  setRp('');
                  setPanel('reset');
                }}
              >
                <IconKey width={13} height={13} />
                {t('admin.users.btnReset')}
              </CimButton>
            )}
            {r.locked && canUnlock && (
              <CimButton size="small" onClick={() => void onUnlock(r)}>
                <IconKey width={13} height={13} />
                {t('admin.users.btnUnlock')}
              </CimButton>
            )}
            {canRemove && (
              <CimButton
                danger
                size="small"
                onClick={() => void onDelete(r)}
                disabled={!r.hasCredential}
              >
                <IconTrash width={13} height={13} />
              </CimButton>
            )}
          </div>
        ),
      });
    }
    return cols;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [t, toggling, readOnly, canUpdate, canOrgs, canStatus, canResetPwd, canUnlock, canRemove]);

  return (
    <Page>
      {readOnly && (
        <div style={{ marginBottom: 10 }}>
          <InlineAlert tone="info">{t('admin.users.readOnlyHint')}</InlineAlert>
        </div>
      )}
      <Panel
        title={t('admin.users.title')}
        actions={
          <>
            <CimTag>{t('admin.users.count', { n: rows.length })}</CimTag>
            {!readOnly && <CimTag tone="ok">{t('admin.users.enabled', { n: enabled })}</CimTag>}
            {adCount > 0 && <CimTag tone="run">{t('admin.users.adSynced', { n: adCount })}</CimTag>}
            {!readOnly && locked > 0 && <CimTag tone="err">{t('admin.users.locked', { n: locked })}</CimTag>}
            {canCreate && (
              <CimButton
                type="primary"
                size="small"
                onClick={() => {
                  setPanel('create');
                  setTarget(null);
                }}
              >
                {t('admin.common.create')}
              </CimButton>
            )}
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
          settingsKey="admin.users"
          scrollX="max-content"
          search={{
            value: keyword,
            onChange: setKeyword,
            placeholderKey: 'admin.users.searchPH',
            width: 180,
          }}
          emptyTitleKey={rows.length ? 'admin.users.emptyMatch' : 'admin.users.emptyNone'}
          // 只读视图没有「新建」入口，不能提示用户去点不存在的按钮
          emptyHintKey={
            rows.length
              ? 'admin.users.emptyHintMatch'
              : canCreate
                ? 'admin.users.emptyHintNone'
                : undefined
          }
        />
      </Panel>

      {panel === 'create' && canCreate && (
        <Panel title={t('admin.users.createTitle')} sub={t('admin.users.createSub')}>
          <div className="row">
            <CimField label={t('admin.users.f.username')} htmlFor="nu">
              <CimInput id="nu" mono placeholder="operator01" value={nu} onChange={setNu} width={180} />
            </CimField>
            <CimField label={t('admin.users.f.password')} htmlFor="np">
              <CimInput
                id="np"
                password
                autoComplete="new-password"
                value={np}
                onChange={setNp}
                width={200}
              />
            </CimField>
            <CimField label={t('admin.users.f.name')} htmlFor="nd">
              <CimInput id="nd" placeholder={t('admin.users.phName')} value={nd} onChange={setNd} width={160} />
            </CimField>
            <CimField label={t('admin.users.f.empNo')} htmlFor="ne">
              <CimInput id="ne" mono placeholder="1001" value={ne} onChange={setNe} width={130} />
            </CimField>
            <CimField label={t('admin.users.f.job')} htmlFor="nj">
              <CimInput id="nj" placeholder={t('admin.users.phJob')} value={nj} onChange={setNj} width={160} />
            </CimField>
            <CimButton type="primary" htmlType="button" disabled={busy || !nu.trim() || !np} onClick={() => void onCreate()}>
              {busy ? t('admin.users.creating') : t('admin.users.createSubmit')}
            </CimButton>
            <CimButton htmlType="button" onClick={closePanel}>
              {t('admin.users.cancel')}
            </CimButton>
          </div>
          <p className="hint">{t('admin.users.createHint')}</p>
        </Panel>
      )}

      {panel === 'reset' && target && canResetPwd && (
        <Panel title={t('admin.users.resetTitle', { id: target.userId })} sub={t('admin.users.resetSub')}>
          <div className="row">
            <CimField label={t('admin.users.resetNew')} htmlFor="rp">
              <CimInput
                id="rp"
                password
                autoComplete="new-password"
                value={rp}
                onChange={setRp}
                width={220}
                onEnter={() => void onReset()}
              />
            </CimField>
            <CimButton type="primary" htmlType="button" disabled={busy || !rp} onClick={() => void onReset()}>
              {busy ? t('admin.users.submitting') : t('admin.users.resetSubmit')}
            </CimButton>
            <CimButton htmlType="button" onClick={closePanel}>
              {t('admin.users.cancel')}
            </CimButton>
          </div>
        </Panel>
      )}

      {panel === 'profile' && target && canUpdate && (
        <Panel
          title={t('admin.users.profileTitle', { id: target.userId })}
          sub={target.source === 'AD_SYNCED' ? t('admin.users.profileSubAd') : t('admin.users.profileSubIam')}
        >
          <div className="row">
            <CimField label={t('admin.users.f.name')} htmlFor="pdn">
              <CimInput
                id="pdn"
                value={edName}
                onChange={setEdName}
                disabled={target.source === 'AD_SYNCED'}
                width={160}
              />
            </CimField>
            <CimField label={t('admin.users.f.email')} htmlFor="pem">
              <CimInput
                id="pem"
                value={edEmail}
                onChange={setEdEmail}
                disabled={target.source === 'AD_SYNCED'}
                placeholder="zhangsan@corp.com"
                width={220}
              />
            </CimField>
            <CimField label={t('admin.users.f.mobile')} htmlFor="pmb">
              <CimInput
                id="pmb"
                value={edMobile}
                onChange={setEdMobile}
                disabled={target.source === 'AD_SYNCED'}
                width={160}
              />
            </CimField>
            <CimField label={t('admin.users.f.job')} htmlFor="pjt">
              <CimInput
                id="pjt"
                value={edJob}
                onChange={setEdJob}
                placeholder={t('admin.users.phJob')}
                width={160}
              />
            </CimField>
            <CimButton type="primary" htmlType="button" disabled={busy} onClick={() => void onSaveProfile()}>
              {busy ? t('admin.users.saving') : t('admin.users.save')}
            </CimButton>
            <CimButton htmlType="button" onClick={closePanel}>
              {t('admin.users.cancel')}
            </CimButton>
          </div>
          <p className="hint">
            {t('admin.users.profileHint', { no: target.employeeNo ?? '—', src: originLabel(target.source) })}
          </p>
        </Panel>
      )}

      {panel === 'orgs' && target && canOrgs && (
        <Panel title={t('admin.users.orgsTitle', { id: target.userId })} sub={t('admin.users.orgsSub')}>
          <div className="org-pick">
            {orgOptions.map((o) => (
              <CimCheckbox
                key={o.id}
                checked={!!orgSel[o.id]}
                onChange={(checked) => {
                  setOrgSel((prev) => ({ ...prev, [o.id]: checked }));
                  if (checked && !orgPrimary) setOrgPrimary(o.id);
                  if (!checked && orgPrimary === o.id) setOrgPrimary('');
                }}
              >
                {o.label}
              </CimCheckbox>
            ))}
            {!orgOptions.length && <span className="dim">{t('admin.users.orgsNone')}</span>}
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <CimField label={t('admin.users.primaryOrg')} htmlFor="prm">
              <CimSelect
                id="prm"
                value={orgPrimary}
                onChange={(v) => setOrgPrimary(typeof v === 'string' ? v : '')}
                options={[
                  { value: '', label: t('admin.users.primaryNone') },
                  ...orgOptions.filter((o) => orgSel[o.id]).map((o) => ({ value: o.id, label: o.label })),
                ]}
                width={280}
              />
            </CimField>
            <CimButton type="primary" htmlType="button" disabled={busy} onClick={() => void onSaveOrgs()}>
              {busy ? t('admin.users.saving') : t('admin.users.saveOrgs')}
            </CimButton>
            <CimButton htmlType="button" onClick={closePanel}>
              {t('admin.users.cancel')}
            </CimButton>
          </div>
          <p className="hint">{t('admin.users.orgsHint')}</p>
        </Panel>
      )}

      {!readOnly && (
        <Panel title={t('admin.users.usageTitle')}>
          <ul className="note-list">
            <li>{t('admin.users.usageOnboard')}</li>
            <li>{t('admin.users.usageTransfer')}</li>
            <li>{t('admin.users.usageOffboard')}</li>
            <li>{t('admin.users.usagePwd')}</li>
          </ul>
        </Panel>
      )}
    </Page>
  );
}
