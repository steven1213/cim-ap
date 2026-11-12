import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimInput,
  CimTag,
  DataTable,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  CimButton,
  CimField,
} from '@cim/ui';
import * as api from '@/lib/api';
import type { AppRegistration, AssignmentDto } from '@/types';
import { ADMISSION_GRANT, ADMISSION_REVOKE } from '@/lib/permCodes';

/**
 * 准入授权：以「用户」为主线，查看并编辑其可进入的 ap 与角色组。
 *
 * <p>任何授予 / 撤销都会 bump 该用户令牌版本（后端自动），其存量令牌立即失效、需重新登录 —
 * 这正是「准入变更即时生效」的落点。写操作按钮按 `iam:admission:grant|revoke` 权限显隐。</p>
 */
export default function AdmissionsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  /** 输入框里的用户（草稿）。 */
  const [userId, setUserId] = useState('admin');
  /** 已提交查询的用户（生效）。表格与写操作一律以它为准 —— 否则「改了输入框没点查询就授权」会把角色授给新输入的人。 */
  const [applied, setApplied] = useState('admin');
  /** 用户在表格里的临时输入；为空表示「沿用服务端值」。查询成功后整体清空。 */
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);

  const appsRes = useResource<AppRegistration[]>(({ signal }) => api.get<AppRegistration[]>('/apps', undefined, { signal }));

  const assignRes = useResource<AssignmentDto[]>(
    ({ signal }) => api.get<AssignmentDto[]>(`/apps/users/${encodeURIComponent(applied)}/assignments`, undefined, { signal }),
    { deps: [applied], enabled: !!applied, keepPreviousData: true },
  );

  const apps = appsRes.data ?? [];
  const assignments = assignRes.data;

  function submit() {
    const next = userId.trim();
    if (!next) return;
    setEdits({});
    if (next === applied) assignRes.refresh();
    else setApplied(next);
  }

  function roleOf(code: string): AssignmentDto | undefined {
    return assignments?.find((a) => a.appCode === code);
  }

  function valueOf(code: string): string {
    return edits[code] ?? roleOf(code)?.roles.join(', ') ?? '';
  }

  async function grant(appCode: string) {
    setBusy(appCode);
    try {
      const roles = (valueOf(appCode) ?? '')
        .split(',')
        .map((r) => r.trim())
        .filter(Boolean);
      await api.post(`/apps/${encodeURIComponent(appCode)}/users`, { userId: applied, roles });
      toast.success(
        t('iam.admissions.msgGranted', {
          user: applied,
          app: appCode,
          roles: roles.join(', ') || t('iam.admissions.noRole'),
        }),
      );
      setEdits({});
      assignRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.admissions.errGrant'));
    } finally {
      setBusy(null);
    }
  }

  async function revoke(appCode: string) {
    const ok = await confirm({
      title: t('iam.admissions.revoke'),
      content: t('iam.admissions.revokeConfirm', { user: applied, app: appCode }),
      danger: true,
      okKey: 'iam.admissions.revoke',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setBusy(appCode);
    try {
      await api.del(`/apps/${encodeURIComponent(appCode)}/users/${encodeURIComponent(applied)}`);
      toast.success(t('iam.admissions.msgRevoked', { user: applied, app: appCode }));
      setEdits({});
      assignRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.admissions.errRevoke'));
    } finally {
      setBusy(null);
    }
  }

  const columns = useMemo<Array<DataTableColumn<AppRegistration>>>(
    () => [
      { key: 'appCode', titleKey: 'iam.admissions.th.appCode', width: 150, mono: true, render: (a) => a.appCode },
      { key: 'appName', titleKey: 'iam.admissions.th.appName', width: 170, render: (a) => a.appName },
      {
        key: 'admitted',
        titleKey: 'iam.admissions.th.admission',
        width: 110,
        render: (a) =>
          roleOf(a.appCode) ? (
            <CimTag size="small" tone="ok">
              {t('iam.admissions.admitted')}
            </CimTag>
          ) : (
            <CimTag size="small">{t('iam.admissions.notAdmitted')}</CimTag>
          ),
      },
      {
        key: 'roles',
        titleKey: 'iam.admissions.th.roles',
        render: (a) => (
          <CimInput
            mono
            value={valueOf(a.appCode)}
            onChange={(v) => setEdits((m) => ({ ...m, [a.appCode]: v }))}
            placeholder="ADMIN, OPERATOR"
            onEnter={() => void grant(a.appCode)}
            width="100%"
          />
        ),
      },
      {
        key: 'actions',
        titleKey: 'iam.admissions.th.actions',
        width: 180,
        render: (a) => {
          const admitted = !!roleOf(a.appCode);
          return (
            <div className="cell-actions">
              <Can perm={ADMISSION_GRANT}>
                <CimButton type="primary" size="small" disabled={busy === a.appCode} onClick={() => void grant(a.appCode)}>
                  {admitted ? t('iam.admissions.update') : t('iam.admissions.grant')}
                </CimButton>
              </Can>
              {admitted && (
                <Can perm={ADMISSION_REVOKE}>
                  <CimButton danger size="small" disabled={busy === a.appCode} onClick={() => void revoke(a.appCode)}>
                    {t('iam.admissions.revoke')}
                  </CimButton>
                </Can>
              )}
            </div>
          );
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, edits, assignments, busy, applied],
  );

  return (
    <Page>
      <Panel title={t('iam.admissions.title')}>
        <div className="row">
          <CimField label={t('iam.admissions.f.userId')} htmlFor="au">
            <CimInput
              id="au"
              mono
              value={userId}
              onChange={setUserId}
              placeholder="admin"
              onEnter={submit}
              width={280}
            />
          </CimField>
          <CimButton type="primary" htmlType="button" onClick={submit} disabled={!userId.trim()}>
            {t('iam.admissions.query')}
          </CimButton>
          <CimTag>{t('iam.admissions.currentUser', { user: applied })}</CimTag>
        </div>

        <div style={{ marginTop: 14 }}>
          <DataTable<AppRegistration>
            flat
            columns={columns}
            rows={apps}
            rowKey={(a) => a.appCode}
            loading={appsRes.loading || assignRes.loading}
            error={appsRes.error?.message ?? assignRes.error?.message}
            onRetry={() => {
              appsRes.refresh();
              assignRes.refresh();
            }}
            onRefresh={() => {
              appsRes.refresh();
              assignRes.refresh();
            }}
            settingsKey="iam.admissions"
            emptyTitleKey="iam.admissions.emptyTitle"
            emptyHintKey="iam.admissions.emptyHint"
          />
        </div>
      </Panel>

      <Panel title={t('iam.admissions.impactTitle')}>
        <ul className="note-list">
          <li>{t('iam.admissions.impact1')}</li>
          <li>{t('iam.admissions.impact2')}</li>
          <li>{t('iam.admissions.impact3')}</li>
        </ul>
      </Panel>
    </Page>
  );
}
