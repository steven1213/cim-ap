import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  DataTable,
  EmptyState,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import GuideBar from '@/components/GuideBar';

type ParamDefRow = api.ParamDef;

type Mode = 'view' | 'edit' | 'create';

interface FormState {
  attrKey: string;
  attrName: string;
  groupCode: string;
  valueType: string;
  enumDict: string;
  unit: string;
  required: boolean;
  scopeOperation: boolean;
  scopeFlow: boolean;
  scopeStep: boolean;
  sortNo: string;
  enabled: boolean;
}

function formFrom(row: ParamDefRow | null): FormState {
  const scopes = (row?.scopes ?? 'STEP').split(',');
  return {
    attrKey: row?.attrKey ?? '',
    attrName: row?.attrName ?? '',
    groupCode: row?.groupCode ?? 'DC',
    valueType: row?.valueType ?? 'STRING',
    enumDict: row?.enumDict ?? '',
    unit: row?.unit ?? '',
    required: Boolean(row?.required),
    scopeOperation: scopes.includes('OPERATION'),
    scopeFlow: scopes.includes('FLOW'),
    scopeStep: scopes.includes('STEP'),
    sortNo: row?.sortNo != null ? String(row.sortNo) : '100',
    enabled: row ? Boolean(row.enabled) : true,
  };
}

/**
 * 参数定义字典（ParamDefPage，V22 ICM）——长尾 KV 参数的一等公民定义层。
 * attr_key 建后不可改（值表引用锚点）；scopes 控可配层级；被引用禁删（2004）。
 */
export default function ParamDefPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const defsRes = useResource(({ signal }) => api.listParamDefs(signal));
  const defs = defsRes.data ?? [];

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>('view');
  const [form, setForm] = useState<FormState>(() => formFrom(null));
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const selected = useMemo(() => defs.find((r) => r.id === selectedId) ?? null, [defs, selectedId]);

  useEffect(() => {
    if (mode === 'view') setForm(formFrom(selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, selected]);

  const set = (patch: Partial<FormState>) => setForm((f) => ({ ...f, ...patch }));

  const enterCreate = () => {
    setForm(formFrom(null));
    setErr('');
    setMode('create');
  };
  const enterEdit = () => {
    setForm(formFrom(selected));
    setErr('');
    setMode('edit');
  };
  const backToView = () => {
    setErr('');
    setMode('view');
  };

  const save = async () => {
    setErr('');
    if (!form.attrName.trim()) {
      setErr(t('mds.paramDef.errNameRequired'));
      return;
    }
    if (mode === 'create' && !form.attrKey.trim()) {
      setErr(t('mds.paramDef.errKeyRequired'));
      return;
    }
    const scopes = [
      ...(form.scopeOperation ? ['OPERATION'] : []),
      ...(form.scopeFlow ? ['FLOW'] : []),
      ...(form.scopeStep ? ['STEP'] : []),
    ];
    if (!scopes.length) {
      setErr(t('mds.paramDef.errScopeRequired'));
      return;
    }
    setBusy(true);
    try {
      const payload: api.ParamDefPayload = {
        attrKey: form.attrKey.trim(),
        attrName: form.attrName.trim(),
        groupCode: form.groupCode.trim(),
        valueType: form.valueType,
        enumDict: form.valueType === 'ENUM' ? form.enumDict.trim() : null,
        unit: form.unit.trim() || null,
        required: form.required,
        scopes,
        sortNo: Number(form.sortNo) || 100,
        enabled: form.enabled,
      };
      const ret = mode === 'create'
        ? await api.createParamDef(payload)
        : await api.updateParamDef(selected!.id, payload);
      toast.success(t('mds.common.saved'));
      defsRes.refresh();
      setSelectedId(ret.id);
      backToView();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    if (!selected) return;
    const ok = await confirm({
      titleKey: 'mds.paramDef.deleteConfirm',
      contentKey: 'mds.paramDef.deleteConfirmText',
      okKey: 'mds.common.delete',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteParamDef(selected.id);
      toast.success(t('mds.common.deleted'));
      setSelectedId(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    defsRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<ParamDefRow>>>(
    () => [
      { key: 'attrKey', titleKey: 'mds.paramDef.field.attrKey', width: 170, mono: true },
      { key: 'attrName', titleKey: 'mds.paramDef.field.attrName', width: 160, ellipsis: true },
      { key: 'groupCode', titleKey: 'mds.paramDef.field.groupCode', width: 90 },
      { key: 'valueType', titleKey: 'mds.paramDef.field.valueType', width: 90 },
      {
        key: 'unit',
        titleKey: 'mds.edc.colUnit',
        width: 80,
        render: (r) => r.unit ?? <span className="dim">—</span>,
      },
      {
        key: 'required',
        titleKey: 'mds.paramDef.field.required',
        width: 70,
        render: (r) => (r.required ? <span className="op-ref-yes">{t('mds.paramDef.requiredYes')}</span> : <span className="dim">—</span>),
      },
      { key: 'scopes', titleKey: 'mds.paramDef.field.scopes', width: 170, mono: true, ellipsis: true },
      {
        key: 'enabled',
        titleKey: 'mds.paramDef.field.enabled',
        width: 70,
        render: (r) => (r.enabled ? t('mds.paramDef.enabledYes') : <span className="dim">{t('mds.paramDef.enabledNo')}</span>),
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  const fields = useMemo<Array<FormField<FormState>>>(() => [
    { kind: 'text', key: 'attrKey', labelKey: 'mds.paramDef.field.attrKey', mono: true, disabled: mode === 'edit', required: mode === 'create' },
    { kind: 'text', key: 'attrName', labelKey: 'mds.paramDef.field.attrName', required: true },
    { kind: 'text', key: 'groupCode', labelKey: 'mds.paramDef.field.groupCode' },
    {
      kind: 'select',
      key: 'valueType',
      labelKey: 'mds.paramDef.field.valueType',
      options: [
        { value: 'STRING', label: 'STRING' },
        { value: 'INT', label: 'INT' },
        { value: 'DECIMAL', label: 'DECIMAL' },
        { value: 'BOOLEAN', label: 'BOOLEAN' },
        { value: 'ENUM', label: 'ENUM' },
        { value: 'JSON', label: 'JSON' },
      ],
    },
    ...(form.valueType === 'ENUM' ? [{ kind: 'text' as const, key: 'enumDict' as const, labelKey: 'mds.paramDef.field.enumDict', mono: true }] : []),
    { kind: 'text', key: 'unit', labelKey: 'mds.edc.colUnit', mono: true, placeholderKey: 'mds.edc.colUnitHint' },
    { kind: 'switch', key: 'required', labelKey: 'mds.paramDef.field.required' },
    { kind: 'switch', key: 'scopeOperation', labelKey: 'mds.paramDef.scope.OPERATION' },
    { kind: 'switch', key: 'scopeFlow', labelKey: 'mds.paramDef.scope.FLOW' },
    { kind: 'switch', key: 'scopeStep', labelKey: 'mds.paramDef.scope.STEP' },
    { kind: 'number', key: 'sortNo', labelKey: 'mds.paramDef.field.sortNo' },
    { kind: 'switch', key: 'enabled', labelKey: 'mds.paramDef.field.enabled' },
  ], [mode, form.valueType]);

  const headerActions = mode === 'view' && selected ? (
    <>
      <Can perm={MDS_PERMS.paramDef.update}>
        <CimButton type="primary" size="small" onClick={enterEdit}>{t('mds.common.edit')}</CimButton>
      </Can>
      <Can perm={MDS_PERMS.paramDef.delete}>
        <CimButton size="small" danger onClick={() => void doDelete()}>{t('mds.common.delete')}</CimButton>
      </Can>
    </>
  ) : null;

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<ParamDefRow>
          titleKey="mds.paramDef.listTitle"
          columns={columns}
          rows={defs}
          rowKey={(r) => r.id}
          loading={defsRes.loading}
          error={defsRes.error?.message ?? null}
          onRetry={defsRes.refresh}
          headActions={
            <Can perm={MDS_PERMS.paramDef.create}>
              <CimButton type="primary" size="small" onClick={enterCreate}>{t('mds.common.create')}</CimButton>
            </Can>
          }
          onRefresh={defsRes.refresh}
          onRowClick={(r) => {
            if (r.id === selectedId && mode === 'view') return;
            setSelectedId(r.id);
            setMode('view');
          }}
          rowClassName={(r) => (r.id === selectedId ? 'row-selected' : '')}
          emptyTitleKey="mds.paramDef.empty"
        />
        <Panel className="op-detail">
          {!selected && mode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {mode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.paramDef.createTitle')}</h3>
                ) : (
                  <h3 className="op-detail-title">{selected!.attrKey}</h3>
                )}
                {mode === 'edit' && <span className="op-editing-badge">{t('mds.common.editing')}</span>}
                <div className="op-detail-actions">
                  {mode === 'view' && headerActions}
                  {(mode === 'edit' || mode === 'create') && (
                    <>
                      <CimButton type="primary" size="small" disabled={busy} onClick={() => void save()}>{t('mds.common.save')}</CimButton>
                      <CimButton size="small" onClick={backToView}>{t('mds.common.cancel')}</CimButton>
                    </>
                  )}
                </div>
              </div>
              {err && <p className="op-error">{err}</p>}
              <FormGrid
                fields={fields}
                value={form}
                disabled={mode === 'view'}
                onChange={(patch: Record<string, unknown>) => set(patch as Partial<FormState>)}
                columns={2}
              />
              {mode === 'view' && selected && (
                <p className="dim" style={{ marginTop: 8 }}>{t('mds.paramDef.keyHint')}</p>
              )}
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
