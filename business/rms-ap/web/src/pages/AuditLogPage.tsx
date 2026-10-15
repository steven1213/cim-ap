import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Panel from '@/components/Panel';
import Drawer from '@/components/Drawer';
import * as api from '@/lib/rmsApi';
import type { AuditRow, AuditSummary } from '@/types';
import {
  IconAlert,
  IconCheckCircle,
  IconInbox,
  IconList,
  IconRefresh,
  IconSearch,
  IconSync,
} from '@/components/Icons';

/** 已知动作类型（供类型过滤下拉；标签经 `rms.audit.t.*` 本地化，缺失回退原码）。 */
const AUDIT_TYPES = [
  'RECIPE_CREATED', 'RECIPE_UPDATED', 'RECIPE_DELETED', 'RECIPE_COPIED',
  'RECIPE_VERSION_CREATED', 'RECIPE_ACTIVATED',
  'SIGNOFF_SUBMITTED', 'SIGNOFF_APPROVED', 'SIGNOFF_REJECTED',
  'SIGNOFF_TEMP_ALLOWED', 'SIGNOFF_TEMP_CANCELLED',
  'COMPARE_RUN', 'COMPARE_BATCH',
  'DEVICE_TYPE_CREATED', 'DEVICE_TYPE_UPDATED', 'DEVICE_TYPE_DELETED',
  'DEVICE_AREA_CREATED', 'DEVICE_AREA_UPDATED', 'DEVICE_AREA_DELETED',
  'DEVICE_CREATED', 'DEVICE_UPDATED', 'DEVICE_DELETED',
];

/** 动作码 → 标签着色。 */
function tone(type: string): string {
  if (type.endsWith('_DELETED') || type === 'SIGNOFF_REJECTED') return 'tag err';
  if (type === 'SIGNOFF_TEMP_CANCELLED') return 'tag warn';
  if (type.startsWith('SIGNOFF_')) return type === 'SIGNOFF_APPROVED' ? 'tag ok' : 'tag warn';
  if (type.startsWith('COMPARE_')) return type === 'COMPARE_RUN' ? 'tag' : 'tag pri';
  if (type.endsWith('_ACTIVATED')) return 'tag ok';
  return 'tag';
}

/**
 * 审计日志（Req 12/17/18/19、FR-H1~H3）：多维过滤 + 汇总统计 + 配方对象历史 + 详情抽屉。
 */
export default function AuditLogPage() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [summary, setSummary] = useState<AuditSummary | null>(null);
  const [type, setType] = useState('');
  const [result, setResult] = useState('');
  const [actor, setActor] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [keyword, setKeyword] = useState('');
  const [limit, setLimit] = useState(200);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState('');
  const [detail, setDetail] = useState<AuditRow | null>(null);

  // 配方对象历史
  const [hCode, setHCode] = useState('');
  const [hFrom, setHFrom] = useState('');
  const [hTo, setHTo] = useState('');
  const [hRows, setHRows] = useState<AuditRow[] | null>(null);
  const [hErr, setHErr] = useState('');

  async function load() {
    setLoading(true);
    setErr('');
    try {
      const [list, sum] = await Promise.all([
        api.listAudit({
          limit,
          ...(type ? { type } : {}),
          ...(result ? { result } : {}),
          ...(actor ? { actor } : {}),
          ...(from ? { from } : {}),
          ...(to ? { to } : {}),
          ...(keyword ? { keyword } : {}),
        }),
        api.auditSummary(),
      ]);
      setRows(list);
      setSummary(sum);
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [limit]);

  async function queryHistory() {
    if (!hCode.trim()) {
      setHErr(t('rms.audit.history.codeRequired'));
      return;
    }
    setHErr('');
    try {
      setHRows(await api.recipeHistory({
        code: hCode.trim(), ...(hFrom ? { from: hFrom } : {}), ...(hTo ? { to: hTo } : {}), max: 200,
      }));
    } catch (e: any) {
      setHErr(e?.message || 'error');
    }
  }

  function typeLabel(tp: string): string {
    return t(`rms.audit.t.${tp}`, { defaultValue: tp });
  }

  function resultCell(r: string) {
    return (
      <span className="status">
        <i className={'led ' + (r === 'FAILURE' ? 'err' : 'ok')} />
        <b>{r === 'FAILURE' ? t('rms.audit.fail') : t('rms.audit.ok')}</b>
      </span>
    );
  }

  return (
    <div className="page">
      {err && <div className="alert err"><IconAlert width={15} height={15} /><span>{err}</span></div>}

      <div className="page-head">
        <div>
          <h1>{t('rms.audit.title')}</h1>
          <p className="dim" style={{ margin: '2px 0 0' }}>{t('rms.audit.sub')}</p>
        </div>
      </div>

      {/* KPI */}
      <div className="kpi-grid">
        <div className="kpi">
          <div className="kpi-ico"><IconList width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.audit.kpi.total')}</div>
            <div className="kpi-val">{summary?.total ?? '—'}</div>
          </div>
        </div>
        <div className="kpi ok">
          <div className="kpi-ico"><IconCheckCircle width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.audit.kpi.success')}</div>
            <div className="kpi-val">{summary?.success ?? '—'}</div>
          </div>
        </div>
        <div className="kpi warn">
          <div className="kpi-ico"><IconAlert width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.audit.kpi.failure')}</div>
            <div className="kpi-val">{summary?.failure ?? '—'}</div>
          </div>
        </div>
        <div className="kpi info">
          <div className="kpi-ico"><IconSync width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.audit.kpi.today')}</div>
            <div className="kpi-val">{summary?.today ?? '—'}</div>
          </div>
        </div>
      </div>

      {/* 动作分布 */}
      {summary && summary.topTypes.length > 0 && (
        <Panel title={t('rms.audit.topTypes')}>
          <div className="tag-list">
            {summary.topTypes.map((tc) => (
              <span key={tc.type} className={tone(tc.type)} title={tc.type}>
                {typeLabel(tc.type)}<b className="tag-count">{tc.count}</b>
              </span>
            ))}
          </div>
        </Panel>
      )}

      {/* 审计流水 */}
      <Panel title={t('rms.audit.title')} sub={t('rms.audit.sub')} flush
        actions={
          <button className="btn btn-ghost btn-sm" onClick={load} disabled={loading}>
            <IconRefresh width={13} height={13} />
            {loading ? t('rms.audit.refreshing') : t('rms.audit.refresh')}
          </button>
        }>
        <div className="toolbar">
          <select className="select-sm" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">{t('rms.audit.filterType')}</option>
            {AUDIT_TYPES.map((tp) => <option key={tp} value={tp}>{typeLabel(tp)}</option>)}
          </select>
          <select className="select-sm" value={result} onChange={(e) => setResult(e.target.value)}>
            <option value="">{t('rms.audit.filterResult')}</option>
            <option value="SUCCESS">{t('rms.audit.resultOk')}</option>
            <option value="FAILURE">{t('rms.audit.resultFail')}</option>
          </select>
          <input className="text-input" style={{ width: 130 }} placeholder={t('rms.audit.actorPh')}
            value={actor} onChange={(e) => setActor(e.target.value)} />
          <input type="date" className="text-input" style={{ width: 145 }} value={from}
            onChange={(e) => setFrom(e.target.value)} title={t('rms.audit.filterFrom')} />
          <input type="date" className="text-input" style={{ width: 145 }} value={to}
            onChange={(e) => setTo(e.target.value)} title={t('rms.audit.filterTo')} />
          <div className="search-inline">
            <IconSearch width={13} height={13} />
            <input className="text-input" placeholder={t('rms.audit.keywordPh')} value={keyword}
              onChange={(e) => setKeyword(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') load(); }} />
          </div>
          <select className="select-sm" value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
            <option value={100}>{t('rms.audit.recent100')}</option>
            <option value={200}>{t('rms.audit.recent200')}</option>
            <option value={500}>{t('rms.audit.recent500')}</option>
          </select>
          <button className="btn btn-primary btn-sm" onClick={load}>
            <IconSearch width={13} height={13} /> {t('rms.common.search')}
          </button>
        </div>

        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 160 }}>{t('rms.audit.th.time')}</th>
                  <th style={{ width: 150 }}>{t('rms.audit.th.type')}</th>
                  <th style={{ width: 80 }}>{t('rms.audit.th.result')}</th>
                  <th style={{ width: 110 }}>{t('rms.audit.th.actor')}</th>
                  <th style={{ width: 140 }}>{t('rms.audit.th.target')}</th>
                  <th>{t('rms.audit.th.detail')}</th>
                  <th style={{ width: 90 }}>{t('rms.common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td className="mono dim">{r.createTime ?? t('rms.common.dash')}</td>
                    <td><span className={tone(r.type)}>{typeLabel(r.type)}</span></td>
                    <td>{resultCell(r.result)}</td>
                    <td className="mono">{r.actor ?? t('rms.common.dash')}</td>
                    <td className="mono">{r.target ?? t('rms.common.dash')}</td>
                    <td className="dim">{r.detail ?? t('rms.common.dash')}</td>
                    <td className="cell-actions">
                      <button className="btn btn-ghost btn-sm" onClick={() => setDetail(r)}>
                        {t('rms.audit.detail')}
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
            <b>{t('rms.audit.empty')}</b>
            <span>{t('rms.audit.emptyHint')}</span>
          </div>
        )}
      </Panel>

      {/* 配方对象历史（FR-H3 / Req 19） */}
      <Panel title={t('rms.audit.history.title')} sub={t('rms.audit.history.sub')} flush>
        <div className="toolbar">
          <input className="text-input" style={{ width: 200 }} placeholder={t('rms.audit.history.codePh')}
            value={hCode} onChange={(e) => setHCode(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') queryHistory(); }} />
          <input type="date" className="text-input" style={{ width: 145 }} value={hFrom}
            onChange={(e) => setHFrom(e.target.value)} title={t('rms.audit.filterFrom')} />
          <input type="date" className="text-input" style={{ width: 145 }} value={hTo}
            onChange={(e) => setHTo(e.target.value)} title={t('rms.audit.filterTo')} />
          <button className="btn btn-primary btn-sm" onClick={queryHistory}>
            <IconSearch width={13} height={13} /> {t('rms.audit.history.query')}
          </button>
        </div>
        {hErr && <div className="alert err" style={{ margin: '0 14px 12px' }}><IconAlert width={15} height={15} /><span>{hErr}</span></div>}
        {hRows && (
          hRows.length ? (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th style={{ width: 160 }}>{t('rms.audit.th.time')}</th>
                    <th style={{ width: 150 }}>{t('rms.audit.th.type')}</th>
                    <th style={{ width: 110 }}>{t('rms.audit.th.actor')}</th>
                    <th>{t('rms.audit.th.detail')}</th>
                  </tr>
                </thead>
                <tbody>
                  {hRows.map((r) => (
                    <tr key={r.id}>
                      <td className="mono dim">{r.createTime ?? t('rms.common.dash')}</td>
                      <td><span className={tone(r.type)}>{typeLabel(r.type)}</span></td>
                      <td className="mono">{r.actor ?? t('rms.common.dash')}</td>
                      <td className="dim">{r.detail ?? t('rms.common.dash')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="empty">
              <IconInbox width={22} height={22} />
              <b>{t('rms.audit.history.empty')}</b>
            </div>
          )
        )}
      </Panel>

      {detail && (
        <Drawer title={<><IconList width={15} height={15} /> {t('rms.audit.detail')}</>} onClose={() => setDetail(null)}>
          <dl className="kv">
            <dt>{t('rms.audit.th.time')}</dt>
            <dd className="mono">{detail.createTime ?? t('rms.common.dash')}</dd>
            <dt>{t('rms.audit.th.type')}</dt>
            <dd><span className={tone(detail.type)}>{typeLabel(detail.type)}</span></dd>
            <dt>{t('rms.audit.th.result')}</dt>
            <dd>{resultCell(detail.result)}</dd>
            <dt>{t('rms.audit.th.actor')}</dt>
            <dd className="mono">{detail.actor ?? t('rms.common.dash')}</dd>
            <dt>{t('rms.audit.th.target')}</dt>
            <dd className="mono">{detail.target ?? t('rms.common.dash')}</dd>
            <dt>{t('rms.audit.th.detail')}</dt>
            <dd>{detail.detail ?? t('rms.common.dash')}</dd>
          </dl>
        </Drawer>
      )}
    </div>
  );
}
