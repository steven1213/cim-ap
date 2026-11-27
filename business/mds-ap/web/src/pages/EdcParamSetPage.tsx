import { useEffect, useMemo, useRef, useState } from 'react';
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
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import type { EdcParamSet, EdcParamSetDetail, EdcSetItemPayload } from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail, type DetailMode } from '@/lib/useMasterDetail';
import GuideBar from '@/components/GuideBar';
import ImportDialog from '@/components/ImportDialog';

/**
 * EDC 参数集（EdcParamSetPage，V28）——参数模板的打包归类（行业 DCP Template 口径）。
 *
 * <p>动线：先在产品管理→参数定义建档参数，再在此打包成集，最后在 EDC 方案一键带入。
 * 左集列表 + 右详情（集信息 + 成员维护）；成员 attr_key 引用参数定义字典，
 * 服务端守门存在且启用。编辑锁 type=edcSet。</p>
 */

interface SetFormState {
  setCode: string;
  setName: string;
  opCode: string;
  description: string;
  enabled: boolean;
}

const NO_ITEMS: EdcSetItemPayload[] = [];

function formFrom(row: EdcParamSet | null): SetFormState {
  return {
    setCode: row?.setCode ?? '',
    setName: row?.setName ?? '',
    opCode: row?.opCode ?? '',
    description: row?.description ?? '',
    enabled: row?.enabled ?? true,
  };
}

export default function EdcParamSetPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const defsRes = useResource(({ signal }) => api.listParamDefs(signal));

  const md = useMasterDetail<EdcParamSet>({
    fetchList: (signal) => api.listEdcParamSets(signal),
    remove: (id) => api.deleteEdcParamSet(id),
    lockType: 'edcSet',
    isDirty: () =>
      modeRef.current !== 'view'
      && (JSON.stringify(form) + JSON.stringify(items)) !== initialJsonRef.current,
    filter: (r, kw) =>
      r.setCode.toLowerCase().includes(kw) ||
      r.setName.toLowerCase().includes(kw) ||
      (r.opCode ?? '').toLowerCase().includes(kw),
  });

  const [form, setForm] = useState<SetFormState>(() => formFrom(null));
  const [items, setItems] = useState<EdcSetItemPayload[]>(NO_ITEMS);
  const [detail, setDetail] = useState<EdcParamSetDetail | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [memberOp, setMemberOp] = useState(false);
  const initialJsonRef = useRef(JSON.stringify(formFrom(null)) + JSON.stringify(NO_ITEMS));
  // 脏检查只对编辑/新建态生效：查看态表单跟随选中行（含成员明细异步拉取），
  // 若不屏蔽会在切行时误判「有未保存的修改」弹放弃确认。
  const modeRef = useRef<DetailMode>('view');

  // 查看态：表单跟随选中行 + 拉成员明细
  useEffect(() => {
    if (md.mode !== 'view') return;
    setForm(formFrom(md.selected));
    if (md.selected) {
      api.getEdcParamSet(md.selected.id)
        .then((d) => {
          setDetail(d);
          setItems(d.items.map((i) => ({ attrKey: i.attrKey, sortNo: i.sortNo })));
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

  const openEdit = async (row: EdcParamSet) => {
    if (!(await md.enterEdit(row))) return;
    const f = formFrom(row);
    setForm(f);
    initialJsonRef.current = JSON.stringify(f) + JSON.stringify(items);
  };

  const save = async () => {
    md.setErr('');
    if (!form.setName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload: api.EdcParamSetPayload = {
        setName: form.setName.trim(),
        opCode: form.opCode.trim() || null,
        enabled: form.enabled,
        description: form.description || null,
      };
      const ret = md.mode === 'create'
        ? await api.createEdcParamSet({ setCode: form.setCode.trim(), ...payload })
        : await api.updateEdcParamSet(md.selected!.id, payload);
      if (md.selected && md.mode === 'edit') {
        await api.saveEdcParamSetItems(ret.id, items);
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

  const memberCandidates = useMemo(() => {
    const inSet = new Set(items.map((i) => i.attrKey));
    return (defsRes.data ?? []).filter((d) => d.enabled && !inSet.has(d.attrKey));
  }, [defsRes.data, items]);

  const columns = useMemo<Array<DataTableColumn<EdcParamSet>>>(
    () => [
      { key: 'setCode', titleKey: 'mds.edc.set.colCode', width: 160, mono: true },
      { key: 'setName', titleKey: 'mds.edc.set.colName', width: 180, ellipsis: true },
      { key: 'opCode', titleKey: 'mds.edc.set.colOp', width: 130, mono: true, render: (r) => r.opCode ?? '—' },
      { key: 'description', titleKey: 'mds.common.remark', ellipsis: true, render: (r) => r.description ?? '—' },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  const fields = useMemo<Array<FormField<SetFormState>>>(
    () => [
      { kind: 'text', key: 'setCode', labelKey: 'mds.edc.set.colCode', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'setName', labelKey: 'mds.edc.set.colName', required: true },
      { kind: 'text', key: 'opCode', labelKey: 'mds.edc.set.colOp', mono: true, placeholderKey: 'mds.edc.set.opHint' },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
      { kind: 'textarea', key: 'description', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode]);

  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.edcSet.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.edcSet.delete}>
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
        <DataTable<EdcParamSet>
          titleKey="mds.edc.set.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.edc.set.searchHint' }}
          headActions={
            <>
              <Can perm={MDS_PERMS.edcSet.update}>
                <CimButton size="small" onClick={() => setImportOpen(true)}>
                  {t('mds.edc.importBtn')}
                </CimButton>
              </Can>
              <Can perm={MDS_PERMS.edcSet.create}>
                <CimButton type="primary" size="small" onClick={openCreate}>
                  {t('mds.common.create')}
                </CimButton>
              </Can>
            </>
          }
          onRefresh={md.res.refresh}
          onRowClick={(r) => void md.onRowClick(r)}
          rowClassName={(r) => (r.id === md.selectedId ? 'row-selected' : '')}
          emptyTitleKey="mds.edc.set.empty"
        />

        <Panel className="op-detail">
          {!md.selected && detailMode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {detailMode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.edc.set.createTitle')}</h3>
                ) : (
                  <h3 className="op-detail-title">{md.selected!.setCode}</h3>
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
                  setForm((f) => ({ ...f, ...(patch as Partial<SetFormState>) }))
                }
                columns={2}
              />

              {/* 成员块：查看态表格；编辑态 chip 增删（整单替换保存） */}
              {md.selected && (
                <div className="pf-bind-section">
                  <h4 className="pf-bind-key">{t('mds.edc.set.members')}</h4>
                  {detailMode === 'view' ? (
                    <DataTable
                      columns={[
                        { key: 'attrKey', titleKey: 'mds.edc.colAttr', width: 170, mono: true },
                        { key: 'name', titleKey: 'mds.edc.colAttrName', width: 160, ellipsis: true,
                          render: (i) => i.param?.attrName ?? '—' },
                        { key: 'unit', titleKey: 'mds.edc.colUnit', width: 90, render: (i) => i.param?.unit ?? '—' },
                        { key: 'sortNo', titleKey: 'mds.edc.colSort', width: 70 },
                      ] as Array<DataTableColumn<{ id: string; attrKey: string; sortNo: number; param: api.EdcParamLite | null }>>}
                      rows={detail?.items ?? []}
                      rowKey={(i) => i.id}
                      loading={false}
                    />
                  ) : (
                    <div className="toolgroup-members">
                      <div className="toolgroup-chips">
                        {items.map((i) => (
                          <span key={i.attrKey} className="toolgroup-chip">
                            <span className="mono">{i.attrKey}</span>
                            <button
                              type="button"
                              className="toolgroup-chip-x"
                              onClick={() => setItems((xs) => xs.filter((x) => x.attrKey !== i.attrKey))}
                            >
                              ×
                            </button>
                          </span>
                        ))}
                        {!items.length && <span className="dim">{t('mds.edc.set.noMembers')}</span>}
                      </div>
                      <div className="toolgroup-adder">
                        {!memberOp ? (
                          <CimButton size="small" onClick={() => setMemberOp(true)}>
                            {t('mds.edc.set.addMember')}
                          </CimButton>
                        ) : (
                          <>
                            <select
                              className="topo-group-filter"
                              onChange={(e) => {
                                const key = e.target.value;
                                if (key) setItems((xs) => [...xs, { attrKey: key, sortNo: xs.length }]);
                                setMemberOp(false);
                              }}
                              defaultValue=""
                            >
                              <option value="" disabled>{t('mds.edc.set.pickMember')}</option>
                              {memberCandidates.map((d) => (
                                <option key={d.id} value={d.attrKey}>
                                  {d.attrKey} {d.attrName}
                                </option>
                              ))}
                            </select>
                            <CimButton size="small" onClick={() => setMemberOp(false)}>
                              {t('mds.common.cancel')}
                            </CimButton>
                          </>
                        )}
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
        titleKey="mds.edc.set.importTitle"
        kind="PARAM_SET_ITEM"
        targetId={md.selected?.id ?? null}
        templateName="edc-param-set-items-template.xlsx"
        onDone={md.res.refresh}
      />
    </Page>
  );
}
