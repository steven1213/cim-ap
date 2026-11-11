import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimInput,
  CimSelect,
  CimSwitch,
  CimTag,
  DataTable,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/api';
import type { AppRegistration, OrgGrantRow, OrgNode } from '@/types';
import { orgTypeLabel, originLabel } from '@/lib/format';
import Perms from '@/components/Perms';
import { GRANT_GRANT, GRANT_REVOKE } from '@/lib/permCodes';
import { IconPlus, IconShield, IconTrash } from '@/components/Icons';

interface Bundle {
  grants: OrgGrantRow[];
  nodes: OrgNode[];
  apps: AppRegistration[];
}

/**
 * 组织授权（组织 × 应用）。
 *
 * <p>「给某个组织授予某 ap + 粗角色组」，IAM 在签发令牌时按用户归属（含祖先链）<b>运行时展开</b>到人，
 * 不落派生行——组织一改，受影响用户令牌版本 bump，重签即带新准入。
 * 个人授予与组织授予取<b>并集</b>（个人只能追加、不能扣减）。写操作按 `iam:grant:*` 显隐。</p>
 */
export default function OrgGrantsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [orgId, setOrgId] = useState('');
  const [appCode, setAppCode] = useState('');
  const [roles, setRoles] = useState('');
  const [includeChildren, setIncludeChildren] = useState(true);
  const [busy, setBusy] = useState(false);
  const [revoking, setRevoking] = useState<string | null>(null);

  // 三份基础字典一次取回：分成三个 useResource 会让「面板先画表格后出选项」闪一下
  const bundleRes = useResource<Bundle>(async ({ signal }) => {
    const [grants, nodes, apps] = await Promise.all([
      api.get<OrgGrantRow[]>('/admin/org-grants', undefined, { signal }),
      api.get<OrgNode[]>('/admin/orgs', undefined, { signal }),
      api.get<AppRegistration[]>('/apps', undefined, { signal }),
    ]);
    return { grants, nodes, apps };
  });

  const grants = bundleRes.data?.grants ?? [];
  const nodes = bundleRes.data?.nodes ?? [];
  const apps = bundleRes.data?.apps ?? [];

  /** 选中组织的在册人数（失败静默：只是个提示数，不能拖垮表单）。 */
  const affectedRes = useResource<string[]>(
    ({ signal }) => api.get<string[]>(`/admin/orgs/${encodeURIComponent(orgId)}/users`, undefined, { signal }),
    { deps: [orgId], enabled: !!orgId },
  );
  const affected = orgId ? affectedRes.data?.length : undefined;

  const orgOptions = useMemo(
    () =>
      nodes
        .slice()
        .sort((a, b) => a.path.localeCompare(b.path))
        .map((n) => ({
          value: n.id,
          label: `${'·'.repeat(Math.max(0, n.path.split('/').filter(Boolean).length - 1))} ${n.name} (${n.code})${
            n.source === 'AD_SYNCED' ? ' ［AD］' : ''
          }`,
          hint: orgTypeLabel(n.nodeType),
        })),
    [nodes],
  );

  const appOptions = useMemo(
    () => apps.map((a) => ({ value: a.appCode, label: a.appCode, hint: a.appName })),
    [apps],
  );

  async function onGrant() {
    if (!orgId || !appCode) return;
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
      toast.success(t('iam.orgGrants.msgGranted', { app: appCode }));
      setRoles('');
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgGrants.errGrant'));
    } finally {
      setBusy(false);
    }
  }

  async function onRevoke(g: OrgGrantRow) {
    const ok = await confirm({
      title: t('iam.orgGrants.revoke'),
      content: t('iam.orgGrants.confirmRevoke', { org: g.orgName ?? g.orgId, app: g.appCode }),
      danger: true,
      okKey: 'iam.orgGrants.revoke',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setRevoking(`${g.orgId}:${g.appCode}`);
    try {
      await api.del(`/admin/org-grants/${encodeURIComponent(g.orgId)}/${encodeURIComponent(g.appCode)}`);
      toast.success(t('iam.orgGrants.msgRevoked'));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgGrants.errRevoke'));
    } finally {
      setRevoking(null);
    }
  }

  const columns = useMemo<Array<DataTableColumn<OrgGrantRow>>>(
    () => [
      { key: 'orgName', titleKey: 'iam.orgGrants.th.org', render: (g) => g.orgName ?? '—' },
      {
        key: 'orgCode',
        titleKey: 'iam.orgGrants.th.code',
        mono: true,
        width: 150,
        render: (g) => <span className="dim">{g.orgCode ?? g.orgId}</span>,
      },
      {
        key: 'appCode',
        titleKey: 'iam.orgGrants.th.appCode',
        width: 140,
        render: (g) => (
          <CimTag size="small" tone="run">
            <span className="mono">{g.appCode}</span>
          </CimTag>
        ),
      },
      {
        key: 'roles',
        titleKey: 'iam.orgGrants.th.roles',
        render: (g) =>
          g.roles.length ? (
            <div className="tag-list">
              {g.roles.map((r) => (
                <CimTag key={r} size="small">
                  {r}
                </CimTag>
              ))}
            </div>
          ) : (
            <span className="dim">{t('iam.orgGrants.onlyAdmission')}</span>
          ),
      },
      {
        key: 'includeChildren',
        titleKey: 'iam.orgGrants.th.children',
        width: 110,
        render: (g) =>
          g.includeChildren ? (
            <CimTag size="small" tone="ok">{t('iam.orgGrants.yes')}</CimTag>
          ) : (
            <CimTag size="small">{t('iam.orgGrants.no')}</CimTag>
          ),
      },
      {
        key: 'actions',
        titleKey: 'iam.orgGrants.th.actions',
        width: 110,
        render: (g) => (
          <Perms code={GRANT_REVOKE}>
            <button
              className="btn-danger btn-sm"
              disabled={revoking === `${g.orgId}:${g.appCode}`}
              onClick={() => void onRevoke(g)}
            >
              <IconTrash width={13} height={13} />
              {t('iam.orgGrants.revoke')}
            </button>
          </Perms>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, revoking],
  );

  return (
    <Page>
      <Panel
        title={t('iam.orgGrants.title')}
        sub={t('iam.orgGrants.sub')}
        actions={<CimTag>{t('iam.orgGrants.count', { n: grants.length })}</CimTag>}
      >
        <DataTable<OrgGrantRow>
          flat
          columns={columns}
          rows={grants}
          rowKey={(g) => `${g.orgId}:${g.appCode}`}
          loading={bundleRes.loading}
          error={bundleRes.error?.message}
          onRetry={bundleRes.refresh}
          onRefresh={bundleRes.refresh}
          settingsKey="iam.orgGrants"
          emptyTitleKey="iam.orgGrants.emptyTitle"
          emptyHintKey="iam.orgGrants.emptyHint"
          scrollX="max-content"
        />
      </Panel>

      <Perms code={GRANT_GRANT}>
        <Panel title={t('iam.orgGrants.grantTitle')} sub={t('iam.orgGrants.grantSub')}>
          <div className="row">
            <div className="field">
              <label className="field-label">{t('iam.orgGrants.f.org')}</label>
              <CimSelect
                value={orgId}
                onChange={(v) => setOrgId(typeof v === 'string' ? v : '')}
                options={orgOptions}
                placeholderKey="iam.orgGrants.selectOrg"
                width={300}
              />
            </div>
            <div className="field">
              <label className="field-label">{t('iam.orgGrants.f.appCode')}</label>
              <CimSelect
                value={appCode}
                onChange={(v) => setAppCode(typeof v === 'string' ? v : '')}
                options={appOptions}
                placeholderKey="iam.orgGrants.selectApp"
                width={220}
              />
            </div>
            <div className="field">
              <label className="field-label">{t('iam.orgGrants.f.roles')}</label>
              <CimInput
                mono
                value={roles}
                onChange={setRoles}
                placeholder="OPERATOR,SHIFT_LEAD"
                width={240}
              />
            </div>
            <div className="field">
              <label className="field-label">{t('iam.orgGrants.includeChildren')}</label>
              <CimSwitch checked={includeChildren} onChange={setIncludeChildren} />
            </div>
            <button type="button" disabled={busy || !orgId || !appCode} onClick={() => void onGrant()}>
              <IconPlus width={13} height={13} />
              {busy ? t('iam.orgGrants.granting') : t('iam.orgGrants.grant')}
            </button>
          </div>
          <p className="hint">
            {t('iam.orgGrants.hint')}
            {affected != null ? <> {t('iam.orgGrants.affected', { n: affected })}</> : null}
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
          <CimTag size="small" tone="run">
            <IconShield width={12} height={12} />
            {t('iam.orgGrants.divIam')}
          </CimTag>
        </div>
      </Panel>
    </Page>
  );
}
