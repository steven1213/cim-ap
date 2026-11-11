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
import type { DeviceType } from '@/types';

/**
 * 机型维护。
 *
 * 迁移要点（对照旧实现）：
 *   - 取数从「手写 useState + useEffect + setTimeout」改为 `useResource`，
 *     竞态、取消、失败态由 hook 保证；搜索防抖从取数函数里拿出来，改用 `useDebounced`，
 *     保证「后端过滤用的值」与「前端兜底过滤用的值」是**同一个滞后值**（旧实现会闪一行空态）。
 *   - 表格/弹窗/表单/确认框全部换成平台组件，页面只剩「取数 + 业务动作」。
 */
export default function DeviceTypePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounced(keyword, 200);

  const [editing, setEditing] = useState<DeviceType | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const { data, loading, error, refresh } = useResource(
    () => api.listDeviceTypes({ keyword: debouncedKeyword || undefined }),
    { deps: [debouncedKeyword] },
  );

  // 后端已按 keyword 过滤；前端再滤一遍兜底（后端忽略参数时仍可用），与旧行为一致
  const rows = useMemo(() => {
    const list = data ?? [];
    if (!debouncedKeyword) return list;
    const k = debouncedKeyword.toLowerCase();
    return list.filter(
      (x) =>
        x.code.toLowerCase().includes(k) ||
        x.name.toLowerCase().includes(k) ||
        (x.manufacturer ?? '').toLowerCase().includes(k),
    );
  }, [data, debouncedKeyword]);

  const doDelete = async (x: DeviceType) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteDeviceType(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      // 删除失败只弹提示，不把整张表换成错误态：列表本身还是可用的
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const columns = useMemo<Array<DataTableColumn<DeviceType>>>(
    () => [
      { key: 'code', titleKey: 'rms.deviceType.code', mono: true, width: 160 },
      { key: 'name', titleKey: 'rms.deviceType.name', width: 220 },
      {
        key: 'manufacturer',
        titleKey: 'rms.deviceType.manufacturer',
        width: 180,
        render: (r) => r.manufacturer ?? t('rms.common.dash'),
      },
      {
        key: 'model',
        titleKey: 'rms.deviceType.model',
        width: 160,
        render: (r) => r.model ?? t('rms.common.dash'),
      },
      {
        key: 'remark',
        titleKey: 'rms.common.remark',
        render: (r) => <span className="muted">{r.remark ?? t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 150,
        render: (r) => (
          <span className="cell-actions">
            <Can perm="rms:device-type:update">
              <CimButton size="small" onClick={() => setEditing(r)}>
                {t('rms.common.edit')}
              </CimButton>
            </Can>
            <Can perm="rms:device-type:delete">
              <CimButton size="small" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.deviceType.title"
        actions={
          <Can perm="rms:device-type:create">
            <CimButton type="primary" size="small" onClick={() => setShowCreate(true)}>
              + {t('rms.deviceType.createNew')}
            </CimButton>
          </Can>
        }
      />

      <DataTable<DeviceType>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="rms.device-type"
        search={{ value: keyword, onChange: setKeyword, placeholderKey: 'rms.common.search' }}
      />

      {(showCreate || editing) && (
        <TypeModal
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={() => {
            const wasEdit = !!editing;
            setShowCreate(false);
            setEditing(null);
            toast.success(t(wasEdit ? 'rms.common.updated' : 'rms.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

interface TypeForm {
  code: string;
  name: string;
  manufacturer: string;
  model: string;
  remark: string;
}

function TypeModal({
  initial,
  onClose,
  onDone,
}: {
  initial: DeviceType | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<TypeForm>({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    manufacturer: initial?.manufacturer ?? '',
    model: initial?.model ?? '',
    remark: initial?.remark ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof TypeForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const fields = useMemo<Array<FormField<TypeForm>>>(
    () => [
      {
        kind: 'text',
        key: 'code',
        labelKey: 'rms.deviceType.code',
        required: true,
        mono: true,
        disabled: !!initial, // 编码是主标识，不允许改
      },
      { kind: 'text', key: 'name', labelKey: 'rms.deviceType.name', required: true },
      { kind: 'text', key: 'manufacturer', labelKey: 'rms.deviceType.manufacturer' },
      { kind: 'text', key: 'model', labelKey: 'rms.deviceType.model' },
      { kind: 'text', key: 'remark', labelKey: 'rms.common.remark', span: 2 },
    ],
    [initial],
  );

  async function submit() {
    const next: Partial<Record<keyof TypeForm, string>> = {};
    if (!form.code) next.code = t('rms.common.required');
    if (!form.name) next.name = t('rms.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      const payload = {
        code: form.code,
        name: form.name,
        manufacturer: form.manufacturer || null,
        model: form.model || null,
        remark: form.remark || null,
      };
      if (initial) await api.updateDeviceType(initial.id, payload);
      else await api.createDeviceType(payload);
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
      titleKey={initial ? 'rms.common.edit' : 'rms.deviceType.createNew'}
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={560}
    >
      <FormGrid<TypeForm>
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
