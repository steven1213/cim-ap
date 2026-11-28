import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Can,
  CimButton,
  DataTable,
  EmptyState,
  FormGrid,
  Page,
  Panel,
  useDict,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail, type DetailMode } from '@/lib/useMasterDetail';
import type { InspectionItemPayload, InspectionStd } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 检验标准（InspectionPage）——「检什么/怎么检/判多严」的静态契约（V33）。
 *
 * <p>布局为「左列表 + 右详情」主从结构；详情 = 标准头表单 + 检验项明细行编辑。
 * 头表：适用产品分组/抽样方案/AQL 水准挂字典；明细：检验项（规格/方法/量具/缺陷
 * 等级），页内行增删改、保存一次提交（后端全量替换，同事务先删后插）。stdCode
 * 建后不可改；检验执行与判定归 MES/QMS 运行域。</p>
 */

interface StdFormState {
  stdCode: string;
  stdName: string;
  productGroup: string;
  samplingType: string;
  aqlLevel: string;
  sampleQty: string;
  enabled: boolean;
}

function formFrom(row: InspectionStd | null): StdFormState {
  return {
    stdCode: row?.stdCode ?? '',
    stdName: row?.stdName ?? '',
    productGroup: row?.productGroup ?? '',
    samplingType: row?.samplingType ?? '',
    aqlLevel: row?.aqlLevel ?? '',
    sampleQty: row?.sampleQty != null ? String(row.sampleQty) : '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

/** 行编辑视图：payload + 页内临时行键（不提交后端）。 */
type ItemRow = InspectionItemPayload & { uid: string };

const EMPTY_ITEM: InspectionItemPayload = {
  itemCode: '',
  itemName: '',
  spec: '',
  method: '',
  instrument: '',
  defectLevel: '',
  sortNo: 0,
};

const ITEM_INPUT_STYLE = { width: '100%' } as const;

export default function InspectionPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const groupDict = useDict('mds.product_group');
  const samplingDict = useDict('mds.sampling_type');
  const aqlDict = useDict('mds.aql_level');
  const defectDict = useDict('mds.defect_level');

  const [form, setForm] = useState<StdFormState>(() => formFrom(null));
  const [items, setItems] = useState<ItemRow[]>([]);
  const initialItemsRef = useRef('[]');
  const uidRef = useRef(0);
  const nextUid = () => `l${++uidRef.current}`;
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<InspectionStd>({
    fetchList: (signal) => api.listInspectionStds(undefined, signal),
    remove: (id) => api.deleteInspectionStd(id),
    lockType: 'inspectionStd',
    filter: (r, kw) =>
      r.stdCode.toLowerCase().includes(kw) || r.stdName.toLowerCase().includes(kw),
    isDirty: () =>
      modeRef.current !== 'view' &&
      (JSON.stringify(form) !== initialJsonRef.current ||
        JSON.stringify(items) !== initialItemsRef.current),
  });
  modeRef.current = md.mode;

  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  // 明细跟随选中行/模式：查看与编辑拉现行明细，新建给一条空行起步
  useEffect(() => {
    if ((md.mode === 'view' || md.mode === 'edit') && md.selected) {
      api
        .listInspectionItems(md.selected.id)
        .then((rows) => {
          const mapped = rows.map((r) => ({
            itemCode: r.itemCode,
            itemName: r.itemName,
            spec: r.spec ?? '',
            method: r.method ?? '',
            instrument: r.instrument ?? '',
            defectLevel: r.defectLevel ?? '',
            sortNo: r.sortNo,
            uid: nextUid(),
          }));
          setItems(mapped);
          if (md.mode === 'edit') initialItemsRef.current = JSON.stringify(mapped);
        })
        .catch(() => setItems([]));
    } else if (md.mode === 'create') {
      const seed: ItemRow[] = [{ ...EMPTY_ITEM, sortNo: 10, uid: nextUid() }];
      setItems(seed);
      initialItemsRef.current = JSON.stringify(seed);
    } else {
      setItems([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    initialJsonRef.current = EMPTY_JSON;
    md.enterCreate();
  };

  const openEdit = async (row: InspectionStd) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const patchItem = (uid: string, patch: Partial<InspectionItemPayload>) => {
    setItems((list) => list.map((it) => (it.uid === uid ? { ...it, ...patch } : it)));
  };

  const removeItem = (uid: string) => {
    setItems((list) => list.filter((it) => it.uid !== uid));
  };

  const save = async () => {
    md.setErr('');
    if (!form.stdName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    // 明细行必填校验：编码/名称
    const bad = items.findIndex((it) => !it.itemCode.trim() || !it.itemName.trim());
    if (bad >= 0) {
      md.setErr(t('mds.inspection.itemRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const headerPayload = {
        stdName: form.stdName.trim(),
        productGroup: form.productGroup || null,
        samplingType: form.samplingType || null,
        aqlLevel: form.aqlLevel || null,
        sampleQty: form.sampleQty === '' ? null : Number(form.sampleQty),
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createInspectionStd({ stdCode: form.stdCode.trim(), ...headerPayload })
          : await api.updateInspectionStd(md.selected!.id, headerPayload);
      await api.replaceInspectionItems(
        ret.id,
        items.map((it, i) => ({
          itemCode: it.itemCode.trim(),
          itemName: it.itemName.trim(),
          spec: it.spec,
          method: it.method,
          instrument: it.instrument,
          defectLevel: it.defectLevel,
          sortNo: (i + 1) * 10,
        })),
      );
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<InspectionStd>>>(
    () => [
      { key: 'stdCode', titleKey: 'mds.inspection.code', width: 150, mono: true },
      { key: 'stdName', titleKey: 'mds.inspection.name', width: 220, ellipsis: true, render: (r) => <span className="flow-name-main">{r.stdName}</span> },
      { key: 'productGroup', titleKey: 'mds.inspection.group', width: 110, render: (r) => r.productGroup ?? '—' },
      { key: 'samplingType', titleKey: 'mds.inspection.sampling', width: 110, render: (r) => r.samplingType ?? '—' },
      { key: 'aqlLevel', titleKey: 'mds.inspection.aql', width: 90, render: (r) => r.aqlLevel ?? '—' },
      { key: 'itemCount', titleKey: 'mds.inspection.itemCount', width: 90 },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? 'flow-status flow-status-active' : 'flow-status flow-status-disabled'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.inspection.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.inspection.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const fields = useMemo<Array<FormField<StdFormState>>>(
    () => [
      { kind: 'text', key: 'stdCode', labelKey: 'mds.inspection.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'stdName', labelKey: 'mds.inspection.name', required: true },
      {
        kind: 'select',
        key: 'productGroup',
        labelKey: 'mds.inspection.group',
        allowClear: true,
        options: groupDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select',
        key: 'samplingType',
        labelKey: 'mds.inspection.sampling',
        allowClear: true,
        options: samplingDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select',
        key: 'aqlLevel',
        labelKey: 'mds.inspection.aql',
        allowClear: true,
        options: aqlDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      { kind: 'number', key: 'sampleQty', labelKey: 'mds.inspection.sampleQty', min: 0 },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, groupDict.items, samplingDict.items, aqlDict.items],
  );

  const readOnly = md.mode === 'view';

  // 检验项列定义：行内控件编辑（input/select），只读态纯文本。
  // 放不下时由 DataTable 内置横向滚动（列宽合计 > 容器自动出滚动条）。
  const itemColumns = useMemo<Array<DataTableColumn<ItemRow>>>(
    () => [
      {
        key: 'itemCode',
        titleKey: 'mds.inspection.itemCode',
        width: 130,
        render: (r) => (
          <input style={ITEM_INPUT_STYLE} value={r.itemCode} readOnly={readOnly} onChange={(e) => patchItem(r.uid, { itemCode: e.target.value })} />
        ),
      },
      {
        key: 'itemName',
        titleKey: 'mds.inspection.itemName',
        width: 150,
        render: (r) => (
          <input style={ITEM_INPUT_STYLE} value={r.itemName} readOnly={readOnly} onChange={(e) => patchItem(r.uid, { itemName: e.target.value })} />
        ),
      },
      {
        key: 'spec',
        titleKey: 'mds.inspection.itemSpec',
        width: 220,
        render: (r) => (
          <input style={ITEM_INPUT_STYLE} value={r.spec ?? ''} readOnly={readOnly} onChange={(e) => patchItem(r.uid, { spec: e.target.value })} />
        ),
      },
      {
        key: 'method',
        titleKey: 'mds.inspection.itemMethod',
        width: 120,
        render: (r) => (
          <input style={ITEM_INPUT_STYLE} value={r.method ?? ''} readOnly={readOnly} onChange={(e) => patchItem(r.uid, { method: e.target.value })} />
        ),
      },
      {
        key: 'instrument',
        titleKey: 'mds.inspection.itemInstrument',
        width: 120,
        render: (r) => (
          <input style={ITEM_INPUT_STYLE} value={r.instrument ?? ''} readOnly={readOnly} onChange={(e) => patchItem(r.uid, { instrument: e.target.value })} />
        ),
      },
      {
        key: 'defectLevel',
        titleKey: 'mds.inspection.itemLevel',
        width: 110,
        render: (r) =>
          readOnly ? (
            r.defectLevel ?? '—'
          ) : (
            <select style={ITEM_INPUT_STYLE} value={r.defectLevel ?? ''} onChange={(e) => patchItem(r.uid, { defectLevel: e.target.value })}>
              <option value="">—</option>
              {defectDict.items.filter((d) => !d.disabled).map((d) => (
                <option key={d.value} value={d.value}>{d.label}</option>
              ))}
            </select>
          ),
      },
      {
        key: 'ops',
        titleKey: 'mds.common.actions',
        width: 76,
        render: (r) =>
          !readOnly && (
            <CimButton size="small" danger onClick={() => removeItem(r.uid)}>
              {t('mds.common.delete')}
            </CimButton>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [readOnly, defectDict.items, t],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<InspectionStd>
          titleKey="mds.inspection.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.inspection.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.inspection.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.inspection.delete}>
              <CimButton
                size="small"
                danger
                disabled={!md.checkedKeys.length}
                onClick={() => void md.doBatchDelete()}
              >
                {t('mds.common.batchDelete')}
                {md.checkedKeys.length ? ` (${md.checkedKeys.length})` : ''}
              </CimButton>
            </Can>
          }
          onRefresh={md.res.refresh}
          selectedKeys={md.checkedKeys}
          onSelectionChange={md.setCheckedKeys}
          onRowClick={(r) => void md.onRowClick(r)}
          rowClassName={(r) => (r.id === md.selectedId ? 'row-selected' : '')}
        />

        <Panel className="op-detail">
          {!md.selected && md.mode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {md.mode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.inspection.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.stdCode}</h3>
                    <span className={md.selected!.enabled ? 'flow-status flow-status-active' : 'flow-status flow-status-disabled'}>
                      {md.selected!.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
                    </span>
                  </>
                )}
                {md.mode === 'edit' && <span className="op-editing-badge">{t('mds.common.editing')}</span>}
                <div className="op-detail-actions">
                  {md.mode === 'view' && headerActions}
                  {(md.mode === 'edit' || md.mode === 'create') && (
                    <>
                      <CimButton type="primary" size="small" disabled={md.busy} onClick={() => void save()}>
                        {t('mds.common.save')}
                      </CimButton>
                      <CimButton size="small" onClick={md.backToView}>
                        {t('mds.common.cancel')}
                      </CimButton>
                    </>
                  )}
                </div>
              </div>

              {md.err && <p className="op-error">{md.err}</p>}

              <FormGrid
                fields={fields}
                value={form}
                disabled={readOnly}
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<StdFormState>) }))}
                columns={2}
              />

              <h4 className="op-detail-title" style={{ marginTop: 16 }}>
                {t('mds.inspection.itemsTitle')}
              </h4>
              <DataTable<ItemRow>
                columns={itemColumns}
                rows={items}
                rowKey={(r) => r.uid}
                size="small"
                flat
                actions={
                  !readOnly && (
                    <CimButton
                      size="small"
                      onClick={() =>
                        setItems((list) => [...list, { ...EMPTY_ITEM, sortNo: (list.length + 1) * 10, uid: nextUid() }])
                      }
                    >
                      {t('mds.inspection.addItem')}
                    </CimButton>
                  )
                }
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
