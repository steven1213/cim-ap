import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import { BatchHoldModal, BatchDownloadModal } from '@/components/BatchOpsModal';
import * as api from '@/lib/rmsApi';
import { INTEGRATION_OPERATE } from '@/lib/permCodes';
import type {
  HoldView, AlarmView, Device, DeviceType, Recipe,
  OnlineCompareResult, RunCheckResult, ToolRecipeMap, DownloadJob, DownloadReceipt,
  EquipmentEvent, InventoryRow, InventoryReport, InventorySummary,
} from '@/types';

/** 集成监控页（IF-E1~M1/A1）：Hold 存量 + 报警 + 接口联调 + 批量操作（W9）。 */
export default function IntegrationPage() {
  const { t } = useTranslation();
  const [holds, setHolds] = useState<HoldView[]>([]);
  const [alarms, setAlarms] = useState<AlarmView[]>([]);
  const [activeOnly, setActiveOnly] = useState(true);
  const [unackOnly, setUnackOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [showBatchHold, setShowBatchHold] = useState(false);
  const [showBatchDownload, setShowBatchDownload] = useState(false);
  const [batchDevices, setBatchDevices] = useState<Device[]>([]);
  const [batchTypes, setBatchTypes] = useState<DeviceType[]>([]);
  const [batchRecipes, setBatchRecipes] = useState<Recipe[]>([]);

  async function refresh() {
    setLoading(true);
    try {
      setHolds(await api.listHolds({ activeOnly }));
      setAlarms(await api.listAlarms({ unackOnly }));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [activeOnly, unackOnly]);

  async function openBatch(kind: 'hold' | 'download') {
    try {
      const [ds, ts, rs] = await Promise.all([
        api.listDevices(), api.listDeviceTypes(), api.listRecipes(),
      ]);
      setBatchDevices(ds);
      setBatchTypes(ts);
      setBatchRecipes(rs);
    } catch {
      // 打开弹窗时的字典加载失败不阻断（弹窗内可看到空列表）
    }
    if (kind === 'hold') setShowBatchHold(true);
    else setShowBatchDownload(true);
  }

  async function release(h: HoldView) {
    const reason = prompt(t('rms.integration.releaseReason'));
    if (!reason) return;
    await api.releaseHold(h.id, reason);
    await refresh();
  }
  async function ack(a: AlarmView) {
    await api.ackAlarm(a.id);
    await refresh();
  }

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.integration.title')}</h1><p className="muted">{t('rms.integration.sub')}</p></div>
        <div className="toolbar">
          <Perms code={INTEGRATION_OPERATE}>
            <button className="btn btn-ghost btn-sm" onClick={() => openBatch('hold')}>{t('rms.batch.holdBtn')}</button>
            <button className="btn btn-ghost btn-sm" onClick={() => openBatch('download')}>{t('rms.batch.downloadBtn')}</button>
          </Perms>
          <button className="btn btn-ghost btn-sm" onClick={refresh}>{t('rms.common.refresh')}</button>
        </div>
      </div>

      {showBatchHold && (
        <BatchHoldModal
          devices={batchDevices.map((d) => ({ id: d.id, code: d.code, name: d.name }))}
          types={batchTypes}
          recipes={batchRecipes.map((r) => ({ id: r.id, code: r.code, name: r.name }))}
          onClose={() => setShowBatchHold(false)}
          onDone={refresh}
        />
      )}
      {showBatchDownload && (
        <BatchDownloadModal
          devices={batchDevices.map((d) => ({ id: d.id, code: d.code, name: d.name }))}
          recipes={batchRecipes.map((r) => ({ id: r.id, code: r.code, name: r.name }))}
          onClose={() => setShowBatchDownload(false)}
          onDone={refresh}
        />
      )}

      <div className="panel">
        <div className="panel-head">
          <h3>{t('rms.integration.holds')}</h3>
          <label className="check">
            <input type="checkbox" checked={activeOnly} onChange={(e) => setActiveOnly(e.target.checked)} />
            {t('rms.integration.onlyActive')}
          </label>
        </div>
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.integration.device')}</th>
              <th>{t('rms.integration.recipe')}</th>
              <th>{t('rms.integration.source')}</th>
              <th>{t('rms.integration.reason')}</th>
              <th>{t('rms.integration.active')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {holds.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.deviceCode ?? x.deviceId}</td>
                <td className="mono">{x.recipeCode ?? t('rms.common.dash')}</td>
                <td>{x.source}</td>
                <td className="muted">{x.reason}</td>
                <td><span className={`led ${x.active ? 'err' : 'ok'}`}>{x.active ? 'HOLD' : 'OK'}</span></td>
                <td className="cell-actions">
                  <Perms code={INTEGRATION_OPERATE}>
                    {x.active && <button className="btn btn-ghost btn-sm" onClick={() => release(x)}>{t('rms.integration.release')}</button>}
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && holds.length === 0 && <tr><td colSpan={6} className="empty-cell">{t('rms.integration.emptyHolds')}</td></tr>}
          </tbody>
        </table>
      </div>

      <div className="panel">
        <div className="panel-head">
          <h3>{t('rms.integration.alarms')}</h3>
          <label className="check">
            <input type="checkbox" checked={unackOnly} onChange={(e) => setUnackOnly(e.target.checked)} />
            {t('rms.integration.onlyUnack')}
          </label>
        </div>
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.integration.alarmCode')}</th>
              <th>{t('rms.integration.severity')}</th>
              <th>{t('rms.integration.device')}</th>
              <th>{t('rms.integration.lotId')}</th>
              <th>{t('rms.integration.message')}</th>
              <th>{t('rms.integration.occurredAt')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {alarms.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.alarmCode}</td>
                <td>
                  <span className={`tag ${x.severity === 'CRITICAL' ? 'err' : x.severity === 'MAJOR' ? 'warn' : ''}`}>{x.severity}</span>
                </td>
                <td className="mono">{x.deviceId ?? '—'}</td>
                <td className="mono">{x.lotId ?? '—'}</td>
                <td className="muted">{x.message}</td>
                <td className="muted">{x.occurredAt}</td>
                <td className="cell-actions">
                  <Perms code={INTEGRATION_OPERATE}>
                    {x.acknowledged
                      ? <span className="tag ok">{t('rms.integration.acknowledged')}</span>
                      : <button className="btn btn-ghost btn-sm" onClick={() => ack(x)}>{t('rms.integration.ack')}</button>}
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && alarms.length === 0 && <tr><td colSpan={7} className="empty-cell">{t('rms.integration.emptyAlarms')}</td></tr>}
          </tbody>
        </table>
      </div>

      <PpidPanel />
      <DownloadPanel onChanged={refresh} />
      <GemEventPanel />
      <InventoryPanel />
      <SimPanel onDone={refresh} />
    </div>
  );
}

/** 机台 PPID ↔ 配方映射（D7 / IF-E2）：EAP 侧只认 PPID，映射由 RMS 维护。 */
function PpidPanel() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<ToolRecipeMap[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [types, setTypes] = useState<DeviceType[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [scope, setScope] = useState('DEVICE');
  const [deviceId, setDeviceId] = useState('');
  const [typeId, setTypeId] = useState('');
  const [ppid, setPpid] = useState('');
  const [recipeId, setRecipeId] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    try {
      setRows(await api.listPpidMaps());
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }
  useEffect(() => {
    load();
    api.listDevices().then((d) => { setDevices(d); if (d.length) setDeviceId(d[0].id); });
    api.listDeviceTypes().then((d) => { setTypes(d); if (d.length) setTypeId(d[0].id); });
    api.listRecipes().then((r) => { setRecipes(r); if (r.length) setRecipeId(r[0].id); });
  }, []);

  async function add() {
    if (!ppid.trim() || !recipeId) {
      setErr(t('rms.integration.ppid.required'));
      return;
    }
    setBusy(true); setErr('');
    try {
      await api.registerPpidMap({
        deviceId: scope === 'DEVICE' ? deviceId : null,
        deviceTypeId: scope === 'TYPE' ? typeId : null,
        ppidOnTool: ppid.trim(), recipeId,
      });
      setPpid('');
      await load();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    await api.deletePpidMap(id);
    await load();
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.integration.ppid.title')}</h3>
        <span className="muted">{t('rms.integration.ppid.sub')}</span>
      </div>
      {err && <div className="alert err"><span>{err}</span></div>}

      <div className="form-grid">
        <div className="field">
          <label className="field-label">{t('rms.integration.ppid.scope')}</label>
          <select className="text-input" value={scope} onChange={(e) => setScope(e.target.value)}>
            <option value="DEVICE">{t('rms.integration.ppid.scopeDevice')}</option>
            <option value="TYPE">{t('rms.integration.ppid.scopeType')}</option>
          </select>
        </div>
        {scope === 'DEVICE' ? (
          <div className="field">
            <label className="field-label">{t('rms.integration.device')}</label>
            <select className="text-input" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
              {devices.map((d) => <option key={d.id} value={d.id}>{d.code} · {d.name}</option>)}
            </select>
          </div>
        ) : (
          <div className="field">
            <label className="field-label">{t('rms.integration.ppid.deviceType')}</label>
            <select className="text-input" value={typeId} onChange={(e) => setTypeId(e.target.value)}>
              {types.map((d) => <option key={d.id} value={d.id}>{d.code} · {d.name}</option>)}
            </select>
          </div>
        )}
        <div className="field">
          <label className="field-label">{t('rms.integration.ppid.ppidOnTool')}</label>
          <input className="text-input" value={ppid} onChange={(e) => setPpid(e.target.value)}
            placeholder={t('rms.integration.ppid.ppidPh')} />
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.recipe')}</label>
          <select className="text-input" value={recipeId} onChange={(e) => setRecipeId(e.target.value)}>
            {recipes.map((r) => <option key={r.id} value={r.id}>{r.code} · {r.name}</option>)}
          </select>
        </div>
      </div>

      <div className="toolbar">
        <Perms code={INTEGRATION_OPERATE}>
          <button className="btn btn-primary btn-sm" disabled={busy} onClick={add}>
            {t('rms.integration.ppid.register')}
          </button>
        </Perms>
      </div>

      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.integration.ppid.scope')}</th>
              <th>{t('rms.integration.ppid.ppidOnTool')}</th>
              <th>{t('rms.integration.recipe')}</th>
              <th>{t('rms.common.createTime')}</th>
              <th style={{ width: 90 }}>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((m) => (
              <tr key={m.id}>
                <td>
                  <span className="tag">{m.deviceId ? t('rms.integration.ppid.scopeDevice') : t('rms.integration.ppid.scopeType')}</span>
                  <span className="mono" style={{ marginLeft: 8 }}>{m.deviceCode ?? m.deviceTypeName ?? '—'}</span>
                </td>
                <td className="mono">{m.ppidOnTool}</td>
                <td className="mono">{m.recipeCode ?? m.recipeId}</td>
                <td className="muted">{m.createTime ?? '—'}</td>
                <td className="cell-actions">
                  <Perms code={INTEGRATION_OPERATE}>
                    <button className="btn btn-ghost btn-sm" onClick={() => remove(m.id)}>{t('rms.common.delete')}</button>
                  </Perms>
                </td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={5} className="empty-cell">{t('rms.integration.ppid.empty')}</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** 集成调试台：以 EAP/MES 视角发起 IF-E3 在线比对与 IF-M1 Run 校验，支持三种比对方式。 */
function SimPanel({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation();
  const [devices, setDevices] = useState<Device[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [toolId, setToolId] = useState('');
  const [recipeUid, setRecipeUid] = useState('');
  const [mode, setMode] = useState('FULL_BODY');
  const [baseline, setBaseline] = useState('ACTIVE');
  const [snapshot, setSnapshot] = useState('');
  const [verifyRequired, setVerifyRequired] = useState(true);
  const [cmp, setCmp] = useState<OnlineCompareResult | null>(null);
  const [chk, setChk] = useState<RunCheckResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.listDevices().then((d) => { setDevices(d); if (d.length) setToolId(d[0].id); });
    api.listRecipes().then((r) => { setRecipes(r); if (r.length) setRecipeUid(r[0].id); });
  }, []);

  async function doCheck() {
    setBusy(true); setErr(''); setChk(null);
    try {
      setChk(await api.runCheck({ toolId, recipeUid, versionPolicy: 'LATEST_ACTIVE', verifyRequired }));
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function doCompare() {
    setBusy(true); setErr(''); setCmp(null);
    try {
      const r = await api.onlineCompare({
        toolId, recipeUid, cmpMode: mode, baselineType: baseline,
        paramSnapshot: snapshot.trim() || null,
        correlationId: `sim-${Date.now()}`,
      });
      setCmp(r);
      onDone();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.integration.sim.title')}</h3>
        <span className="muted">{t('rms.integration.sim.sub')}</span>
      </div>
      {err && <div className="alert err"><span>{err}</span></div>}

      <div className="form-grid">
        <div className="field">
          <label className="field-label">{t('rms.integration.device')}</label>
          <select className="text-input" value={toolId} onChange={(e) => setToolId(e.target.value)}>
            {devices.map((d) => <option key={d.id} value={d.id}>{d.code} · {d.name}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.recipe')}</label>
          <select className="text-input" value={recipeUid} onChange={(e) => setRecipeUid(e.target.value)}>
            {recipes.map((r) => <option key={r.id} value={r.id}>{r.code} · {r.name}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.compare.mode')}</label>
          <select className="text-input" value={mode} onChange={(e) => setMode(e.target.value)}>
            {['FULL_BODY', 'PARAMETER', 'STRUCTURE'].map((m) => (
              <option key={m} value={m}>{t('rms.compare.mode.' + m)}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.sim.baseline')}</label>
          <select className="text-input" value={baseline} onChange={(e) => setBaseline(e.target.value)}>
            <option value="ACTIVE">{t('rms.integration.sim.baselineActive')}</option>
            <option value="GOLDEN">{t('rms.integration.sim.baselineGolden')}</option>
          </select>
        </div>
      </div>

      <div className="field" style={{ marginTop: 11 }}>
        <label className="field-label">{t('rms.integration.sim.snapshot')}</label>
        <textarea className="text-input" rows={3} value={snapshot}
          placeholder={t('rms.integration.sim.snapshotPh')} onChange={(e) => setSnapshot(e.target.value)} />
      </div>

      <div className="toolbar">
        <button className="btn btn-ghost btn-sm" disabled={busy} onClick={doCheck}>
          {t('rms.integration.sim.runCheck')}
        </button>
        <button className="btn btn-primary btn-sm" disabled={busy} onClick={doCompare}>
          {busy ? t('rms.common.loading') : t('rms.integration.sim.onlineCompare')}
        </button>
        <label className="check">
          <input type="checkbox" checked={verifyRequired} onChange={(e) => setVerifyRequired(e.target.checked)} />
          {t('rms.integration.sim.verifyRequired')}
        </label>
      </div>

      {chk && (
        <dl className="kv">
          <dt>{t('rms.integration.sim.checkResult')}</dt>
          <dd><span className={'tag ' + (chk.checkResult === 'QUALIFIED' ? 'ok' : 'err')}>{chk.checkResult}</span></dd>
          <dt>{t('rms.integration.sim.activeVersion')}</dt>
          <dd className="mono">{chk.activeVersion ?? '—'}</dd>
          <dt>{t('rms.integration.reason')}</dt>
          <dd className="muted">{chk.reason ?? '—'}</dd>
        </dl>
      )}

      {cmp && (
        <>
          <div style={{ marginTop: 12 }}>
            <span className={'tag ' + (cmp.cmpResult === 'PASS' ? 'ok' : cmp.cmpResult === 'BYPASSED' ? 'warn' : 'err')}>
              {cmp.cmpResult}
            </span>
            <span className="muted">
              {' · '}{t('rms.integration.sim.baselineVersion')} v{cmp.baselineVersion ?? '—'}
              {' · '}{cmp.durationMs} ms
            </span>
            {cmp.holdApplied && <span className="tag err" style={{ marginLeft: 8 }}>{t('rms.integration.sim.holdApplied')}</span>}
            {cmp.alarmRaised && <span className="tag warn" style={{ marginLeft: 6 }}>{t('rms.integration.sim.alarmRaised')}</span>}
          </div>
          {cmp.detail && <div className="alert warn" style={{ marginTop: 8 }}><span>{cmp.detail}</span></div>}
          {cmp.diffSummary.length > 0 && (
            <div className="table-wrap" style={{ marginTop: 8 }}>
              <table className="data">
                <thead>
                  <tr>
                    <th style={{ width: 200 }}>{t('rms.spec.paramKey')}</th>
                    <th>{t('rms.compare.expected')}</th>
                    <th>{t('rms.compare.actual')}</th>
                    <th style={{ width: 140 }}>{t('rms.compare.diffType')}</th>
                  </tr>
                </thead>
                <tbody>
                  {cmp.diffSummary.map((c, i) => (
                    <tr key={i} className="diff-changed">
                      <td className="mono">{c.paramKey}</td>
                      <td className="mono">{c.expected ?? '—'}</td>
                      <td className="mono">{c.actual ?? '—'}</td>
                      <td>{c.diffType ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}

/** 下发阶段 → 标签样式。 */
function stageTagClass(stage: string): string {
  if (stage === 'VERIFIED') return 'tag ok';
  if (stage === 'FAILED') return 'tag err';
  return 'tag warn';
}

/** 阶段机的合法下一步（与后端 DownloadStage.canMoveTo 对齐）。 */
function nextStage(stage: string): string | null {
  switch (stage) {
    case 'QUEUED': return 'GRANTED';
    case 'GRANTED': return 'SENT_TO_TOOL';
    case 'SENT_TO_TOOL': return 'TOOL_ACKED';
    case 'TOOL_ACKED': return 'VERIFIED';
    case 'FAILED': return 'QUEUED'; // 失败重试入队
    default: return null;           // VERIFIED 为终态
  }
}

/**
 * IF-E2 下发任务看板（Req §5.3 RecipeDownloadProgress）。
 *
 * <p>下发是异步长流程：受理后落一条任务，EAP 逐步回执 GRANTED→SENT_TO_TOOL→TOOL_ACKED，
 * 最终由回读 HASH 比对判定 VERIFIED（不一致则 FAILED 并报 RMS_DRIFT）。</p>
 */
function DownloadPanel({ onChanged }: { onChanged: () => void }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<DownloadJob[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [toolId, setToolId] = useState('');
  const [recipeUid, setRecipeUid] = useState('');
  const [ppid, setPpid] = useState('');
  const [mode, setMode] = useState('IMMEDIATE');
  const [pendingOnly, setPendingOnly] = useState(false);
  const [receipt, setReceipt] = useState<DownloadReceipt | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function load() {
    try {
      setRows(await api.listDownloads({ pendingOnly }));
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  useEffect(() => {
    load();
    api.listDevices().then((d) => { setDevices(d); if (d.length) setToolId(d[0].id); });
    api.listRecipes().then((r) => { setRecipes(r); if (r.length) setRecipeUid(r[0].id); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingOnly]);

  async function issue() {
    setBusy(true); setErr(''); setReceipt(null);
    try {
      const r = await api.downloadRecipe({
        toolId, recipeUid, ppidOnTool: ppid.trim() || null, downloadMode: mode as any,
        requestor: 'console', correlationId: `dl-${Date.now()}`,
      });
      setReceipt(r);
      await load();
      onChanged();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function advance(job: DownloadJob, mismatch = false) {
    const next = nextStage(job.stage);
    if (!next) return;
    setBusy(true); setErr('');
    try {
      await api.ackDownload(job.correlationId, {
        stage: next,
        toolAck: next === 'SENT_TO_TOOL' || next === 'TOOL_ACKED' ? 'ACK' : null,
        // 回读校验：正常用下发时的 bodyHash；mismatch 传错值以验证漂移判定
        readbackHash: next === 'VERIFIED' ? (mismatch ? 'deadbeef' : job.bodyHash) : null,
      });
      await load();
      onChanged();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function markFailed(job: DownloadJob) {
    const reason = window.prompt(t('rms.integration.dl.reasonPrompt'));
    if (reason === null) return;
    setBusy(true); setErr('');
    try {
      await api.ackDownload(job.correlationId, { stage: 'FAILED', errorDesc: reason });
      await load();
      onChanged();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.integration.dl.title')}</h3>
        <span className="muted">{t('rms.integration.dl.sub')}</span>
      </div>
      {err && <div className="alert err"><span>{err}</span></div>}
      {receipt && (
        <div className={`alert ${receipt.result === 'ACCEPTED' ? 'ok' : 'err'}`}>
          <span>
            {receipt.result} · {t('rms.integration.dl.stage')} {receipt.stage}
            {receipt.errorDesc ? ` · ${receipt.errorDesc}` : ''}
          </span>
        </div>
      )}

      <div className="form-grid">
        <div className="field">
          <label className="field-label">{t('rms.integration.device')}</label>
          <select className="text-input" value={toolId} onChange={(e) => setToolId(e.target.value)}>
            {devices.map((d) => <option key={d.id} value={d.id}>{d.code} · {d.name}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.recipe')}</label>
          <select className="text-input" value={recipeUid} onChange={(e) => setRecipeUid(e.target.value)}>
            {recipes.map((r) => <option key={r.id} value={r.id}>{r.code} · {r.name}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.dl.ppidOnTool')}</label>
          <input className="text-input" value={ppid} placeholder={t('rms.integration.ppid.ppidPh')}
            onChange={(e) => setPpid(e.target.value)} />
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.dl.mode')}</label>
          <select className="text-input" value={mode} onChange={(e) => setMode(e.target.value)}>
            <option value="IMMEDIATE">IMMEDIATE</option>
            <option value="ON_IDLE">ON_IDLE</option>
            <option value="BATCH">BATCH</option>
          </select>
        </div>
        <div className="field">
          <label className="field-label">&nbsp;</label>
          <Perms code={INTEGRATION_OPERATE}>
            <button className="btn btn-primary" disabled={busy || !toolId || !recipeUid} onClick={issue}>
              {t('rms.integration.dl.issue')}
            </button>
          </Perms>
        </div>
      </div>

      <div className="toolbar">
        <label className="check">
          <input type="checkbox" checked={pendingOnly}
            onChange={(e) => setPendingOnly(e.target.checked)} />
          {t('rms.integration.dl.pendingOnly')}
        </label>
        <button className="btn btn-ghost btn-sm" onClick={load}>{t('rms.common.refresh')}</button>
      </div>

      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.integration.dl.correlationId')}</th>
              <th>{t('rms.integration.device')}</th>
              <th>{t('rms.integration.dl.ppidOnTool')}</th>
              <th>{t('rms.integration.dl.mode')}</th>
              <th>{t('rms.integration.dl.stage')}</th>
              <th>{t('rms.integration.dl.toolAck')}</th>
              <th>{t('rms.integration.dl.error')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((x) => {
              const next = nextStage(x.stage);
              return (
                <tr key={x.id}>
                  <td className="mono">{x.correlationId}</td>
                  <td className="mono">{x.deviceId}</td>
                  <td className="mono">{x.ppidOnTool ?? '—'}</td>
                  <td className="mono">{x.downloadMode}</td>
                  <td><span className={stageTagClass(x.stage)}>{x.stage}</span></td>
                  <td className="mono">{x.toolAck ?? '—'}</td>
                  <td className="muted">{x.errorDesc ?? '—'}</td>
                  <td className="cell-actions">
                    <Perms code={INTEGRATION_OPERATE}>
                      {next && (
                        <button className="btn btn-ghost btn-sm" disabled={busy}
                          onClick={() => advance(x)}>
                          {x.stage === 'FAILED' ? t('rms.integration.dl.retry') : `→ ${next}`}
                        </button>
                      )}
                      {x.stage === 'TOOL_ACKED' && (
                        <button className="btn btn-ghost btn-sm" disabled={busy}
                          onClick={() => advance(x, true)}>
                          {t('rms.integration.dl.readbackMismatch')}
                        </button>
                      )}
                      {!x.terminal && (
                        <button className="btn btn-ghost btn-sm btn-danger" disabled={busy}
                          onClick={() => markFailed(x)}>
                          {t('rms.integration.dl.fail')}
                        </button>
                      )}
                    </Perms>
                  </td>
                </tr>
              );
            })}
            {!busy && rows.length === 0 && (
              <tr><td colSpan={8} className="empty-cell">{t('rms.integration.dl.empty')}</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** 设备事件 → 标签样式（漂移最重、报警次之）。 */
function evtTagClass(e: EquipmentEvent): string {
  if (e.driftDetected) return 'tag err';
  if (e.alarmRaised) return 'tag warn';
  return 'tag ok';
}

/**
 * GEM 设备事件面板（SEMI E30）：机台主动上报的配方变更（PPChange）与机台校验失败（PPVerificationFailed）。
 *
 * <p>事件驱动与「巡检」互补——工程师在机台改完配方，主机几秒内即知，不必等下一轮巡检；
 * 校验失败还会联动把在途下发任务判 FAILED。</p>
 */
function GemEventPanel() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<EquipmentEvent[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [toolId, setToolId] = useState('');
  const [eventType, setEventType] = useState<'PP_CHANGE' | 'PP_VERIFICATION_FAILED'>('PP_CHANGE');
  const [ppid, setPpid] = useState('');
  const [changeType, setChangeType] = useState<'CREATED' | 'EDITED' | 'DELETED'>('EDITED');
  const [errorDesc, setErrorDesc] = useState('');
  const [holdOnDrift, setHoldOnDrift] = useState(true);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [last, setLast] = useState<EquipmentEvent | null>(null);

  async function load() {
    try {
      setRows(await api.listEquipmentEvents());
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  useEffect(() => {
    load();
    api.listDevices().then((d) => { setDevices(d); if (d.length) setToolId(d[0].id); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function post() {
    if (!toolId || !ppid.trim()) { setErr(t('rms.integration.evt.required')); return; }
    setBusy(true); setErr(''); setLast(null);
    try {
      const r = await api.postEquipmentEvent({
        eventId: `evt-${Date.now()}`,
        toolId,
        eventType,
        ppid: ppid.trim(),
        changeType: eventType === 'PP_CHANGE' ? changeType : null,
        errorDesc: eventType === 'PP_VERIFICATION_FAILED' ? (errorDesc.trim() || null) : null,
        holdOnDrift,
        correlationId: `corr-evt-${Date.now()}`,
      });
      setLast(r);
      await load();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.integration.evt.title')}</h3>
        <span className="muted">{t('rms.integration.evt.sub')}</span>
      </div>
      {err && <div className="alert err"><span>{err}</span></div>}
      {last && (
        <div className={`alert ${last.alarmRaised ? 'err' : 'ok'}`}>
          <span>
            {last.eventType} · {last.ppid ?? t('rms.common.dash')} · {last.handlingResult}
            {last.driftDetected ? ` · ${t('rms.integration.evt.drift')}` : ''}
            {last.holdApplied ? ` · ${t('rms.integration.evt.held')}` : ''}
          </span>
        </div>
      )}

      <div className="form-grid">
        <div className="field">
          <label className="field-label">{t('rms.integration.device')}</label>
          <select className="text-input" value={toolId} onChange={(e) => setToolId(e.target.value)}>
            {devices.map((d) => <option key={d.id} value={d.id}>{d.code} · {d.name}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.evt.eventType')}</label>
          <select className="text-input" value={eventType}
            onChange={(e) => setEventType(e.target.value as any)}>
            <option value="PP_CHANGE">PP_CHANGE</option>
            <option value="PP_VERIFICATION_FAILED">PP_VERIFICATION_FAILED</option>
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.integration.evt.ppid')}</label>
          <input className="text-input" value={ppid} placeholder="ETCH_STD_01"
            onChange={(e) => setPpid(e.target.value)} />
        </div>
        {eventType === 'PP_CHANGE' ? (
          <div className="field">
            <label className="field-label">{t('rms.integration.evt.changeType')}</label>
            <select className="text-input" value={changeType}
              onChange={(e) => setChangeType(e.target.value as any)}>
              <option value="CREATED">CREATED</option>
              <option value="EDITED">EDITED</option>
              <option value="DELETED">DELETED</option>
            </select>
          </div>
        ) : (
          <div className="field">
            <label className="field-label">{t('rms.integration.evt.ppError')}</label>
            <input className="text-input" value={errorDesc} placeholder="STEP3 out of range"
              onChange={(e) => setErrorDesc(e.target.value)} />
          </div>
        )}
        <div className="field">
          <label className="field-label">&nbsp;</label>
          <label className="check">
            <input type="checkbox" checked={holdOnDrift}
              onChange={(e) => setHoldOnDrift(e.target.checked)} />
            {t('rms.integration.evt.holdOnDrift')}
          </label>
        </div>
        <div className="field">
          <label className="field-label">&nbsp;</label>
          <Perms code={INTEGRATION_OPERATE}>
            <button className="btn btn-primary" disabled={busy} onClick={post}>
              {t('rms.integration.evt.report')}
            </button>
          </Perms>
        </div>
      </div>

      <div className="toolbar">
        <button className="btn btn-ghost btn-sm" onClick={load}>{t('rms.common.refresh')}</button>
      </div>

      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.integration.device')}</th>
              <th>{t('rms.integration.evt.eventType')}</th>
              <th>{t('rms.integration.evt.ppid')}</th>
              <th>{t('rms.integration.evt.changeType')}</th>
              <th>{t('rms.integration.evt.flags')}</th>
              <th>{t('rms.integration.evt.handlingResult')}</th>
              <th>{t('rms.integration.evt.occurredAt')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.deviceCode ?? x.toolId}</td>
                <td className="mono">{x.eventType}</td>
                <td className="mono">{x.ppid ?? t('rms.common.dash')}</td>
                <td className="mono">{x.changeType ?? t('rms.common.dash')}</td>
                <td>
                  <span className={evtTagClass(x)}>
                    {x.driftDetected ? 'DRIFT' : x.alarmRaised ? 'ALARM' : 'OK'}
                  </span>
                  {x.holdApplied && <span className="tag warn" style={{ marginLeft: 4 }}>HOLD</span>}
                </td>
                <td className="muted">{x.handlingResult ?? t('rms.common.dash')}</td>
                <td className="muted">{x.occurredAt ?? t('rms.common.dash')}</td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan={7} className="empty-cell">{t('rms.integration.evt.empty')}</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** 核对结论 → 标签样式。 */
function invTagClass(r: string): string {
  if (r === 'ORPHAN') return 'tag warn';
  if (r === 'DRIFT') return 'tag err';
  return 'tag ok';
}

/**
 * 机台配方清单面板（S7F19 目录核对）：回答「机台上到底装了哪些配方，和 RMS 对得上吗」。
 *
 * <p>识别三类风险：ORPHAN（机台有、RMS 无映射 → 未纳管）、DRIFT（机台 HASH 与生效版不一致）、
 * 以及 MISSING（RMS 有映射、机台未上报）。</p>
 */
function InventoryPanel() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<InventoryRow[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [toolId, setToolId] = useState('');
  const [text, setText] = useState('');
  const [report, setReport] = useState<InventoryReport | null>(null);
  const [summary, setSummary] = useState<InventorySummary | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function load(id: string) {
    try {
      setRows(await api.listToolInventory(id ? { deviceId: id } : {}));
      setSummary(await api.toolInventorySummary(id || undefined));
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  useEffect(() => {
    api.listDevices().then((d) => {
      setDevices(d);
      const first = d.length ? d[0].id : '';
      setToolId(first);
      load(first);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function submit() {
    if (!toolId) { setErr(t('rms.integration.inv.required')); return; }
    setBusy(true); setErr(''); setReport(null);
    try {
      const items = text.split('\n').map((l) => l.trim()).filter(Boolean).map((line) => {
        const eq = line.indexOf('=');
        return eq < 0
          ? { ppid: line, bodyHash: null as string | null }
          : { ppid: line.slice(0, eq).trim(), bodyHash: line.slice(eq + 1).trim() };
      });
      setReport(await api.reportToolInventory({
        toolId, correlationId: `inv-${Date.now()}`, items,
      }));
      await load(toolId);
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.integration.inv.title')}</h3>
        <span className="muted">{t('rms.integration.inv.sub')}</span>
      </div>
      {err && <div className="alert err"><span>{err}</span></div>}
      {report && (
        <div className={`alert ${report.orphan > 0 || report.drift > 0 ? 'err' : 'ok'}`}>
          <span>
            {t('rms.integration.inv.report')}: {t('rms.integration.inv.total')} {report.total}
            {' · OK '}{report.ok}{' · DRIFT '}{report.drift}{' · ORPHAN '}{report.orphan}
            {report.missing.length > 0
              ? ` · ${t('rms.integration.inv.missing')}: ${report.missing.join(', ')}`
              : ''}
          </span>
        </div>
      )}

      {summary && (
        <div className="kpi-grid">
          <div className="kpi"><div className="kpi-main">
            <div className="kpi-label">{t('rms.integration.inv.total')}</div>
            <div className="kpi-val">{summary.total}</div></div></div>
          <div className="kpi ok"><div className="kpi-main">
            <div className="kpi-label">OK</div>
            <div className="kpi-val">{summary.ok}</div></div></div>
          <div className="kpi warn"><div className="kpi-main">
            <div className="kpi-label">DRIFT</div>
            <div className="kpi-val">{summary.drift}</div></div></div>
          <div className="kpi warn"><div className="kpi-main">
            <div className="kpi-label">ORPHAN</div>
            <div className="kpi-val">{summary.orphan}</div></div></div>
        </div>
      )}

      <div className="form-grid">
        <div className="field">
          <label className="field-label">{t('rms.integration.device')}</label>
          <select className="text-input" value={toolId}
            onChange={(e) => { setToolId(e.target.value); load(e.target.value); }}>
            {devices.map((d) => <option key={d.id} value={d.id}>{d.code} · {d.name}</option>)}
          </select>
        </div>
        <div className="field" style={{ gridColumn: 'span 2' }}>
          <label className="field-label">{t('rms.integration.inv.items')}</label>
          <textarea className="text-input" rows={3} value={text}
            placeholder={t('rms.integration.inv.itemsPh')}
            onChange={(e) => setText(e.target.value)} />
        </div>
        <div className="field">
          <label className="field-label">&nbsp;</label>
          <Perms code={INTEGRATION_OPERATE}>
            <button className="btn btn-primary" disabled={busy} onClick={submit}>
              {t('rms.integration.inv.reportAction')}
            </button>
          </Perms>
        </div>
      </div>

      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.integration.inv.ppid')}</th>
              <th>{t('rms.integration.recipe')}</th>
              <th>{t('rms.integration.inv.toolHash')}</th>
              <th>{t('rms.integration.inv.expectedHash')}</th>
              <th>{t('rms.integration.inv.result')}</th>
              <th>{t('rms.integration.inv.lastSeen')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.ppidOnTool}</td>
                <td className="mono">{x.recipeCode ?? t('rms.common.dash')}</td>
                <td className="mono">{shortHash(x.toolBodyHash)}</td>
                <td className="mono">{shortHash(x.expectedHash)}</td>
                <td><span className={invTagClass(x.reconcileResult)}>{x.reconcileResult}</span></td>
                <td className="muted">{x.lastSeenAt ?? t('rms.common.dash')}</td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan={6} className="empty-cell">{t('rms.integration.inv.empty')}</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** HASH 缩略展示（前后各 8 位）。 */
function shortHash(h: string | null): string {
  if (!h) return '—';
  return h.length <= 20 ? h : `${h.slice(0, 8)}…${h.slice(-8)}`;
}
