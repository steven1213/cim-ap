import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Panel from '@/components/Panel';
import Drawer from '@/components/Drawer';
import DiffTable from '@/components/DiffTable';
import * as api from '@/lib/rmsApi';
import type {
  Recipe,
  RecipeVersion,
  CompareResult,
  CompareRun,
  CompareLog,
  CompareLogDetail,
  CompareLogSummary,
  CompareMode,
} from '@/types';
import {
  IconAlert,
  IconCheckCircle,
  IconList,
  IconRefresh,
  IconSearch,
  IconSliders,
  IconSync,
} from '@/components/Icons';

const MODES: CompareMode[] = ['FULL_BODY', 'PARAMETER', 'STRUCTURE'];

/** 在线比对（Req.md FR-C1~C8、Req 21/24~28/33/37~39）：三模式版本比对 + 批量 + 校验日志下钻。 */
export default function ComparePage() {
  const { t } = useTranslation();
  const [recipes, setRecipes] = useState<Recipe[]>([]);

  // 单次比对
  const [leftRecipe, setLeftRecipe] = useState('');
  const [rightRecipe, setRightRecipe] = useState('');
  const [leftVersions, setLeftVersions] = useState<RecipeVersion[]>([]);
  const [rightVersions, setRightVersions] = useState<RecipeVersion[]>([]);
  const [leftVersion, setLeftVersion] = useState('');
  const [rightVersion, setRightVersion] = useState('');
  const [mode, setMode] = useState<CompareMode>('FULL_BODY');
  const [result, setResult] = useState<CompareResult | null>(null);
  const [busy, setBusy] = useState(false);

  // 批量比对
  const [bRecipe, setBRecipe] = useState('');
  const [bVersions, setBVersions] = useState<RecipeVersion[]>([]);
  const [bBaseline, setBBaseline] = useState('');
  const [bTargets, setBTargets] = useState<Set<string>>(new Set());
  const [bResults, setBResults] = useState<CompareRun[]>([]);
  const [bBusy, setBBusy] = useState(false);

  // 校验日志
  const [logs, setLogs] = useState<CompareLog[]>([]);
  const [logSummary, setLogSummary] = useState<CompareLogSummary | null>(null);
  const [logFilter, setLogFilter] = useState('');
  const [logDetailId, setLogDetailId] = useState<string | null>(null);

  const [err, setErr] = useState('');

  useEffect(() => {
    api.listRecipes().then(setRecipes).catch(() => setRecipes([]));
    loadLogs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!leftRecipe) { setLeftVersions([]); setLeftVersion(''); return; }
    api.listVersions(leftRecipe).then(setLeftVersions).catch(() => setLeftVersions([]));
  }, [leftRecipe]);
  useEffect(() => {
    if (!rightRecipe) { setRightVersions([]); setRightVersion(''); return; }
    api.listVersions(rightRecipe).then(setRightVersions).catch(() => setRightVersions([]));
  }, [rightRecipe]);
  useEffect(() => {
    if (!bRecipe) { setBVersions([]); setBBaseline(''); setBTargets(new Set()); return; }
    api.listVersions(bRecipe).then(setBVersions).catch(() => setBVersions([]));
  }, [bRecipe]);

  async function loadLogs(filter = logFilter) {
    try {
      const [ls, sum] = await Promise.all([
        api.listCompareLogs({ ...(filter ? { result: filter } : {}), limit: 200 }),
        api.compareLogSummary(),
      ]);
      setLogs(ls);
      setLogSummary(sum);
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  async function runOnce() {
    if (!leftVersion || !rightVersion) {
      setErr(t('rms.compare.pickRequired'));
      return;
    }
    setBusy(true);
    setErr('');
    setResult(null);
    try {
      setResult(await api.compareVersions(leftVersion, rightVersion, mode));
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function runBatch() {
    if (!bBaseline || bTargets.size === 0) {
      setErr(t('rms.compare.pickTargets'));
      return;
    }
    setBBusy(true);
    setErr('');
    try {
      const runs = await api.batchCompare({
        baselineVersionId: bBaseline,
        targetVersionIds: [...bTargets],
        mode,
      });
      setBResults(runs);
      await loadLogs();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBBusy(false);
    }
  }

  function vlabel(v: RecipeVersion): string {
    return `v${v.versionNo} · ${t('rms.recipe.status.' + v.status, { defaultValue: v.status })} · ${v.changeSummary ?? ''}`;
  }

  function toggleTarget(id: string) {
    setBTargets((prev) => {
      const n = new Set(prev);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
  }

  return (
    <div className="page">
      {err && <div className="alert err"><IconAlert width={15} height={15} /><span>{err}</span></div>}

      <div className="page-head">
        <div>
          <h1>{t('rms.compare.title')}</h1>
          <p className="dim" style={{ margin: '2px 0 0' }}>{t('rms.compare.sub')}</p>
        </div>
      </div>

      {/* 校验日志 KPI */}
      <div className="kpi-grid">
        <div className="kpi">
          <div className="kpi-ico"><IconList width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.compare.summary.total')}</div>
            <div className="kpi-val">{logSummary?.total ?? '—'}</div>
          </div>
        </div>
        <div className="kpi ok">
          <div className="kpi-ico"><IconCheckCircle width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.compare.summary.pass')}</div>
            <div className="kpi-val">{logSummary?.pass ?? '—'}</div>
          </div>
        </div>
        <div className="kpi warn">
          <div className="kpi-ico"><IconAlert width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.compare.summary.fail')}</div>
            <div className="kpi-val">{logSummary?.fail ?? '—'}</div>
          </div>
        </div>
        <div className="kpi info">
          <div className="kpi-ico"><IconSliders width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.compare.summary.diffTotal')}</div>
            <div className="kpi-val">{logSummary?.diffTotal ?? '—'}</div>
          </div>
        </div>
      </div>

      {/* 版本比对 */}
      <Panel
        title={t('rms.compare.title')}
        sub={t('rms.compare.sub')}
        actions={
          <>
            <select className="select-sm" value={mode} onChange={(e) => setMode(e.target.value as CompareMode)}>
              {MODES.map((m) => <option key={m} value={m}>{t('rms.compare.mode.' + m)}</option>)}
            </select>
            <button className="btn btn-primary btn-sm" onClick={runOnce} disabled={busy}>
              <IconSync width={13} height={13} /> {busy ? t('rms.common.loading') : t('rms.compare.run')}
            </button>
          </>
        }
      >
        <div className="grid-2">
          <div className="field">
            <label className="field-label">{t('rms.compare.left')}</label>
            <select className="select-sm" style={{ width: '100%' }} value={leftRecipe}
              onChange={(e) => setLeftRecipe(e.target.value)}>
              <option value="">{t('rms.signoff.pickRecipe')}</option>
              {recipes.map((r) => <option key={r.id} value={r.id}>{r.code} · {r.name}</option>)}
            </select>
            <select className="select-sm" style={{ width: '100%', marginTop: 6 }} value={leftVersion}
              onChange={(e) => setLeftVersion(e.target.value)} disabled={!leftRecipe}>
              <option value="">{t('rms.signoff.pickVersion')}</option>
              {leftVersions.map((v) => <option key={v.id} value={v.id}>{vlabel(v)}</option>)}
            </select>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.compare.right')}</label>
            <select className="select-sm" style={{ width: '100%' }} value={rightRecipe}
              onChange={(e) => setRightRecipe(e.target.value)}>
              <option value="">{t('rms.signoff.pickRecipe')}</option>
              {recipes.map((r) => <option key={r.id} value={r.id}>{r.code} · {r.name}</option>)}
            </select>
            <select className="select-sm" style={{ width: '100%', marginTop: 6 }} value={rightVersion}
              onChange={(e) => setRightVersion(e.target.value)} disabled={!rightRecipe}>
              <option value="">{t('rms.signoff.pickVersion')}</option>
              {rightVersions.map((v) => <option key={v.id} value={v.id}>{vlabel(v)}</option>)}
            </select>
          </div>
        </div>

        {result ? (
          <>
            <div className={'alert ' + (result.identical ? 'ok' : 'warn')} style={{ marginTop: 12 }}>
              {result.identical ? <IconCheckCircle width={15} height={15} /> : <IconAlert width={15} height={15} />}
              <span>
                {result.identical ? t('rms.compare.identical') : t('rms.compare.diff')}
                {' · '}
                {result.left.recipeCode} v{result.left.versionNo} ↔ {result.right.recipeCode} v{result.right.versionNo}
                {!result.identical && ' · ' + result.items.filter((i) => i.changed).length + ' / ' + result.items.length}
              </span>
            </div>
            <div style={{ marginTop: 10 }}>
              <DiffTable items={result.items} />
            </div>
          </>
        ) : (
          <div className="empty" style={{ marginTop: 12 }}>
            <IconSliders width={22} height={22} />
            <b>{t('rms.compare.emptyResult')}</b>
          </div>
        )}
      </Panel>

      {/* 批量比对 */}
      <Panel title={t('rms.compare.batch.title')} sub={t('rms.compare.batch.sub')}
        actions={
          <button className="btn btn-primary btn-sm" onClick={runBatch} disabled={bBusy}>
            <IconSync width={13} height={13} /> {bBusy ? t('rms.common.loading') : t('rms.compare.batchRun')}
          </button>
        }>
        <div className="grid-2">
          <div className="field">
            <label className="field-label">{t('rms.compare.baseline')}</label>
            <select className="select-sm" style={{ width: '100%' }} value={bRecipe}
              onChange={(e) => setBRecipe(e.target.value)}>
              <option value="">{t('rms.signoff.pickRecipe')}</option>
              {recipes.map((r) => <option key={r.id} value={r.id}>{r.code} · {r.name}</option>)}
            </select>
            <select className="select-sm" style={{ width: '100%', marginTop: 6 }} value={bBaseline}
              onChange={(e) => setBBaseline(e.target.value)} disabled={!bRecipe}>
              <option value="">{t('rms.signoff.pickVersion')}</option>
              {bVersions.map((v) => <option key={v.id} value={v.id}>{vlabel(v)}</option>)}
            </select>
          </div>
          <div className="field">
            <label className="field-label">
              {t('rms.compare.targets')}
              {bTargets.size > 0 && <span className="tag pri" style={{ marginLeft: 8 }}>{t('rms.compare.batchPicked', { n: bTargets.size })}</span>}
            </label>
            <div className="pick-list">
              {bVersions.filter((v) => v.id !== bBaseline).map((v) => (
                <label key={v.id} className="pick-item">
                  <span className="check"><input type="checkbox" checked={bTargets.has(v.id)} onChange={() => toggleTarget(v.id)} /></span>
                  <span className="mono">{vlabel(v)}</span>
                </label>
              ))}
              {bRecipe && bVersions.length <= 1 && <span className="dim">{t('rms.common.noData')}</span>}
            </div>
          </div>
        </div>

        {bResults.length ? (
          <div className="table-wrap" style={{ marginTop: 12 }}>
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 180 }}>{t('rms.compare.col.target')}</th>
                  <th style={{ width: 90 }}>{t('rms.compare.col.result')}</th>
                  <th>{t('rms.compare.col.diffCount')}</th>
                </tr>
              </thead>
              <tbody>
                {bResults.map((r) => (
                  <tr key={r.logId}>
                    <td className="mono">{r.result.right.recipeCode} v{r.result.right.versionNo}</td>
                    <td>
                      <span className={'tag ' + (r.result.identical ? 'ok' : 'err')}>
                        {r.result.identical ? t('rms.compare.pass') : t('rms.compare.fail')}
                      </span>
                    </td>
                    <td>{r.result.items.filter((i) => i.changed).length}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty" style={{ marginTop: 12 }}>
            <IconSearch width={22} height={22} />
            <b>{t('rms.compare.emptyBatch')}</b>
          </div>
        )}
      </Panel>

      {/* 校验日志 */}
      <Panel title={t('rms.compare.logs.title')} sub={t('rms.compare.logs.sub')} flush
        actions={
          <>
            <select className="select-sm" value={logFilter}
              onChange={(e) => { setLogFilter(e.target.value); loadLogs(e.target.value); }}>
              <option value="">{t('rms.compare.filterResult')}</option>
              <option value="PASS">{t('rms.compare.onlyPass')}</option>
              <option value="FAIL">{t('rms.compare.onlyFail')}</option>
            </select>
            <button className="btn btn-ghost btn-sm" onClick={() => loadLogs()}>
              <IconRefresh width={13} height={13} /> {t('rms.common.refresh')}
            </button>
          </>
        }>
        {logs.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 160 }}>{t('rms.audit.th.time')}</th>
                  <th style={{ width: 110 }}>{t('rms.compare.mode')}</th>
                  <th style={{ width: 160 }}>{t('rms.compare.col.target')}</th>
                  <th style={{ width: 90 }}>{t('rms.compare.col.result')}</th>
                  <th>{t('rms.compare.col.diffCount')}</th>
                  <th style={{ width: 120 }}>{t('rms.common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id}>
                    <td className="mono dim">{l.createTime ?? t('rms.common.dash')}</td>
                    <td><span className="tag">{t('rms.compare.mode.' + l.mode, { defaultValue: l.mode })}</span></td>
                    <td className="mono">{l.recipeCode ?? t('rms.common.dash')}</td>
                    <td>
                      <span className={'tag ' + (l.identical ? 'ok' : 'err')}>
                        {l.identical ? t('rms.compare.pass') : t('rms.compare.fail')}
                      </span>
                    </td>
                    <td>{l.diffCount}</td>
                    <td className="cell-actions">
                      <button className="btn btn-ghost btn-sm" onClick={() => setLogDetailId(l.id)}>
                        {t('rms.compare.logView')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconList width={22} height={22} />
            <b>{t('rms.compare.log.empty')}</b>
            <span>{t('rms.compare.log.emptyHint')}</span>
          </div>
        )}
      </Panel>

      {logDetailId && <LogDetailDrawer id={logDetailId} onClose={() => setLogDetailId(null)} />}
    </div>
  );
}

/** 校验日志明细抽屉：两级下钻的叶子（差异项逐条）。 */
function LogDetailDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation();
  const [detail, setDetail] = useState<CompareLogDetail | null>(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.compareLogDetail(id).then(setDetail).catch((e) => setErr(e?.message || 'error'));
  }, [id]);

  return (
    <Drawer title={<><IconList width={15} height={15} /> {t('rms.compare.detail.title')}</>} onClose={onClose}>
      {err && <div className="alert err"><IconAlert width={15} height={15} /><span>{err}</span></div>}
      {!detail ? (
        <div className="dim">{t('rms.common.loading')}</div>
      ) : (
        <>
          <dl className="kv">
            <dt>{t('rms.audit.th.time')}</dt>
            <dd className="mono">{detail.view.createTime}</dd>
            <dt>{t('rms.compare.mode')}</dt>
            <dd>{t('rms.compare.mode.' + detail.view.mode, { defaultValue: detail.view.mode })}</dd>
            <dt>{t('rms.compare.col.target')}</dt>
            <dd className="mono">{detail.view.recipeCode}</dd>
            <dt>{t('rms.compare.col.result')}</dt>
            <dd>
              <span className={'tag ' + (detail.view.identical ? 'ok' : 'err')}>
                {detail.view.identical ? t('rms.compare.pass') : t('rms.compare.fail')}
              </span>
              <span className="dim"> · {detail.view.diffCount} {t('rms.compare.col.diffCount')}</span>
            </dd>
          </dl>
          <DiffTable items={detail.items} />
        </>
      )}
    </Drawer>
  );
}
