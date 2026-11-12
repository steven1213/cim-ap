import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimModal,
  CimSelect,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { EXPERIMENT_MANAGE } from '@/lib/permCodes';
import type { Device, Experiment, Recipe, RecipeVersion } from '@/types';

const NO_ROWS: Experiment[] = [];
const NO_DEVICES: Device[] = [];
const NO_RECIPES: Recipe[] = [];
const NO_VERSIONS: RecipeVersion[] = [];
const STATUSES: Array<Experiment['status']> = ['REQUESTED', 'APPROVED', 'REJECTED', 'CLOSED'];

/** 待办动作：批准/拒绝带审批意见，提前结束带结束原因。 */
type PendingAction = { kind: 'approve' | 'reject' | 'close'; row: Experiment };

/**
 * 实验配方通道页（W11）：DOE 临时配方受控通道——审批后在「机台 × 版本 × 窗口」内
 * 放行 IF-E2 下发，绕开「仅 ACTIVE 可下发」闸门；过期/关闭自动失效，全程留痕。
 *
 * 迁移要点：`window.prompt` 两处（审批意见、结束原因）换成带 `CimModal` 的意见框——
 * prompt 会阻塞主线程、无法带默认值与多行输入，且在有审批记录的页面上无法审计是「点了取消」
 * 还是「留了空意见」。现在取消即不发请求，空意见仍可提交，与旧行为一致但语义明确。
 */
export default function ExperimentPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const [status, setStatus] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [pending, setPending] = useState<PendingAction | null>(null);

  const { data, loading, error, refresh } = useResource(
    () => api.listExperiments(status || undefined),
    { deps: [status], keepPreviousData: true },
  );
  const rows = data ?? NO_ROWS;

  const statusOptions = useMemo(
    () => [
      { value: '', labelKey: 'rms.exp.allStatus' },
      ...STATUSES.map((s) => ({ value: s, labelKey: `rms.exp.status.${s}` })),
    ],
    [],
  );

  const submitAction = async (text: string) => {
    if (!pending) return;
    const { kind, row } = pending;
    try {
      if (kind === 'close') {
        // 传空串而非 undefined：后端区分 null 与空串（旧实现的 prompt 未填时就是空串）
        await api.closeExperiment(row.id, text);
        toast.success(t('rms.exp.close'));
      } else {
        await api.decideExperiment(row.id, kind, text);
        toast.success(t(kind === 'approve' ? 'rms.common.approved' : 'rms.common.rejected'));
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setPending(null);
    refresh();
  };

  const columns = useMemo<Array<DataTableColumn<Experiment>>>(
    () => [
      { key: 'code', titleKey: 'rms.exp.code', mono: true, width: 150 },
      {
        key: 'recipe',
        titleKey: 'rms.exp.recipe',
        mono: true,
        width: 180,
        render: (r) => `${r.recipeCode ?? r.recipeId} v${r.versionNo ?? '—'}`,
      },
      {
        key: 'device',
        titleKey: 'rms.exp.device',
        mono: true,
        width: 130,
        render: (r) => r.deviceCode ?? r.deviceId,
      },
      {
        key: 'window',
        titleKey: 'rms.exp.window',
        width: 320,
        render: (r) => (
          <span className="muted">
            {r.validFrom ?? '—'} ~ {r.validUntil ?? '—'}
          </span>
        ),
      },
      {
        key: 'status',
        titleKey: 'rms.exp.statusLabel',
        width: 100,
        render: (r) => (
          <CimTag
            tone={
              r.status === 'APPROVED'
                ? r.expired
                  ? 'warn'
                  : 'ok'
                : r.status === 'REJECTED' || r.status === 'CLOSED'
                  ? 'err'
                  : 'warn'
            }
            labelKey={r.status === 'APPROVED' && r.expired ? 'rms.exp.expired' : `rms.exp.status.${r.status}`}
          />
        ),
      },
      {
        key: 'requestedBy',
        titleKey: 'rms.exp.requestedBy',
        width: 110,
        render: (r) => <span className="muted">{r.requestedBy ?? '—'}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 190,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={EXPERIMENT_MANAGE}>
              {r.status === 'REQUESTED' && (
                <>
                  <CimButton
                    size="small"
                    onClick={() => setPending({ kind: 'approve', row: r })}
                  >
                    {t('rms.exp.approve')}
                  </CimButton>
                  <CimButton
                    size="small"
                    onClick={() => setPending({ kind: 'reject', row: r })}
                  >
                    {t('rms.exp.reject')}
                  </CimButton>
                </>
              )}
              {r.status === 'APPROVED' && !r.expired && (
                <CimButton
                  size="small"
                  onClick={() => setPending({ kind: 'close', row: r })}
                >
                  {t('rms.exp.close')}
                </CimButton>
              )}
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      

      <DataTable<Experiment>
        headActions={<Can perm={EXPERIMENT_MANAGE}>
            <CimButton type="primary" size="small" onClick={() => setShowCreate(true)}>
              + {t('rms.exp.create')}
            </CimButton>
          </Can>}
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="rms.experiment"
        scrollX="max-content"
        actions={
          <CimSelect
            value={status || null}
            onChange={(v) => setStatus(typeof v === 'string' ? v : '')}
            options={statusOptions}
            width={180}
            showSearch={false}
          />
        }
        emptyTitleKey="rms.exp.empty"
      />

      {showCreate && (
        <CreateModal
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            toast.success(t('rms.common.created'));
            refresh();
          }}
        />
      )}

      {pending && (
        <ActionModal
          kind={pending.kind}
          onClose={() => setPending(null)}
          onSubmit={(text) => void submitAction(text)}
        />
      )}
    </Page>
  );
}

interface CreateForm {
  deviceId: string;
  recipeId: string;
  versionId: string;
  reason: string;
  validUntil: string;
}

function CreateModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { t } = useTranslation();
  const [form, setForm] = useState<CreateForm>({
    deviceId: '',
    recipeId: '',
    versionId: '',
    reason: '',
    validUntil: '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof CreateForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const devices = useResource(() => api.listDevices()).data ?? NO_DEVICES;
  const recipes = useResource(() => api.listRecipes()).data ?? NO_RECIPES;
  const versions =
    useResource(
      () => (form.recipeId ? api.listVersions(form.recipeId) : Promise.resolve(NO_VERSIONS)),
      { deps: [form.recipeId], enabled: !!form.recipeId, keepPreviousData: true },
    ).data ?? NO_VERSIONS;

  const fields = useMemo<Array<FormField<CreateForm>>>(
    () => [
      {
        kind: 'select',
        key: 'deviceId',
        labelKey: 'rms.exp.device',
        required: true,
        options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
      },
      {
        kind: 'select',
        key: 'recipeId',
        labelKey: 'rms.exp.recipe',
        required: true,
        options: recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
      },
      {
        kind: 'select',
        key: 'versionId',
        labelKey: 'rms.exp.version',
        required: true,
        hintKey: 'rms.exp.versionHint',
        options: versions.map((v) => ({
          value: v.id,
          label: `v${v.versionNo} · ${v.status}`,
        })),
      },
      {
        kind: 'datetime',
        key: 'validUntil',
        labelKey: 'rms.exp.validUntil',
        required: true,
        // 后端收 ISO 串再转 Instant；界面显示照旧是本地时间
        valueFormat: 'YYYY-MM-DDTHH:mm',
      },
      { kind: 'textarea', key: 'reason', labelKey: 'rms.exp.reason', span: 2, rows: 2 },
    ],
    [devices, recipes, versions],
  );

  async function submit() {
    const next: Partial<Record<keyof CreateForm, string>> = {};
    if (!form.deviceId) next.deviceId = t('rms.common.required');
    if (!form.recipeId) next.recipeId = t('rms.common.required');
    if (!form.versionId) next.versionId = t('rms.common.required');
    if (!form.validUntil) next.validUntil = t('rms.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      await api.createExperiment({
        recipeId: form.recipeId,
        versionId: form.versionId,
        deviceId: form.deviceId,
        reason: form.reason || undefined,
        validUntil: new Date(form.validUntil).toISOString(),
      });
      onCreated();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.exp.create"
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={640}
    >
      <FormGrid<CreateForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      <p className="muted" style={{ marginTop: 10 }}>
        {t('rms.exp.hint')}
      </p>
      {err ? (
        <p className="cim-field-error" style={{ marginTop: 6 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}

function ActionModal({
  kind,
  onClose,
  onSubmit,
}: {
  kind: PendingAction['kind'];
  onClose: () => void;
  onSubmit: (text: string) => void;
}) {
  const { t } = useTranslation();
  const [text, setText] = useState('');

  const fields: Array<FormField<{ text: string }>> = [
    {
      kind: 'textarea',
      key: 'text',
      labelKey: kind === 'close' ? 'rms.exp.closeReason' : 'rms.exp.comment',
      span: 1,
      rows: 3,
    },
  ];

  return (
    <CimModal
      open
      onClose={onClose}
      title={
        kind === 'approve'
          ? t('rms.exp.approve')
          : kind === 'reject'
            ? t('rms.exp.reject')
            : t('rms.exp.close')
      }
      onOk={() => onSubmit(text)}
      okKey="rms.common.submit"
      cancelKey="rms.common.cancel"
      okDanger={kind === 'reject'}
      width={480}
    >
      <FormGrid<{ text: string }>
        fields={fields}
        value={{ text }}
        onChange={(patch) => setText(patch.text ?? '')}
        columns={1}
      />
    </CimModal>
  );
}
