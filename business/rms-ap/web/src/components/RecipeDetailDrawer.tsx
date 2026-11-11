import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimDescriptions,
  CimDrawer,
  CimModal,
  CimSelect,
  CimTag,
  DataTable,
  EmptyState,
  FormGrid,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type DescItem,
  type FormField,
  CimButton,
  InlineAlert,
  CimField,
  CimInput,
} from '@cim/ui';
import SpecEditModal from '@/components/SpecEditModal';
import * as api from '@/lib/rmsApi';
import type {
  BodyFormat,
  ChangeEffect,
  Device,
  DeviceChamber,
  ParamSpec,
  RecipeParsedView,
  RecipeToolUsage,
  RecipeValidationResult,
  RecipeVersion,
  SignRoute,
} from '@/types';

/** UTF-8 安全的文本 → Base64。 */
function utf8ToBase64(s: string): string {
  return btoa(unescape(encodeURIComponent(s)));
}

/** Base64 → UTF-8 文本。 */
function base64ToUtf8(b64: string): string {
  try {
    return decodeURIComponent(escape(atob(b64)));
  } catch {
    return b64;
  }
}

/** HASH 缩略。 */
function shortHash(h: string | null | undefined): string {
  if (!h) return '—';
  return h.length <= 20 ? h : `${h.slice(0, 8)}…${h.slice(-8)}`;
}

/** 版本状态 → 语义档（与 @cim/theme 的 recipeState 口径一致）。 */
function statusTone(status: string | undefined) {
  if (status === 'ACTIVE') return 'ok' as const;
  if (status === 'PENDING_SIGN') return 'warn' as const;
  if (status === 'DRAFT') return 'run' as const;
  return 'idle' as const;
}

/** 规格值摘要（与规格参数页同规则）。 */
function specValueText(x: ParamSpec): string {
  switch (x.specType) {
    case 'RANGE':
      return `[${x.rangeMin ?? '-'}, ${x.rangeMax ?? '-'}]`;
    case 'LIST_IN':
      return `{${x.listValues ?? '-'}}`;
    case 'TOLERANCE':
      return `${x.targetValue ?? '-'} ±${x.tolerancePct ?? '-'}% / ${x.toleranceAbs ?? '-'}`;
    default:
      return x.targetValue ?? '-';
  }
}

const NO_VERSIONS: RecipeVersion[] = [];
const NO_SPECS: ParamSpec[] = [];
const NO_USAGES: RecipeToolUsage[] = [];
const NO_DEVICES: Device[] = [];
const NO_CHAMBERS: DeviceChamber[] = [];
const NO_EFFECTS: ChangeEffect[] = [];

interface Props {
  recipeId: string;
  recipeCode?: string | null;
  recipeName?: string | null;
  /** 版本列表（版本号倒序）；抽屉内可切换版本。 */
  versions: RecipeVersion[];
  /** 初始展示的版本（通常取生效版）。 */
  initialVersionId?: string | null;
  onClose: () => void;
  /** 编辑成功后回调（刷新外层列表）。 */
  onChanged?: () => void;
}

/**
 * 配方详情抽屉（W7）。
 *
 * <p>回答「这个配方里到底是什么」：结构化参数 / 步序 / 子配方 + 良构可执行校验 +
 * Body 原文，并支持对 <b>DRAFT</b> 版本直接编辑（已生效版本不可变，须另存新版本）。</p>
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>手写 `.drawer-backdrop` → `CimDrawer`；四处 `window.confirm` → `useConfirm`。</li>
 *   <li>解析 / 校验 / 规格 / 机台 / 变更效果五个区块各自 `useResource`：
 *       切版本时 `parsed` 与 `validation` 并发取，旧实现的串行 `.then` 会在
 *       快速切版本时把 A 的校验结果画到 B 的版本上。</li>
 *   <li>各区块表格 → `DataTable flat`；错误从页内红条改为 toast（红条不会消失，
 *       下一次操作前一直挂着，看起来像当前状态）。</li>
 * </ul>
 */
export default function RecipeDetailDrawer({
  recipeId,
  recipeCode,
  recipeName,
  versions,
  initialVersionId,
  onClose,
  onChanged,
}: Props) {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [versionId, setVersionId] = useState<string>(initialVersionId || versions[0]?.id || '');
  const [bodyText, setBodyText] = useState('');
  const [showRaw, setShowRaw] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [showSignoff, setShowSignoff] = useState(false);
  const [busyExport, setBusyExport] = useState(false);

  useEffect(() => {
    setVersionId(initialVersionId || versions[0]?.id || '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipeId, initialVersionId]);

  const current = versions.find((x) => x.id === versionId) ?? null;
  const editable = current?.status === 'DRAFT';

  const parsedRes = useResource(() => api.parsedVersion(recipeId, versionId), {
    deps: [recipeId, versionId],
    enabled: !!versionId,
    keepPreviousData: true,
  });
  const validationRes = useResource(() => api.validateVersion(recipeId, versionId), {
    deps: [recipeId, versionId],
    enabled: !!versionId,
    keepPreviousData: true,
  });

  const parsed: RecipeParsedView | undefined = parsedRes.data;
  const validation: RecipeValidationResult | undefined = validationRes.data;
  const p = parsed?.parsed ?? null;

  const loadRaw = async () => {
    if (bodyText) {
      setShowRaw(!showRaw);
      return;
    }
    try {
      const b = await api.getVersionBody(versionId);
      setBodyText(b.bodyBase64 ? base64ToUtf8(b.bodyBase64) : '');
      setShowRaw(true);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const startEdit = async () => {
    setEditing(true);
    if (bodyText) {
      setDraft(bodyText);
      return;
    }
    try {
      const b = await api.getVersionBody(versionId);
      const txt = b.bodyBase64 ? base64ToUtf8(b.bodyBase64) : '';
      setBodyText(txt);
      setDraft(txt);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const save = async () => {
    if (!current) return;
    setBusy(true);
    try {
      // 乐观锁传「库中旧 hash」（后端先比对旧值再覆盖）；sha256Hex(draft) 是新值语义，勿混用
      await api.updateVersionBody(recipeId, versionId, {
        bodyFormat: current.bodyFormat as BodyFormat,
        bodyBase64: utf8ToBase64(draft),
        expectedBodyHash: current.bodyHash,
        changeSummary: t('rms.recipe.detail.edited'),
      });
      setBodyText(draft);
      setEditing(false);
      toast.success(t('rms.recipe.detail.saved'));
      parsedRes.refresh();
      validationRes.refresh();
      onChanged?.();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  /**
   * 另存新版本（生命周期闭环的关键一环）：
   * ACTIVE/OBSOLETE 版本不可变，以当前内容建新 DRAFT 并切换过去——
   * 用户在抽屉内即可开始下一轮「编辑 → 发起签核」，无需跳回配方库页。
   */
  const saveAsNewVersion = async () => {
    if (!current) return;
    const ok = await confirm({
      titleKey: 'rms.recipe.detail.saveAsNew',
      contentKey: 'rms.recipe.detail.saveAsNewConfirm',
      okKey: 'rms.recipe.detail.saveAsNew',
    });
    if (!ok) return;
    setBusy(true);
    try {
      let txt = bodyText;
      if (!txt) {
        const b = await api.getVersionBody(versionId);
        txt = b.bodyBase64 ? base64ToUtf8(b.bodyBase64) : '';
        setBodyText(txt);
      }
      const v = await api.newVersion(recipeId, {
        bodyFormat: current.bodyFormat,
        body: txt,
        expectedBodyHash: current.bodyHash,
        remark: t('rms.recipe.detail.saveAsNewRemark', { n: current.versionNo }),
      });
      toast.success(t('rms.recipe.detail.saveAsNewDone', { n: v.versionNo }));
      setVersionId(v.id);
      setEditing(false);
      setBodyText('');
      setShowRaw(false);
      onChanged?.();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const doExport = async () => {
    setBusyExport(true);
    try {
      const blob = await api.exportRecipes(recipeId ? [recipeId] : undefined);
      api.saveBlob(blob, `${recipeCode || 'recipe'}-export.json`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusyExport(false);
    }
  };

  const headerItems = useMemo<Array<DescItem<RecipeVersion>>>(
    () => [
      {
        key: 'versionNo',
        labelKey: 'rms.recipe.version',
        render: (v) => (
          <>
            <span className="cim-mono">v{v.versionNo}</span>{' '}
            <CimTag size="small" tone={statusTone(v.status)}>
              {t('rms.recipe.status.' + v.status, { defaultValue: v.status })}
            </CimTag>
          </>
        ),
      },
      {
        key: 'bodyFormat',
        labelKey: 'rms.recipe.bodyFormat',
        render: (v) => <CimTag size="small">{v.bodyFormat}</CimTag>,
      },
      {
        key: 'bodyHash',
        labelKey: 'rms.recipe.detail.bodyHash',
        render: (v) => <span className="cim-mono muted">{shortHash(v.bodyHash)}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const paramColumns = useMemo<Array<DataTableColumn<NonNullable<typeof p>['params'][number]>>>(
    () => [
      { key: 'key', titleKey: 'rms.recipe.detail.key', mono: true, width: 190 },
      {
        key: 'name',
        titleKey: 'rms.recipe.detail.name',
        width: 160,
        render: (x) => x.name ?? '—',
      },
      {
        key: 'value',
        titleKey: 'rms.recipe.detail.value',
        mono: true,
        width: 140,
        render: (x) => x.value ?? '—',
      },
      {
        key: 'unit',
        titleKey: 'rms.recipe.detail.unit',
        width: 80,
        render: (x) => <span className="muted">{x.unit ?? '—'}</span>,
      },
      {
        key: 'range',
        titleKey: 'rms.recipe.detail.range',
        width: 160,
        render: (x) => (
          <span className="muted">
            {x.min || x.max ? `${x.min ?? ''} ~ ${x.max ?? ''}` : '—'}
          </span>
        ),
      },
    ],
    [t],
  );

  const stepColumns = useMemo<Array<DataTableColumn<NonNullable<typeof p>['steps'][number]>>>(
    () => [
      { key: 'stepNo', titleKey: 'rms.recipe.detail.stepNo', mono: true, width: 80 },
      { key: 'name', titleKey: 'rms.recipe.detail.name', render: (s) => s.name ?? '—' },
      {
        key: 'params',
        titleKey: 'rms.recipe.detail.params',
        render: (s) => <span className="cim-mono muted">{s.params.join(', ') || '—'}</span>,
      },
    ],
    [t],
  );

  const subColumns = useMemo<Array<DataTableColumn<NonNullable<typeof p>['subRecipes'][number]>>>(
    () => [
      { key: 'name', titleKey: 'rms.recipe.detail.name', render: (s) => s.name ?? '—' },
      {
        key: 'ref',
        titleKey: 'rms.recipe.detail.ref',
        mono: true,
        render: (s) => s.ref ?? '—',
      },
      {
        key: 'params',
        titleKey: 'rms.recipe.detail.params',
        render: (s) => (
          <span className="cim-mono muted">{s.params.map((x) => x.key).join(', ') || '—'}</span>
        ),
      },
    ],
    [t],
  );

  return (
    <CimDrawer
      open
      onClose={onClose}
      title={`${recipeCode ?? '—'}${recipeName ? ` · ${recipeName}` : ''}`}
      width={920}
      extra={
        <Can perm="rms:body:export">
          <CimButton size="small" disabled={busyExport} onClick={() => void doExport()}>
            {t('rms.recipe.exportOne')}
          </CimButton>
        </Can>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {/* 版本切换 + 动作 */}
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div style={{ width: 240 }}>
            <CimSelect
              value={versionId || null}
              onChange={(v) => {
                setVersionId(typeof v === 'string' ? v : '');
                setEditing(false);
                setBodyText('');
                setShowRaw(false);
              }}
              options={(versions.length ? versions : NO_VERSIONS).map((v) => ({
                value: v.id,
                label: `v${v.versionNo} · ${t('rms.recipe.status.' + v.status, { defaultValue: v.status })}`,
                hint: v.changeSummary ?? undefined,
              }))}
              allowClear={false}
              width="100%"
            />
          </div>
          <div className="cell-actions" style={{ flex: 1 }}>
            <CimButton size="small" onClick={() => void loadRaw()}>
              {showRaw ? t('rms.recipe.detail.hideRaw') : t('rms.recipe.detail.showRaw')}
            </CimButton>
            <Can perm="rms:recipe:version">
              <CimButton
                size="small"
                disabled={!editable || editing}
                title={!editable ? t('rms.recipe.detail.editBlocked') : ''}
                onClick={() => void startEdit()}
              >
                {t('rms.common.edit')}
              </CimButton>
            </Can>
            <Can perm="rms:recipe:update">
              <CimButton
                type="primary"
                size="small"
                disabled={!editable || busy}
                title={!editable ? t('rms.recipe.detail.signoffBlocked') : t('rms.recipe.detail.signoffGo')}
                onClick={() => setShowSignoff(true)}
              >
                {t('rms.recipe.detail.signoffGo')}
              </CimButton>
            </Can>
            {!editable && (
              <Can perm="rms:recipe:version">
                <CimButton size="small" disabled={busy} onClick={() => void saveAsNewVersion()}>
                  {t('rms.recipe.detail.saveAsNew')}
                </CimButton>
              </Can>
            )}
          </div>
        </div>

        {current ? <CimDescriptions record={current} items={headerItems} column={3} /> : null}

        {/* 良构 / 可执行 */}
        {validation ? (
          <InlineAlert tone={(validation.executable ? 'ok' : 'err')}>{validation.wellFormed
                ? t('rms.recipe.detail.wellFormed')
                : t('rms.recipe.detail.notWellFormed')}
              {' · '}
              {validation.executable
                ? t('rms.recipe.detail.executable')
                : t('rms.recipe.detail.notExecutable')}
              {validation.errors.length > 0 ? ` · ${validation.errors.join(' | ')}` : ''}</InlineAlert>
        ) : null}

        {parsed && !parsed.ok ? (
          <InlineAlert tone="warn">{t('rms.recipe.detail.parseFail')}: {parsed.error}</InlineAlert>
        ) : null}

        {/* Body 编辑 */}
        {editing ? (
          <Panel titleKey="rms.recipe.detail.editBody" subKey="rms.recipe.detail.editHint">
            <CimInputArea value={draft} onChange={setDraft} />
            <div className="cell-actions" style={{ marginTop: 10 }}>
              <CimButton type="primary" size="small" disabled={busy} onClick={() => void save()}>
                {t('rms.common.save')}
              </CimButton>
              <CimButton size="small" disabled={busy} onClick={() => setEditing(false)}>
                {t('rms.common.cancel')}
              </CimButton>
            </div>
          </Panel>
        ) : null}

        {/* Meta */}
        {p && p.meta && Object.keys(p.meta).length > 0 ? (
          <Panel titleKey="rms.recipe.detail.meta" flush>
            <table className="data">
              <tbody>
                {Object.entries(p.meta).map(([k, v]) => (
                  <tr key={k}>
                    <td className="mono">{k}</td>
                    <td className="mono">{v}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        ) : null}

        {/* 参数 */}
        <Panel titleKey="rms.recipe.detail.params" flush>
          <DataTable
            flat
            columns={paramColumns}
            rows={p?.params ?? []}
            rowKey={(x) => x.key}
            rowClassName={(x) =>
              (validation?.checks ?? []).some((c) => c.paramKey === x.key) ? 'cim-row-warn' : ''
            }
            scrollX="max-content"
            emptyTitleKey="rms.recipe.detail.noParams"
          />
        </Panel>

        {/* 步序 */}
        {(p?.steps ?? []).length > 0 ? (
          <Panel titleKey="rms.recipe.detail.steps" flush>
            <DataTable flat columns={stepColumns} rows={p?.steps ?? []} rowKey={(s) => String(s.stepNo)} scrollX="max-content" />
          </Panel>
        ) : null}

        {/* 子配方 */}
        {(p?.subRecipes ?? []).length > 0 ? (
          <Panel titleKey="rms.recipe.detail.subRecipes" flush>
            <DataTable
              flat
              columns={subColumns}
              rows={p?.subRecipes ?? []}
              rowKey={(s) => s.ref ?? s.name ?? Math.random().toString(36)}
              scrollX="max-content"
            />
          </Panel>
        ) : null}

        {p && p.warnings && p.warnings.length > 0 ? (
          <InlineAlert tone="warn">{p.warnings.join(' | ')}</InlineAlert>
        ) : null}

        {/* Body 原文 */}
        {showRaw ? (
          <Panel titleKey="rms.recipe.detail.raw">
            <pre className="cim-mono muted" style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
              {bodyText || t('rms.common.noData')}
            </pre>
          </Panel>
        ) : null}

        <SpecsSection recipeId={recipeId} />

        <ToolsSection recipeId={recipeId} onChanged={onChanged} />

        <ChangeEffectsSection recipeId={recipeId} />
      </div>

      {showSignoff && current ? (
        <SignoffSubmitModal
          recipeId={recipeId}
          versionId={versionId}
          versionNo={current.versionNo ?? null}
          onClose={() => setShowSignoff(false)}
          onDone={() => {
            setShowSignoff(false);
            toast.success(t('rms.recipe.detail.signoffDone'));
            onChanged?.();
          }}
        />
      ) : null}
    </CimDrawer>
  );
}

/** 等宽文本框（Body 编辑用）。`CimInput` 自带 `mono` 与 `width`，这里只固定行数。 */
function CimInputArea({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return <CimInput rows={12} mono width="100%" value={value} onChange={onChange} />;
}

/**
 * 发起签核弹窗（就地闭环）：对当前 DRAFT 版本直接提交签核——
 * 不再要求用户跳到签核页重新选配方、选版本。
 */
const ROUTE_OPTIONS = [
  { value: 'SINGLE', labelKey: 'rms.signoff.route.SINGLE' },
  { value: 'COUNTERSIGN', labelKey: 'rms.signoff.route.COUNTERSIGN' },
  { value: 'ANY', labelKey: 'rms.signoff.route.ANY' },
];

interface SignoffForm {
  reason: string;
  signRoute: SignRoute;
}

function SignoffSubmitModal({
  recipeId,
  versionId,
  versionNo,
  onClose,
  onDone,
}: {
  recipeId: string;
  versionId: string;
  versionNo: number | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<SignoffForm>({ reason: '', signRoute: 'SINGLE' });
  const [busy, setBusy] = useState(false);

  const fields: Array<FormField<SignoffForm>> = [
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
  ];

  const submit = async () => {
    if (!form.reason.trim()) {
      toast.warning(t('rms.signoff.reason') + ' *');
      return;
    }
    setBusy(true);
    try {
      await api.submitSignoff({
        recipeId,
        versionId,
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
      title={`${t('rms.recipe.detail.signoffGo')}${versionNo != null ? ` · v${versionNo}` : ''}`}
      width={620}
      onOk={submit}
      okLoading={busy}
      okKey="rms.signoff.submit"
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      <FormGrid<SignoffForm>
        fields={fields}
        value={form}
        onChange={(p) => setForm((prev) => ({ ...prev, ...p }))}
        columns={2}
        disabled={busy}
      />
    </CimModal>
  );
}

/**
 * 规格参数区块（就地维护）：配方详情内直接增删改规格，
 * 复用与规格参数页相同的共享编辑弹窗——规格与 Body/解析同屏，无需跨页定位。
 */
function SpecsSection({ recipeId }: { recipeId: string }) {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [editing, setEditing] = useState<ParamSpec | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const specsRes = useResource(() => api.listSpecs(recipeId), { deps: [recipeId] });

  const toggle = async (x: ParamSpec) => {
    try {
      await api.setSpecEnabled(x.id, !x.enabled);
      specsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const remove = async (x: ParamSpec) => {
    const ok = await confirm({
      titleKey: 'rms.common.delete',
      contentKey: 'rms.spec.deleteConfirm',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteSpec(x.id);
      toast.success(t('rms.common.deleted'));
      specsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const columns = useMemo<Array<DataTableColumn<ParamSpec>>>(
    () => [
      { key: 'paramKey', titleKey: 'rms.spec.paramKey', mono: true, width: 190 },
      {
        key: 'specType',
        titleKey: 'rms.spec.specType',
        width: 110,
        render: (x) => (
          <CimTag size="small">{t(`rms.spec.type.${x.specType}`, { defaultValue: x.specType })}</CimTag>
        ),
      },
      {
        key: 'value',
        titleKey: 'rms.recipe.detail.value',
        mono: true,
        width: 180,
        render: (x) => specValueText(x),
      },
      {
        key: 'unit',
        titleKey: 'rms.spec.unit',
        width: 80,
        render: (x) => <span className="muted">{x.unit ?? '—'}</span>,
      },
      {
        key: 'enabled',
        titleKey: 'rms.common.status',
        width: 100,
        render: (x) => (
          <CimTag size="small" tone={x.enabled ? 'ok' : 'idle'}>
            {x.enabled ? t('rms.spec.enabled') : t('rms.spec.disabled')}
          </CimTag>
        ),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 190,
        render: (x) => (
          <Can perm="rms:spec:manage">
            <span className="cell-actions">
              <CimButton size="small" onClick={() => setEditing(x)}>
                {t('rms.common.edit')}
              </CimButton>
              <CimButton size="small" onClick={() => void toggle(x)}>
                {x.enabled ? t('rms.spec.disable') : t('rms.spec.enable')}
              </CimButton>
              <CimButton size="small" onClick={() => void remove(x)}>
                {t('rms.common.delete')}
              </CimButton>
            </span>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <>
      <Panel
        titleKey="rms.recipe.detail.specs"
        flush
        actions={
          <Can perm="rms:spec:manage">
            <CimButton size="small" onClick={() => setShowCreate(true)}>
              {t('rms.spec.add')}
            </CimButton>
          </Can>
        }
      >
        <DataTable<ParamSpec>
          flat
          columns={columns}
          rows={specsRes.data ?? NO_SPECS}
          rowKey={(x) => x.id}
          loading={specsRes.loading}
          error={specsRes.error?.message}
          onRetry={specsRes.refresh}
          scrollX="max-content"
          emptyTitleKey="rms.spec.empty"
        />
      </Panel>

      {(showCreate || editing) && (
        <SpecEditModal
          recipeId={recipeId}
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={() => {
            setShowCreate(false);
            setEditing(null);
            specsRes.refresh();
          }}
        />
      )}
    </>
  );
}

/**
 * 装载机台区块（W7 体验整合）：配方视角直接看/绑/解机台——
 * 不再要求用户跳到集成监控页交叉比对；绑定 = 机台 + PPID（腔体可选，随所选机台联动）。
 */
function ToolsSection({ recipeId, onChanged }: { recipeId: string; onChanged?: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [deviceId, setDeviceId] = useState('');
  const [chamberId, setChamberId] = useState('');
  const [ppid, setPpid] = useState('');
  const [busy, setBusy] = useState(false);

  const usagesRes = useResource(() => api.recipeTools(recipeId), { deps: [recipeId] });
  const devicesRes = useResource(() => api.listDevices());
  const chambersRes = useResource(() => api.listChambers(deviceId), {
    deps: [deviceId],
    enabled: !!deviceId,
  });

  const bind = async () => {
    if (!deviceId || !ppid.trim()) {
      toast.warning(t('rms.tools.bindRequired'));
      return;
    }
    setBusy(true);
    try {
      await api.registerPpidMap({
        deviceId,
        deviceTypeId: null,
        ppidOnTool: ppid.trim(),
        recipeId,
        chamberId: chamberId || null,
      });
      setPpid('');
      toast.success(t('rms.tools.bound'));
      usagesRes.refresh();
      onChanged?.();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const unbind = async (x: RecipeToolUsage) => {
    const ok = await confirm({
      titleKey: 'rms.tools.unbind',
      contentKey: 'rms.tools.unbindConfirm',
      danger: true,
      okKey: 'rms.tools.unbind',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.deletePpidMap(x.id);
      usagesRes.refresh();
      onChanged?.();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<RecipeToolUsage>>>(
    () => [
      {
        key: 'deviceCode',
        titleKey: 'rms.tools.device',
        mono: true,
        width: 170,
        render: (u) => (
          <>
            {u.deviceCode ?? '—'}
            {u.deviceName ? <span className="muted"> · {u.deviceName}</span> : null}
          </>
        ),
      },
      { key: 'ppidOnTool', titleKey: 'rms.tools.ppid', mono: true, width: 140 },
      {
        key: 'chamberCode',
        titleKey: 'rms.tools.chamber',
        mono: true,
        width: 150,
        render: (u) =>
          u.chamberCode ? (
            <>
              {u.chamberCode}
              {u.deviceId ? null : <span className="muted"> · {t('rms.tools.typeLevel')}</span>}
            </>
          ) : (
            <span className="muted">{t('rms.tools.noChamberBound')}</span>
          ),
      },
      {
        key: 'qualificationStatus',
        titleKey: 'rms.tools.qualification',
        width: 130,
        render: (u) =>
          u.qualificationStatus ? (
            <CimTag size="small" tone={u.qualificationStatus === 'GRANTED' ? 'ok' : 'warn'}>
              {u.qualificationStatus}
            </CimTag>
          ) : (
            <span className="muted">{t('rms.tools.unqualified')}</span>
          ),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 100,
        render: (u) => (
          <Can perm="rms:recipe:update">
            <CimButton size="small" disabled={busy} onClick={() => void unbind(u)}>
              {t('rms.tools.unbind')}
            </CimButton>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, busy],
  );

  const usages = usagesRes.data ?? NO_USAGES;
  const devices = devicesRes.data ?? NO_DEVICES;
  const chambers = chambersRes.data ?? NO_CHAMBERS;

  return (
    <Panel titleKey="rms.tools.title" flush actions={<span className="muted">{usages.length}</span>}>
      <div className="cim-form-grid" style={{ padding: '12px 14px 4px', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))' }}>
        <CimField label={t('rms.tools.device')}>
          <CimSelect
            value={deviceId || null}
            onChange={(v) => {
              setDeviceId(typeof v === 'string' ? v : '');
              setChamberId('');
            }}
            options={devices.map((d) => ({ value: d.id, label: d.code, hint: d.name }))}
            placeholderKey="rms.tools.pickDevice"
            width="100%"
          />
        </CimField>
        <CimField label={t('rms.tools.ppid')}>
          <CimInput value={ppid} placeholder="PPID-001" onChange={setPpid} />
        </CimField>
        <CimField label={t('rms.tools.chamber')}>
          <CimSelect
            value={chamberId || null}
            onChange={(v) => setChamberId(typeof v === 'string' ? v : '')}
            options={chambers.map((c) => ({ value: c.id, label: c.code, hint: c.name }))}
            placeholderKey="rms.tools.noChamber"
            disabled={!deviceId}
            width="100%"
          />
        </CimField>
        <CimField label={'\u00A0'}>
          <Can perm="rms:recipe:update">
            <CimButton type="primary" disabled={busy} onClick={() => void bind()}>
              {t('rms.tools.bind')}
            </CimButton>
          </Can>
        </CimField>
      </div>

      <DataTable<RecipeToolUsage>
        flat
        columns={columns}
        rows={usages}
        rowKey={(u) => u.id}
        loading={usagesRes.loading}
        error={usagesRes.error?.message}
        onRetry={usagesRes.refresh}
        scrollX="max-content"
        emptyTitleKey="rms.tools.empty"
      />
    </Panel>
  );
}

/**
 * 变更效果区块（W11）：该配方的版本生效历史（谁在何时从哪版切到哪版、参数差异几项），
 * 供质量侧按变更时点回溯 SPC/Cpk（跨域引用 spcRef 预留）。
 */
function ChangeEffectsSection({ recipeId }: { recipeId: string }) {
  const { t } = useTranslation();
  const effectsRes = useResource(() => api.listChangeEffects(recipeId), { deps: [recipeId] });

  const columns = useMemo<Array<DataTableColumn<ChangeEffect>>>(
    () => [
      {
        key: 'fromVersionNo',
        titleKey: 'rms.effect.from',
        mono: true,
        width: 100,
        render: (e) => (e.fromVersionNo == null ? t('rms.common.dash') : 'v' + e.fromVersionNo),
      },
      {
        key: 'toVersionNo',
        titleKey: 'rms.effect.to',
        mono: true,
        width: 100,
        render: (e) => `v${e.toVersionNo}`,
      },
      {
        key: 'activatedBy',
        titleKey: 'rms.effect.by',
        width: 130,
        render: (e) => e.activatedBy ?? t('rms.common.dash'),
      },
      {
        key: 'activatedAt',
        titleKey: 'rms.effect.at',
        width: 170,
        render: (e) => <span className="muted">{e.activatedAt}</span>,
      },
      { key: 'paramDiffCount', titleKey: 'rms.effect.diffCount', width: 110, align: 'right' },
      {
        key: 'changeSummary',
        titleKey: 'rms.common.remark',
        render: (e) => <span className="muted">{e.changeSummary ?? t('rms.common.dash')}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const rows = effectsRes.data ?? NO_EFFECTS;
  // 取数未回来时不渲染（旧实现用 loaded 标志做同样的事），避免闪一帧「无变更记录」
  if (!effectsRes.loaded) return null;

  return (
    <Panel titleKey="rms.effect.title" subKey="rms.effect.sub" flush>
      {rows.length === 0 ? (
        <div style={{ padding: 14 }}>
          <EmptyState titleKey="rms.effect.empty" />
        </div>
      ) : (
        <DataTable<ChangeEffect>
          flat
          columns={columns}
          rows={rows}
          rowKey={(e) => e.id}
          scrollX="max-content"
        />
      )}
    </Panel>
  );
}
