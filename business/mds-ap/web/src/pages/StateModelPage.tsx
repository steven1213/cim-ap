import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimCheckTag,
  CimCheckbox,
  CimInfoTable,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useDict,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import { useHasPermission } from '@/lib/usePermission';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { StateModel, StateTransition } from '@/types';

/**
 * 状态模型（StateModelPage）——左侧模型列表，右侧三块：模型信息 / 状态定义 / 转换矩阵，
 * 三块均可自行维护（SEMI E30 GEM 状态模型方法论：状态模型 = 状态集合 + 转换集合）。
 *
 * <p>模型 modelCode/dictCode/objectType 建后不可改；状态集须为挂载词表子集，收缩时
 * 涉及它的转换规则要先清（后端守门）；矩阵勾选=建行、取消=删行，对角线自环不可点。
 * 设备/子单元服务改状态时按「旧态→新态」查这张表守门，非法迁移拒绝。</p>
 *
 * <p>渲染全部走 @cim/ui 公用组件（DataTable / Panel / CimInfoTable / CimModal /
 * FormGrid / CimCheckbox）；页面只保留布局网格与矩阵表格两段结构性样式。</p>
 */

const NO_MODELS: StateModel[] = [];
const NO_TRANSITIONS: StateTransition[] = [];

/** 子单元形态选项（与设备页 UNIT_TYPE_OPTIONS 同口径，E120/E164 判据）。 */
const UNIT_FORMS = [
  'LOAD_PORT',
  'PROCESS_CHAMBER',
  'ALIGNER',
  'ROBOT',
  'LOAD_LOCK',
  'BUFFER',
  'OTHER',
] as const;

/** 形态显示文案键（labelKey 字面量形态，check-i18n 认；模板字面量会判死）。 */
const UNIT_FORM_LABEL = {
  LOAD_PORT: { labelKey: 'mds.unit.type.LOAD_PORT' },
  PROCESS_CHAMBER: { labelKey: 'mds.unit.type.PROCESS_CHAMBER' },
  ALIGNER: { labelKey: 'mds.unit.type.ALIGNER' },
  ROBOT: { labelKey: 'mds.unit.type.ROBOT' },
  LOAD_LOCK: { labelKey: 'mds.unit.type.LOAD_LOCK' },
  BUFFER: { labelKey: 'mds.unit.type.BUFFER' },
  OTHER: { labelKey: 'mds.unit.type.OTHER' },
} as const;

const OBJECT_TYPES = ['EQUIPMENT', 'EQUIPMENT_UNIT'] as const;

/** 挂载词表选项（后端通用机制不限于此二项；页面下拉给最常用的两张）。 */
const DICT_OPTIONS = [
  { value: 'mds.equip_state', labelKey: 'mds.stateModel.dictEquip' },
  { value: 'mds.unit_state', labelKey: 'mds.stateModel.dictUnit' },
] as const;

export default function StateModelPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canEdit = useHasPermission(MDS_PERMS.stateModel.update);
  // 两张词表都常驻（hook 不能条件调用），按选中模型的 dictCode 取用
  const equipDict = useDict('mds.equip_state');
  const unitDict = useDict('mds.unit_state');

  const modelsRes = useResource(({ signal }) => api.listStateModels(signal));
  const models = modelsRes.data ?? NO_MODELS;
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<string | null>(null);

  const rows = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return models;
    return models.filter(
      (m) =>
        m.modelCode.toLowerCase().includes(kw) || m.modelName.toLowerCase().includes(kw),
    );
  }, [models, keyword]);

  const active = useMemo(
    () => models.find((m) => m.modelCode === selected) ?? models[0] ?? null,
    [models, selected],
  );

  const trRes = useResource(
    ({ signal }) => api.listStateTransitions(active?.modelCode ?? '', signal),
    { deps: [active?.modelCode ?? ''], enabled: !!active },
  );
  const transitions = trRes.data ?? NO_TRANSITIONS;

  const dict = active?.dictCode === 'mds.unit_state' ? unitDict : equipDict;
  const labelOf = (code: string) => dict.labelOf(code);

  const checked = useMemo(() => {
    const set = new Set<string>();
    for (const tr of transitions) set.add(`${tr.fromState}\u0001${tr.toState}`);
    return set;
  }, [transitions]);

  const [formState, setFormState] = useState<
    { mode: 'create' } | { mode: 'edit'; model: StateModel } | null
  >(null);
  const [addingState, setAddingState] = useState(false);

  const scopeLabel = (m: StateModel) => {
    if (m.objectType === 'EQUIPMENT') return t('mds.stateModel.scopeEquipment');
    return m.unitForm ? t(`mds.unit.type.${m.unitForm}`) : t('mds.stateModel.scopeUnitGeneric');
  };

  /** 状态集变更（状态定义表增删）：走 update（只吃 modelName/unitForm/stateCodes）。 */
  const changeStates = async (states: string[]) => {
    if (!active) return;
    try {
      await api.updateStateModel(active.modelCode, {
        modelName: active.modelName,
        unitForm: active.unitForm,
        stateCodes: states,
      });
      toast.success(t('mds.common.updated'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    modelsRes.refresh();
  };

  const doDelete = async (m: StateModel) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.stateModel.deleteConfirm', { code: m.modelCode }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteStateModel(m.modelCode);
      toast.success(t('mds.common.deleted'));
      setSelected(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    modelsRes.refresh();
  };

  const toggle = async (from: string, to: string, allowed: boolean) => {
    if (!active) return;
    try {
      await api.setStateTransition(active.modelCode, { fromState: from, toState: to, allowed });
      toast.success(t('mds.common.updated'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    trRes.refresh();
  };

  // ── 左侧：模型列表 ──
  const modelColumns = useMemo<Array<DataTableColumn<StateModel>>>(
    () => [
      { key: 'modelCode', titleKey: 'mds.stateModel.field.code', width: 170, mono: true },
      { key: 'modelName', titleKey: 'mds.stateModel.field.name', width: 180 },
      {
        key: 'scope',
        titleKey: 'mds.stateModel.field.objectType',
        width: 140,
        render: (m) => scopeLabel(m),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  // ── 状态定义表 ──
  const stateRows = useMemo(
    () => (active?.states ?? []).map((code) => ({ code, label: labelOf(code) })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [active, dict.items],
  );
  const stateColumns = useMemo<Array<DataTableColumn<{ code: string; label: string }>>>(
    () => [
      { key: 'code', titleKey: 'mds.stateModel.col.state', width: 220, mono: true },
      { key: 'label', titleKey: 'mds.stateModel.col.label' },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 100,
        render: (r) => (
          <Can perm={MDS_PERMS.stateModel.update}>
            <CimButton
              size="small"
              danger
              title={t('mds.stateModel.removeState')}
              onClick={() =>
                void changeStates((active?.states ?? []).filter((s) => s !== r.code))
              }
            >
              {t('mds.common.delete')}
            </CimButton>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, active, dict.items],
  );
  const stateCandidates = useMemo(
    () =>
      dict.items.filter(
        (i) => !i.disabled && !(active?.states ?? []).includes(i.value),
      ),
    [dict.items, active],
  );

  return (
    <Page>
      <div className="state-model-layout">
        <DataTable<StateModel>
          columns={modelColumns}
          rows={rows}
          rowKey={(m) => m.modelCode}
          rowClassName={(m) => (active?.modelCode === m.modelCode ? 'row-selected' : '')}
          onRowClick={(m) => setSelected(m.modelCode)}
          loading={modelsRes.loading}
          error={modelsRes.error?.message ?? null}
          onRetry={modelsRes.refresh}
          headActions={
            <Can perm={MDS_PERMS.stateModel.create}>
              <CimButton type="primary" size="small" onClick={() => setFormState({ mode: 'create' })}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          search={{
            value: keyword,
            onChange: setKeyword,
            placeholderKey: 'mds.stateModel.searchPlaceholder',
          }}
        />

        {active && (
          <div className="state-model-main">
            {/* 块一：模型信息（code/dict/objectType 建后不可改） */}
            <Panel
              titleKey="mds.stateModel.info"
              actions={
                <>
                  <Can perm={MDS_PERMS.stateModel.update}>
                    <CimButton
                      size="small"
                      onClick={() => setFormState({ mode: 'edit', model: active })}
                    >
                      {t('mds.common.edit')}
                    </CimButton>
                  </Can>
                  <Can perm={MDS_PERMS.stateModel.delete}>
                    <CimButton size="small" danger onClick={() => void doDelete(active)}>
                      {t('mds.common.delete')}
                    </CimButton>
                  </Can>
                </>
              }
            >
              <CimInfoTable
                record={active}
                columns={2}
                items={[
                  { key: 'modelCode', labelKey: 'mds.stateModel.field.code', dataIndex: 'modelCode', mono: true },
                  { key: 'modelName', labelKey: 'mds.stateModel.field.name', dataIndex: 'modelName' },
                  {
                    key: 'objectType',
                    labelKey: 'mds.stateModel.field.objectType',
                    render: (m) =>
                      m.objectType === 'EQUIPMENT'
                        ? t('mds.stateModel.objectType.EQUIPMENT')
                        : t('mds.stateModel.objectType.EQUIPMENT_UNIT'),
                  },
                  {
                    key: 'unitForm',
                    labelKey: 'mds.stateModel.field.unitForm',
                    render: (m) => (m.unitForm ? t(`mds.unit.type.${m.unitForm}`) : '—'),
                  },
                  { key: 'dictCode', labelKey: 'mds.stateModel.field.dict', dataIndex: 'dictCode', mono: true },
                ]}
              />
            </Panel>

            {/* 块二：状态定义（词表子集，表格增删即时保存） */}
            <Panel
              titleKey="mds.stateModel.legend"
              sub={t('mds.stateModel.statesMeta', { count: active.states.length })}
              actions={
                <Can perm={MDS_PERMS.stateModel.update}>
                  <CimButton
                    type="primary"
                    size="small"
                    disabled={stateCandidates.length === 0}
                    onClick={() => setAddingState(true)}
                  >
                    {t('mds.stateModel.addState')}
                  </CimButton>
                </Can>
              }
            >
              <DataTable<{ code: string; label: string }>
                columns={stateColumns}
                rows={stateRows}
                rowKey={(r) => r.code}
                loading={modelsRes.loading}
              />
            </Panel>

            {/* 块三：转换矩阵（勾选=建行、取消=删行） */}
            <Panel
              titleKey="mds.stateModel.matrixTitle"
              sub={t('mds.stateModel.matrixMeta', {
                count: transitions.length,
                total: Math.max(0, active.states.length * (active.states.length - 1)),
              })}
            >
              {active.states.length < 2 ? (
                <p className="dim">{t('mds.stateModel.statesTooFew')}</p>
              ) : (
                <div className="state-model-matrix-wrap">
                  <table className="state-matrix">
                    <thead>
                      <tr>
                        <th className="state-matrix-corner">{t('mds.stateModel.fromTo')}</th>
                        {active.states.map((to) => (
                          <th key={to} className="mono" title={labelOf(to)}>
                            {to}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {active.states.map((from) => (
                        <tr key={from}>
                          <th className="mono" title={labelOf(from)}>
                            {from}
                          </th>
                          {active.states.map((to) => {
                            if (from === to) {
                              return (
                                <td key={to} className="state-matrix-diag">
                                  —
                                </td>
                              );
                            }
                            const on = checked.has(`${from}\u0001${to}`);
                            return (
                              <td key={to}>
                                <CimCheckbox
                                  checked={on}
                                  disabled={!canEdit}
                                  title={
                                    on
                                      ? t('mds.stateModel.clickToDisable')
                                      : t('mds.stateModel.clickToEnable')
                                  }
                                  onChange={(next) => void toggle(from, to, next)}
                                />
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Panel>
          </div>
        )}
      </div>

      {formState && (
        <ModelFormModal
          initial={formState.mode === 'edit' ? formState.model : null}
          equipDict={equipDict}
          unitDict={unitDict}
          onClose={() => setFormState(null)}
          onDone={() => {
            const wasCreate = formState.mode === 'create';
            setFormState(null);
            toast.success(t(wasCreate ? 'mds.common.created' : 'mds.common.updated'));
            modelsRes.refresh();
          }}
        />
      )}
      {addingState && active && (
        <AddStateModal
          candidates={stateCandidates}
          onClose={() => setAddingState(false)}
          onDone={(code) => {
            setAddingState(false);
            void changeStates([...active.states, code]);
          }}
        />
      )}
    </Page>
  );
}

const MODEL_TITLE = {
  editKey: 'mds.stateModel.editTitle',
  createKey: 'mds.stateModel.createTitle',
} as const;

/** 添加状态弹窗：从挂载词表中挑一个尚未加入的状态（FormGrid 单 select）。 */
function AddStateModal({
  candidates,
  onClose,
  onDone,
}: {
  candidates: Array<{ value: string; label: string }>;
  onClose: () => void;
  onDone: (code: string) => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState<{ code: string }>({ code: '' });

  const fields = useMemo<Array<FormField<{ code: string }>>>(
    () => [
      {
        kind: 'select' as const,
        key: 'code' as const,
        labelKey: 'mds.stateModel.col.state',
        required: true,
        options: candidates,
      },
    ],
    [candidates],
  );

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.stateModel.addStateTitle"
      onOk={() => {
        if (!form.code) {
          setErr(t('mds.common.required'));
          return;
        }
        setBusy(true);
        onDone(form.code);
      }}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={420}
    >
      <FormGrid fields={fields} value={form} onChange={(patch) => setForm((p) => ({ ...p, ...patch }))} />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 模型新建/编辑弹窗：create 全字段（含初始状态集）；edit 只开放名称/形态。 */
function ModelFormModal({
  initial,
  equipDict,
  unitDict,
  onClose,
  onDone,
}: {
  initial: StateModel | null;
  equipDict: { items: Array<{ value: string; label: string; disabled?: boolean }> };
  unitDict: { items: Array<{ value: string; label: string; disabled?: boolean }> };
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.modelCode ?? '',
    name: initial?.modelName ?? '',
    objectType: (initial?.objectType ?? 'EQUIPMENT') as string,
    unitForm: initial?.unitForm ?? '',
    dictCode: initial?.dictCode ?? 'mds.equip_state',
  });
  // 初始状态集（仅 create；edit 在页面状态定义块维护）
  const [states, setStates] = useState<string[]>(initial?.states ?? []);
  const stateDict = form.dictCode === 'mds.unit_state' ? unitDict : equipDict;

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [
            {
              kind: 'text' as const,
              key: 'code' as const,
              labelKey: 'mds.stateModel.field.code',
              required: true,
              mono: true,
            },
          ]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.stateModel.field.name', required: true },
      {
        kind: 'select' as const,
        key: 'objectType' as const,
        labelKey: 'mds.stateModel.field.objectType',
        required: true,
        // 建后不可改：编辑时不给选项
        options: initial
          ? []
          : OBJECT_TYPES.map((o) => ({
              value: o,
              label: t(`mds.stateModel.objectType.${o}`),
            })),
      },
      ...(form.objectType === 'EQUIPMENT_UNIT'
        ? ([
            {
              kind: 'select' as const,
              key: 'unitForm' as const,
              labelKey: 'mds.stateModel.field.unitForm',
              allowClear: true,
              options: UNIT_FORMS.map((u) => ({
                value: u,
                label: t(UNIT_FORM_LABEL[u].labelKey),
              })),
            },
          ] as Array<FormField<typeof form>>)
        : []),
      ...(initial
        ? []
        : ([
            {
              kind: 'select' as const,
              key: 'dictCode' as const,
              labelKey: 'mds.stateModel.field.dict',
              required: true,
              options: DICT_OPTIONS.map((d) => ({ value: d.value, label: t(d.labelKey) })),
            },
          ] as Array<FormField<typeof form>>)),
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial, form.objectType],
  );

  async function submit() {
    if (!form.name.trim() || (!initial && !form.code.trim())) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateStateModel(initial.modelCode, {
          modelName: form.name.trim(),
          unitForm: form.unitForm || null,
          stateCodes: initial.states,
        });
      } else {
        await api.createStateModel({
          modelCode: form.code.trim(),
          modelName: form.name.trim(),
          objectType: form.objectType,
          unitForm: form.unitForm || null,
          dictCode: form.dictCode,
          stateCodes: states,
        });
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  const stateCandidates = stateDict.items.filter(
    (i) => !i.disabled && !states.includes(i.value),
  );

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? MODEL_TITLE.editKey : MODEL_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={560}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => {
          setForm((prev) => ({ ...prev, ...patch }));
          // 换词表后旧状态可能不属于新词表：create 场景直接清空重挑
          if (patch.dictCode && patch.dictCode !== form.dictCode) setStates([]);
        }}
        columns={2}
      />
      {!initial && (
        <>
          <p className="dim state-editor-label">
            {t('mds.stateModel.legend')}
            {states.length > 0 ? `（${t('mds.stateModel.clickChipToRemove')}）` : ''}
          </p>
          <div className="state-initial-states">
            {states.length === 0 ? (
              <span className="dim">{t('mds.stateModel.statesEmpty')}</span>
            ) : (
              states.map((s) => (
                <CimCheckTag
                  key={s}
                  checked
                  onChange={() => setStates((prev) => prev.filter((x) => x !== s))}
                >
                  <span className="mono">{s}</span>
                  {' · '}
                  {stateDict.items.find((i) => i.value === s)?.label ?? s}
                </CimCheckTag>
              ))
            )}
          </div>
          {stateCandidates.length > 0 && (
            <FormGrid
              fields={[
                {
                  kind: 'select' as const,
                  key: 'pick' as const,
                  labelKey: 'mds.stateModel.addStateTitle',
                  allowClear: true,
                  placeholderKey: 'mds.stateModel.addStatePlaceholder',
                  options: stateCandidates.map((i) => ({ value: i.value, label: i.label })),
                },
              ]}
              value={{ pick: '' }}
              onChange={(patch) => {
                const pick = (patch as { pick?: string }).pick;
                if (pick) setStates((prev) => (prev.includes(pick) ? prev : [...prev, pick]));
              }}
              columns={1}
            />
          )}
        </>
      )}
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
