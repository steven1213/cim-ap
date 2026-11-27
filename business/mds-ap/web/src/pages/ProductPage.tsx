import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useResource } from '@cim/data';
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
import type { FlowView, Product, ProductNpwType, ProductUsage } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 产品（ProductPage，动线改版）——产品管理域第二个页面（SiView：Technology → Product → Route）。
 *
 * <p>布局为「左列表 + 右详情」主从结构（{@link useMasterDetail} 统一动线）：高频动线
 * （搜索 → 点选 → 就地编辑 → 保存）零弹窗；新建/批量删除在列表工具栏；编辑/删除贴详情头。
 * 编辑先抢锁（type=product）、保存同事务清锁、取消显式释放。行业通用口径（V26 定稿）：
 * usageType 5 值（量产/工程/实验/测试验证/NPW），NPW 必选二级细分；tech_code 逻辑引用
 * 制程技术（下拉选择，引用守门在后端）；productCode 建后不可改（MES 按 code 引用）。</p>
 */

interface ProductFormState {
  productCode: string;
  productName: string;
  techCode: string;
  usageType: ProductUsage;
  npwType: ProductNpwType | '';
  productGroup: string;
  substrateType: string;
  waferSize: string;
  dieSizeX: string;
  dieSizeY: string;
  owner: string;
  remark: string;
  enabled: boolean;
}

function formFrom(row: Product | null): ProductFormState {
  return {
    productCode: row?.productCode ?? '',
    productName: row?.productName ?? '',
    techCode: row?.techCode ?? '',
    usageType: row?.usageType ?? 'PRODUCTION',
    npwType: row?.npwType ?? '',
    productGroup: row?.productGroup ?? '',
    substrateType: row?.substrateType ?? '',
    waferSize: row?.waferSize ?? '',
    dieSizeX: row?.dieSizeX != null ? String(row.dieSizeX) : '',
    dieSizeY: row?.dieSizeY != null ? String(row.dieSizeY) : '',
    owner: row?.owner ?? '',
    remark: row?.remark ?? '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

export default function ProductPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const groupDict = useDict('mds.product_group');
  const substrateDict = useDict('mds.substrate_type');

  const [form, setForm] = useState<ProductFormState>(() => formFrom(null));
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<Product>({
    fetchList: (signal) => api.listProducts(undefined, signal),
    remove: (id) => api.deleteProduct(id),
    lockType: 'product',
    filter: (r, kw) =>
      r.productCode.toLowerCase().includes(kw) ||
      r.productName.toLowerCase().includes(kw) ||
      (r.productGroup ?? '').toLowerCase().includes(kw) ||
      (r.techCode ?? '').toLowerCase().includes(kw),
    isDirty: () => modeRef.current !== 'view' && JSON.stringify(form) !== initialJsonRef.current,
  });
  modeRef.current = md.mode;

  // 绑定状态（ACTIVE 流程列 + 「绑定流程」跳转）：全量绑定一次加载，产品码映射
  const navigate = useNavigate();
  const bindsRes = useResource(({ signal }) => api.listProductFlows(undefined, signal));
  const flowsRes = useResource(({ signal }) => api.listFlows(undefined, signal));
  const activeByProduct = useMemo(() => {
    const m = new Map<string, api.ProductFlowBinding>();
    (bindsRes.data ?? []).forEach((b) => {
      if (b.bindState === 'ACTIVE') m.set(b.productCode, b);
    });
    return m;
  }, [bindsRes.data]);
  // 族+版本 → 具体流程（取流程名，绑定信息直观展示）
  const flowByVersion = useMemo(() => {
    const m = new Map<string, FlowView>();
    (flowsRes.data ?? []).forEach((f) => {
      m.set(`${f.familyCode ?? f.flowCode}#${f.versionNo ?? 1}`, f);
    });
    return m;
  }, [flowsRes.data]);
  const selectedActiveBind = md.selected ? activeByProduct.get(md.selected.productCode) ?? null : null;
  const selectedActiveFlow = selectedActiveBind
    ? flowByVersion.get(`${selectedActiveBind.flowFamilyCode}#${selectedActiveBind.flowVersionNo}`) ?? null
    : null;

  // 详情表单下拉数据源（页面级加载一次）
  const [techs, setTechs] = useState<Awaited<ReturnType<typeof api.listTechnologies>>>([]);
  useEffect(() => {
    let alive = true;
    api.listTechnologies().then((rows) => {
      if (alive) setTechs(rows);
    }).catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 查看态表单跟随选中行（防止切行后详情残留上一行的表单值）
  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    initialJsonRef.current = EMPTY_JSON;
    md.enterCreate();
  };

  const openEdit = async (row: Product) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const save = async () => {
    md.setErr('');
    if (!form.productName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        productName: form.productName.trim(),
        techCode: form.techCode || null,
        usageType: form.usageType,
        npwType: form.usageType === 'NPW' ? form.npwType || null : null,
        productGroup: form.productGroup || null,
        substrateType: form.substrateType || null,
        waferSize: form.waferSize || null,
        dieSizeX: form.dieSizeX === '' ? null : Number(form.dieSizeX),
        dieSizeY: form.dieSizeY === '' ? null : Number(form.dieSizeY),
        owner: form.owner || null,
        remark: form.remark || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createProduct({ productCode: form.productCode.trim(), ...payload })
          : await api.updateProduct(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  // 左列表列（瘦身：全字段在右侧详情，操作列删除）
  const columns = useMemo<Array<DataTableColumn<Product>>>(
    () => [
      { key: 'productCode', titleKey: 'mds.product.code', width: 150, mono: true },
      { key: 'productName', titleKey: 'mds.product.name', width: 180, ellipsis: true, render: (r) => <span className="flow-name-main">{r.productName}</span> },
      {
        key: 'usageType',
        titleKey: 'mds.product.usageType',
        width: 120,
        render: (r) =>
          r.usageType === 'NPW' && r.npwType
            ? `${t(`mds.product.usage.${r.usageType}`)} · ${t(`mds.product.npw.${r.npwType}`)}`
            : t(`mds.product.usage.${r.usageType}`),
      },
      { key: 'productGroup', titleKey: 'mds.product.group', width: 110, render: (r) => groupDict.labelOf(r.productGroup) },
      { key: 'techCode', titleKey: 'mds.product.tech', width: 100, mono: true, render: (r) => r.techCode ?? '—' },
      {
        key: 'activeFlow',
        titleKey: 'mds.productFlow.col.activeFlow',
        width: 150,
        render: (r) => {
          const b = activeByProduct.get(r.productCode);
          return b ? (
            <span className="op-ref-yes">{b.flowFamilyCode} V{b.flowVersionNo}</span>
          ) : (
            <span className="dim">{t('mds.productFlow.notBound')}</span>
          );
        },
      },
      { key: 'substrateType', titleKey: 'mds.product.substrateType', width: 100, render: (r) => substrateDict.labelOf(r.substrateType) },
      { key: 'waferSize', titleKey: 'mds.product.waferSize', width: 90, render: (r) => (r.waferSize ? `${r.waferSize}"` : '—') },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 84,
        render: (r) => (
          <span className={r.enabled ? 'flow-status flow-status-active' : 'flow-status flow-status-disabled'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      { key: 'owner', titleKey: 'mds.product.owner', width: 90, render: (r) => r.owner ?? '—' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, groupDict, substrateDict, activeByProduct],
  );

  // 详情头动作（对象级，贴详情头）
  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.product.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.productFlow.view}>
        <CimButton
          size="small"
          onClick={() => navigate(`/product-bindings?product=${encodeURIComponent(md.selected!.productCode)}`)}
        >
          {t('mds.product.bindFlow')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.product.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const techOptions = useMemo(
    () => techs.map((x) => ({ value: x.techCode, label: `${x.techCode} · ${x.techName}` })),
    [techs],
  );

  const fields = useMemo<Array<FormField<ProductFormState>>>(
    () => [
      { kind: 'text', key: 'productCode', labelKey: 'mds.product.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'productName', labelKey: 'mds.product.name', required: true },
      {
        kind: 'select',
        key: 'usageType',
        labelKey: 'mds.product.usageType',
        options: (['PRODUCTION', 'ENGINEERING', 'EXPERIMENT', 'TEST', 'NPW'] as ProductUsage[]).map((v) => ({
          value: v,
          label: t(`mds.product.usage.${v}`),
        })),
      },
      ...(form.usageType === 'NPW'
        ? [
            {
              kind: 'select' as const,
              key: 'npwType' as const,
              labelKey: 'mds.product.npwType',
              required: true,
              options: (['MONITOR', 'DUMMY', 'SEASON', 'LITHO', 'GENERIC'] as ProductNpwType[]).map((v) => ({
                value: v,
                label: t(`mds.product.npw.${v}`),
              })),
            },
          ]
        : []),
      {
        kind: 'select',
        key: 'techCode',
        labelKey: 'mds.product.tech',
        options: techOptions,
        allowClear: true,
        placeholderKey: 'mds.product.techPlaceholder',
      },
      { kind: 'select', key: 'productGroup', labelKey: 'mds.product.group', dict: 'mds.product_group', allowClear: true },
      { kind: 'select', key: 'substrateType', labelKey: 'mds.product.substrateType', dict: 'mds.substrate_type', allowClear: true },
      { kind: 'select', key: 'waferSize', labelKey: 'mds.product.waferSize', dict: 'mds.wafer_size', allowClear: true },
      { kind: 'number', key: 'dieSizeX', labelKey: 'mds.product.dieSizeX', min: 0, step: 0.001 },
      { kind: 'number', key: 'dieSizeY', labelKey: 'mds.product.dieSizeY', min: 0, step: 0.001 },
      { kind: 'text', key: 'owner', labelKey: 'mds.product.owner' },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
      { kind: 'textarea', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, techOptions, t, form.usageType],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<Product>
          titleKey="mds.product.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.product.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.product.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.product.delete}>
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
                  <h3 className="op-detail-title">{t('mds.product.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.productCode}</h3>
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
                disabled={md.mode === 'view'}
                onChange={(patch: Record<string, unknown>) =>
                  setForm((f) => {
                    const next = { ...f, ...(patch as Partial<ProductFormState>) };
                    // 切离 NPW 时清空二级细分（服务端守门：非 NPW 必须为空）
                    if (patch.usageType && patch.usageType !== 'NPW') next.npwType = '';
                    return next;
                  })
                }
                columns={2}
              />

              {md.mode === 'view' && (
                <div className="pf-bind-section">
                  <h4 className="ov-section">{t('mds.product.flowSection')}</h4>
                  {selectedActiveBind ? (
                    <div className="pf-bind-card">
                      <span className="pf-bind-key">
                        {selectedActiveBind.flowFamilyCode} V{selectedActiveBind.flowVersionNo}
                      </span>
                      {selectedActiveFlow && <span className="dim">{selectedActiveFlow.flowName}</span>}
                      <span className="flow-status flow-status-active">
                        {t('mds.productFlow.state.ACTIVE')}
                      </span>
                      <span className="dim">
                        {selectedActiveBind.activeTime?.replace('T', ' ').slice(0, 16) ?? '—'}
                        {selectedActiveBind.activeUser ? ` · ${selectedActiveBind.activeUser}` : ''}
                      </span>
                    </div>
                  ) : (
                    <p className="dim">{t('mds.product.noActiveBind')}</p>
                  )}
                </div>
              )}
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
