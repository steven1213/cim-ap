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
import { useResource } from '@cim/data';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail, type DetailMode } from '@/lib/useMasterDetail';
import type { Equipment, EquipmentClass, ToolGroup, ToolGroupMemberItem } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 机台组（ToolGroupPage，v2 新增）——派工候选池（ISA-95 Work Center 口径）。
 *
 * <p>布局「左组列表 + 右详情」，详情含成员块：添加成员按「同机型 + 启用」预筛
 * （派工候选池同质性，后端守门同机型才能入组）；成员 chip 就地移出。
 * V35：成员携带 priority（派工优先级，小值优先），入组时可选填。
 * 组编辑先抢锁（type=toolGroup）、保存同事务清锁、取消显式释放。</p>
 */

const NO_EQP: Equipment[] = [];
const NO_MEMBERS: ToolGroupMemberItem[] = [];

interface GroupFormState {
  groupCode: string;
  groupName: string;
  classCode: string;
  remark: string;
  enabled: boolean;
}

function formFrom(row: ToolGroup | null, classes: EquipmentClass[]): GroupFormState {
  return {
    groupCode: row?.groupCode ?? '',
    groupName: row?.groupName ?? '',
    classCode: row?.classCode ?? classes[0]?.classCode ?? '',
    remark: row?.remark ?? '',
    enabled: row?.enabled ?? true,
  };
}

export default function ToolGroupPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const [memberOp, setMemberOp] = useState(false);
  const [pendingPriority, setPendingPriority] = useState('');
  const modeRef = useRef<DetailMode>('view');

  const classRes = useResource(({ signal }) => api.listEquipmentClasses(undefined, signal));
  const eqpRes = useResource(({ signal }) => api.listEquipments({}, signal));

  const md = useMasterDetail<ToolGroup>({
    fetchList: (signal) => api.listToolGroups({}, signal),
    remove: (id) => api.deleteToolGroup(id),
    lockType: 'toolGroup',
    filter: (r, kw) =>
      r.groupCode.toLowerCase().includes(kw) ||
      r.groupName.toLowerCase().includes(kw) ||
      r.classCode.toLowerCase().includes(kw),
    isDirty: () => false,
  });
  modeRef.current = md.mode;

  const [form, setForm] = useState<GroupFormState>(() => formFrom(null, []));
  const initialJsonRef = useRef(JSON.stringify(formFrom(null, [])));

  // 查看态表单跟随选中行
  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected, classRes.data ?? []));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected, classRes.data]);

  const openCreate = () => {
    const fresh = formFrom(null, classRes.data ?? []);
    setForm(fresh);
    initialJsonRef.current = JSON.stringify(fresh);
    md.enterCreate();
  };

  const openEdit = async (row: ToolGroup) => {
    if (!(await md.enterEdit(row))) return;
    const f = formFrom(row, classRes.data ?? []);
    setForm(f);
    initialJsonRef.current = JSON.stringify(f);
  };

  const save = async () => {
    md.setErr('');
    if (!form.groupName.trim() || !form.classCode) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        groupName: form.groupName.trim(),
        classCode: form.classCode,
        remark: form.remark || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createToolGroup({ groupCode: form.groupCode.trim(), ...payload })
          : await api.updateToolGroup(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  /** 成员添加候选：同机型 + 启用 + 未入组（派工候选池同质性；后端同口径守门）。
   *  注意：memberOp 只是「添加器展开」的 UI 开关，不参与候选计算——
   *  添加器仅在维护态渲染，若此处再按 memberOp 清空候选，添加器将永远为空。 */
  const memberCandidates = useMemo(() => {
    if (!md.selected) return NO_EQP;
    const inGroup = new Set((md.selected.members ?? NO_MEMBERS).map((m) => m.equipmentCode));
    return (eqpRes.data ?? NO_EQP).filter(
      (e) => e.classCode === md.selected!.classCode && e.enabled && !inGroup.has(e.equipmentCode),
    );
  }, [md.selected, eqpRes.data]);

  const addMember = async (equipment: Equipment) => {
    try {
      const priority = pendingPriority === '' ? null : Number(pendingPriority);
      await api.addToolGroupMember(md.selected!.id, equipment.id, priority);
      setPendingPriority('');
      setMemberOp(false);
      toast.success(t('mds.common.saved'));
      md.res.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const removeMember = async (equipmentCode: string) => {
    if (!md.selected) return;
    const target = (eqpRes.data ?? NO_EQP).find((e) => e.equipmentCode === equipmentCode);
    if (!target) return;
    try {
      await api.removeToolGroupMember(md.selected.id, target.id);
      toast.success(t('mds.common.saved'));
      md.res.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const columns = useMemo<Array<DataTableColumn<ToolGroup>>>(
    () => [
      { key: 'groupCode', titleKey: 'mds.toolGroup.code', width: 160, mono: true },
      { key: 'groupName', titleKey: 'mds.toolGroup.name', width: 180, ellipsis: true, render: (r) => <span className="flow-name-main">{r.groupName}</span> },
      {
        key: 'classCode',
        titleKey: 'mds.toolGroup.boundClass',
        width: 160,
        ellipsis: true,
        render: (r) => (
          <span className="mono">{`${r.classCode}${r.className ? ` ${r.className}` : ''}`}</span>
        ),
      },
      {
        key: 'members',
        titleKey: 'mds.toolGroup.members',
        width: 180,
        ellipsis: true,
        render: (r) => (
          <span className="mono">
            {(r.members ?? NO_MEMBERS)
              .map((m) => (m.priority != null ? `${m.equipmentCode}#${m.priority}` : m.equipmentCode))
              .join('、') || '—'}
          </span>
        ),
      },
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

  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.toolGroup.update}>
        <CimButton size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.toolGroup.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const classOptions = useMemo(
    () =>
      (classRes.data ?? [])
        .filter((c) => c.enabled)
        .map((c) => ({ value: c.classCode, label: `${c.classCode} ${c.className}` })),
    [classRes.data],
  );

  const fields = useMemo<Array<FormField<GroupFormState>>>(
    () => [
      { kind: 'text', key: 'groupCode', labelKey: 'mds.toolGroup.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'groupName', labelKey: 'mds.toolGroup.name', required: true },
      {
        kind: 'select',
        key: 'classCode',
        labelKey: 'mds.toolGroup.boundClass',
        required: true,
        allowClear: false,
        disabled: md.mode === 'edit' && (md.selected?.members.length ?? 0) > 0,
        options: classOptions,
      },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
      { kind: 'textarea', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, md.selected, classOptions],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<ToolGroup>
          titleKey="mds.toolGroup.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.toolGroup.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.toolGroup.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          onRefresh={md.res.refresh}
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
                  <h3 className="op-detail-title">{t('mds.toolGroup.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.groupCode}</h3>
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
                  setForm((f) => ({ ...f, ...(patch as Partial<GroupFormState>) }))
                }
                columns={2}
              />

              {/* 成员块（仅查看态展示）：同机型成员 chip + 添加器 */}
              {md.mode === 'view' && md.selected && (
                <div className="toolgroup-members">
                  <h4 className="toolgroup-members-title">{t('mds.toolGroup.members')}</h4>
                  <div className="toolgroup-chips">
                    {(md.selected.members ?? NO_MEMBERS).map((m) => (
                      <span key={m.equipmentCode} className="toolgroup-chip">
                        <span className="mono">
                          {m.equipmentCode}
                          {m.priority != null ? ` #${m.priority}` : ''}
                        </span>
                        <Can perm={MDS_PERMS.toolGroup.update}>
                          <button
                            type="button"
                            className="toolgroup-chip-x"
                            onClick={() => void removeMember(m.equipmentCode)}
                            title={t('mds.toolGroup.removeMember')}
                          >
                            ×
                          </button>
                        </Can>
                      </span>
                    ))}
                    {!md.selected.members?.length && (
                      <span className="dim">{t('mds.toolGroup.noMembers')}</span>
                    )}
                  </div>
                  <Can perm={MDS_PERMS.toolGroup.update}>
                    <div className="toolgroup-adder">
                      {!memberOp ? (
                        <CimButton size="small" onClick={() => setMemberOp(true)}>
                          {t('mds.toolGroup.addMember')}
                        </CimButton>
                      ) : (
                        <>
                          <input
                            className="topo-group-filter"
                            type="number"
                            min={0}
                            style={{ width: 90 }}
                            value={pendingPriority}
                            onChange={(e) => setPendingPriority(e.target.value)}
                            placeholder={t('mds.toolGroup.priority')}
                            title={t('mds.toolGroup.priorityHint')}
                          />
                          <select
                            className="topo-group-filter"
                            onChange={(e) => {
                              const target = memberCandidates.find((c) => c.id === e.target.value);
                              if (target) void addMember(target);
                            }}
                            defaultValue=""
                          >
                            <option value="" disabled>
                              {t('mds.toolGroup.pickMember')}
                            </option>
                            {memberCandidates.map((c) => (
                              <option key={c.id} value={c.id}>
                                {c.equipmentCode} {c.equipmentName}
                              </option>
                            ))}
                          </select>
                          <CimButton size="small" onClick={() => { setMemberOp(false); setPendingPriority(''); }}>
                            {t('mds.common.cancel')}
                          </CimButton>
                        </>
                      )}
                    </div>
                  </Can>
                </div>
              )}
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
