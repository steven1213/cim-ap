import { useEffect, useState } from 'react';
import * as api from '@/lib/api';
import type { AuditEvent } from '@/types';
import { AUDIT_LABELS, auditLabel, auditTone, fmtDateTime } from '@/lib/format';
import Panel from '@/components/Panel';
import { IconAlert, IconList, IconRefresh } from '@/components/Icons';

/** 审计日志（需 iam-ap:ADMIN）：关键动作流水，支持按类型过滤。 */
export default function AuditPage() {
  const [rows, setRows] = useState<AuditEvent[]>([]);
  const [type, setType] = useState('');
  const [limit, setLimit] = useState(200);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    setErr('');
    try {
      setRows(
        await api.get<AuditEvent[]>('/admin/audit', {
          limit,
          ...(type ? { type } : {}),
        }),
      );
    } catch (e: any) {
      setErr(e?.msg || '加载审计日志失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, limit]);

  const types = Object.keys(AUDIT_LABELS);

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}

      <Panel
        title="审计流水"
        sub="登录、登出、改密、应用与准入变更、强制下线等动作的追溯记录"
        flush
        actions={
          <>
            <select className="select-sm" value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">全部类型</option>
              {types.map((t) => (
                <option key={t} value={t}>
                  {AUDIT_LABELS[t]}
                </option>
              ))}
            </select>
            <select className="select-sm" value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
              <option value={100}>最近 100</option>
              <option value={200}>最近 200</option>
              <option value={500}>最近 500</option>
            </select>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? '刷新中' : '刷新'}
            </button>
          </>
        }
      >
        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 180 }}>时间</th>
                  <th style={{ width: 130 }}>类型</th>
                  <th style={{ width: 70 }}>结果</th>
                  <th style={{ width: 120 }}>操作人</th>
                  <th style={{ width: 140 }}>对象</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => (
                  <tr key={e.id}>
                    <td className="mono dim">{fmtDateTime(e.createTime)}</td>
                    <td>
                      <span className={'tag ' + auditTone(e.type, e.result)}>{auditLabel(e.type)}</span>
                    </td>
                    <td>
                      <span className={'status'}>
                        <i className={'led ' + (e.result === 'FAILURE' ? 'err' : 'ok')} />
                        <b>{e.result === 'FAILURE' ? '失败' : '成功'}</b>
                      </span>
                    </td>
                    <td className="mono">{e.actor ?? '—'}</td>
                    <td className="mono">{e.subject ?? '—'}</td>
                    <td className="dim">{e.detail ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconList width={22} height={22} />
            <b>暂无审计事件</b>
            <span>关键动作发生后会自动记录</span>
          </div>
        )}
      </Panel>

      <Panel title="记录范围" sub="审计埋点覆盖的关键路径">
        <div className="tag-list">
          {types.map((t) => (
            <span key={t} className={'tag ' + auditTone(t)}>
              {AUDIT_LABELS[t]}
            </span>
          ))}
        </div>
      </Panel>
    </div>
  );
}
