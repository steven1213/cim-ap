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
import type { Technology } from '@/types';

/**
 * 制程技术（TechnologyPage，动线改版）——产品管理域第一个页面（技术节点分类，全局唯一编码）。
 *
 * <p>布局为「左列表 + 右详情」主从结构（{@link useMasterDetail} 统一动线）：高频动线
 * （搜索 → 点选 → 就地编辑 → 保存）零弹窗；新建/批量删除在列表工具栏；编辑/删除贴详情头。
 * 编辑先抢锁（type=technology）、保存同事务清锁、取消显式释放；techCode 建后不可改
 * （将来 Product 按 code 引用）。</p>
 */

interface TechFormState {
  techCode: string;
  techName: string;
  techNode: string;
  remark: string;
  enabled: boolean;
}

function formFrom(row: Technology | null): TechFormState {
  return {
    techCode: row?.techCode ?? '',
    techName: row?.techName ?? '',
    techNode: row?.techNode ?? '',
    remark: row?.remark ?? '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

export default function TechnologyPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const [form, setForm] = useState<TechFormState>(() => formFrom(null));
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<Technology>({
    fetchList: (signal) => api.listTechnologies(undefined, signal),
    remove: (id) => api.deleteTechnology(id),
    lockType: 'technology',
    filter: (r, kw) =>
      r.techCode.toLowerCase().includes(kw) ||
      r.techName.toLowerCase().includes(kw) ||
      (r.techNode ?? '').toLowerCase().includes(kw),
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

  const openEdit = async (row: Technology) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const save = async () => {
    md.setErr('');
    if (!form.techName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        techName: form.techName.trim(),
        techNode: form.techNode.trim() || null,
        remark: form.remark || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createTechnology({ techCode: form.techCode.trim(), ...payload })
          : await api.updateTechnology(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  // 左列表列（瘦身：全字段在右侧详情，操作列删除）
  const columns = useMemo<Array<DataTableColumn<Technology>>>(
    () => [
      { key: 'techCode', titleKey: 'mds.tech.code', width: 180, mono: true },
      { key: 'techName', titleKey: 'mds.tech.name', width: 200, ellipsis: true, render: (r) => <span className="flow-name-main">{r.techName}</span> },
      { key: 'techNode', titleKey: 'mds.tech.node', width: 110, mono: true, render: (r) => r.techNode ?? '—' },
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
      <Can perm={MDS_PERMS.tech.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.tech.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const fields = useMemo<Array<FormField<TechFormState>>>(
    () => [
      { kind: 'text', key: 'techCode', labelKey: 'mds.tech.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'techName', labelKey: 'mds.tech.name', required: true },
      { kind: 'text', key: 'techNode', labelKey: 'mds.tech.node', mono: true, placeholder: '0.35um' },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
      { kind: 'textarea', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode],
  );

  return (
    <Page>
      <div className="op-layout">
        <DataTable<Technology>
          titleKey="mds.tech.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.tech.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.tech.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.tech.createNew')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.tech.delete}>
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
                  <h3 className="op-detail-title">{t('mds.tech.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.techCode}</h3>
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
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<TechFormState>) }))}
                columns={2}
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
