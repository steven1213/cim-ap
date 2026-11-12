import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  CimSelect,
  DataTable,
  FormGrid,
  Page,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  type FormOption,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { Area, Fab } from '@/types';

/** 模块级空数组：`data ?? []` 每次渲染都会造新引用。 */
const NO_AREAS: Area[] = [];
const NO_FABS: Fab[] = [];

/**
 * 区域维护（Site → Fab → Area 的第三层）。
 *
 * <p>Area 编码在**同一个 Fab 内**唯一，不是全局唯一——后端按 (fabCode, areaCode) 查重，
 * 所以 `A-LITHO` 在 FAB1 与 FAB2 下可以同时存在。前端不做该规则的本地校验：
 * 换个归属再保存时本地无从判断，规则只有后端一处。</p>
 */
export default function AreaPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canViewFab = useHasPermission(MDS_PERMS.fab.view);

  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounced(keyword, 200);
  const [fabFilter, setFabFilter] = useState<string | null>(null);

  const [editing, setEditing] = useState<Area | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  // Fab 下拉（顶部筛选 + 表单共用）：没有「Fab 查看」权限就不拉，避免整页 403
  const fabsRes = useResource(({ signal }) => api.listFabs(undefined, undefined, signal), { enabled: canViewFab });
  const fabOptions = useMemo<FormOption[]>(
    () => (fabsRes.data ?? NO_FABS).map((f) => ({ value: f.fabCode, label: f.fabName, hint: f.fabCode })),
    [fabsRes.data],
  );

  const { data, loading, error, refresh } = useResource(
    ({ signal }) => api.listAreas(fabFilter ?? undefined, debouncedKeyword || undefined, signal),
    { deps: [fabFilter, debouncedKeyword], keepPreviousData: true },
  );
  const areas = data ?? NO_AREAS;

  const doDelete = useCallback(
    async (x: Area) => {
      const ok = await confirm({
        title: t('mds.common.delete'),
        contentKey: 'mds.common.confirmDelete',
        danger: true,
        okKey: 'mds.common.delete',
      });
      if (!ok) return;
      try {
        await api.deleteArea(x.id);
        toast.success(t('mds.common.deleted'));
      } catch (e) {
        toast.error(e instanceof Error ? e.message : String(e));
        return;
      }
      refresh();
    },
    [t, confirm, toast, refresh],
  );

  const columns = useMemo<Array<DataTableColumn<Area>>>(
    () => [
      { key: 'areaCode', titleKey: 'mds.area.code', mono: true, width: 150 },
      { key: 'areaName', titleKey: 'mds.area.name', width: 180 },
      {
        key: 'fabName',
        titleKey: 'mds.area.fab',
        width: 180,
        render: (r) => r.fabName ?? <span className="dim">{r.fabCode}</span>,
      },
      {
        key: 'areaType',
        titleKey: 'mds.area.type',
        width: 140,
        render: (r) => r.areaType ?? <span className="dim">{t('mds.common.dash')}</span>,
      },
      {
        key: 'remark',
        titleKey: 'mds.common.remark',
        render: (r) => <span className="dim">{r.remark ?? t('mds.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'mds.common.actions',
        width: 160,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.area.update}>
              <CimButton size="small" onClick={() => setEditing(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.area.delete}>
              <CimButton size="small" onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    [t, doDelete],
  );

  return (
    <Page>
      

      <DataTable<Area>
        headActions={<Can perm={MDS_PERMS.area.create}>
            <CimButton type="primary" size="small" onClick={() => setShowCreate(true)}>
              + {t('mds.area.createNew')}
            </CimButton>
          </Can>}
        columns={columns}
        rows={areas}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="mds.area"
        scrollX="max-content"
        search={{ value: keyword, onChange: setKeyword, placeholderKey: 'mds.common.search' }}
        actions={
          <CimSelect
            value={fabFilter}
            onChange={(v) => setFabFilter(typeof v === 'string' && v ? v : null)}
            options={fabOptions}
            placeholderKey="mds.common.all"
            allowClear
            loading={fabsRes.loading}
            width={200}
          />
        }
      />

      {(showCreate || editing) && (
        <AreaModal
          initial={editing}
          fabOptions={fabOptions}
          defaultFabCode={fabFilter}
          canPickFab={canViewFab}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={() => {
            const wasEdit = !!editing;
            setShowCreate(false);
            setEditing(null);
            toast.success(t(wasEdit ? 'mds.common.updated' : 'mds.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

interface AreaForm {
  areaCode: string;
  fabCode: string;
  areaName: string;
  areaType: string;
  remark: string;
}

function AreaModal({
  initial,
  fabOptions,
  defaultFabCode,
  canPickFab,
  onClose,
  onDone,
}: {
  initial: Area | null;
  fabOptions: FormOption[];
  defaultFabCode: string | null;
  canPickFab: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<AreaForm>({
    areaCode: initial?.areaCode ?? '',
    fabCode: initial?.fabCode ?? defaultFabCode ?? '',
    areaName: initial?.areaName ?? '',
    areaType: initial?.areaType ?? '',
    remark: initial?.remark ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof AreaForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const fields = useMemo<Array<FormField<AreaForm>>>(
    () => [
      {
        kind: 'text',
        key: 'areaCode',
        labelKey: 'mds.area.code',
        required: true,
        mono: true,
        disabled: !!initial, // (fabCode, areaCode) 是业务键，改编码等于换了一条记录
      },
      {
        kind: 'select',
        key: 'fabCode',
        labelKey: 'mds.area.fab',
        required: true,
        options: fabOptions,
        showSearch: true,
        placeholderKey: 'mds.area.fabPlaceholder',
        disabled: !canPickFab,
      },
      { kind: 'text', key: 'areaName', labelKey: 'mds.area.name', required: true },
      {
        kind: 'text',
        key: 'areaType',
        labelKey: 'mds.area.type',
        // 工艺大类是自由口径（PHOTO / ETCH / DIFF…），各厂分类粒度不同，先不收窄成枚举
        placeholder: 'PHOTO',
      },
      { kind: 'text', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    [initial, fabOptions, canPickFab],
  );

  async function submit() {
    const next: Partial<Record<keyof AreaForm, string>> = {};
    if (!form.areaCode.trim()) next.areaCode = t('mds.common.required');
    if (!form.fabCode.trim()) next.fabCode = t('mds.common.required');
    if (!form.areaName.trim()) next.areaName = t('mds.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      const payload = {
        areaCode: form.areaCode.trim(),
        fabCode: form.fabCode.trim(),
        areaName: form.areaName.trim(),
        areaType: form.areaType.trim() || null,
        remark: form.remark || null,
      };
      if (initial) await api.updateArea(initial.id, payload);
      else await api.createArea(payload);
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
      titleKey={initial ? 'mds.common.edit' : 'mds.area.createNew'}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
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
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
