import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { AuditEvent } from '@/types';
import { auditLabel, auditTone, fmtDateTime } from '@/lib/format';

/** 审计事件类型（用于类型过滤下拉与覆盖范围展示；标签经 `auditLabel` 翻译）。 */
const AUDIT_TYPES = [
  'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGIN_REJECTED', 'LOGOUT',
  'PASSWORD_CHANGED', 'PASSWORD_RESET',
  'USER_CREATED', 'USER_ENABLED', 'USER_DISABLED', 'USER_DELETED', 'USER_UNLOCKED',
  'APP_REGISTERED', 'APP_UPDATED',
  'ADMISSION_GRANTED', 'ADMISSION_REVOKED', 'SESSION_REVOKED',
  'ORG_CREATED', 'ORG_UPDATED', 'ORG_MOVED', 'ORG_DELETED',
  'PROFILE_CREATED', 'PROFILE_UPDATED', 'USER_ORGS_CHANGED',
  'ORG_GRANT_GRANTED', 'ORG_GRANT_REVOKED', 'DIRECTORY_SYNCED',
];
import Panel from '@/components/Panel';
import { IconAlert, IconList, IconRefresh } from '@/components/Icons';

/** 审计日志（路由级 `RequirePerm(i18n)`，需 `iam:audit:list`）：关键动作流水，支持按类型过滤。 */
export default function AuditPage() {
  const { t } = useTranslation();
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
      setErr(e?.msg || t('iam.audit.errLoad'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, limit]);

  const types = AUDIT_TYPES;

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}

      <Panel
        title={t('iam.audit.title')}
        sub={t('iam.audit.sub')}
        flush
        actions={
          <>
            <select className="select-sm" value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">{t('iam.audit.filterType')}</option>
              {types.map((t) => (
                <option key={t} value={t}>
                  {auditLabel(t)}
                </option>
              ))}
            </select>
            <select className="select-sm" value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
              <option value={100}>{t('iam.audit.recent100')}</option>
              <option value={200}>{t('iam.audit.recent200')}</option>
              <option value={500}>{t('iam.audit.recent500')}</option>
            </select>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.audit.refreshing') : t('iam.audit.refresh')}
            </button>
          </>
        }
      >
        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 180 }}>{t('iam.audit.th.time')}</th>
                  <th style={{ width: 130 }}>{t('iam.audit.th.type')}</th>
                  <th style={{ width: 70 }}>{t('iam.audit.th.result')}</th>
                  <th style={{ width: 120 }}>{t('iam.audit.th.operator')}</th>
                  <th style={{ width: 140 }}>{t('iam.audit.th.target')}</th>
                  <th>{t('iam.audit.th.detail')}</th>
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
                        <b>{e.result === 'FAILURE' ? t('iam.audit.resultFail') : t('iam.audit.resultOk')}</b>
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
            <b>{t('iam.audit.emptyTitle')}</b>
            <span>{t('iam.audit.emptyHint')}</span>
          </div>
        )}
      </Panel>

      <Panel title={t('iam.audit.scopeTitle')} sub={t('iam.audit.scopeSub')}>
        <div className="tag-list">
          {types.map((t) => (
            <span key={t} className={'tag ' + auditTone(t)}>
              {auditLabel(t)}
            </span>
          ))}
        </div>
      </Panel>
    </div>
  );
}
