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
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import type { EdcEquipMap } from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail } from '@/lib/useMasterDetail';
import GuideBar from '@/components/GuideBar';
import ImportDialog from '@/components/ImportDialog';

/**
 * EDC 设备接口映射（EdcEquipMapPage，V28）——「机台 × CEID → 报告模板」静态绑定。
 *
 * <p>报告结构（SVID→参数列表）由报告模板显式承载；本页只回答「哪台机台的哪个
 * 事件上报哪份报告」。一个 CEID 可绑多个模板，一个模板可被多机台复用；
 * 运行时解析归 EAP/设备集成层。换型/设备接入时集中配置，支持 Excel 批量导入。</p>
 */

interface MapFormState {
  equipmentCode: string;
  ceid: string;
  tplId: string;
  sourceChannel: string;
  description: string;
}

function formFrom(row: EdcEquipMap | null): MapFormState {
  return {
    equipmentCode: row?.equipmentCode ?? '',
    ceid: row?.ceid != null ? String(row.ceid) : '',
    tplId: row?.tplId ?? '',
    sourceChannel: row?.sourceChannel ?? 'GEM',
    description: row?.description ?? '',
  };
}

export default function EdcEquipMapPage() {
  const { t } = useTranslation();

  const tplsRes = useResource(({ signal }) => api.listEdcReportTpls(signal));
  const tplOptions = useMemo(
    () => (tplsRes.data ?? []).filter((tpl) => tpl.enabled !== false)
      .map((tpl) => ({ value: tpl.id, label: `${tpl.tplCode} ${tpl.tplName}` })),
    [tplsRes.data],
  );
  const tplCodeById = useMemo(() => {
    const m = new Map<string, string>();
    (tplsRes.data ?? []).forEach((tpl) => m.set(tpl.id, tpl.tplCode));
    return m;
  }, [tplsRes.data]);

  const md = useMasterDetail<EdcEquipMap>({
    fetchList: (signal) => api.listEdcEquipMaps(undefined, signal),
    remove: (id) => api.deleteEdcEquipMap(id),
    filter: (r, kw) =>
      r.equipmentCode.toLowerCase().includes(kw)
      || (r.tplCode ?? '').toLowerCase().includes(kw),
  });

  const [form, setForm] = useState<MapFormState>(() => formFrom(null));
  const [importOpen, setImportOpen] = useState(false);

  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    md.enterCreate();
  };

  const openEdit = async (row: EdcEquipMap) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
  };

  const save = async () => {
    md.setErr('');
    if (!form.equipmentCode.trim() || !form.tplId) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    const ceid = Number(form.ceid);
    if (!Number.isInteger(ceid) || ceid <= 0) {
      md.setErr(t('mds.edc.map.errCeid'));
      return;
    }
    md.setBusy(true);
    try {
      const payload: api.EdcEquipMapPayload = {
        equipmentCode: form.equipmentCode.trim(),
        ceid,
        tplId: form.tplId,
        sourceChannel: form.sourceChannel,
        description: form.description || null,
      };
      const ret = md.mode === 'create'
        ? await api.createEdcEquipMap(payload)
        : await api.updateEdcEquipMap(md.selected!.id, payload);
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<EdcEquipMap>>>(
    () => [
      { key: 'equipmentCode', titleKey: 'mds.edc.map.colEquipment', width: 150, mono: true },
      { key: 'ceid', titleKey: 'mds.edc.map.colCeid', width: 90, mono: true },
      { key: 'tplCode', titleKey: 'mds.edc.map.colTpl', width: 180, mono: true,
        render: (r) => r.tplCode ?? '—' },
      {
        key: 'tplName',
        titleKey: 'mds.edc.tpl.colName',
        width: 180,
        ellipsis: true,
        render: (r) => <span className="dim">{r.tplName ?? '—'}</span>,
      },
      { key: 'sourceChannel', titleKey: 'mds.edc.map.colChannel', width: 100 },
      { key: 'description', titleKey: 'mds.common.remark', ellipsis: true, render: (r) => r.description ?? '—' },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  const fields = useMemo<Array<FormField<MapFormState>>>(
    () => [
      { kind: 'text', key: 'equipmentCode', labelKey: 'mds.edc.map.colEquipment', mono: true, disabled: md.mode === 'edit', required: md.mode === 'create' },
      { kind: 'number', key: 'ceid', labelKey: 'mds.edc.map.colCeid', required: md.mode === 'create' },
      {
        kind: 'select',
        key: 'tplId',
        labelKey: 'mds.edc.map.colTpl',
        required: true,
        allowClear: false,
        options: tplOptions,
        placeholderKey: 'mds.edc.map.pickTpl',
      },
      {
        kind: 'select',
        key: 'sourceChannel',
        labelKey: 'mds.edc.map.colChannel',
        allowClear: false,
        options: [
          { value: 'GEM', label: 'GEM' },
          { value: 'EDA', label: 'EDA' },
          { value: 'FILE', label: 'FILE' },
        ],
      },
      { kind: 'textarea', key: 'description', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, tplOptions]);

  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.edcMap.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.edcMap.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const selTplCode = md.selected ? tplCodeById.get(md.selected.tplId) ?? md.selected.tplCode ?? '' : '';

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<EdcEquipMap>
          titleKey="mds.edc.map.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.edc.map.searchHint' }}
          headActions={
            <>
              <Can perm={MDS_PERMS.edcMap.update}>
                <CimButton size="small" onClick={() => setImportOpen(true)}>
                  {t('mds.edc.importBtn')}
                </CimButton>
              </Can>
              <Can perm={MDS_PERMS.edcMap.create}>
                <CimButton type="primary" size="small" onClick={openCreate}>
                  {t('mds.common.create')}
                </CimButton>
              </Can>
            </>
          }
          onRefresh={md.res.refresh}
          onRowClick={(r) => void md.onRowClick(r)}
          rowClassName={(r) => (r.id === md.selectedId ? 'row-selected' : '')}
          emptyTitleKey="mds.edc.map.empty"
        />

        <Panel className="op-detail">
          {!md.selected && md.mode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {md.mode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.edc.map.createTitle')}</h3>
                ) : (
                  <h3 className="op-detail-title">
                    {md.selected!.equipmentCode} · CEID {md.selected!.ceid}
                    {selTplCode ? ` × ${selTplCode}` : ''}
                  </h3>
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
                  setForm((f) => ({ ...f, ...(patch as Partial<MapFormState>) }))
                }
                columns={2}
              />
              {md.mode === 'view' && md.selected && (
                <p className="dim" style={{ marginTop: 8 }}>{t('mds.edc.map.hint')}</p>
              )}
            </div>
          )}
        </Panel>
      </div>

      <ImportDialog
        open={importOpen}
        onClose={() => setImportOpen(false)}
        titleKey="mds.edc.map.importTitle"
        kind="EQUIP_MAP"
        templateName="edc-equip-maps-template.xlsx"
        onDone={md.res.refresh}
      />
    </Page>
  );
}
