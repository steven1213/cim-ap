import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
} from '@cim/ui';
import * as api from '@/lib/api';
import type { AppRegistration, AppStatus } from '@/types';
import { APP_CREATE, APP_UPDATE } from '@/lib/permCodes';
import { IconPlus } from '@/components/Icons';

interface RegisterForm {
  appCode: string;
  appName: string;
  sortNo: number;
}

const EMPTY: RegisterForm = { appCode: '', appName: '', sortNo: 0 };

/** 应用注册：登记业务接入码、维护状态（按钮按 `iam:app:*` 权限显隐）。准入分配见「准入授权」。 */
export default function AppMgmtPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [form, setForm] = useState<RegisterForm>(EMPTY);
  const [busy, setBusy] = useState(false);
  const [toggling, setToggling] = useState<string | null>(null);

  const appsRes = useResource<AppRegistration[]>(({ signal }) => api.get<AppRegistration[]>('/apps', undefined, { signal }));
  const apps = appsRes.data ?? [];
  const enabled = apps.filter((a) => a.status === 'ENABLED').length;

  async function onRegister() {
    if (!form.appCode.trim() || !form.appName.trim()) return;
    setBusy(true);
    try {
      await api.post<AppRegistration>('/apps', {
        appCode: form.appCode.trim(),
        appName: form.appName.trim(),
        sortNo: Number(form.sortNo) || 0,
      });
      toast.success(t('iam.appMgmt.msgRegistered', { code: form.appCode.trim() }));
      setForm(EMPTY);
      appsRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.appMgmt.errRegister'));
    } finally {
      setBusy(false);
    }
  }

  async function toggleStatus(a: AppRegistration) {
    const next: AppStatus = a.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    // 只在「停用」时确认：停用会连带撤掉该 ap 下所有人的准入（登录时 apps claim 不再包含它）
    if (next === 'DISABLED') {
      const ok = await confirm({
        title: t('iam.appMgmt.disable'),
        content: t('iam.appMgmt.confirmDisable', { code: a.appCode }),
        danger: true,
        okKey: 'iam.appMgmt.disable',
        cancelKey: 'iam.common.cancel',
      });
      if (!ok) return;
    }
    setToggling(a.appCode);
    try {
      await api.put(`/apps/${encodeURIComponent(a.appCode)}`, { appName: a.appName, status: next });
      toast.success(
        t('iam.appMgmt.msgToggled', {
          code: a.appCode,
          state: next === 'ENABLED' ? t('iam.appMgmt.stateEnabled') : t('iam.appMgmt.stateDisabled'),
        }),
      );
      appsRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.appMgmt.errUpdate'));
    } finally {
      setToggling(null);
    }
  }

  const fields = useMemo<Array<FormField<RegisterForm>>>(
    () => [
      { key: 'appCode', labelKey: 'iam.appMgmt.f.appCode', kind: 'text', mono: true, required: true, placeholder: 'mds-ap' },
      { key: 'appName', labelKey: 'iam.appMgmt.f.name', kind: 'text', required: true, placeholderKey: 'iam.appMgmt.namePH' },
      { key: 'sortNo', labelKey: 'iam.appMgmt.f.sort', kind: 'number', min: 0 },
    ],
    [],
  );

  const columns = useMemo<Array<DataTableColumn<AppRegistration>>>(
    () => [
      { key: 'appCode', titleKey: 'iam.appMgmt.th.appCode', mono: true, width: 180, render: (a) => a.appCode },
      { key: 'appName', titleKey: 'iam.appMgmt.th.name', render: (a) => a.appName },
      {
        key: 'status',
        titleKey: 'iam.appMgmt.th.status',
        width: 110,
        render: (a) =>
          a.status === 'ENABLED' ? (
            <CimTag size="small" tone="ok">{t('iam.appMgmt.enabled2')}</CimTag>
          ) : (
            <CimTag size="small" tone="err">{t('iam.appMgmt.disabled2')}</CimTag>
          ),
      },
      {
        key: 'sortNo',
        titleKey: 'iam.appMgmt.th.sort',
        width: 90,
        align: 'right',
        render: (a) => a.sortNo,
      },
      {
        key: 'actions',
        titleKey: 'iam.appMgmt.th.actions',
        width: 120,
        render: (a) => (
          <Can perm={APP_UPDATE}>
            <CimButton
              size="small"
              disabled={toggling === a.appCode}
              onClick={() => void toggleStatus(a)}
            >
              {a.status === 'ENABLED' ? t('iam.appMgmt.disable') : t('iam.appMgmt.enable')}
            </CimButton>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, toggling],
  );

  return (
    <Page>
      <Panel
        title={t('iam.appMgmt.title')}
        actions={
          <>
            <CimTag>{t('iam.appMgmt.count', { n: apps.length })}</CimTag>
            <CimTag tone="ok">{t('iam.appMgmt.enabled', { n: enabled })}</CimTag>
          </>
        }
      >
        <DataTable<AppRegistration>
          flat
          columns={columns}
          rows={apps}
          rowKey={(a) => a.id}
          loading={appsRes.loading}
          error={appsRes.error?.message}
          onRetry={appsRes.refresh}
          onRefresh={appsRes.refresh}
          settingsKey="iam.appMgmt"
          emptyTitleKey="iam.appMgmt.emptyTitle"
          emptyHintKey="iam.appMgmt.emptyHint"
        />
      </Panel>

      <Can perm={APP_CREATE}>
        <Panel title={t('iam.appMgmt.registerTitle')}>
          <FormGrid<RegisterForm>
            fields={fields}
            value={form}
            onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
            columns={3}
          />
          <p className="hint">{t('iam.appMgmt.hint')}</p>
          <CimButton
            type="primary"
            block
            htmlType="button"
            style={{ marginTop: 12 }}
            disabled={busy || !form.appCode.trim() || !form.appName.trim()}
            onClick={() => void onRegister()}
          >
            <IconPlus width={13} height={13} />
            {t('iam.appMgmt.register')}
          </CimButton>
        </Panel>
      </Can>

      <Panel title={t('iam.appMgmt.guideTitle')}>
        <ul className="note-list">
          <li>{t('iam.appMgmt.guide1')}</li>
          <li>{t('iam.appMgmt.guide2')}</li>
          <li>{t('iam.appMgmt.guide3')}</li>
        </ul>
      </Panel>
    </Page>
  );
}
