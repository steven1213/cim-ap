import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimCheckbox,
  CimDescriptions,
  CimDrawer,
  CimInput,
  CimModal,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
  CimField,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { BODY_TEMPLATE_MANAGE, BODY_TEMPLATE_VIEW } from '@/lib/permCodes';
import type { BodyTemplate, BodyTemplateDetail, DeviceType, TemplateUpsertItem } from '@/types';

const NO_TEMPLATES: BodyTemplate[] = [];
const NO_TYPES: DeviceType[] = [];

const BODY_FORMATS = ['JSON_STRUCTURED', 'TEXT'];

/**
 * Body 模板页（W10，对齐 camLine ROBDEF）：强制键 + 默认值 + 骨架 Body。
 * 新建配方时向导从这里取骨架；enforced 模板在校验时把缺键记为错误。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>「查看」改为只读抽屉（`CimDrawer` + `CimDescriptions` + 条目表）。旧实现是
 *       **拿编辑弹窗当查看用**——只有 `rms:btemplate:view` 权限的人打开后也能点保存，
 *       等于查看权限顺手给了写权限；现在查看与编辑分开，编辑挂 manage 权限。</li>
 *   <li>取数交给 `useResource`，删除走 `useConfirm` + toast（旧实现删除失败时静默，
 *       列表不动也不说话）。</li>
 *   <li>编码在编辑时可改——后端 update 会做「排除自身」的重名校验，这条能力不收紧。</li>
 * </ul>
 */
export default function BodyTemplatePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<BodyTemplateDetail | null>(null);
  const [viewId, setViewId] = useState<string | null>(null);
  // 编辑前要拉完整详情（含条目与骨架明文）
  const [loadingDetail, setLoadingDetail] = useState(false);

  const listRes = useResource(() => api.listBodyTemplates());
  const typesRes = useResource(() => api.listDeviceTypes());
  const rows = listRes.data ?? NO_TEMPLATES;
  const types = typesRes.data ?? NO_TYPES;

  const viewRes = useResource(
    () => (viewId ? api.getBodyTemplate(viewId) : Promise.resolve(null)),
    { deps: [viewId], enabled: !!viewId },
  );
  const viewing = viewRes.data;

  const typeName = useMemo(() => Object.fromEntries(types.map((x) => [x.id, x.name])), [types]);

  const openEdit = async (x: BodyTemplate) => {
    setLoadingDetail(true);
    try {
      setEditing(await api.getBodyTemplate(x.id));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setLoadingDetail(false);
    }
  };

  const doDelete = async (x: BodyTemplate) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.btpl.deleteConfirm',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteBodyTemplate(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    if (viewId === x.id) setViewId(null);
    listRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<BodyTemplate>>>(
    () => [
      { key: 'code', titleKey: 'rms.btpl.code', mono: true, width: 180 },
      { key: 'name', titleKey: 'rms.btpl.name', width: 220 },
      {
        key: 'bodyFormat',
        titleKey: 'rms.recipe.bodyFormat',
        width: 160,
        render: (r) => <CimTag tone="run">{r.bodyFormat}</CimTag>,
      },
      {
        key: 'scopeDeviceTypeId',
        titleKey: 'rms.btpl.scope',
        width: 180,
        render: (r) =>
          r.scopeDeviceTypeId ? (
            typeName[r.scopeDeviceTypeId] ?? r.scopeDeviceTypeId
          ) : (
            <span className="muted">{t('rms.btpl.scopeGeneric')}</span>
          ),
      },
      {
        key: 'enforced',
        titleKey: 'rms.btpl.enforced',
        width: 110,
        render: (r) => (
          <CimTag
            labelKey={r.enforced ? 'rms.btpl.enforcedYes' : 'rms.btpl.enforcedNo'}
            tone={r.enforced ? 'err' : 'idle'}
          />
        ),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 190,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={BODY_TEMPLATE_VIEW}>
              <CimButton size="small" onClick={() => setViewId(r.id)}>
                {t('rms.common.view')}
              </CimButton>
            </Can>
            <Can perm={BODY_TEMPLATE_MANAGE}>
              <CimButton size="small" onClick={() => void openEdit(r)}>
                {t('rms.common.edit')}
              </CimButton>
              <CimButton size="small" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, typeName, viewId],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.btpl.title"
        subtitleKey="rms.btpl.sub"
        actions={
          <Can perm={BODY_TEMPLATE_MANAGE}>
            <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
              + {t('rms.btpl.create')}
            </CimButton>
          </Can>
        }
      />

      <DataTable<BodyTemplate>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={listRes.loading || loadingDetail}
        error={listRes.error?.message}
        onRetry={listRes.refresh}
        onRefresh={listRes.refresh}
        settingsKey="rms.body-template"
        emptyTitleKey="rms.btpl.empty"
      />

      <CimDrawer
        open={!!viewId}
        onClose={() => setViewId(null)}
        title={viewing ? `${viewing.code} · ${viewing.name}` : t('rms.common.view')}
        width={680}
      >
        {viewRes.loading ? <p className="muted">{t('rms.common.loading')}</p> : null}
        {viewRes.error ? <p className="cim-field-error">{viewRes.error.message}</p> : null}
        {viewing ? (
          <>
            <CimDescriptions<BodyTemplateDetail>
              record={viewing}
              items={[
                { key: 'code', labelKey: 'rms.btpl.code', dataIndex: 'code', render: (r) => <span className="cim-mono">{r.code}</span> },
                { key: 'name', labelKey: 'rms.btpl.name', dataIndex: 'name' },
                { key: 'bodyFormat', labelKey: 'rms.recipe.bodyFormat', render: (r) => <CimTag tone="run">{r.bodyFormat}</CimTag> },
                {
                  key: 'scope',
                  labelKey: 'rms.btpl.scope',
                  render: (r) =>
                    r.scopeDeviceTypeId ? typeName[r.scopeDeviceTypeId] ?? r.scopeDeviceTypeId : t('rms.btpl.scopeGeneric'),
                },
                {
                  key: 'enforced',
                  labelKey: 'rms.btpl.enforced',
                  render: (r) => (
                    <CimTag
                      labelKey={r.enforced ? 'rms.btpl.enforcedYes' : 'rms.btpl.enforcedNo'}
                      tone={r.enforced ? 'err' : 'idle'}
                    />
                  ),
                },
              ]}
            />

            <p className="cim-field-hint" style={{ marginTop: 14 }}>
              {t('rms.btpl.skeleton')}
            </p>
            <pre
              className="cim-mono"
              style={{
                margin: '4px 0 14px',
                padding: 10,
                borderRadius: 6,
                background: 'var(--panel-2, rgba(0,0,0,.03))',
                maxHeight: 200,
                overflow: 'auto',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
              }}
            >
              {viewing.templateBody || '—'}
            </pre>

            <DataTable<TemplateRow>
              columns={itemColumns}
              rows={viewing.items.map((i) => ({
                key: i.paramKey,
                required: i.required,
                defaultValue: i.defaultValue,
                unit: i.unit,
              }))}
              rowKey={(r) => r.key}
              settingsKey="rms.body-template.items"
              emptyTitleKey="rms.common.noData"
            />
          </>
        ) : null}
      </CimDrawer>

      {(creating || editing) && (
        <TemplateModal
          types={types}
          initial={editing}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSaved={() => {
            const wasEdit = !!editing;
            setCreating(false);
            setEditing(null);
            toast.success(t(wasEdit ? 'rms.common.updated' : 'rms.common.created'));
            listRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

interface TemplateRow {
  key: string;
  required: boolean;
  defaultValue: string | null;
  unit: string | null;
}

const itemColumns: Array<DataTableColumn<TemplateRow>> = [
  { key: 'key', titleKey: 'rms.spec.paramKey', mono: true, width: 220 },
  {
    key: 'required',
    titleKey: 'rms.btpl.required',
    width: 90,
    render: (r) => (r.required ? '✓' : '—'),
  },
  {
    key: 'defaultValue',
    titleKey: 'rms.btpl.defaultValue',
    mono: true,
    width: 160,
    render: (r) => r.defaultValue ?? '—',
  },
  { key: 'unit', titleKey: 'rms.spec.unit', width: 90, render: (r) => r.unit ?? '—' },
];

interface TemplateForm {
  code: string;
  name: string;
  bodyFormat: string;
  scopeDeviceTypeId: string;
  enforced: boolean;
  templateBody: string;
}

function TemplateModal({
  types,
  initial,
  onClose,
  onSaved,
}: {
  types: DeviceType[];
  initial: BodyTemplateDetail | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<TemplateForm>({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    bodyFormat: initial?.bodyFormat ?? 'JSON_STRUCTURED',
    scopeDeviceTypeId: initial?.scopeDeviceTypeId ?? '',
    enforced: initial?.enforced ?? true,
    templateBody: initial?.templateBody ?? '',
  });
  const [items, setItems] = useState<TemplateUpsertItem[]>(
    (initial?.items ?? []).map((i) => ({
      paramKey: i.paramKey,
      required: i.required,
      defaultValue: i.defaultValue,
      unit: i.unit,
    })),
  );
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof TemplateForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const scopeOptions = useMemo(
    () => [
      { value: '', labelKey: 'rms.btpl.scopeGeneric' },
      ...types.map((x) => ({ value: x.id, label: x.name, hint: x.code })),
    ],
    [types],
  );

  const fields = useMemo<Array<FormField<TemplateForm>>>(
    () => [
      { kind: 'text', key: 'code', labelKey: 'rms.btpl.code', required: true, mono: true },
      { kind: 'text', key: 'name', labelKey: 'rms.btpl.name', required: true },
      {
        kind: 'select',
        key: 'bodyFormat',
        labelKey: 'rms.recipe.bodyFormat',
        options: BODY_FORMATS.map((f) => ({ value: f, label: f })),
        allowClear: false,
      },
      { kind: 'select', key: 'scopeDeviceTypeId', labelKey: 'rms.btpl.scope', options: scopeOptions },
      { kind: 'switch', key: 'enforced', labelKey: 'rms.btpl.enforced', hintKey: 'rms.btpl.enforcedHint' },
      { kind: 'textarea', key: 'templateBody', labelKey: 'rms.btpl.skeleton', rows: 7, span: 2 },
    ],
    [scopeOptions],
  );

  function setItem(i: number, patch: Partial<TemplateUpsertItem>) {
    setItems((list) => list.map((x, idx) => (idx === i ? { ...x, ...patch } : x)));
  }

  async function save() {
    const next: Partial<Record<keyof TemplateForm, string>> = {};
    if (!form.code) next.code = t('rms.common.required');
    if (!form.name) next.name = t('rms.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      const body = {
        code: form.code,
        name: form.name,
        bodyFormat: form.bodyFormat,
        scopeDeviceTypeId: form.scopeDeviceTypeId || null,
        enforced: form.enforced,
        templateBody: form.templateBody || null,
        items: items.filter((i) => i.paramKey?.trim()),
      };
      if (initial) await api.updateBodyTemplate(initial.id, body);
      else await api.createBodyTemplate(body);
      onSaved();
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
      titleKey={initial ? 'rms.btpl.edit' : 'rms.btpl.create'}
      onOk={() => void save()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={720}
    >
      <FormGrid<TemplateForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />

      {/* 强制键清单是「不定行数的子表」，不是单个字段，故不进 FormGrid，仍手写这一块 */}
      <CimField label={t('rms.btpl.keys')} style={{ marginTop: 14 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 6 }}>
          {items.map((it, i) => (
            <div
              key={i}
              style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 96px 32px', gap: 8, alignItems: 'center' }}
            >
              <CimInput
                value={it.paramKey}
                mono
                placeholder="PARAM_KEY"
                onChange={(v) => setItem(i, { paramKey: v })}
              />
              <CimInput
                value={it.defaultValue ?? ''}
                placeholder={t('rms.btpl.defaultValue')}
                onChange={(v) => setItem(i, { defaultValue: v })}
              />
              <CimCheckbox
                checked={it.required !== false}
                onChange={(checked) => setItem(i, { required: checked })}
                label={t('rms.btpl.required')}
              />
              <CimButton
                size="small"
                onClick={() => setItems((l) => l.filter((_, idx) => idx !== i))}
              >
                ✕
              </CimButton>
            </div>
          ))}
        </div>
        <CimButton
          size="small"
          style={{ marginTop: 8 }}
          onClick={() => setItems((l) => [...l, { paramKey: '', required: true }])}
        >
          + {t('rms.btpl.addKey')}
        </CimButton>
      </CimField>

      {err ? (
        <p className="cim-field-error" style={{ marginTop: 10 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}
