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
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { OperationView } from '@/types';

/**
 * 工序库（OperationsPage，V20）——P-T-R-O 四层建模的 O 层管理页。
 *
 * <p>Operation = 独立主数据（工序），跨路线复用；Step = 路线内工序实例（流程画布里
 * 引用本库 + 局部覆盖）。生命周期与制造流程同款四态：UNFROZEN（草稿，可编辑）→
 * FROZEN（冻结待激活）→ ACTIVE（生效，同族唯一，只有 ACTIVE 可被路线引用）→
 * DISABLED（禁用）；改冻结/生效版本 = 复制同族新版本。被路线步骤引用的工序
 * 禁删禁禁用（后端 2004 守门，前端 referenced 列提示）。12 寸预留字段
 * （光罩/测试程序/Inhibit/监测片/腔体）建列不启用，表单不暴露。</p>
 */
export default function OperationsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [keyword, setKeyword] = useState('');
  const opsRes = useResource(({ signal }) => api.listOperations(keyword || undefined, signal), {
    deps: [keyword],
  });
  const ops = opsRes.data ?? [];

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<OperationView | null>(null);
  const [copying, setCopying] = useState<OperationView | null>(null);

  const doAction = async (row: OperationView, action: 'freeze' | 'unfreeze' | 'activate' | 'disable') => {
    const ok = await confirm({
      titleKey: `mds.operation.${action}Confirm`,
      contentKey: 'mds.operation.actionConfirmText',
      okKey: 'mds.common.ok',
    });
    if (!ok) return;
    try {
      if (action === 'freeze') await api.freezeOperation(row.id);
      else if (action === 'unfreeze') await api.unfreezeOperation(row.id);
      else if (action === 'activate') await api.activateOperation(row.id);
      else await api.disableOperation(row.id);
      toast.success(t('mds.common.saved'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    opsRes.refresh();
  };

  const doDelete = async (row: OperationView) => {
    const ok = await confirm({
      titleKey: 'mds.operation.deleteConfirm',
      contentKey: 'mds.operation.deleteConfirmText',
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteOperation(row.id);
      toast.success(t('mds.common.saved'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    opsRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<OperationView>>>(
    () => [
      {
        key: 'opCode',
        titleKey: 'mds.operation.field.opCode',
        width: 150,
        mono: true,
        render: (r) => (
          <span>
            {r.opCode}
            <span className="flow-name-sub"> V{r.versionNo ?? 1}</span>
          </span>
        ),
      },
      {
        key: 'opName',
        titleKey: 'mds.operation.field.opName',
        width: 180,
        render: (r) => <span className="flow-name-main">{r.opName}</span>,
      },
      {
        key: 'opType',
        titleKey: 'mds.operation.field.opType',
        width: 90,
        render: (r) => t(`mds.operation.type.${r.opType}`),
      },
      {
        key: 'status',
        titleKey: 'mds.operation.field.status',
        width: 88,
        render: (r) => (
          <span className={`flow-status flow-status-${r.status.toLowerCase()}`}>
            {t(`mds.operation.status.${r.status}`)}
          </span>
        ),
      },
      { key: 'techCode', titleKey: 'mds.operation.field.techCode', width: 110, mono: true },
      { key: 'recipeRef', titleKey: 'mds.operation.field.recipeRef', width: 130, mono: true },
      { key: 'equipType', titleKey: 'mds.operation.field.equipType', width: 110, mono: true },
      {
        key: 'referenced',
        titleKey: 'mds.operation.field.referenced',
        width: 90,
        render: (r) =>
          r.referenced ? <span className="op-ref-yes">{t('mds.operation.referencedYes')}</span> : <span className="dim">—</span>,
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 260,
        render: (r) => (
          <span className="cell-actions">
            {r.status === 'UNFROZEN' && (
              <Can perm={MDS_PERMS.operation.update}>
                <CimButton size="small" onClick={() => void doAction(r, 'freeze')}>
                  {t('mds.operation.freeze')}
                </CimButton>
              </Can>
            )}
            {r.status === 'FROZEN' && (
              <Can perm={MDS_PERMS.operation.update}>
                <CimButton size="small" onClick={() => void doAction(r, 'activate')}>
                  {t('mds.operation.activate')}
                </CimButton>
                <CimButton size="small" onClick={() => void doAction(r, 'unfreeze')}>
                  {t('mds.operation.unfreeze')}
                </CimButton>
              </Can>
            )}
            {(r.status === 'FROZEN' || r.status === 'ACTIVE') && (
              <Can perm={MDS_PERMS.operation.update}>
                <CimButton size="small" onClick={() => void doAction(r, 'disable')}>
                  {t('mds.operation.disable')}
                </CimButton>
              </Can>
            )}
            {(r.status === 'FROZEN' || r.status === 'ACTIVE') && (
              <Can perm={MDS_PERMS.operation.create}>
                <CimButton size="small" onClick={() => setCopying(r)}>
                  {t('mds.operation.copy')}
                </CimButton>
              </Can>
            )}
            {r.status === 'UNFROZEN' && (
              <Can perm={MDS_PERMS.operation.update}>
                <CimButton size="small" onClick={() => setEditing(r)}>
                  {t('mds.common.edit')}
                </CimButton>
              </Can>
            )}
            {(r.status === 'UNFROZEN' || r.status === 'DISABLED') && (
              <Can perm={MDS_PERMS.operation.delete}>
                <CimButton size="small" danger onClick={() => void doDelete(r)}>
                  {t('mds.common.delete')}
                </CimButton>
              </Can>
            )}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <Panel
        titleKey="mds.operation.listTitle"
        actions={
          <>
            <input
              className="op-search"
              placeholder={t('mds.operation.searchPlaceholder')}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Can perm={MDS_PERMS.operation.create}>
              <CimButton
                type="primary"
                size="small"
                onClick={() => {
                  setEditing(null);
                  setFormOpen(true);
                }}
              >
                {t('mds.common.create')}
              </CimButton>
            </Can>
          </>
        }
      >
        <DataTable
          rows={ops}
          columns={columns}
          rowKey={(r) => r.id}
          loading={opsRes.loading}
        />
      </Panel>

      {formOpen && (
        <OpFormModal
          key={editing?.id ?? 'new'}
          editing={editing}
          onClose={() => setFormOpen(false)}
          onDone={() => {
            setFormOpen(false);
            opsRes.refresh();
          }}
        />
      )}

      {copying && (
        <CopyModal
          source={copying}
          onClose={() => setCopying(null)}
          onDone={() => {
            setCopying(null);
            opsRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 工序新建/编辑弹窗（编辑态 opCode/opType 锁定；12 寸预留字段不进表单）。 */
function OpFormModal(props: {
  editing: OperationView | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const editing = props.editing;
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const techsRes = useResource(({ signal }) => api.listTechnologies(undefined, signal));
  const equipTypesRes = useResource(({ signal }) => api.listEquipmentTypes(undefined, signal));
  const [form, setForm] = useState({
    opCode: editing?.opCode ?? '',
    opName: editing?.opName ?? '',
    opType: (editing?.opType ?? 'PROCESS') as OperationView['opType'],
    techCode: editing?.techCode ?? '',
    recipeRef: editing?.recipeRef ?? '',
    equipType: editing?.equipType ?? '',
    qtimeMin: editing?.qtimeMin != null ? String(editing.qtimeMin) : '',
    qtimeMax: editing?.qtimeMax != null ? String(editing.qtimeMax) : '',
    reworkLimit: editing?.reworkLimit != null ? String(editing.reworkLimit) : '',
    holdPoint: Boolean(editing?.holdPoint),
    holdReason: editing?.holdReason ?? '',
    batchMode: editing?.batchMode ?? '',
    batchSize: editing?.batchSize != null ? String(editing.batchSize) : '',
    inspectPlan: editing?.inspectPlan ?? '',
    samplingRate: editing?.samplingRate != null ? String(editing.samplingRate) : '',
    remark: editing?.remark ?? '',
  });

  const set = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }));
  const numOrNull = (v: string) => (v.trim() === '' ? null : Number(v));

  const submit = async () => {
    setErr('');
    if (!form.opName.trim()) {
      setErr(t('mds.operation.errNameRequired'));
      return;
    }
    setBusy(true);
    try {
      const payload = {
        opCode: form.opCode.trim() || null,
        opName: form.opName.trim(),
        opType: form.opType,
        techCode: form.techCode || null,
        recipeRef: form.recipeRef || null,
        equipType: form.equipType || null,
        qtimeMin: numOrNull(form.qtimeMin),
        qtimeMax: numOrNull(form.qtimeMax),
        reworkLimit: numOrNull(form.reworkLimit),
        holdPoint: form.holdPoint,
        holdReason: form.holdReason || null,
        batchMode: form.batchMode || null,
        batchSize: numOrNull(form.batchSize),
        inspectPlan: form.inspectPlan || null,
        samplingRate: numOrNull(form.samplingRate),
        remark: form.remark || null,
      };
      if (editing) await api.updateOperation(editing.id, payload);
      else await api.createOperation(payload);
      toast.success(t('mds.common.saved'));
      props.onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <CimModal
      open
      titleKey={editing ? 'mds.operation.editTitle' : 'mds.operation.createTitle'}
      onClose={props.onClose}
      footer={
        <>
          <CimButton onClick={props.onClose}>{t('mds.common.cancel')}</CimButton>
          <CimButton type="primary" disabled={busy} onClick={() => void submit()}>
            {t('mds.common.ok')}
          </CimButton>
        </>
      }
    >
      {err && <p className="op-error">{err}</p>}
      <FormGrid
        fields={[
          { kind: 'text', key: 'opCode', labelKey: 'mds.operation.field.opCode', mono: true, disabled: Boolean(editing), required: !editing },
          { kind: 'text', key: 'opName', labelKey: 'mds.operation.field.opName', required: true },
          {
            kind: 'select',
            key: 'opType',
            labelKey: 'mds.operation.field.opType',
            disabled: Boolean(editing),
            options: [
              { value: 'PROCESS', label: t('mds.operation.type.PROCESS') },
              { value: 'INSPECT', label: t('mds.operation.type.INSPECT') },
              { value: 'REWORK', label: t('mds.operation.type.REWORK') },
            ],
          },
          {
            kind: 'select',
            key: 'techCode',
            labelKey: 'mds.operation.field.techCode',
            allowClear: true,
            options: (techsRes.data ?? []).map((tech) => ({ value: tech.techCode, label: tech.techName ?? tech.techCode })),
          },
          { kind: 'text', key: 'recipeRef', labelKey: 'mds.operation.field.recipeRef', mono: true },
          {
            kind: 'select',
            key: 'equipType',
            labelKey: 'mds.operation.field.equipType',
            allowClear: true,
            options: (equipTypesRes.data ?? []).map((eq) => ({ value: eq.typeCode, label: eq.typeName ?? eq.typeCode })),
          },
          { kind: 'number', key: 'qtimeMin', labelKey: 'mds.operation.field.qtimeMin' },
          { kind: 'number', key: 'qtimeMax', labelKey: 'mds.operation.field.qtimeMax' },
          { kind: 'number', key: 'reworkLimit', labelKey: 'mds.operation.field.reworkLimit' },
          { kind: 'switch', key: 'holdPoint', labelKey: 'mds.operation.field.holdPoint' },
          ...(form.holdPoint ? [{ kind: 'text' as const, key: 'holdReason' as const, labelKey: 'mds.operation.field.holdReason' }] : []),
          {
            kind: 'select',
            key: 'batchMode',
            labelKey: 'mds.operation.field.batchMode',
            allowClear: true,
            options: [
              { value: 'SERIAL', label: t('mds.operation.batch.SERIAL') },
              { value: 'PARALLEL', label: t('mds.operation.batch.PARALLEL') },
              { value: 'BATCH', label: t('mds.operation.batch.BATCH') },
            ],
          },
          { kind: 'number', key: 'batchSize', labelKey: 'mds.operation.field.batchSize' },
          ...(form.opType === 'INSPECT'
            ? [
                { kind: 'text' as const, key: 'inspectPlan' as const, labelKey: 'mds.operation.field.inspectPlan', mono: true },
                { kind: 'number' as const, key: 'samplingRate' as const, labelKey: 'mds.operation.field.samplingRate' },
              ]
            : []),
          { kind: 'textarea', key: 'remark', labelKey: 'mds.operation.field.remark' },
        ]}
        value={form}
        onChange={(patch: Record<string, unknown>) => set(patch as Partial<typeof form>)}
        columns={2}
      />
    </CimModal>
  );
}

/** 复制为同族新版本弹窗（opCode 手填必填）。 */
function CopyModal(props: { source: OperationView; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [newOpCode, setNewOpCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const submit = async () => {
    setErr('');
    if (!newOpCode.trim()) {
      setErr(t('mds.operation.errCodeRequired'));
      return;
    }
    setBusy(true);
    try {
      await api.copyOperation(props.source.id, newOpCode.trim());
      toast.success(t('mds.common.saved'));
      props.onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <CimModal
      open
      titleKey="mds.operation.copyTitle"
      onClose={props.onClose}
      footer={
        <>
          <CimButton onClick={props.onClose}>{t('mds.common.cancel')}</CimButton>
          <CimButton type="primary" disabled={busy} onClick={() => void submit()}>
            {t('mds.common.ok')}
          </CimButton>
        </>
      }
    >
      {err && <p className="op-error">{err}</p>}
      <p className="dim">
        {t('mds.operation.copyFrom', { code: props.source.opCode, version: props.source.versionNo ?? 1 })}
      </p>
      <FormGrid
        fields={[{ kind: 'text', key: 'newOpCode', labelKey: 'mds.operation.field.newOpCode', mono: true, required: true }]}
        value={{ newOpCode }}
        onChange={(patch: Record<string, unknown>) => setNewOpCode(String(patch.newOpCode ?? ''))}
        columns={1}
      />
    </CimModal>
  );
}
