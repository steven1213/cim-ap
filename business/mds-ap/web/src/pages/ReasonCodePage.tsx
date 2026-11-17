import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
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
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { ReasonCodeTypeView, ReasonCodeView } from '@/types';

/**
 * 原因码（ReasonCodePage）——左侧语义域（Type）列表，右侧码表（树展开序）。
 *
 * <p>建模口径（V16）：树即码——层级不另立表，parent + node_path 物化路径长在码上；
 * reasonCode/typeKey 建后不可改；停用只影响新事务下拉，历史事件照常解析；有子节点
 * 或被 WIP 事件引用的码不可删。归属部门（字典 mds.owning_dept）只表达统计归属，
 * 不表达处置权限。</p>
 *
 * <p>渲染全部走 @cim/ui 公用组件；页面只保留左右两栏布局样式。</p>
 */

const NO_TYPES: ReasonCodeTypeView[] = [];
const NO_CODES: ReasonCodeView[] = [];

/** 弹窗标题键（属性名以 Key 结尾，供 check-i18n 扫描）。 */
const TYPE_TITLE = {
  editKey: 'mds.reason.type.editTitle',
  createKey: 'mds.reason.type.createTitle',
} as const;
const CODE_TITLE = {
  editKey: 'mds.reason.code.editTitle',
  createKey: 'mds.reason.code.createTitle',
} as const;

export default function ReasonCodePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const deptDict = useDict('mds.owning_dept');

  const typesRes = useResource(({ signal }) => api.listReasonCodeTypes(signal));
  const types = typesRes.data ?? NO_TYPES;
  const [typeKeyword, setTypeKeyword] = useState('');
  const [selectedType, setSelectedType] = useState<string | null>(null);

  const [codeKeyword, setCodeKeyword] = useState('');
  const codesRes = useResource(
    ({ signal }) => api.listReasonCodes({ typeKey: selectedType ?? undefined, keyword: undefined }, signal),
    { deps: [selectedType ?? ''], enabled: true },
  );
  const codes = codesRes.data ?? NO_CODES;

  const typeRows = useMemo(() => {
    const kw = typeKeyword.trim().toLowerCase();
    if (!kw) return types;
    return types.filter(
      (x) => x.typeKey.toLowerCase().includes(kw) || x.typeName.toLowerCase().includes(kw),
    );
  }, [types, typeKeyword]);

  const activeType = useMemo(
    () => types.find((x) => x.typeKey === selectedType) ?? null,
    [types, selectedType],
  );

  const codeRows = useMemo(() => {
    const kw = codeKeyword.trim().toLowerCase();
    if (!kw) return codes;
    return codes.filter(
      (c) =>
        c.reasonCode.toLowerCase().includes(kw) ||
        (c.shortName ?? '').toLowerCase().includes(kw) ||
        c.nodePath.toLowerCase().includes(kw),
    );
  }, [codes, codeKeyword]);

  // ── 类型弹窗 ──
  const [typeForm, setTypeForm] = useState<
    { mode: 'create' } | { mode: 'edit'; type: ReasonCodeTypeView } | null
  >(null);

  const doDeleteType = async (row: ReasonCodeTypeView) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.reason.type.deleteConfirm', { key: row.typeKey }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteReasonCodeType(row.id);
      toast.success(t('mds.common.deleted'));
      if (selectedType === row.typeKey) setSelectedType(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    typesRes.refresh();
  };

  // ── 码弹窗 ──
  const [codeForm, setCodeForm] = useState<
    { mode: 'create'; typeKey: string } | { mode: 'edit'; code: ReasonCodeView } | null
  >(null);

  const doDeleteCode = async (row: ReasonCodeView) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.reason.code.deleteConfirm', { code: row.reasonCode }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteReasonCode(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    codesRes.refresh();
    typesRes.refresh();
  };

  // ── 左侧类型表 ──
  const typeColumns = useMemo<Array<DataTableColumn<ReasonCodeTypeView>>>(
    () => [
      { key: 'typeKey', titleKey: 'mds.reason.type.key', width: 120, mono: true },
      { key: 'typeName', titleKey: 'mds.reason.type.name', width: 190 },
      { key: 'codeCount', titleKey: 'mds.reason.type.codeCount', width: 80 },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 120,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.reason.update}>
              <CimButton size="small" onClick={() => setTypeForm({ mode: 'edit', type: r })}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.reason.delete}>
              <CimButton size="small" danger onClick={() => void doDeleteType(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  // ── 右侧码表（node_path 升序 = 树展开序；层级缩进进名称列） ──
  const codeColumns = useMemo<Array<DataTableColumn<ReasonCodeView>>>(
    () => [
      { key: 'reasonCode', titleKey: 'mds.reason.code.code', width: 170, mono: true },
      {
        key: 'shortName',
        titleKey: 'mds.reason.code.name',
        render: (r) => (
          <span style={{ paddingLeft: (r.levelNo - 1) * 20 }}>
            {r.parentCode ? <span className="dim">├ </span> : null}
            {r.shortName ?? '—'}
          </span>
        ),
      },
      { key: 'parentCode', titleKey: 'mds.reason.code.parent', width: 150, mono: true, render: (r) => r.parentCode ?? '—' },
      { key: 'owningDept', titleKey: 'mds.reason.code.dept', width: 110, render: (r) => deptDict.labelOf(r.owningDept ?? '') || '—' },
      { key: 'sortNo', titleKey: 'mds.reason.code.sortNo', width: 80 },
      {
        key: 'enabled',
        titleKey: 'mds.reason.code.enabled',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? '' : 'dim'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.reason.update}>
              <CimButton size="small" onClick={() => setCodeForm({ mode: 'edit', code: r })}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.reason.delete}>
              <CimButton size="small" danger onClick={() => void doDeleteCode(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, deptDict.items],
  );

  return (
    <Page>
      <div className="reason-layout">
        <DataTable<ReasonCodeTypeView>
          columns={typeColumns}
          rows={typeRows}
          rowKey={(r) => r.typeKey}
          rowClassName={(r) => (activeType?.typeKey === r.typeKey ? 'row-selected' : '')}
          onRowClick={(r) => setSelectedType(r.typeKey === selectedType ? null : r.typeKey)}
          loading={typesRes.loading}
          error={typesRes.error?.message ?? null}
          onRetry={typesRes.refresh}
          headActions={
            <Can perm={MDS_PERMS.reason.create}>
              <CimButton type="primary" size="small" onClick={() => setTypeForm({ mode: 'create' })}>
                {t('mds.reason.type.createNew')}
              </CimButton>
            </Can>
          }
          search={{
            value: typeKeyword,
            onChange: setTypeKeyword,
            placeholderKey: 'mds.reason.type.searchPlaceholder',
          }}
        />

        <div className="reason-main">
          <Panel
            titleKey="mds.reason.code.panelTitle"
            sub={activeType
              ? t('mds.reason.code.panelSubTyped', { name: activeType.typeName })
              : t('mds.reason.code.panelSubAll')}
            actions={
              <Can perm={MDS_PERMS.reason.create}>
                <CimButton
                  type="primary"
                  size="small"
                  onClick={() => setCodeForm({ mode: 'create', typeKey: activeType?.typeKey ?? '' })}
                >
                  {t('mds.reason.code.createNew')}
                </CimButton>
              </Can>
            }
          >
            <DataTable<ReasonCodeView>
              columns={codeColumns}
              rows={codeRows}
              rowKey={(r) => r.id}
              loading={codesRes.loading}
              error={codesRes.error?.message ?? null}
              onRetry={codesRes.refresh}
              search={{
                value: codeKeyword,
                onChange: setCodeKeyword,
                placeholderKey: 'mds.reason.code.searchPlaceholder',
              }}
            />
          </Panel>
        </div>
      </div>

      {typeForm && (
        <TypeFormModal
          state={typeForm}
          onClose={() => setTypeForm(null)}
          onDone={() => {
            setTypeForm(null);
            toast.success(t('mds.common.updated'));
            typesRes.refresh();
          }}
        />
      )}
      {codeForm && (
        <CodeFormModal
          state={codeForm}
          types={types}
          codes={codes}
          onClose={() => setCodeForm(null)}
          onDone={() => {
            setCodeForm(null);
            toast.success(t('mds.common.updated'));
            codesRes.refresh();
            typesRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 类型新建/编辑弹窗（typeKey 建后不可改——编辑态不渲染该字段）。 */
function TypeFormModal({
  state,
  onClose,
  onDone,
}: {
  state: { mode: 'create' } | { mode: 'edit'; type: ReasonCodeTypeView };
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const initial = state.mode === 'edit' ? state.type : null;
  const [form, setForm] = useState({
    typeKey: initial?.typeKey ?? '',
    typeName: initial?.typeName ?? '',
    sortNo: initial?.sortNo ?? 100,
    description: initial?.description ?? '',
  });

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'typeKey' as const, labelKey: 'mds.reason.type.key', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'typeName' as const, labelKey: 'mds.reason.type.name', required: true },
      { kind: 'number' as const, key: 'sortNo' as const, labelKey: 'mds.reason.code.sortNo', precision: 0 },
      { kind: 'text' as const, key: 'description' as const, labelKey: 'mds.common.remark', span: 2 },
    ],
    [initial],
  );

  async function submit() {
    if (!form.typeName.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateReasonCodeType(initial.id, {
          typeName: form.typeName.trim(),
          sortNo: form.sortNo,
          description: form.description || null,
        });
      } else {
        await api.createReasonCodeType({
          typeKey: form.typeKey.trim(),
          typeName: form.typeName.trim(),
          sortNo: form.sortNo,
          description: form.description || null,
        });
      }
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
      titleKey={initial ? TYPE_TITLE.editKey : TYPE_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 原因码新建/编辑弹窗（reasonCode/typeKey 建后不可改；父节点限同语义域）。 */
function CodeFormModal({
  state,
  types,
  codes,
  onClose,
  onDone,
}: {
  state: { mode: 'create'; typeKey: string } | { mode: 'edit'; code: ReasonCodeView };
  types: ReasonCodeTypeView[];
  codes: ReasonCodeView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const initial = state.mode === 'edit' ? state.code : null;
  const [form, setForm] = useState({
    reasonCode: initial?.reasonCode ?? '',
    typeKey: initial?.typeKey ?? (state.mode === 'create' ? state.typeKey : ''),
    shortName: initial?.shortName ?? '',
    owningDept: initial?.owningDept ?? '',
    parentId: initial?.parentId ?? '',
    sortNo: initial?.sortNo ?? 100,
    description: initial?.description ?? '',
    enabled: initial?.enabled ?? true,
  });

  const typeOptions = useMemo(
    () => types.map((x) => ({ value: x.typeKey, label: `${x.typeKey} · ${x.typeName}` })),
    [types],
  );
  /** 父节点候选：与当前语义域一致的码（编辑态排除自身）。 */
  const parentOptions = useMemo(
    () =>
      codes
        .filter((c) => c.typeKey === form.typeKey && c.id !== initial?.id)
        .map((c) => ({
          value: c.id,
          label: `${c.reasonCode} · ${c.shortName ?? ''}（L${c.levelNo}）`,
        })),
    [codes, form.typeKey, initial],
  );

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'reasonCode' as const, labelKey: 'mds.reason.code.code', required: true, mono: true }]
        : []),
      { kind: 'select' as const, key: 'typeKey' as const, labelKey: 'mds.reason.type.key', options: typeOptions, disabled: !!initial },
      { kind: 'text' as const, key: 'shortName' as const, labelKey: 'mds.reason.code.name' },
      { kind: 'select' as const, key: 'parentId' as const, labelKey: 'mds.reason.code.parent', options: parentOptions, allowClear: true, placeholderKey: 'mds.reason.code.parentPlaceholder' },
      { kind: 'select' as const, key: 'owningDept' as const, labelKey: 'mds.reason.code.dept', dict: 'mds.owning_dept', allowClear: true },
      { kind: 'number' as const, key: 'sortNo' as const, labelKey: 'mds.reason.code.sortNo', precision: 0 },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.reason.code.enabled' },
      { kind: 'text' as const, key: 'description' as const, labelKey: 'mds.common.remark', span: 2 },
    ],
    [initial, typeOptions, parentOptions],
  );

  async function submit() {
    if (!form.typeKey) {
      setErr(t('mds.reason.type.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateReasonCode(initial.id, {
          shortName: form.shortName || null,
          owningDept: form.owningDept || null,
          parentId: form.parentId || null,
          sortNo: form.sortNo,
          description: form.description || null,
          enabled: form.enabled,
        });
      } else {
        await api.createReasonCode({
          reasonCode: form.reasonCode.trim(),
          typeKey: form.typeKey,
          shortName: form.shortName || null,
          owningDept: form.owningDept || null,
          parentId: form.parentId || null,
          sortNo: form.sortNo,
          description: form.description || null,
          enabled: form.enabled,
        });
      }
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
      titleKey={initial ? CODE_TITLE.editKey : CODE_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={560}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
