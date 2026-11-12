import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimDescriptions,
  CimDrawer,
  CimField,
  CimSelect,
  CimTag,
  DataTable,
  EmptyState,
  FieldGrid,
  Page,
  Panel,
  useToast,
  type DataTableColumn,
  type DescItem,
  CimButton,
  InlineAlert,
  KpiCard,

  KpiGrid,
} from '@cim/ui';
import DiffTable from '@/components/DiffTable';
import * as api from '@/lib/rmsApi';
import type {
  CompareLog,
  CompareLogDetail,
  CompareLogSummary,
  CompareMode,
  CompareResult,
  CompareRun,
  Recipe,
  RecipeVersion,
} from '@/types';

const MODES: CompareMode[] = ['FULL_BODY', 'PARAMETER', 'STRUCTURE'];
const MODE_OPTIONS = MODES.map((m) => ({ value: m, labelKey: `rms.compare.mode.${m}` }));

const NO_RECIPES: Recipe[] = [];
const NO_VERSIONS: RecipeVersion[] = [];
const NO_LOGS: CompareLog[] = [];

const VERTICAL = { display: 'flex', flexDirection: 'column', gap: 8 } as const;

/**
 * 在线比对（Req.md FR-C1~C8、Req 21/24~28/33~37~39）：三模式版本比对 + 批量 + 校验日志下钻。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>「左侧 / 右侧 / 批量」三处版本列表各是一份 `useResource`（`enabled` 绑配方），
 *       切配方即重取且旧请求被取消 —— 旧实现三处 `useEffect` 各发一次请求，快速切配方时
 *       慢回包会把新配方的版本列表冲掉，界面上出现「A 的版本挂在 B 下」。</li>
 *   <li>批量目标从一长列 checkbox 改为多选下拉（`CimSelect multiple`）：版本多时
 *       原列表会把面板撑到两屏高，且无法搜索。</li>
 *   <li>错误提示统一走 toast；面板内的取数错误由 `DataTable` 的错误态兜底。</li>
 * </ul>
 */
export default function ComparePage() {
  const { t } = useTranslation();
  const toast = useToast();

  // 单次比对
  const [leftRecipe, setLeftRecipe] = useState('');
  const [rightRecipe, setRightRecipe] = useState('');
  const [leftVersion, setLeftVersion] = useState('');
  const [rightVersion, setRightVersion] = useState('');
  const [mode, setMode] = useState<CompareMode>('FULL_BODY');
  const [result, setResult] = useState<CompareResult | null>(null);
  const [busy, setBusy] = useState(false);

  // 批量比对
  const [bRecipe, setBRecipe] = useState('');
  const [bBaseline, setBBaseline] = useState('');
  const [bTargets, setBTargets] = useState<string[]>([]);
  const [bResults, setBResults] = useState<CompareRun[]>([]);
  const [bBusy, setBBusy] = useState(false);

  // 校验日志
  const [logFilter, setLogFilter] = useState('');
  const [logDetailId, setLogDetailId] = useState<string | null>(null);

  const recipesRes = useResource(() => api.listRecipes());
  const recipes = recipesRes.data ?? NO_RECIPES;

  const leftRes = useResource(
    () => (leftRecipe ? api.listVersions(leftRecipe) : Promise.resolve(NO_VERSIONS)),
    { deps: [leftRecipe], enabled: !!leftRecipe, keepPreviousData: true },
  );
  const rightRes = useResource(
    () => (rightRecipe ? api.listVersions(rightRecipe) : Promise.resolve(NO_VERSIONS)),
    { deps: [rightRecipe], enabled: !!rightRecipe, keepPreviousData: true },
  );
  const batchRes = useResource(
    () => (bRecipe ? api.listVersions(bRecipe) : Promise.resolve(NO_VERSIONS)),
    { deps: [bRecipe], enabled: !!bRecipe, keepPreviousData: true },
  );

  const logsRes = useResource(
    async () => {
      const [list, sum] = await Promise.all([
        api.listCompareLogs({ ...(logFilter ? { result: logFilter } : {}), limit: 200 }),
        api.compareLogSummary(),
      ]);
      return { list, sum };
    },
    { deps: [logFilter], keepPreviousData: true },
  );
  const logs = logsRes.data?.list ?? NO_LOGS;
  const logSummary: CompareLogSummary | undefined = logsRes.data?.sum;

  const recipeOptions = useMemo(
    () => recipes.map((r) => ({ value: r.id, label: `${r.code} · ${r.name}` })),
    [recipes],
  );

  const versionOptions = (list: RecipeVersion[]) =>
    list.map((v) => ({
      value: v.id,
      label: `v${v.versionNo} · ${t('rms.recipe.status.' + v.status, { defaultValue: v.status })}`,
      hint: v.changeSummary ?? undefined,
    }));

  const runOnce = async () => {
    if (!leftVersion || !rightVersion) {
      toast.warning(t('rms.compare.pickRequired'));
      return;
    }
    setBusy(true);
    setResult(null);
    try {
      setResult(await api.compareVersions(leftVersion, rightVersion, mode));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const runBatch = async () => {
    if (!bBaseline || bTargets.length === 0) {
      toast.warning(t('rms.compare.pickTargets'));
      return;
    }
    setBBusy(true);
    try {
      setBResults(
        await api.batchCompare({
          baselineVersionId: bBaseline,
          targetVersionIds: bTargets,
          mode,
        }),
      );
      logsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBBusy(false);
    }
  };

  const logColumns = useMemo<Array<DataTableColumn<CompareLog>>>(
    () => [
      { key: 'createTime', titleKey: 'rms.audit.th.time', mono: true, width: 170 },
      {
        key: 'mode',
        titleKey: 'rms.compare.mode',
        width: 110,
        render: (l) => <CimTag size="small">{t('rms.compare.mode.' + l.mode, { defaultValue: l.mode })}</CimTag>,
      },
      {
        key: 'recipeCode',
        titleKey: 'rms.compare.col.target',
        mono: true,
        width: 170,
        render: (l) => l.recipeCode ?? t('rms.common.dash'),
      },
      {
        key: 'identical',
        titleKey: 'rms.compare.col.result',
        width: 90,
        render: (l) => (
          <CimTag tone={l.identical ? 'ok' : 'err'} size="small">
            {l.identical ? t('rms.compare.pass') : t('rms.compare.fail')}
          </CimTag>
        ),
      },
      { key: 'diffCount', titleKey: 'rms.compare.col.diffCount', width: 100 },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 110,
        render: (l) => (
          <CimButton size="small" onClick={() => setLogDetailId(l.id)}>
            {t('rms.compare.logView')}
          </CimButton>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const batchColumns = useMemo<Array<DataTableColumn<CompareRun>>>(
    () => [
      {
        key: 'target',
        titleKey: 'rms.compare.col.target',
        width: 200,
        mono: true,
        render: (r) => `${r.result.right.recipeCode} v${r.result.right.versionNo}`,
      },
      {
        key: 'identical',
        titleKey: 'rms.compare.col.result',
        width: 90,
        render: (r) => (
          <CimTag tone={r.result.identical ? 'ok' : 'err'} size="small">
            {r.result.identical ? t('rms.compare.pass') : t('rms.compare.fail')}
          </CimTag>
        ),
      },
      {
        key: 'diffCount',
        titleKey: 'rms.compare.col.diffCount',
        render: (r) => r.result.items.filter((i) => i.changed).length,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      

      {/* 校验日志 KPI */}
      <KpiGrid>
        <KpiCard label={t('rms.compare.summary.total')} value={logSummary?.total ?? '—'} />
        <KpiCard tone="ok" label={t('rms.compare.summary.pass')} value={logSummary?.pass ?? '—'} />
        <KpiCard tone="warn" label={t('rms.compare.summary.fail')} value={logSummary?.fail ?? '—'} />
        <KpiCard tone="info" label={t('rms.compare.summary.diffTotal')} value={logSummary?.diffTotal ?? '—'} />
      </KpiGrid>

      {/* 版本比对 */}
      <Panel
        titleKey="rms.compare.title"
        actions={
          <>
            <CimSelect
              value={mode}
              onChange={(v) => setMode((typeof v === 'string' ? v : 'FULL_BODY') as CompareMode)}
              options={MODE_OPTIONS}
              allowClear={false}
              width={150}
            />
            <CimButton type="primary" size="small" onClick={runOnce} disabled={busy}>
              {busy ? t('rms.common.loading') : t('rms.compare.run')}
            </CimButton>
          </>
        }
      >
        {/* 字段网格一律走 FieldGrid：手写内联 grid 吃不到 .cim-form-grid 的相邻 margin 豁免，
            第二列会整体下移 11px（右列标签比左列低一截的「不对齐」就是这么来的） */}
        <FieldGrid columns={2} gap={12}>
          <CimField labelKey="rms.compare.left">
            <div style={VERTICAL}>
              <CimSelect
                value={leftRecipe || null}
                onChange={(v) => {
                  setLeftRecipe(typeof v === 'string' ? v : '');
                  setLeftVersion('');
                  setResult(null);
                }}
                options={recipeOptions}
                placeholderKey="rms.signoff.pickRecipe"
                width="100%"
              />
              <CimSelect
                value={leftVersion || null}
                onChange={(v) => setLeftVersion(typeof v === 'string' ? v : '')}
                options={versionOptions(leftRes.data ?? NO_VERSIONS)}
                placeholderKey="rms.signoff.pickVersion"
                disabled={!leftRecipe}
                loading={leftRes.loading}
                width="100%"
              />
            </div>
          </CimField>
          <CimField labelKey="rms.compare.right">
            <div style={VERTICAL}>
              <CimSelect
                value={rightRecipe || null}
                onChange={(v) => {
                  setRightRecipe(typeof v === 'string' ? v : '');
                  setRightVersion('');
                  setResult(null);
                }}
                options={recipeOptions}
                placeholderKey="rms.signoff.pickRecipe"
                width="100%"
              />
              <CimSelect
                value={rightVersion || null}
                onChange={(v) => setRightVersion(typeof v === 'string' ? v : '')}
                options={versionOptions(rightRes.data ?? NO_VERSIONS)}
                placeholderKey="rms.signoff.pickVersion"
                disabled={!rightRecipe}
                loading={rightRes.loading}
                width="100%"
              />
            </div>
          </CimField>
        </FieldGrid>

        <div style={{ marginTop: 12 }}>
          {result ? (
            <>
              <InlineAlert tone={(result.identical ? 'ok' : 'warn')} style={{ marginBottom: 10 }}>{result.identical ? t('rms.compare.identical') : t('rms.compare.diff')}
                  {' · '}
                  {result.left.recipeCode} v{result.left.versionNo} ↔ {result.right.recipeCode} v
                  {result.right.versionNo}
                  {!result.identical &&
                    ' · ' + result.items.filter((i) => i.changed).length + ' / ' + result.items.length}</InlineAlert>
              <DiffTable items={result.items} />
            </>
          ) : (
            <EmptyState titleKey="rms.compare.emptyResult" />
          )}
        </div>
      </Panel>

      {/* 批量比对 */}
      <Panel
        titleKey="rms.compare.batch.title"
        actions={
          <CimButton type="primary" size="small" onClick={runBatch} disabled={bBusy}>
            {bBusy ? t('rms.common.loading') : t('rms.compare.batchRun')}
          </CimButton>
        }
      >
        <FieldGrid columns={2} gap={12}>
          <CimField labelKey="rms.compare.baseline">
            <div style={VERTICAL}>
              <CimSelect
                value={bRecipe || null}
                onChange={(v) => {
                  setBRecipe(typeof v === 'string' ? v : '');
                  setBBaseline('');
                  setBTargets([]);
                }}
                options={recipeOptions}
                placeholderKey="rms.signoff.pickRecipe"
                width="100%"
              />
              <CimSelect
                value={bBaseline || null}
                onChange={(v) => {
                  setBBaseline(typeof v === 'string' ? v : '');
                  setBTargets((prev) => prev.filter((id) => id !== v));
                }}
                options={versionOptions(batchRes.data ?? NO_VERSIONS)}
                placeholderKey="rms.signoff.pickVersion"
                disabled={!bRecipe}
                loading={batchRes.loading}
                width="100%"
              />
            </div>
          </CimField>
          <CimField labelKey="rms.compare.targets">
            <CimSelect
              multiple
              value={bTargets}
              onChange={(v) => setBTargets(Array.isArray(v) ? v : v ? [v] : [])}
              options={versionOptions(batchRes.data ?? NO_VERSIONS).filter((o) => o.value !== bBaseline)}
              placeholderKey="rms.signoff.pickVersion"
              disabled={!bRecipe}
              notFoundKey="rms.common.noData"
              width="100%"
            />
          </CimField>
        </FieldGrid>

        <div style={{ marginTop: 12 }}>
          {bResults.length ? (
            <DataTable<CompareRun>
              flat
              columns={batchColumns}
              rows={bResults}
              rowKey={(r) => r.logId}
              scrollX="max-content"
            />
          ) : (
            <EmptyState titleKey="rms.compare.emptyBatch" />
          )}
        </div>
      </Panel>

      {/* 校验日志 */}
      <Panel
        titleKey="rms.compare.logs.title"
        flush
        actions={
          <CimSelect
            value={logFilter}
            onChange={(v) => setLogFilter(typeof v === 'string' ? v : '')}
            options={[
              { value: 'PASS', labelKey: 'rms.compare.onlyPass' },
              { value: 'FAIL', labelKey: 'rms.compare.onlyFail' },
            ]}
            placeholderKey="rms.compare.filterResult"
            width={140}
          />
        }
      >
        <DataTable<CompareLog>
          flat
          columns={logColumns}
          rows={logs}
          rowKey={(l) => l.id}
          loading={logsRes.loading}
          error={logsRes.error?.message}
          onRetry={logsRes.refresh}
          onRefresh={logsRes.refresh}
          settingsKey="rms.compare.logs"
          scrollX="max-content"
          emptyTitleKey="rms.compare.log.empty"
          emptyHintKey="rms.compare.log.emptyHint"
        />
      </Panel>

      <LogDetailDrawer id={logDetailId} onClose={() => setLogDetailId(null)} />
    </Page>
  );
}

/** 校验日志明细抽屉：两级下钻的叶子（差异项逐条）。 */
function LogDetailDrawer({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { t } = useTranslation();

  const { data, loading, error } = useResource<CompareLogDetail>(
    () => api.compareLogDetail(id as string),
    { deps: [id], enabled: !!id },
  );

  const items = useMemo<Array<DescItem<CompareLogDetail>>>(
    () => [
      { key: 'time', labelKey: 'rms.audit.th.time', render: (v) => v.view.createTime },
      {
        key: 'mode',
        labelKey: 'rms.compare.mode',
        render: (v) => t('rms.compare.mode.' + v.view.mode, { defaultValue: v.view.mode }),
      },
      { key: 'target', labelKey: 'rms.compare.col.target', render: (v) => v.view.recipeCode },
      {
        key: 'result',
        labelKey: 'rms.compare.col.result',
        render: (v) => (
          <>
            <CimTag tone={v.view.identical ? 'ok' : 'err'} size="small">
              {v.view.identical ? t('rms.compare.pass') : t('rms.compare.fail')}
            </CimTag>
            <span className="dim">
              {' '}
              · {v.view.diffCount} {t('rms.compare.col.diffCount')}
            </span>
          </>
        ),
      },
    ],
    [t],
  );

  return (
    <CimDrawer
      open={!!id}
      onClose={onClose}
      titleKey="rms.compare.detail.title"
      width={820}
    >
      {error ? (
        <InlineAlert tone="err">{error.message}</InlineAlert>
      ) : null}
      {loading && !data ? <div className="dim">{t('rms.common.loading')}</div> : null}
      {data ? (
        <>
          <CimDescriptions record={data} items={items} column={2} />
          <div style={{ marginTop: 12 }}>
            <DiffTable items={data.items} />
          </div>
        </>
      ) : null}
    </CimDrawer>
  );
}
