import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { DEVICE_AUTH_MANAGE } from '@/lib/permCodes';
import type { Device, DeviceAuth, DeviceArea, DeviceType } from '@/types';

const NO_AUTHS: DeviceAuth[] = [];
// 模块级空数组：`data ?? []` 每次渲染都会造新引用，会让下面的 columns / fields 依赖恒变
const NO_DEVICES: Device[] = [];
const NO_TYPES: DeviceType[] = [];
const NO_AREAS: DeviceArea[] = [];

/**
 * 设备授权页（FR-P4，Req 11/12）。
 *
 * 迁移要点：授权主体搜索加 200ms 防抖（原来每敲一个字符发一次请求），取数交给 `useResource`
 * 并保留旧数据，输入过程中表格不会闪空态；弹窗的「设备/机型/区域至少一项」校验从页顶一条
 * 错误行改为字段级 + 表单级两处提示。
 */
export default function DeviceAuthPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [subject, setSubject] = useState('');
  const debouncedSubject = useDebounced(subject, 200);
  const [show, setShow] = useState(false);

  const { data, loading, error, refresh } = useResource(
    () => api.listDeviceAuth({ subject: debouncedSubject || undefined }),
    { deps: [debouncedSubject], keepPreviousData: true },
  );
  const rows = data ?? NO_AUTHS;

  // 弹窗用的参照数据：一次取齐，开关弹窗不重复请求
  const devices = useResource(() => api.listDevices()).data ?? NO_DEVICES;
  const types = useResource(() => api.listDeviceTypes()).data ?? NO_TYPES;
  const areas = useResource(() => api.listDeviceAreas()).data ?? NO_AREAS;

  const label = (x: DeviceAuth) => {
    if (x.deviceId) return `${t('rms.deviceAuth.device')}: ${x.deviceLabel ?? x.deviceId}`;
    if (x.deviceTypeId)
      return `${t('rms.deviceAuth.deviceType')}: ${
        types.find((z) => z.id === x.deviceTypeId)?.name ?? x.deviceTypeId
      }`;
    if (x.areaId)
      return `${t('rms.deviceAuth.area')}: ${areas.find((z) => z.id === x.areaId)?.name ?? x.areaId}`;
    return t('rms.common.dash');
  };

  const revoke = async (x: DeviceAuth) => {
    const ok = await confirm({
      title: t('rms.common.revoke'),
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.revoke',
    });
    if (!ok) return;
    try {
      await api.revokeDeviceAuth(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const columns = useMemo<Array<DataTableColumn<DeviceAuth>>>(
    () => [
      { key: 'subject', titleKey: 'rms.deviceAuth.subject', mono: true, width: 200 },
      { key: 'target', titleKey: 'rms.deviceAuth.target', render: (r) => label(r) },
      {
        key: 'canOperate',
        titleKey: 'rms.deviceAuth.canOperate',
        width: 110,
        align: 'center',
        render: (r) => (r.canOperate ? t('rms.common.yes') : t('rms.common.no')),
      },
      {
        key: 'createTime',
        titleKey: 'rms.common.createTime',
        width: 180,
        render: (r) => <span className="muted">{r.createTime ?? t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 120,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={DEVICE_AUTH_MANAGE}>
              <CimButton size="small" onClick={() => void revoke(r)}>
                {t('rms.common.revoke')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, types, areas],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.deviceAuth.title"
        subtitleKey="rms.deviceAuth.sub"
        actions={
          <Can perm={DEVICE_AUTH_MANAGE}>
            <CimButton type="primary" size="small" onClick={() => setShow(true)}>
              + {t('rms.deviceAuth.grant')}
            </CimButton>
          </Can>
        }
      />

      <DataTable<DeviceAuth>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="rms.device-auth"
        search={{
          value: subject,
          onChange: setSubject,
          placeholderKey: 'rms.deviceAuth.subject',
        }}
        emptyTitleKey="rms.deviceAuth.empty"
      />

      {show && (
        <AuthModal
          devices={devices}
          types={types}
          areas={areas}
          onClose={() => setShow(false)}
          onDone={() => {
            setShow(false);
            toast.success(t('rms.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

interface AuthForm {
  subject: string;
  deviceId: string;
  deviceTypeId: string;
  areaId: string;
  canOperate: boolean;
}

function AuthModal({
  devices,
  types,
  areas,
  onClose,
  onDone,
}: {
  devices: Device[];
  types: DeviceType[];
  areas: DeviceArea[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<AuthForm>({
    subject: '',
    deviceId: '',
    deviceTypeId: '',
    areaId: '',
    canOperate: true,
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof AuthForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const fields = useMemo<Array<FormField<AuthForm>>>(
    () => [
      { kind: 'text', key: 'subject', labelKey: 'rms.deviceAuth.subject', required: true, mono: true, span: 2 },
      {
        kind: 'select',
        key: 'deviceId',
        labelKey: 'rms.deviceAuth.device',
        options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
      },
      {
        kind: 'select',
        key: 'deviceTypeId',
        labelKey: 'rms.deviceAuth.deviceType',
        options: types.map((d) => ({ value: d.id, label: d.name })),
      },
      {
        kind: 'select',
        key: 'areaId',
        labelKey: 'rms.deviceAuth.area',
        options: areas.map((d) => ({ value: d.id, label: d.name })),
      },
      { kind: 'switch', key: 'canOperate', labelKey: 'rms.deviceAuth.canOperate' },
    ],
    [devices, types, areas],
  );

  async function submit() {
    const next: Partial<Record<keyof AuthForm, string>> = {};
    if (!form.subject) next.subject = t('rms.common.required');
    if (Object.keys(next).length) {
      setFieldErrors(next);
      return;
    }
    setFieldErrors({});
    if (!form.deviceId && !form.deviceTypeId && !form.areaId) {
      setErr(t('rms.deviceAuth.targetRequired'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.grantDeviceAuth({
        subject: form.subject,
        subjectType: 'USER',
        deviceId: form.deviceId || null,
        deviceTypeId: form.deviceTypeId || null,
        areaId: form.areaId || null,
        canOperate: form.canOperate,
      });
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.deviceAuth.grant"
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={600}
    >
      <FormGrid<AuthForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      {err ? (
        <p className="cim-field-error" style={{ marginTop: 10 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}
