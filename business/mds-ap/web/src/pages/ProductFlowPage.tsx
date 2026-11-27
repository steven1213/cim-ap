import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  DataTable,
  EmptyState,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { FlowView, Product } from '@/types';
import GuideBar from '@/components/GuideBar';

type Binding = api.ProductFlowBinding;

/**
 * 产品↔流程绑定（ProductFlowPage，V22 ICM；2026-11-25 改产品为主视角主从页）——
 * 左侧产品清单（含「ACTIVE 流程」状态列），右侧选中产品的绑定关系与绑定操作；
 * 产品码点选不手输（消灭敲错 404）；支持 ?product=CODE 预选（产品页「绑定流程」跳转带参）。
 * 同 (product, family) 唯一 ACTIVE，换版=旧 ACTIVE 降 HISTORY 留痕；解绑=HISTORY。
 * 绑定版本须 ACTIVE（或 FROZEN+VALID 预绑定）。
 */
export default function ProductFlowPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [searchParams] = useSearchParams();

  // 左：产品清单；绑定/流程全量加载（列表规模小，前端过滤与联动）
  const productsRes = useResource(({ signal }) => api.listProducts(undefined, signal));
  const products = productsRes.data ?? [];
  const bindsRes = useResource(({ signal }) => api.listProductFlows(undefined, signal));
  const binds = bindsRes.data ?? [];
  const flowsRes = useResource(({ signal }) => api.listFlows(undefined, signal));
  const flows = flowsRes.data ?? [];

  // 选中产品（?product=CODE 预选，产品页跳转带参）
  const [selectedCode, setSelectedCode] = useState('');
  useEffect(() => {
    const q = searchParams.get('product');
    if (q) setSelectedCode(q);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 左列搜索（本地过滤）
  const [kw, setKw] = useState('');
  const filteredProducts = useMemo(() => {
    const k = kw.trim().toLowerCase();
    if (!k) return products;
    return products.filter(
      (p) =>
        p.productCode.toLowerCase().includes(k) ||
        p.productName.toLowerCase().includes(k) ||
        (p.techCode ?? '').toLowerCase().includes(k),
    );
  }, [products, kw]);

  // 产品 → ACTIVE 绑定（左列状态 + 右侧清单过滤）
  const activeByProduct = useMemo(() => {
    const m = new Map<string, Binding>();
    binds.forEach((b) => {
      if (b.bindState === 'ACTIVE') m.set(b.productCode, b);
    });
    return m;
  }, [binds]);
  const productBinds = useMemo(
    () => binds.filter((b) => b.productCode === selectedCode),
    [binds, selectedCode],
  );
  const selected = products.find((p) => p.productCode === selectedCode) ?? null;

  // 绑定操作（版本族 → 版本联动；productCode 来自左侧选中产品）
  const [family, setFamily] = useState('');
  const [version, setVersion] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    setVersion('');
  }, [family]);

  // 版本族去重（family_code 唯一集合）
  const families = useMemo(() => {
    const set = new Set<string>();
    flows.forEach((f) => set.add(f.familyCode ?? f.flowCode));
    return Array.from(set).sort();
  }, [flows]);

  const familyVersions = useMemo(() => {
    const fam = family || null;
    return flows
      .filter((f) => !fam || (f.familyCode ?? f.flowCode) === fam)
      .sort((a, b) => (b.versionNo ?? 1) - (a.versionNo ?? 1));
  }, [flows, family]);

  // 族+版本 → 具体流程（绑定行显示流程名，直观可见绑的是哪条流程哪个版本）
  const flowByVersion = useMemo(() => {
    const m = new Map<string, FlowView>();
    flows.forEach((f) => {
      m.set(`${f.familyCode ?? f.flowCode}#${f.versionNo ?? 1}`, f);
    });
    return m;
  }, [flows]);

  const doBind = async () => {
    setErr('');
    if (!selectedCode) {
      setErr(t('mds.productFlow.errSelectProduct'));
      return;
    }
    if (!family || !version) {
      setErr(t('mds.productFlow.errParamRequired'));
      return;
    }
    setBusy(true);
    try {
      await api.bindProductFlow(selectedCode, family, Number(version));
      toast.success(t('mds.common.saved'));
      bindsRes.refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const doUnbind = async (row: Binding) => {
    const ok = await confirm({
      titleKey: 'mds.productFlow.unbindConfirm',
      contentKey: 'mds.productFlow.unbindConfirmText',
      okKey: 'mds.common.ok',
    });
    if (!ok) return;
    try {
      await api.unbindProductFlow(row.id);
      toast.success(t('mds.common.saved'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    bindsRes.refresh();
  };

  // V25 L3：产品级覆盖编辑弹窗（列型覆盖 + PRODUCT 指派 + KV）
  const [overrideRow, setOverrideRow] = useState<Binding | null>(null);

  // 左列（产品清单 + ACTIVE 流程状态）
  const productColumns = useMemo<Array<DataTableColumn<Product>>>(
    () => [
      { key: 'productCode', titleKey: 'mds.product.code', width: 140, mono: true },
      {
        key: 'productName',
        titleKey: 'mds.product.name',
        ellipsis: true,
        render: (r) => <span className="flow-name-main">{r.productName}</span>,
      },
      {
        key: 'activeFlow',
        titleKey: 'mds.productFlow.col.activeFlow',
        width: 160,
        render: (r) => {
          const b = activeByProduct.get(r.productCode);
          return b ? (
            <span className="op-ref-yes">
              {b.flowFamilyCode} V{b.flowVersionNo}
            </span>
          ) : (
            <span className="dim">{t('mds.productFlow.notBound')}</span>
          );
        },
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ],
    [t, activeByProduct],
  );

  // 右列（选中产品的绑定清单；产品编码列省略——面板标题即产品）
  const bindColumns = useMemo<Array<DataTableColumn<Binding>>>(
    () => [
      {
        key: 'flowFamilyCode',
        titleKey: 'mds.productFlow.field.flowFamilyCode',
        width: 200,
        render: (r) => {
          const f = flowByVersion.get(`${r.flowFamilyCode}#${r.flowVersionNo}`);
          return (
            <span>
              <span className="pf-bind-key">{r.flowFamilyCode} V{r.flowVersionNo}</span>
              {f && <span className="pf-bind-name dim">{f.flowName}</span>}
            </span>
          );
        },
      },
      {
        key: 'flowVersionNo',
        titleKey: 'mds.productFlow.field.flowVersionNo',
        width: 80,
        render: (r) => `V${r.flowVersionNo}`,
      },
      {
        key: 'bindState',
        titleKey: 'mds.productFlow.field.bindState',
        width: 90,
        render: (r) => (
          <span className={r.bindState === 'ACTIVE' ? 'op-ref-yes' : 'dim'}>
            {t(`mds.productFlow.state.${r.bindState}`)}
          </span>
        ),
      },
      {
        key: 'activeTime',
        titleKey: 'mds.productFlow.field.activeTime',
        width: 150,
        render: (r) => (r.activeTime ? r.activeTime.replace('T', ' ').slice(0, 16) : '—'),
      },
      { key: 'activeUser', titleKey: 'mds.productFlow.field.activeUser', width: 100 },
      {
        key: 'ops',
        titleKey: 'mds.common.actions',
        width: 150,
        render: (r) =>
          r.bindState === 'ACTIVE' ? (
            <span className="cell-actions">
              <Can perm={MDS_PERMS.productFlow.update}>
                <CimButton size="small" onClick={() => setOverrideRow(r)}>
                  {t('mds.productFlow.overrideBtn')}
                </CimButton>
              </Can>
              <Can perm={MDS_PERMS.productFlow.delete}>
                <CimButton size="small" danger onClick={() => void doUnbind(r)}>
                  {t('mds.productFlow.unbind')}
                </CimButton>
              </Can>
            </span>
          ) : (
            <span className="dim">—</span>
          ),
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ],
    [t, flowByVersion],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<Product>
          titleKey="mds.productFlow.productList"
          columns={productColumns}
          rows={filteredProducts}
          rowKey={(r) => r.id}
          loading={productsRes.loading}
          error={productsRes.error?.message ?? null}
          onRetry={productsRes.refresh}
          search={{ value: kw, onChange: setKw, placeholderKey: 'mds.productFlow.searchPlaceholder' }}
          onRefresh={productsRes.refresh}
          onRowClick={(r) => setSelectedCode(r.productCode)}
          rowClassName={(r) => (r.productCode === selectedCode ? 'row-selected' : '')}
        />

        <Panel className="op-detail pf-detail">
          {!selected ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body pf-detail-body">
              <div className="op-detail-head">
                <h3 className="op-detail-title">{selected.productCode}</h3>
                <span className="dim">{selected.productName}</span>
                {activeByProduct.has(selectedCode) ? (
                  <span className="flow-status flow-status-active">
                    {t('mds.productFlow.state.ACTIVE')}
                  </span>
                ) : (
                  <span className="dim">{t('mds.productFlow.notBound')}</span>
                )}
              </div>

              <h4 className="ov-section">{t('mds.productFlow.bindTitle')}</h4>
              {err && <p className="op-error">{err}</p>}
              <div className="op-copy-bar" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <select
                  className="op-search"
                  style={{ width: 200 }}
                  value={family}
                  onChange={(e) => setFamily(e.target.value)}
                >
                  <option value="">{t('mds.productFlow.selectFamily')}</option>
                  {families.map((f) => (
                    <option key={f} value={f}>{f}</option>
                  ))}
                </select>
                <select
                  className="op-search"
                  style={{ width: 120 }}
                  value={version}
                  onChange={(e) => setVersion(e.target.value)}
                  disabled={!family}
                >
                  <option value="">{t('mds.productFlow.selectVersion')}</option>
                  {familyVersions.map((f) => (
                    <option key={f.id} value={f.versionNo ?? 1}>
                      V{f.versionNo ?? 1}（{f.status}
                      {f.validateState ? ` · ${f.validateState}` : ''}）
                    </option>
                  ))}
                </select>
                <Can perm={MDS_PERMS.productFlow.create}>
                  <CimButton type="primary" size="small" disabled={busy} onClick={() => void doBind()}>
                    {t('mds.productFlow.bind')}
                  </CimButton>
                </Can>
              </div>
              <p className="dim">{t('mds.productFlow.bindHint')}</p>

              <div className="pf-bind-wrap">
                <DataTable<Binding>
                  titleKey="mds.productFlow.listTitle"
                  columns={bindColumns}
                  rows={productBinds}
                  rowKey={(r) => r.id}
                  loading={bindsRes.loading}
                  error={bindsRes.error?.message ?? null}
                  onRetry={bindsRes.refresh}
                  onRefresh={bindsRes.refresh}
                  emptyTitleKey="mds.productFlow.empty"
                />
              </div>
            </div>
          )}
        </Panel>
      </div>
      {overrideRow && (
        <OverrideModal
          binding={overrideRow}
          onClose={() => setOverrideRow(null)}
          onSaved={() => bindsRes.refresh()}
        />
      )}
    </Page>
  );
}

/**
 * 产品级覆盖配置弹窗（V25 L3）：三段一体——
 * ① 列型覆盖（光罩/污染级/禁入/NPW 落绑定行，空=继承流程/工序层）；
 * ② PRODUCT 层指派（product+family 定位，nodeId 可空=整条流程生效）；
 * ③ PRODUCT 层 KV 快照。
 * 仅按产品解析端点生效（GET /flows/{id}/effective?product=CODE），validate 主数据闸不带产品轴。
 */
function OverrideModal({
  binding,
  onClose,
  onSaved,
}: {
  binding: Binding;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // ① 列型覆盖
  const [reticleRef, setReticleRef] = useState(binding.reticleRef ?? '');
  const [contamLevel, setContamLevel] = useState(binding.contamLevel ?? '');
  const [contamForbidden, setContamForbidden] = useState(binding.contamForbidden ?? '');
  const [npwLimit, setNpwLimit] = useState(binding.npwLimit == null ? '' : String(binding.npwLimit));

  // ② PRODUCT 指派
  const assignsRes = useResource(({ signal }) => api.listProductAssigns(binding.id, signal));
  const [nodeId, setNodeId] = useState('');
  const [assignType, setAssignType] = useState('GROUP');
  const [assignRef, setAssignRef] = useState('');

  // ③ PRODUCT KV
  const attrsRes = useResource(({ signal }) => api.listProductAttrs(binding.id, signal));
  const [kvRows, setKvRows] = useState<Array<{ key: string; value: string }>>([]);
  const [kvLoaded, setKvLoaded] = useState(false);
  useEffect(() => {
    if (!kvLoaded && attrsRes.data) {
      setKvRows(Object.entries(attrsRes.data).map(([key, value]) => ({ key, value })));
      setKvLoaded(true);
    }
  }, [attrsRes.data, kvLoaded]);

  const saveOverrides = async () => {
    setErr('');
    if (contamForbidden && contamLevel) {
      for (const f of contamForbidden.split(/[,，]/)) {
        if (f.trim() && f.trim() === contamLevel.trim()) {
          setErr(t('mds.productFlow.errContamSelf'));
          return;
        }
      }
    }
    setBusy(true);
    try {
      await api.updateProductBindingOverrides(binding.id, {
        reticleRef: reticleRef.trim() || undefined,
        contamLevel: contamLevel.trim() || undefined,
        contamForbidden: contamForbidden.trim() || undefined,
        npwLimit: npwLimit === '' ? undefined : Number(npwLimit),
      });
      toast.success(t('mds.common.saved'));
      onSaved();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const addAssign = async () => {
    setErr('');
    if (!assignRef.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    try {
      await api.createProductAssign(binding.id, nodeId.trim(), {
        assignType,
        assignRef: assignRef.trim(),
        recipeRule: 'GENERATE',
        enabled: true,
      });
      setAssignRef('');
      assignsRes.refresh();
      toast.success(t('mds.common.saved'));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const delAssign = async (id: string) => {
    try {
      await api.deleteAssign(id);
      assignsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const saveKv = async () => {
    setErr('');
    setBusy(true);
    try {
      const values: Record<string, string> = {};
      kvRows.forEach((r) => {
        if (r.key.trim()) values[r.key.trim()] = r.value;
      });
      await api.putProductAttrs(binding.id, values);
      setKvRows(Object.entries(values).map(([key, value]) => ({ key, value })));
      toast.success(t('mds.common.saved'));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <CimModal open onClose={onClose} titleKey="mds.productFlow.overrideTitle" width={720}>
      <p className="dim">{t('mds.productFlow.overrideHint')}</p>
      {err && <p className="op-error">{err}</p>}

      <h4 className="ov-section">{t('mds.productFlow.colTitle')}</h4>
      <div className="op-copy-bar" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <input className="op-search" style={{ width: 150 }} placeholder={t('mds.flow.field.reticleRef')}
          value={reticleRef} onChange={(e) => setReticleRef(e.target.value)} />
        <input className="op-search" style={{ width: 110 }} placeholder={t('mds.flow.field.contamLevel')}
          value={contamLevel} onChange={(e) => setContamLevel(e.target.value)} />
        <input className="op-search" style={{ width: 130 }} placeholder={t('mds.flow.field.contamForbidden')}
          value={contamForbidden} onChange={(e) => setContamForbidden(e.target.value)} />
        <input className="op-search" style={{ width: 110 }} placeholder={t('mds.flow.field.npwLimit')} type="number" min={1}
          value={npwLimit} onChange={(e) => setNpwLimit(e.target.value)} />
        <CimButton size="small" type="primary" loading={busy} onClick={() => void saveOverrides()}>
          {t('mds.common.save')}
        </CimButton>
      </div>

      <h4 className="ov-section">{t('mds.productFlow.assignTitle')}</h4>
      <div className="op-copy-bar" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <input className="op-search" style={{ width: 170 }} placeholder={t('mds.productFlow.nodeIdOptional')}
          value={nodeId} onChange={(e) => setNodeId(e.target.value)} />
        <select className="op-search" style={{ width: 110 }} value={assignType}
          onChange={(e) => setAssignType(e.target.value)}>
          <option value="GROUP">GROUP</option>
          <option value="EQUIP">EQUIP</option>
          <option value="CLASS">CLASS</option>
        </select>
        <input className="op-search" style={{ width: 150 }} placeholder={t('mds.productFlow.assignRef')}
          value={assignRef} onChange={(e) => setAssignRef(e.target.value)} />
        <CimButton size="small" loading={busy} onClick={() => void addAssign()}>
          {t('mds.productFlow.add')}
        </CimButton>
      </div>
      <DataTable<api.EquipAssign>
        columns={[
          { key: 'stepId', titleKey: 'mds.productFlow.nodeIdOptional', width: 150, render: (r) => r.stepId || <span className="dim">{t('mds.overview.mark.mainline')}</span> },
          { key: 'assignType', titleKey: 'mds.productFlow.assignType', width: 90, mono: true },
          { key: 'assignRef', titleKey: 'mds.productFlow.assignRef', mono: true },
          {
            key: 'ops',
            titleKey: 'mds.common.actions',
            width: 80,
            render: (r) => (
              <CimButton size="small" danger onClick={() => void delAssign(r.id)}>×</CimButton>
            ),
          },
        ]}
        rows={assignsRes.data ?? []}
        rowKey={(r) => r.id}
        loading={assignsRes.loading}
        emptyTitleKey="mds.productFlow.empty"
      />

      <h4 className="ov-section">{t('mds.productFlow.kvTitle')}</h4>
      <div className="flow-bin-list">
        {kvRows.map((r, idx) => (
          <div className="flow-bin-row" key={idx}>
            <input className="flow-bin-input" value={r.key} placeholder="attrKey"
              onChange={(e) => setKvRows((rs) => rs.map((x, i) => (i === idx ? { ...x, key: e.target.value } : x)))} />
            <input className="flow-bin-input" value={r.value} placeholder={t('mds.productFlow.kvValue')}
              onChange={(e) => setKvRows((rs) => rs.map((x, i) => (i === idx ? { ...x, value: e.target.value } : x)))} />
            <CimButton size="small" danger onClick={() => setKvRows((rs) => rs.filter((_, i) => i !== idx))}>
              ×
            </CimButton>
          </div>
        ))}
        <div style={{ display: 'flex', gap: 8 }}>
          <CimButton size="small" onClick={() => setKvRows((rs) => [...rs, { key: '', value: '' }])}>
            {t('mds.productFlow.add')}
          </CimButton>
          <CimButton size="small" type="primary" loading={busy} onClick={() => void saveKv()}>
            {t('mds.common.save')}
          </CimButton>
        </div>
      </div>
    </CimModal>
  );
}
