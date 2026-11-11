import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimDescriptions,
  CimDrawer,
  CimInput,
  CimModal,
  CimSelect,
  CimTag,
  DataTable,
  EmptyState,
  FormGrid,
  Page,
  PageHead,
  Panel,
  Toolbar,
  useConfirm,
  useToast,
  type DataTableColumn,
  type DescItem,
  type FormField,
  CimButton,
  InlineAlert,
  KpiCard,
  CimField,
  StatusTag,
} from '@cim/ui';
import DiffTable from '@/components/DiffTable';
import * as api from '@/lib/rmsApi';
import type {
  BatchResult,
  Recipe,
  RecipeVersion,
  SignRoute,
  Signoff,
  SignoffDetail,
  SignoffSummary,
} from '@/types';

const NO_ROWS: Signoff[] = [];
const NO_RECIPES: Recipe[] = [];
const NO_VERSIONS: RecipeVersion[] = [];
const NO_BATCH: BatchResult[] = [];

const STATUS_OPTIONS = [
  { value: 'PENDING', labelKey: 'rms.signoff.pending' },
  { value: 'APPROVED', labelKey: 'rms.signoff.approved' },
  { value: 'REJECTED', labelKey: 'rms.signoff.rejected' },
];

const ROUTE_OPTIONS = [
  { value: 'SINGLE', labelKey: 'rms.signoff.route.SINGLE' },
  { value: 'COUNTERSIGN', labelKey: 'rms.signoff.route.COUNTERSIGN' },
  { value: 'ANY', labelKey: 'rms.signoff.route.ANY' },
];

/**
 * 签核流（Req.md FR-L2/L4/L5、Req 3/20/41）：待办 KPI + 审批队列 + 批量决策 +
 * 预设限时使用 + 详情抽屉（参数级差异附件 / 事件时间线 / 电子签证据链）。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>行选择改用 `DataTable` 的受控 selection（`selectable` 只放行 PENDING），
 *       旧的「全选」是表头一个 checkbox 手算，筛选后点它会选到看不见的行。</li>
 *   <li>批量决策保留**逐条独立事务**语义：结果不再只弹一次汇总框，成功后还会把
 *       失败条数用 toast 说出来（原来只在弹窗里，关掉就看不到）。</li>
 *   <li>四个弹窗 → `CimModal` + `FormGrid`；时间线保留 `.timeline` 私有样式
 *       （事件流不是步骤条，`CimSteps` 的「current 之前都算完成」语义会失真）。</li>
 * </ul>
 */
export default function SignoffPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [status, setStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const kw = useDebounced(keyword, 250);
  const [selected, setSelected] = useState<string[]>([]);
  const [showSubmit, setShowSubmit] = useState(false);
  const [decision, setDecision] = useState<{ row: Signoff; kind: 'APPROVE' | 'REJECT' } | null>(null);
  const [tempRow, setTempRow] = useState<Signoff | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [batchResults, setBatchResults] = useState<BatchResult[] | null>(null);

  const listRes = useResource(
    () =>
      api.listSignoffs({
        ...(status ? { status } : {}),
        ...(kw.trim() ? { keyword: kw.trim() } : {}),
      }),
    { deps: [status, kw], keepPreviousData: true },
  );
  const summaryRes = useResource(() => api.getSignoffSummary());

  const rows = listRes.data ?? NO_ROWS;
  const summary: SignoffSummary | undefined = summaryRes.data;

  /** 数据变化后：刷新列表与 KPI，并清掉已不在当前列表里的选择。 */
  const reload = () => {
    listRes.refresh();
    summaryRes.refresh();
    setSelected([]);
  };

  const doBatch = async (kind: 'APPROVE' | 'REJECT') => {
    if (!selected.length) return;
    const ok = await confirm({
      titleKey: kind === 'APPROVE' ? 'rms.signoff.batchApprove' : 'rms.signoff.batchReject',
      contentKey: 'rms.signoff.batchConfirm',
      danger: kind === 'REJECT',
      okKey: 'rms.common.confirm',
    });
    if (!ok) return;
    try {
      const res = await api.batchDecision(selected, kind, undefined);
      setBatchResults(res);
      const failed = res.filter((r) => !r.ok).length;
      if (failed) toast.warning(t('rms.signoff.batchPartial', { n: failed }));
      else toast.success(t('rms.signoff.batchAllOk', { n: res.length }));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    reload();
  };

  const cancelTemp = async (row: Signoff) => {
    const ok = await confirm({
      titleKey: 'rms.signoff.cancelTemp',
      contentKey: 'rms.signoff.cancelTempConfirm',
      danger: true,
      okKey: 'rms.signoff.cancelTemp',
    });
    if (!ok) return;
    try {
      await api.cancelTempAllow(row.id);
      toast.success(t('rms.common.updated'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    reload();
  };

  const columns = useMemo<Array<DataTableColumn<Signoff>>>(
    () => [
      {
        key: 'recipeCode',
        titleKey: 'rms.recipe.title',
        width: 180,
        render: (r) => (
          <>
            <span className="cim-mono">{r.recipeCode ?? t('rms.common.dash')}</span>
            {r.recipeName ? (
              <div className="dim" style={{ fontSize: 11 }}>
                {r.recipeName}
              </div>
            ) : null}
          </>
        ),
      },
      {
        key: 'versionNo',
        titleKey: 'rms.recipe.version',
        mono: true,
        width: 80,
        render: (r) => `v${r.versionNo ?? '-'}`,
      },
      {
        key: 'reason',
        titleKey: 'rms.signoff.reason',
        ellipsis: true,
        render: (r) => <span className="dim">{r.reason ?? t('rms.signoff.noReason')}</span>,
      },
      {
        key: 'diffCount',
        titleKey: 'rms.signoff.diffCount',
        width: 110,
        render: (r) =>
          r.diffCount > 0 ? (
            <CimTag size="small" tone="warn">
              {t('rms.signoff.diffUnit', { n: r.diffCount })}
            </CimTag>
          ) : (
            <span className="dim">{t('rms.common.dash')}</span>
          ),
      },
      {
        key: 'status',
        titleKey: 'rms.common.status',
        width: 100,
        render: (r) => (
          <StatusTag
            size="small"
            dict="signoffState"
            value={r.status}
            label={t('rms.signoff.' + r.status.toLowerCase(), { defaultValue: r.status })}
          />
        ),
      },
      {
        key: 'tempAllowStatus',
        titleKey: 'rms.signoff.temp',
        width: 110,
        render: (r) =>
          r.tempAllowStatus !== 'NONE' ? (
            <StatusTag
            size="small"
            dict="tempReleaseState"
            value={r.tempAllowStatus}
            label={t('rms.signoff.tempStatus.' + r.tempAllowStatus, { defaultValue: r.tempAllowStatus })}
          />
          ) : (
            <span className="dim">{t('rms.common.dash')}</span>
          ),
      },
      {
        key: 'initiator',
        titleKey: 'rms.signoff.initiator',
        mono: true,
        width: 110,
        render: (r) => r.initiator ?? t('rms.common.dash'),
      },
      {
        key: 'approver',
        titleKey: 'rms.signoff.approver',
        mono: true,
        width: 110,
        render: (r) => r.approver ?? t('rms.common.dash'),
      },
      {
        key: 'createTime',
        titleKey: 'rms.audit.th.time',
        width: 160,
        render: (r) => <span className="cim-mono dim">{r.createTime ?? t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 300,
        render: (r) => (
          <span className="cell-actions">
            <CimButton size="small" onClick={() => setDetailId(r.id)}>
              {t('rms.signoff.detail')}
            </CimButton>
            {r.status === 'PENDING' ? (
              <Can perm="rms:signoff:approve">
                <CimButton
                  size="small"
                  onClick={() => setDecision({ row: r, kind: 'APPROVE' })}
                >
                  {t('rms.signoff.approve')}
                </CimButton>
                <CimButton
                  size="small"
                  onClick={() => setDecision({ row: r, kind: 'REJECT' })}
                >
                  {t('rms.signoff.reject')}
                </CimButton>
                {r.tempAllowStatus === 'NONE' || r.tempAllowStatus === 'EXPIRED' ? (
                  <CimButton size="small" onClick={() => setTempRow(r)}>
                    {t('rms.signoff.temp')}
                  </CimButton>
                ) : (
                  <CimButton size="small" onClick={() => void cancelTemp(r)}>
                    {t('rms.signoff.cancelTemp')}
                  </CimButton>
                )}
              </Can>
            ) : null}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.signoff.title"
        subtitleKey="rms.signoff.sub"
        actions={
          <Can perm="rms:signoff:approve">
            <CimButton type="primary" size="small" onClick={() => setShowSubmit(true)}>
              + {t('rms.signoff.submit')}
            </CimButton>
          </Can>
        }
      />

      {/* KPI 指标块 */}
      <div className="kpi-grid">
        <KpiCard label={t('rms.signoff.kpi.pending')} value={summary?.pending ?? '—'} />
        <KpiCard tone="ok" label={t('rms.signoff.kpi.approvedToday')} value={summary?.approvedToday ?? '—'} />
        <KpiCard label={t('rms.signoff.kpi.rejectedToday')} value={summary?.rejectedToday ?? '—'} />
        <KpiCard tone="info" label={t('rms.signoff.kpi.tempActive')} value={summary?.tempActive ?? '—'} />
        <KpiCard tone="warn" label={t('rms.signoff.kpi.expiringSoon')} value={summary?.expiringSoon ?? '—'} />
      </div>

      {/* 批量操作条：只在有选中时出现 */}
      {selected.length > 0 ? (
        <Panel flush>
          <Toolbar
            left={
              <>
                <CimTag tone="run">{t('rms.signoff.selectedN', { n: selected.length })}</CimTag>
                <Can perm="rms:signoff:approve">
                  <CimButton type="primary" size="small" onClick={() => void doBatch('APPROVE')}>
                    {t('rms.signoff.batchApprove')}
                  </CimButton>
                  <CimButton size="small" onClick={() => void doBatch('REJECT')}>
                    {t('rms.signoff.batchReject')}
                  </CimButton>
                </Can>
                <CimButton size="small" onClick={() => setSelected([])}>
                  {t('rms.signoff.clearSelection')}
                </CimButton>
              </>
            }
          />
        </Panel>
      ) : null}

      <DataTable<Signoff>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={listRes.loading}
        error={listRes.error?.message}
        onRetry={reload}
        onRefresh={reload}
        settingsKey="rms.signoff"
        scrollX="max-content"
        emptyTitleKey="rms.signoff.empty"
        emptyHintKey="rms.signoff.emptyHint"
        selectedKeys={selected}
        onSelectionChange={setSelected}
        selectable={(r) => r.status === 'PENDING'}
        actions={
          <>
            <CimSelect
              value={status}
              onChange={(v) => setStatus(typeof v === 'string' ? v : '')}
              options={STATUS_OPTIONS}
              placeholderKey="rms.signoff.allStatus"
              width={150}
            />
            <CimInput
              value={keyword}
              onChange={setKeyword}
              placeholderKey="rms.signoff.searchPh"
              width={200}
            />
          </>
        }
      />

      {showSubmit ? (
        <SubmitModal
          onClose={() => setShowSubmit(false)}
          onDone={() => {
            setShowSubmit(false);
            toast.success(t('rms.signoff.submitted'));
            reload();
          }}
        />
      ) : null}

      {decision ? (
        <DecisionModal
          row={decision.row}
          kind={decision.kind}
          onClose={() => setDecision(null)}
          onDone={() => {
            setDecision(null);
            toast.success(t('rms.common.updated'));
            reload();
          }}
        />
      ) : null}

      {tempRow ? (
        <TempAllowModal
          row={tempRow}
          onClose={() => setTempRow(null)}
          onDone={() => {
            setTempRow(null);
            toast.success(t('rms.common.updated'));
            reload();
          }}
        />
      ) : null}

      <DetailDrawer id={detailId} onClose={() => setDetailId(null)} />

      {batchResults ? <BatchResultModal results={batchResults} onClose={() => setBatchResults(null)} /> : null}
    </Page>
  );
}

/** 发起签核：选配方 → 选 DRAFT 版本 → 填原因/路由。 */
interface SubmitForm {
  recipeId: string;
  versionId: string;
  reason: string;
  signRoute: SignRoute;
}

function SubmitModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<SubmitForm>({ recipeId: '', versionId: '', reason: '', signRoute: 'SINGLE' });
  const [busy, setBusy] = useState(false);

  const recipesRes = useResource(() => api.listRecipes());
  const versionsRes = useResource(() => api.listVersions(form.recipeId), {
    deps: [form.recipeId],
    enabled: !!form.recipeId,
  });

  const recipes = recipesRes.data ?? NO_RECIPES;
  const drafts = (versionsRes.data ?? NO_VERSIONS).filter((v) => v.status === 'DRAFT');

  const fields = useMemo<Array<FormField<SubmitForm>>>(
    () => [
      {
        key: 'recipeId',
        labelKey: 'rms.recipe.title',
        kind: 'select',
        required: true,
        options: recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
        placeholderKey: 'rms.signoff.pickRecipe',
      },
      {
        key: 'versionId',
        labelKey: 'rms.signoff.draftVersion',
        kind: 'select',
        required: true,
        disabled: !form.recipeId,
        options: drafts.map((v) => ({
          value: v.id,
          label: `v${v.versionNo}`,
          hint: v.changeSummary ?? undefined,
        })),
        placeholderKey: 'rms.signoff.pickVersion',
        hintKey: form.recipeId && drafts.length === 0 ? 'rms.signoff.noDraft' : undefined,
      },
      {
        key: 'reason',
        labelKey: 'rms.signoff.reason',
        kind: 'textarea',
        rows: 3,
        required: true,
        span: 2,
        placeholderKey: 'rms.signoff.reasonPh',
      },
      {
        key: 'signRoute',
        labelKey: 'rms.signoff.route',
        kind: 'select',
        options: ROUTE_OPTIONS,
        allowClear: false,
        hintKey: 'rms.signoff.routeHint',
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [recipes, drafts, form.recipeId, t],
  );

  const submit = async () => {
    if (!form.recipeId || !form.versionId) {
      toast.warning(t('rms.signoff.pickRequired'));
      return;
    }
    if (!form.reason.trim()) {
      toast.warning(t('rms.signoff.reasonRequired'));
      return;
    }
    setBusy(true);
    try {
      await api.submitSignoff({
        recipeId: form.recipeId,
        versionId: form.versionId,
        reason: form.reason.trim(),
        signRoute: form.signRoute,
      });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onDone();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.signoff.submit"
      width={680}
      onOk={submit}
      okLoading={busy}
      okKey="rms.common.confirm"
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      <FormGrid<SubmitForm>
        fields={fields}
        value={form}
        onChange={(p) => {
          // 切换配方必须清空版本，否则会把 A 的版本 id 提交到 B 上（后端报「版本不属于该配方」）
          setForm((prev) => ({
            ...prev,
            ...p,
            ...(p.recipeId !== undefined ? { versionId: '' } : {}),
          }));
        }}
        columns={2}
        disabled={busy}
      />
      <InlineAlert tone="info" style={{ marginTop: 10 }}>{t('rms.signoff.diffWillGenerate')}</InlineAlert>
    </CimModal>
  );
}

/** 审批决定：通过 / 拒绝 + 意见。 */
function DecisionModal({
  row,
  kind,
  onClose,
  onDone,
}: {
  row: Signoff;
  kind: 'APPROVE' | 'REJECT';
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    try {
      if (kind === 'APPROVE') await api.approveSignoff(row.id, comment || undefined);
      else await api.rejectSignoff(row.id, comment || undefined);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onDone();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      title={`${kind === 'APPROVE' ? t('rms.signoff.approve') : t('rms.signoff.reject')} · ${row.recipeCode} v${row.versionNo}`}
      width={620}
      onOk={submit}
      okLoading={busy}
      okDanger={kind === 'REJECT'}
      okKey="rms.common.confirm"
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      {row.reason ? (
        <CimField label={t('rms.signoff.reason')}>
          <div className="dim">{row.reason}</div>
        </CimField>
      ) : null}
      <CimField label={t('rms.signoff.comment')} style={{ marginTop: 10 }}>
        <CimInput rows={3} value={comment} onChange={setComment} />
      </CimField>
      <InlineAlert tone="warn" style={{ marginTop: 10 }}>{kind === 'APPROVE' ? t('rms.signoff.approveHint') : t('rms.signoff.decisionHint')}</InlineAlert>
    </CimModal>
  );
}

/** 预设限时使用（Req 20）。 */
interface TempForm {
  from: string;
  to: string;
  scope: string;
  reason: string;
}

/** Date → datetime-local 输入值。 */
function toLocalInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

const TEMP_FORMAT = 'YYYY-MM-DDTHH:mm';

function TempAllowModal({ row, onClose, onDone }: { row: Signoff; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const now = new Date();
  const [form, setForm] = useState<TempForm>({
    from: toLocalInput(now),
    to: toLocalInput(new Date(now.getTime() + 4 * 3600_000)),
    scope: '',
    reason: '',
  });
  const [busy, setBusy] = useState(false);

  const fields: Array<FormField<TempForm>> = [
    {
      key: 'from',
      labelKey: 'rms.signoff.tempDialog.from',
      kind: 'datetime',
      valueFormat: TEMP_FORMAT,
      displayFormat: 'YYYY-MM-DD HH:mm',
    },
    {
      key: 'to',
      labelKey: 'rms.signoff.tempDialog.to',
      kind: 'datetime',
      required: true,
      valueFormat: TEMP_FORMAT,
      displayFormat: 'YYYY-MM-DD HH:mm',
    },
    {
      key: 'scope',
      labelKey: 'rms.signoff.tempDialog.scope',
      kind: 'text',
      span: 2,
      placeholderKey: 'rms.signoff.tempDialog.scopePh',
    },
    {
      key: 'reason',
      labelKey: 'rms.signoff.tempDialog.reason',
      kind: 'text',
      required: true,
      span: 2,
      placeholderKey: 'rms.signoff.tempDialog.reasonPh',
    },
  ];

  const submit = async () => {
    if (!form.to) {
      toast.warning(t('rms.signoff.tempDialog.toRequired'));
      return;
    }
    if (!form.reason.trim()) {
      toast.warning(t('rms.signoff.tempDialog.reasonRequired'));
      return;
    }
    setBusy(true);
    try {
      await api.presetTempAllow(row.id, {
        from: form.from || null,
        to: form.to,
        scope: form.scope || null,
        reason: form.reason.trim(),
      });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onDone();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      title={`${t('rms.signoff.tempDialog.title')} · ${row.recipeCode} v${row.versionNo}`}
      width={680}
      onOk={submit}
      okLoading={busy}
      okKey="rms.common.confirm"
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      <InlineAlert tone="info" style={{ marginBottom: 10 }}>{t('rms.signoff.tempDialog.hint')}</InlineAlert>
      <FormGrid<TempForm>
        fields={fields}
        value={form}
        onChange={(p) => setForm((prev) => ({ ...prev, ...p }))}
        columns={2}
        disabled={busy}
      />
    </CimModal>
  );
}

/** 批量决策逐项结果（Req 3 部分成功语义）。 */
function BatchResultModal({ results, onClose }: { results: BatchResult[]; onClose: () => void }) {
  const { t } = useTranslation();
  const ok = results.filter((r) => r.ok).length;
  const fail = results.length - ok;

  const columns = useMemo<Array<DataTableColumn<BatchResult>>>(
    () => [
      {
        key: 'ok',
        titleKey: 'rms.common.status',
        width: 110,
        render: (r) => (
          <CimTag size="small" tone={r.ok ? 'ok' : 'err'}>
            {r.ok ? t('rms.signoff.batchOk') : t('rms.signoff.batchFail')}
          </CimTag>
        ),
      },
      {
        key: 'message',
        titleKey: 'rms.audit.th.detail',
        render: (r) => <span className="dim">{r.message ?? r.id}</span>,
      },
    ],
    [t],
  );

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.signoff.batchResultTitle"
      width={680}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <CimButton type="primary" onClick={onClose}>
            {t('rms.common.close')}
          </CimButton>
        </div>
      }
    >
      <div className="kpi-grid" style={{ marginBottom: 10 }}>
        <KpiCard tone="ok" label={t('rms.signoff.batchOk')} value={ok} />
        <KpiCard tone="warn" label={t('rms.signoff.batchFail')} value={fail} />
      </div>
      <DataTable<BatchResult>
        flat
        columns={columns}
        rows={results.length ? results : NO_BATCH}
        rowKey={(r) => r.id}
        scrollX="max-content"
        showCount={false}
      />
    </CimModal>
  );
}

/** 签核单详情抽屉：基本信息 + 参数级差异附件 + 事件时间线 + 电子签证据链。 */
function DetailDrawer({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { t } = useTranslation();

  const { data: detail, loading, error } = useResource<SignoffDetail>(
    () => api.getSignoff(id as string),
    { deps: [id], enabled: !!id },
  );

  const items = useMemo<Array<DescItem<SignoffDetail>>>(
    () => [
      {
        key: 'recipe',
        labelKey: 'rms.recipe.title',
        render: (d) => (
          <span className="cim-mono">
            {d.recipeCode} · {d.recipeName}
          </span>
        ),
      },
      {
        key: 'version',
        labelKey: 'rms.recipe.version',
        render: (d) => <span className="cim-mono">v{d.versionNo}</span>,
      },
      {
        key: 'status',
        labelKey: 'rms.common.status',
        render: (d) => (
          <StatusTag
            size="small"
            dict="signoffState"
            value={d.status}
            label={t('rms.signoff.' + d.status.toLowerCase(), { defaultValue: d.status })}
          />
        ),
      },
      {
        key: 'route',
        labelKey: 'rms.signoff.route',
        render: (d) => t('rms.signoff.route.' + d.signRoute, { defaultValue: d.signRoute }),
      },
      {
        key: 'reason',
        labelKey: 'rms.signoff.reason',
        span: 2,
        render: (d) => d.reason ?? t('rms.signoff.noReason'),
      },
      {
        key: 'initiator',
        labelKey: 'rms.signoff.initiator',
        render: (d) => (
          <span className="cim-mono">
            {d.initiator ?? t('rms.common.dash')} · {d.createTime ?? ''}
          </span>
        ),
      },
      {
        key: 'approver',
        labelKey: 'rms.signoff.approver',
        render: (d) =>
          d.approver ? (
            <span className="cim-mono">
              {d.approver} · {d.decidedTime ?? ''}
            </span>
          ) : (
            t('rms.common.dash')
          ),
      },
      {
        key: 'comment',
        labelKey: 'rms.signoff.comment',
        span: 2,
        render: (d) => d.decisionComment ?? t('rms.common.dash'),
      },
      {
        key: 'temp',
        labelKey: 'rms.signoff.temp',
        span: 3,
        render: (d) => (
          <>
            <StatusTag
            size="small"
            dict="tempReleaseState"
            value={d.tempAllowStatus}
            label={t('rms.signoff.tempStatus.' + d.tempAllowStatus, { defaultValue: d.tempAllowStatus })}
          />
            {d.tempAllowTo ? (
              <span className="dim"> · {t('rms.signoff.tempUntil', { t: d.tempAllowTo })}</span>
            ) : null}
            {d.tempAllowScope ? <div className="dim">{d.tempAllowScope}</div> : null}
            {d.tempAllowReason ? <div className="dim">{d.tempAllowReason}</div> : null}
          </>
        ),
      },
      {
        key: 'eSign',
        labelKey: 'rms.signoff.eSign',
        span: 3,
        render: (d) =>
          d.eSignHash ? (
            <span className="cim-mono" style={{ wordBreak: 'break-all', fontSize: 11 }}>
              {d.eSignHash}
            </span>
          ) : (
            t('rms.common.dash')
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const changed = (detail?.paramDiff ?? []).filter((d) => d.changed).length;

  return (
    <CimDrawer open={!!id} onClose={onClose} titleKey="rms.signoff.detail.title" width={860}>
      {error ? (
        <InlineAlert tone="err">{error.message}</InlineAlert>
      ) : null}
      {loading && !detail ? <div className="dim">{t('rms.common.loading')}</div> : null}
      {detail ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <CimDescriptions record={detail} items={items} column={3} />

          <Panel titleKey="rms.signoff.paramDiff" flush>
            {detail.paramDiff.length === 0 ? (
              <div style={{ padding: 14 }}>
                <EmptyState titleKey="rms.signoff.diffNone" />
              </div>
            ) : (
              <>
                <DiffTable items={detail.paramDiff} showCategory={false} />
                <div className="dim" style={{ padding: '4px 14px 8px', fontSize: 11 }}>
                  {changed} / {detail.paramDiff.length}
                </div>
              </>
            )}
          </Panel>

          <Panel titleKey="rms.signoff.timeline">
            <div className="timeline">
              {detail.timeline.map((e, i) => (
                <div className="timeline-item" key={e.eventType + i}>
                  <span
                    className={
                      'timeline-dot ' +
                      (e.eventType === 'APPROVED'
                        ? 'ok'
                        : e.eventType === 'REJECTED' || e.eventType === 'TEMP_EXPIRED'
                          ? 'err'
                          : e.eventType === 'TEMP_ALLOWED' || e.eventType === 'TEMP_CANCELLED'
                            ? 'warn'
                            : '')
                    }
                  />
                  <div className="timeline-title">
                    {t('rms.signoff.event.' + e.eventType, { defaultValue: e.eventType })}
                    {e.actor ? (
                      <span className="dim mono" style={{ fontWeight: 400 }}>
                        {' '}
                        · {e.actor}
                      </span>
                    ) : null}
                  </div>
                  <div className="timeline-time">{e.occurredAt ?? ''}</div>
                  {e.detail || e.comment ? <div className="timeline-detail">{e.detail ?? e.comment}</div> : null}
                </div>
              ))}
            </div>
          </Panel>
        </div>
      ) : null}
    </CimDrawer>
  );
}
