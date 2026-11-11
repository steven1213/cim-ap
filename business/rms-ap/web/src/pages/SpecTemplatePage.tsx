import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimDrawer,
  CimInput,
  CimModal,
  CimSelect,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { SPEC_MANAGE } from '@/lib/permCodes';
import type { SpecTemplate, SpecTemplateItem, SpecType } from '@/types';

const NO_TEMPLATES: SpecTemplate[] = [];
const NO_ITEMS: SpecTemplateItem[] = [];

const SPEC_TYPES: SpecType[] = ['TARGET', 'RANGE', 'LIST_IN', 'TOLERANCE'];

interface ItemDraft {
  paramKey: string;
  paramName: string;
  specType: SpecType;
  dataType: string;
  targetValue: string;
  rangeMin: string;
  rangeMax: string;
  listValues: string;
  tolerancePct: string;
  toleranceAbs: string;
  unit: string;
}

const emptyItem = (): ItemDraft => ({
  paramKey: '',
  paramName: '',
  specType: 'TARGET',
  dataType: 'NUMBER',
  targetValue: '',
  rangeMin: '',
  rangeMax: '',
  listValues: '',
  tolerancePct: '',
  toleranceAbs: '',
  unit: '',
});

/** 规格模板页（FR-S2，Req 35）：模板化管理参数规格，可整体应用到配方。 */
export default function SpecTemplatePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<SpecTemplate | null>(null);
  const [viewId, setViewId] = useState<string | null>(null);

  const listRes = useResource(() => api.listSpecTemplates());
  const rows = listRes.data ?? NO_TEMPLATES;

  const viewRes = useResource(
    () => (viewId ? api.listSpecTemplateItems(viewId) : Promise.resolve(NO_ITEMS)),
    { deps: [viewId], enabled: !!viewId },
  );
  const viewTpl = rows.find((r) => r.id === viewId);

  const doDelete = async (x: SpecTemplate) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteSpecTemplate(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    // 删掉的正是抽屉里那条时关掉抽屉，否则抽屉会继续展示已不存在的模板
    if (viewId === x.id) setViewId(null);
    listRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<SpecTemplate>>>(
    () => [
      { key: 'code', titleKey: 'rms.specTpl.code', mono: true, width: 180 },
      { key: 'name', titleKey: 'rms.specTpl.name', width: 220 },
      {
        key: 'templateVer',
        titleKey: 'rms.specTpl.ver',
        width: 110,
        render: (r) => <span className="cim-mono">v{r.templateVer}</span>,
      },
      { key: 'itemCount', titleKey: 'rms.specTpl.itemCount', width: 100, align: 'right' },
      {
        key: 'scopeType',
        titleKey: 'rms.specTpl.scopeType',
        width: 160,
        render: (r) => r.scopeType ?? <span className="muted">{t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 220,
        render: (r) => (
          <span className="cell-actions">
            <button className="btn btn-ghost btn-sm" onClick={() => setViewId(r.id)}>
              {t('rms.specTpl.items')}
            </button>
            <Can perm={SPEC_MANAGE}>
              <button className="btn btn-ghost btn-sm" onClick={() => setEditing(r)}>
                {t('rms.common.edit')}
              </button>
              <button className="btn btn-ghost btn-sm danger" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </button>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, viewId],
  );

  const itemColumns = useMemo<Array<DataTableColumn<SpecTemplateItem>>>(
    () => [
      { key: 'paramKey', titleKey: 'rms.spec.paramKey', mono: true, width: 200 },
      {
        key: 'specType',
        titleKey: 'rms.spec.specType',
        width: 110,
        render: (r) => <CimTag labelKey={`rms.spec.type.${r.specType}`} />,
      },
      {
        key: 'value',
        titleKey: 'rms.spec.value',
        mono: true,
        render: (r) =>
          r.specType === 'RANGE'
            ? `[${r.rangeMin ?? '-'}, ${r.rangeMax ?? '-'}]`
            : r.specType === 'LIST_IN'
              ? `{${r.listValues ?? '-'}}`
              : (r.targetValue ?? '-'),
      },
      { key: 'unit', titleKey: 'rms.spec.unit', width: 90, render: (r) => r.unit ?? t('rms.common.dash') },
    ],
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.specTpl.title"
        subtitleKey="rms.specTpl.sub"
        actions={
          <Can perm={SPEC_MANAGE}>
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
              + {t('rms.specTpl.add')}
            </button>
          </Can>
        }
      />

      <DataTable<SpecTemplate>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={listRes.loading}
        error={listRes.error?.message}
        onRetry={listRes.refresh}
        onRefresh={listRes.refresh}
        settingsKey="rms.spec-template"
        emptyTitleKey="rms.specTpl.empty"
      />

      <CimDrawer
        open={!!viewId}
        onClose={() => setViewId(null)}
        title={viewTpl ? `${viewTpl.code} · ${t('rms.specTpl.items')}` : t('rms.specTpl.items')}
        width={720}
      >
        <DataTable<SpecTemplateItem>
          columns={itemColumns}
          rows={viewRes.data ?? NO_ITEMS}
          rowKey={(r) => r.id}
          loading={viewRes.loading}
          error={viewRes.error?.message}
          onRetry={viewRes.refresh}
          emptyTitleKey="rms.common.noData"
        />
      </CimDrawer>

      {(showCreate || editing) && (
        <TemplateModal
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={() => {
            const wasEdit = !!editing;
            const editedId = editing?.id;
            setShowCreate(false);
            setEditing(null);
            toast.success(t(wasEdit ? 'rms.common.updated' : 'rms.common.created'));
            listRes.refresh();
            // 抽屉里正看着这条模板时重拉条目：旧实现保存后查看面板仍是编辑前的条目
            if (editedId && editedId === viewId) viewRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

function TemplateModal({
  initial,
  onClose,
  onDone,
}: {
  initial: SpecTemplate | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    scopeType: initial?.scopeType ?? '',
    remark: initial?.remark ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<'code' | 'name', string>>>({});
  const [items, setItems] = useState<ItemDraft[]>(initial ? [] : [emptyItem()]);
  const [loadingItems, setLoadingItems] = useState(!!initial);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 编辑时把已有条目读进来；拉失败要说话，不能静默给个空表（旧实现是空 catch，
  // 表现成「模板明明有 12 个条目，打开编辑却是空的，一保存全没了」）
  useEffect(() => {
    if (!initial) return;
    let alive = true;
    setLoadingItems(true);
    api
      .listSpecTemplateItems(initial.id)
      .then((its) => {
        if (!alive) return;
        setItems(
          its.map((i) => ({
            paramKey: i.paramKey,
            paramName: i.paramName ?? '',
            specType: i.specType,
            dataType: i.dataType,
            targetValue: i.targetValue ?? '',
            rangeMin: i.rangeMin ?? '',
            rangeMax: i.rangeMax ?? '',
            listValues: i.listValues ?? '',
            tolerancePct: i.tolerancePct?.toString() ?? '',
            toleranceAbs: i.toleranceAbs?.toString() ?? '',
            unit: i.unit ?? '',
          })),
        );
      })
      .catch((e) => {
        if (alive) setErr(e instanceof Error ? e.message : String(e));
      })
      .finally(() => {
        if (alive) setLoadingItems(false);
      });
    return () => {
      alive = false;
    };
  }, [initial]);

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'text', key: 'code', labelKey: 'rms.specTpl.code', required: true, mono: true },
      { kind: 'text', key: 'name', labelKey: 'rms.specTpl.name', required: true },
      { kind: 'text', key: 'scopeType', labelKey: 'rms.specTpl.scopeType' },
      { kind: 'text', key: 'remark', labelKey: 'rms.common.remark' },
    ],
    [],
  );

  const drafts = items;

  function upd(i: number, patch: Partial<ItemDraft>) {
    setItems((prev) => prev.map((x, idx) => (idx === i ? { ...x, ...patch } : x)));
  }

  async function submit() {
    const next: Partial<Record<'code' | 'name', string>> = {};
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
        scopeType: form.scopeType || null,
        description: form.remark || null,
        items: drafts
          .filter((i) => i.paramKey)
          .map((i) => ({
            paramKey: i.paramKey,
            paramName: i.paramName || null,
            specType: i.specType,
            dataType: i.dataType,
            // 按类型取值：切过类型的行不再把上一个类型的值一起提交
            targetValue: i.specType === 'RANGE' || i.specType === 'LIST_IN' ? null : i.targetValue || null,
            rangeMin: i.specType === 'RANGE' ? i.rangeMin || null : null,
            rangeMax: i.specType === 'RANGE' ? i.rangeMax || null : null,
            listValues: i.specType === 'LIST_IN' ? i.listValues || null : null,
            tolerancePct: i.specType === 'TOLERANCE' && i.tolerancePct ? Number(i.tolerancePct) : null,
            toleranceAbs: i.specType === 'TOLERANCE' && i.toleranceAbs ? Number(i.toleranceAbs) : null,
            unit: i.unit || null,
            enabled: true,
          })),
      };
      if (initial) await api.updateSpecTemplate(initial.id, payload);
      else await api.createSpecTemplate(payload);
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
      titleKey={initial ? 'rms.specTpl.edit' : 'rms.specTpl.add'}
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={860}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />

      {/* 条目是「不定行数的子表」，不是单个字段：仍手写这一块，但控件统一用组件层的 */}
      <div className="cim-field" style={{ marginTop: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <label className="cim-field-label">{t('rms.specTpl.items')}</label>
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => setItems((prev) => [...prev, emptyItem()])}
          >
            + {t('rms.specTpl.addItem')}
          </button>
        </div>

        {loadingItems ? <p className="muted" style={{ marginTop: 6 }}>{t('rms.common.loading')}</p> : null}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 6 }}>
          {drafts.map((it, i) => (
            <div
              key={i}
              style={{ display: 'grid', gridTemplateColumns: '1.2fr 130px 1.6fr 90px 32px', gap: 8, alignItems: 'center' }}
            >
              <CimInput
                value={it.paramKey}
                mono
                placeholder="CHAMBER.TEMP"
                onChange={(v) => upd(i, { paramKey: v })}
              />
              <CimSelect
                value={it.specType}
                allowClear={false}
                options={SPEC_TYPES.map((s) => ({ value: s, labelKey: `rms.spec.type.${s}` }))}
                onChange={(v) => upd(i, { specType: (v as SpecType) ?? 'TARGET' })}
              />
              {it.specType === 'RANGE' ? (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                  <CimInput value={it.rangeMin} mono placeholder="min" onChange={(v) => upd(i, { rangeMin: v })} />
                  <CimInput value={it.rangeMax} mono placeholder="max" onChange={(v) => upd(i, { rangeMax: v })} />
                </div>
              ) : it.specType === 'LIST_IN' ? (
                <CimInput
                  value={it.listValues}
                  mono
                  placeholder="A,B,C"
                  onChange={(v) => upd(i, { listValues: v })}
                />
              ) : (
                <CimInput value={it.targetValue} mono onChange={(v) => upd(i, { targetValue: v })} />
              )}
              <CimInput value={it.unit} onChange={(v) => upd(i, { unit: v })} />
              <button
                className="btn btn-ghost btn-sm danger"
                onClick={() => setItems((prev) => prev.filter((_, idx) => idx !== i))}
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      </div>

      {err ? (
        <p className="cim-field-error" style={{ marginTop: 10 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}
