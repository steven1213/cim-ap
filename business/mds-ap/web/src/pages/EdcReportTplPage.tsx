import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  DataTable,
  EmptyState,
  FormGrid,
  Page,
  Panel,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import type { EdcReportTpl, EdcReportTplDetail, EdcTplItemPayload } from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail, type DetailMode } from '@/lib/useMasterDetail';
import GuideBar from '@/components/GuideBar';
import ImportDialog from '@/components/ImportDialog';

/**
 * EDC 报告模板（EdcReportTplPage，V28）——GEM 报告结构的显式主数据。
 *
 * <p>动线：参数定义建档参数 → 参数集打包 → 此处定义报告结构（SVID→参数）→
 * 设备接口映射把机台事件绑定到模板。模板与机台/事件解耦：一个模板可被多机台、
 * 多 CEID 复用；RPTID 是 GEM 会话态标识，由 EAP 运行时推导、不落 MDS。
 * 编辑锁 type=edcReportTpl。</p>
 */

interface TplFormState {
  tplCode: string;
  tplName: string;
  description: string;
  enabled: boolean;
}

const NO_ITEMS: EdcTplItemPayload[] = [];

function formFrom(row: EdcReportTpl | null): TplFormState {
  return {
    tplCode: row?.tplCode ?? '',
    tplName: row?.tplName ?? '',
    description: row?.description ?? '',
    enabled: row?.enabled ?? true,
  };
}

export default function EdcReportTplPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const defsRes = useResource(({ signal }) => api.listParamDefs(signal));
  const setsRes = useResource(({ signal }) => api.listEdcParamSets(signal));

  const md = useMasterDetail<EdcReportTpl>({
    fetchList: (signal) => api.listEdcReportTpls(signal),
    remove: (id) => api.deleteEdcReportTpl(id),
    lockType: 'edcReportTpl',
    isDirty: () =>
      modeRef.current !== 'view'
      && (JSON.stringify(form) + JSON.stringify(items)) !== initialJsonRef.current,
    filter: (r, kw) =>
      r.tplCode.toLowerCase().includes(kw) || r.tplName.toLowerCase().includes(kw),
  });

  const [form, setForm] = useState<TplFormState>(() => formFrom(null));
  const [items, setItems] = useState<EdcTplItemPayload[]>(NO_ITEMS);
  const [detail, setDetail] = useState<EdcReportTplDetail | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [pickOpen, setPickOpen] = useState(false);
  const [pickSetId, setPickSetId] = useState('');
  const [pickErr, setPickErr] = useState('');
  const [pickBusy, setPickBusy] = useState(false);
  const initialJsonRef = useRef(JSON.stringify(formFrom(null)) + JSON.stringify(NO_ITEMS));
  // 脏检查只对编辑/新建态生效（查看态表单跟随选中行，不屏蔽会误判脏 → 切行弹「放弃修改」）。
  const modeRef = useRef<DetailMode>('view');

  // 查看态：表单跟随选中行 + 拉成员明细
  useEffect(() => {
    if (md.mode !== 'view') return;
    setForm(formFrom(md.selected));
    if (md.selected) {
      api.getEdcReportTpl(md.selected.id)
        .then((d) => {
          setDetail(d);
          setItems(d.items.map((i) => ({ svid: i.svid, attrKey: i.attrKey, sortNo: i.sortNo })));
        })
        .catch(() => setDetail(null));
    } else {
      setDetail(null);
      setItems(NO_ITEMS);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    const f = formFrom(null);
    setForm(f);
    setItems(NO_ITEMS);
    setDetail(null);
    initialJsonRef.current = JSON.stringify(f) + JSON.stringify(NO_ITEMS);
    md.enterCreate();
  };

  const openEdit = async (row: EdcReportTpl) => {
    if (!(await md.enterEdit(row))) return;
    const f = formFrom(row);
    setForm(f);
    initialJsonRef.current = JSON.stringify(f) + JSON.stringify(items);
  };

  const save = async () => {
    md.setErr('');
    if (!form.tplName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    if (items.some((i) => !Number.isInteger(i.svid) || i.svid <= 0)) {
      md.setErr(t('mds.common.positiveInt'));
      return;
    }
    md.setBusy(true);
    try {
      const payload: api.EdcReportTplPayload = {
        tplName: form.tplName.trim(),
        enabled: form.enabled,
        description: form.description || null,
      };
      const ret = md.mode === 'create'
        ? await api.createEdcReportTpl({ tplCode: form.tplCode.trim(), ...payload })
        : await api.updateEdcReportTpl(md.selected!.id, payload);
      if (md.selected && md.mode === 'edit') {
        await api.saveEdcReportTplItems(ret.id, items);
      }
      toast.success(t('mds.common.saved'));
      initialJsonRef.current = JSON.stringify(form) + JSON.stringify(items);
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  /** 从参数集一键带入成员：SVID 从现有最大值（≥1000）起自动连续分配；模板已有的参数跳过。 */
  const importFromSet = async () => {
    if (!pickSetId) {
      setPickErr(t('mds.edc.tpl.pickSetRequired'));
      return;
    }
    setPickBusy(true);
    setPickErr('');
    try {
      const d = await api.getEdcParamSet(pickSetId);
      const attrs = new Set(items.filter((i) => i.attrKey).map((i) => i.attrKey));
      const svids = new Set(items.filter((i) => i.svid > 0).map((i) => i.svid));
      let next = Math.max(1000, ...svids) + 1;
      const added: EdcTplItemPayload[] = [];
      for (const m of d.items) {
        if (attrs.has(m.attrKey)) continue;
        while (svids.has(next)) next += 1;
        attrs.add(m.attrKey);
        svids.add(next);
        added.push({ svid: next, attrKey: m.attrKey, sortNo: items.length + added.length });
        next += 1;
      }
      if (added.length) setItems((xs) => [...xs, ...added]);
      setPickOpen(false);
      setPickSetId('');
      toast.success(t('mds.edc.tpl.pickDone', { n: added.length, m: d.items.length - added.length }));
    } catch (e) {
      setPickErr(e instanceof Error ? e.message : String(e));
    } finally {
      setPickBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<EdcReportTpl>>>(
    () => [
      { key: 'tplCode', titleKey: 'mds.edc.tpl.colCode', width: 180, mono: true },
      { key: 'tplName', titleKey: 'mds.edc.tpl.colName', width: 200, ellipsis: true },
      { key: 'description', titleKey: 'mds.common.remark', ellipsis: true, render: (r) => r.description ?? '—' },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  const fields = useMemo<Array<FormField<TplFormState>>>(
    () => [
      { kind: 'text', key: 'tplCode', labelKey: 'mds.edc.tpl.colCode', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'tplName', labelKey: 'mds.edc.tpl.colName', required: true },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
      { kind: 'textarea', key: 'description', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode]);

  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.edcTpl.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.edcTpl.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const detailMode: DetailMode = md.mode;
  modeRef.current = md.mode;

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<EdcReportTpl>
          titleKey="mds.edc.tpl.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.edc.tpl.searchHint' }}
          headActions={
            <>
              <Can perm={MDS_PERMS.edcTpl.update}>
                <CimButton size="small" onClick={() => setImportOpen(true)}>
                  {t('mds.edc.importBtn')}
                </CimButton>
              </Can>
              <Can perm={MDS_PERMS.edcTpl.create}>
                <CimButton type="primary" size="small" onClick={openCreate}>
                  {t('mds.common.create')}
                </CimButton>
              </Can>
            </>
          }
          onRefresh={md.res.refresh}
          onRowClick={(r) => void md.onRowClick(r)}
          rowClassName={(r) => (r.id === md.selectedId ? 'row-selected' : '')}
          emptyTitleKey="mds.edc.tpl.empty"
        />

        <Panel className="op-detail">
          {!md.selected && detailMode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {detailMode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.edc.tpl.createTitle')}</h3>
                ) : (
                  <h3 className="op-detail-title">{md.selected!.tplCode}</h3>
                )}
                {detailMode === 'edit' && <span className="op-editing-badge">{t('mds.common.editing')}</span>}
                <div className="op-detail-actions">
                  {detailMode === 'view' && headerActions}
                  {(detailMode === 'edit' || detailMode === 'create') && (
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
                disabled={detailMode === 'view'}
                onChange={(patch: Record<string, unknown>) =>
                  setForm((f) => ({ ...f, ...(patch as Partial<TplFormState>) }))
                }
                columns={2}
              />

              {/* 成员块：查看态表格；编辑态 SVID+参数行增删（整单替换保存） */}
              {md.selected && (
                <div className="pf-bind-section">
                  <h4 className="pf-bind-key">{t('mds.edc.tpl.members')}</h4>
                  {detailMode === 'view' ? (
                    <DataTable
                      columns={[
                        { key: 'svid', titleKey: 'mds.edc.tpl.svid', width: 90, mono: true },
                        { key: 'attrKey', titleKey: 'mds.edc.colAttr', width: 170, mono: true },
                        { key: 'name', titleKey: 'mds.edc.colAttrName', width: 160, ellipsis: true,
                          render: (i) => i.param?.attrName ?? '—' },
                        { key: 'unit', titleKey: 'mds.edc.colUnit', width: 90, render: (i) => i.param?.unit ?? '—' },
                        { key: 'sortNo', titleKey: 'mds.edc.colSort', width: 70 },
                      ] as Array<DataTableColumn<{ id: string; svid: number; attrKey: string; sortNo: number; param: api.EdcParamLite | null }>>}
                      rows={detail?.items ?? []}
                      rowKey={(i) => i.id}
                      loading={false}
                    />
                  ) : (
                    <div className="edc-tpl-rows">
                      {items.map((it, idx) => (
                        <div key={idx} className="edc-tpl-row">
                          <input
                            type="number"
                            min={1}
                            className="topo-group-filter edc-tpl-svid"
                            value={it.svid}
                            aria-label={t('mds.edc.tpl.svid')}
                            onChange={(e) => {
                              const v = e.target.value === '' ? 0 : Number(e.target.value);
                              setItems((xs) => xs.map((x, i) => (i === idx ? { ...x, svid: v } : x)));
                            }}
                          />
                          <select
                            className="topo-group-filter"
                            value={it.attrKey}
                            aria-label={t('mds.edc.colAttr')}
                            onChange={(e) =>
                              setItems((xs) => xs.map((x, i) => (i === idx ? { ...x, attrKey: e.target.value } : x)))
                            }
                          >
                            <option value="" disabled>{t('mds.edc.set.pickMember')}</option>
                            {(defsRes.data ?? []).filter((d) => d.enabled).map((d) => (
                              <option key={d.id} value={d.attrKey}>
                                {d.attrKey} {d.attrName}
                              </option>
                            ))}
                          </select>
                          <button
                            type="button"
                            className="toolgroup-chip-x"
                            aria-label={t('mds.common.delete')}
                            onClick={() => setItems((xs) => xs.filter((_, i) => i !== idx))}
                          >
                            ×
                          </button>
                        </div>
                      ))}
                      {!items.length && <span className="dim">{t('mds.edc.tpl.noMembers')}</span>}
                      <div className="toolgroup-adder">
                        <CimButton
                          size="small"
                          disabled={!(setsRes.data ?? []).some((s) => s.enabled !== false)}
                          onClick={() => {
                            setPickErr('');
                            setPickOpen(true);
                          }}
                        >
                          {t('mds.edc.tpl.pickBtn')}
                        </CimButton>
                        <CimButton
                          size="small"
                          onClick={() => setItems((xs) => [...xs, { svid: 0, attrKey: '', sortNo: xs.length }])}
                        >
                          {t('mds.edc.tpl.addMember')}
                        </CimButton>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </Panel>
      </div>

      <ImportDialog
        open={importOpen}
        onClose={() => setImportOpen(false)}
        titleKey="mds.edc.tpl.importTitle"
        kind="REPORT_ITEM"
        targetId={md.selected?.id ?? null}
        templateName="edc-report-tpl-items-template.xlsx"
        onDone={md.res.refresh}
      />

      <CimModal
        open={pickOpen}
        onClose={() => setPickOpen(false)}
        titleKey="mds.edc.tpl.pickTitle"
        onOk={() => void importFromSet()}
        okKey="mds.edc.tpl.pickOk"
        cancelKey="mds.common.cancel"
        okLoading={pickBusy}
        width={480}
      >
        <FormGrid
          fields={[
            {
              kind: 'select',
              key: 'setId',
              labelKey: 'mds.edc.tpl.pickSetLabel',
              required: true,
              options: (setsRes.data ?? [])
                .filter((s) => s.enabled !== false)
                .map((s) => ({ value: s.id, label: `${s.setCode} ${s.setName}` })),
            },
          ]}
          value={{ setId: pickSetId }}
          onChange={(patch: Record<string, unknown>) => {
            setPickSetId(String(patch.setId ?? ''));
            setPickErr('');
          }}
          columns={1}
        />
        <p className="dim">{t('mds.edc.tpl.pickHint')}</p>
        {pickErr && <p className="op-error">{pickErr}</p>}
      </CimModal>
    </Page>
  );
}
