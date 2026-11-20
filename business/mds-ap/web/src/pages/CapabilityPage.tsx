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
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail, type DetailMode } from '@/lib/useMasterDetail';
import type { Capability } from '@/types';

/**
 * 设备能力（CapabilityPage，动线改版）——能力字典。
 *
 * <p>布局为「左列表 + 右详情」主从结构（{@link useMasterDetail} 统一动线）：高频动线
 * （搜索 → 点选 → 就地编辑 → 保存）零弹窗；新建/批量删除在列表工具栏；编辑/删除贴详情头。
 * 编辑先抢锁（type=capability）、保存同事务清锁、取消显式释放。能力回答「能不能做」
 * （如 E84 接口、双端口搬运、6 英寸加工）；设备↔能力关联在设备行内维护。
 * capabilityCode 建后不可改（关联表按 code 引用）；被设备关联的能力不可删（后端守门 2004）。</p>
 */

interface CapFormState {
  capabilityCode: string;
  capabilityName: string;
  remark: string;
  enabled: boolean;
}

function formFrom(row: Capability | null): CapFormState {
  return {
    capabilityCode: row?.capabilityCode ?? '',
    capabilityName: row?.capabilityName ?? '',
    remark: row?.remark ?? '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

export default function CapabilityPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const [form, setForm] = useState<CapFormState>(() => formFrom(null));
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<Capability>({
    fetchList: (signal) => api.listCapabilities(undefined, signal),
    remove: (id) => api.deleteCapability(id),
    lockType: 'capability',
    filter: (r, kw) =>
      r.capabilityCode.toLowerCase().includes(kw) || r.capabilityName.toLowerCase().includes(kw),
    isDirty: () => modeRef.current !== 'view' && JSON.stringify(form) !== initialJsonRef.current,
  });
  modeRef.current = md.mode;

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

  const openEdit = async (row: Capability) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const save = async () => {
    md.setErr('');
    if (!form.capabilityName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        capabilityName: form.capabilityName.trim(),
        remark: form.remark || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createCapability({ capabilityCode: form.capabilityCode.trim(), ...payload })
          : await api.updateCapability(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  // 左列表列（瘦身：全字段在右侧详情，操作列删除）
  const columns = useMemo<Array<DataTableColumn<Capability>>>(
    () => [
      { key: 'capabilityCode', titleKey: 'mds.capability.code', width: 180, mono: true },
      { key: 'capabilityName', titleKey: 'mds.capability.name', width: 220, ellipsis: true, render: (r) => <span className="flow-name-main">{r.capabilityName}</span> },
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
      { key: 'remark', titleKey: 'mds.common.remark', ellipsis: true, render: (r) => r.remark ?? '—' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  // 详情头动作（对象级，贴详情头）
  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.capability.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.capability.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const fields = useMemo<Array<FormField<CapFormState>>>(
    () => [
      { kind: 'text', key: 'capabilityCode', labelKey: 'mds.capability.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'capabilityName', labelKey: 'mds.capability.name', required: true },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
      { kind: 'textarea', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode],
  );

  return (
    <Page>
      <div className="op-layout">
        <DataTable<Capability>
          titleKey="mds.capability.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.capability.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.capability.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.capability.delete}>
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
                  <h3 className="op-detail-title">{t('mds.capability.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.capabilityCode}</h3>
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
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<CapFormState>) }))}
                columns={2}
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
