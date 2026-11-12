import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimSelect,
  CimTag,
  DataTable,
  Page,
  Panel,
  type DataTableColumn,
} from '@cim/ui';
import type { StatusTone } from '@cim/theme';
import * as api from '@/lib/api';
import type { AuditEvent } from '@/types';
import { auditLabel, auditTone, fmtDateTime } from '@/lib/format';

/**
 * 审计事件类型（用于类型过滤下拉与覆盖范围展示；标签经 `auditLabel` 翻译）。
 *
 * <p>顺序按「登录 → 口令 → 用户 → 接入 → 会话 → 组织 → 授权」分组，与后端枚举声明顺序一致，
 * 便于人工对数：新增事件类型时漏加到这里，下拉里就选不到（覆盖范围面板会立刻显示缺口）。</p>
 */
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

/**
 * `auditTone` 的 4 档（ok/warn/err/info）→ 平台语义 5 档。
 *
 * <p>`info` 落 `run`（蓝）：平台只承认 ok/warn/err/run/idle 五档，没有「中性蓝」这一档，
 * 而 `idle`（灰）会被读成「无状态」，用在常规事件上语义反了。</p>
 */
function toneOf(type: string, result?: string | null): StatusTone {
  const t = auditTone(type, result);
  return t === 'info' ? 'run' : t;
}

const LIMIT_OPTIONS = [
  { value: '100', labelKey: 'iam.audit.recent100' },
  { value: '200', labelKey: 'iam.audit.recent200' },
  { value: '500', labelKey: 'iam.audit.recent500' },
];

/** 审计日志（路由级 `RequirePerm(i18n)`，需 `iam:audit:list`）：关键动作流水，支持按类型过滤。 */
export default function AuditPage() {
  const { t } = useTranslation();
  const [type, setType] = useState('');
  const [limit, setLimit] = useState('200');

  const typeOptions = useMemo(
    () => [{ value: '', label: t('iam.audit.filterType') }, ...AUDIT_TYPES.map((v) => ({ value: v, label: auditLabel(v) }))],
    [t],
  );

  const listRes = useResource<AuditEvent[]>(
    ({ signal }) =>
      api.get<AuditEvent[]>(
        '/admin/audit',
        { limit: Number(limit), ...(type ? { type } : {}) },
        { signal },
      ),
    // keepPreviousData：切类型时保留上一批结果占位，表格不闪空再跳回来
    { deps: [type, limit], keepPreviousData: true },
  );

  const rows = listRes.data ?? [];

  const columns = useMemo<Array<DataTableColumn<AuditEvent>>>(
    () => [
      {
        key: 'createTime',
        titleKey: 'iam.audit.th.time',
        width: 180,
        mono: true,
        render: (e) => <span className="dim">{fmtDateTime(e.createTime)}</span>,
      },
      {
        key: 'type',
        titleKey: 'iam.audit.th.type',
        width: 150,
        render: (e) => (
          <CimTag size="small" tone={toneOf(e.type, e.result)}>
            {auditLabel(e.type)}
          </CimTag>
        ),
      },
      {
        key: 'result',
        titleKey: 'iam.audit.th.result',
        width: 90,
        render: (e) => (
          <span className="status">
            <i className={'led ' + (e.result === 'FAILURE' ? 'err' : 'ok')} />
            <b>{e.result === 'FAILURE' ? t('iam.audit.resultFail') : t('iam.audit.resultOk')}</b>
          </span>
        ),
      },
      {
        key: 'actor',
        titleKey: 'iam.audit.th.operator',
        width: 140,
        mono: true,
        ellipsis: true,
        render: (e) => e.actor ?? '—',
      },
      {
        key: 'subject',
        titleKey: 'iam.audit.th.target',
        width: 160,
        mono: true,
        ellipsis: true,
        render: (e) => e.subject ?? '—',
      },
      {
        key: 'detail',
        titleKey: 'iam.audit.th.detail',
        ellipsis: true,
        render: (e) => <span className="dim">{e.detail ?? '—'}</span>,
      },
    ],
    [t],
  );

  return (
    <Page>
      <Panel
        title={t('iam.audit.title')}
        actions={
          <CimSelect
            value={type}
            onChange={(v) => setType(typeof v === 'string' ? v : '')}
            options={typeOptions}
            width={190}
            allowClear={false}
          />
        }
      >
        <DataTable<AuditEvent>
          flat
          columns={columns}
          rows={rows}
          rowKey={(e) => e.id}
          loading={listRes.loading}
          error={listRes.error?.message}
          onRetry={listRes.refresh}
          onRefresh={listRes.refresh}
          settingsKey="iam.audit"
          scrollX="max-content"
          emptyTitleKey="iam.audit.emptyTitle"
          emptyHintKey="iam.audit.emptyHint"
          actions={
            <CimSelect
              value={limit}
              onChange={(v) => setLimit(typeof v === 'string' ? v : '200')}
              options={LIMIT_OPTIONS}
              width={130}
              allowClear={false}
            />
          }
          showCount
        />
      </Panel>

      <Panel title={t('iam.audit.scopeTitle')}>
        <div className="tag-list">
          {AUDIT_TYPES.map((v) => (
            <CimTag key={v} tone={toneOf(v)}>
              {auditLabel(v)}
            </CimTag>
          ))}
        </div>
      </Panel>
    </Page>
  );
}
