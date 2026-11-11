import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  CimModal,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import type { DeviceArea } from '@/types';

/**
 * 区域维护。
 *
 * 本页是「平台组件替换手写结构」的样板页（P1 首个迁移页面），
 * 对照旧实现可以看到省掉的东西：
 *   - 手写 `<table className="data">` + 手动 thead/tbody + 手工空态 colSpan  → DataTable
 *   - 手写 `const [loading]` 三态（载入/空/错）                          → loading / error / empty
 *   - 手写 `.modal-backdrop/.modal-head/.modal-body/.modal-foot`        → CimModal
 *   - 手写 `.form-grid` + 5 个 `.field`/`.field-label`                   → FormGrid 字段数组
 *   - 手写 `.page-head`/`.toolbar`                                       → PageHead / DataTable 工具栏
 *   - `window.confirm()` 阻塞式确认 + 页顶 `useState` 提示条            → useConfirm / useToast
 * 页面里只剩「取数 + 业务动作」。
 */
export default function DeviceAreaPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [list, setList] = useState<DeviceArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');

  const [editing, setEditing] = useState<DeviceArea | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listDeviceAreas({ keyword: keyword || undefined });
      setList(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    // 输入防抖 200ms，避免每个字符打一次接口
    const id = setTimeout(() => void refresh(), 200);
    return () => clearTimeout(id);
  }, [refresh]);

  const doDelete = useCallback(
    async (x: DeviceArea) => {
      const ok = await confirm({
        title: t('rms.common.delete'),
        contentKey: 'rms.common.confirmDelete',
        danger: true,
        okKey: 'rms.common.delete',
      });
      if (!ok) return;
      try {
        await api.deleteDeviceArea(x.id);
        toast.success(t('rms.common.deleted'));
      } catch (e) {
        // 删除失败用 toast 而不占 error 态：列表本身仍是可用的，整块替换成错误页反而更糟
        toast.error(e instanceof Error ? e.message : String(e));
        return;
      }
      await refresh();
    },
    [t, refresh, confirm, toast],
  );

  // 后端已按 keyword 过滤；前端再滤一遍兜底（后端忽略参数时仍可用），与旧行为一致
  const rows = useMemo(() => {
    if (!keyword) return list;
    const k = keyword.toLowerCase();
    return list.filter((x) => x.code.toLowerCase().includes(k) || x.name.toLowerCase().includes(k));
  }, [list, keyword]);

  const columns = useMemo<Array<DataTableColumn<DeviceArea>>>(
    () => [
      { key: 'code', titleKey: 'rms.deviceArea.code', mono: true, width: 160 },
      { key: 'name', titleKey: 'rms.deviceArea.name', width: 220 },
      {
        key: 'parentName',
        titleKey: 'rms.deviceArea.parent',
        width: 180,
        render: (r) => r.parentName ?? t('rms.common.dash'),
      },
      {
        key: 'remark',
        titleKey: 'rms.common.remark',
        render: (r) => <span className="muted">{r.remark ?? t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 160,
        render: (r) => (
          <span className="cell-actions">
            <Perms code="rms:device-area:update">
              <button className="btn btn-ghost btn-sm" onClick={() => setEditing(r)}>
                {t('rms.common.edit')}
              </button>
            </Perms>
            <Perms code="rms:device-area:delete">
              <button className="btn btn-ghost btn-sm danger" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </button>
            </Perms>
          </span>
        ),
      },
    ],
    [t, doDelete],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.deviceArea.title"
        actions={
          <Perms code="rms:device-area:create">
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
              + {t('rms.deviceArea.createNew')}
            </button>
          </Perms>
        }
      />

      <DataTable<DeviceArea>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error}
        onRetry={() => void refresh()}
        onRefresh={() => void refresh()}
        settingsKey="rms.device-area"
        search={{ value: keyword, onChange: setKeyword, placeholderKey: 'rms.common.search' }}
      />

      {(showCreate || editing) && (
        <AreaModal
          areas={list}
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={async () => {
            const wasEdit = !!editing;
            setShowCreate(false);
            setEditing(null);
            toast.success(t(wasEdit ? 'rms.common.updated' : 'rms.common.created'));
            await refresh();
          }}
        />
      )}
    </Page>
  );
}

interface AreaForm {
  code: string;
  name: string;
  parentId: string;
  remark: string;
}

function AreaModal({
  areas,
  initial,
  onClose,
  onDone,
}: {
  areas: DeviceArea[];
  initial: DeviceArea | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<AreaForm>({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    parentId: initial?.parentId ?? '',
    remark: initial?.remark ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof AreaForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 编辑时不能选自己作为父级（避免成环）
  const parentOptions = useMemo(
    () =>
      areas
        .filter((a) => !initial || a.id !== initial.id)
        .map((a) => ({ value: a.id, label: a.name })),
    [areas, initial],
  );

  const fields = useMemo<Array<FormField<AreaForm>>>(
    () => [
      {
        kind: 'text',
        key: 'code',
        labelKey: 'rms.deviceArea.code',
        required: true,
        mono: true,
        disabled: !!initial, // 编码是主标识，不允许改
      },
      { kind: 'text', key: 'name', labelKey: 'rms.deviceArea.name', required: true },
      {
        kind: 'select',
        key: 'parentId',
        labelKey: 'rms.deviceArea.parent',
        options: parentOptions,
        placeholderKey: 'rms.deviceArea.parentEmpty',
        span: 2,
      },
      { kind: 'text', key: 'remark', labelKey: 'rms.common.remark', span: 2 },
    ],
    [parentOptions, initial],
  );

  async function submit() {
    const next: Partial<Record<keyof AreaForm, string>> = {};
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
        parentId: form.parentId || null,
        remark: form.remark || null,
      };
      if (initial) await api.updateDeviceArea(initial.id, payload);
      else await api.createDeviceArea(payload);
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
      titleKey={initial ? 'rms.common.edit' : 'rms.deviceArea.createNew'}
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={560}
    >
      <FormGrid<AreaForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      {err ? <p className="cim-field-error" style={{ marginTop: 10 }}>{err}</p> : null}
    </CimModal>
  );
}
