import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimInput,
  CimTag,
  DataTable,
  Page,
  Panel,
  type DataTableColumn,
  InlineAlert,
} from '@cim/ui';
import type { StatusTone } from '@cim/theme';
import { IconShield } from '@/components/Icons';
import { listAudit } from '@/lib/api';
import { fmtDate, shortHash } from '@/lib/format';
import type { AuditView } from '@/types';

/**
 * 审计动作 → 语义档。
 *
 * <p>刻意只落 5 档（`StatusTone`）：改造前用 `.tag info`（第 6 种颜色）表达「常规事件」，
 * 与「进行中」共用蓝色会让值守的人误以为还有流程在跑。信息类事件归 `run`、无关注价值的
 * 归 `idle`——这里没有「任意颜色」的口子。</p>
 */
function actionTone(action: string): StatusTone {
  if (action.startsWith('COMPLETE_APPROVE') || action === 'START') return 'ok';
  if (action.startsWith('COMPLETE_REJECT') || action.startsWith('CALLBACK_FAILED')) return 'err';
  if (action === 'RETURN' || action === 'REASSIGN' || action === 'WITHDRAW') return 'warn';
  return 'run';
}

export default function AuditPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();

  /** 输入框是草稿；`applied` 是真正查询的单号（分离，避免「一打字就发请求」）。 */
  const [key, setKey] = useState(params.get('businessKey') ?? '');
  const [applied, setApplied] = useState(params.get('businessKey') ?? '');

  const auditRes = useResource<AuditView[]>(
    ({ signal }) => listAudit(applied, { signal }),
    { deps: [applied], enabled: !!applied, keepPreviousData: true },
  );

  const rows = auditRes.data ?? [];

  /** 哈希链校验：每条 `prevHash` 应等于上一条的 `hash`。纯派生，不进 state。 */
  const chain = useMemo(() => {
    if (!applied || auditRes.loading || auditRes.error) return null;
    if (rows.length === 0) return null;
    for (let i = 1; i < rows.length; i++) {
      if (rows[i].prevHash !== rows[i - 1].hash) return 'broken' as const;
    }
    return 'ok' as const;
  }, [applied, rows, auditRes.loading, auditRes.error]);

  function search() {
    const next = key.trim();
    if (!next) return;
    setApplied(next);
    setParams({ businessKey: next });
  }

  const columns: Array<DataTableColumn<AuditView>> = [
    { key: 'seq', titleKey: 'audit.colSeq', width: 56, align: 'right', mono: true },
    {
      key: 'action',
      titleKey: 'audit.colAction',
      width: 110,
      render: (a) => <CimTag tone={actionTone(a.action)}>{t('audit.action.' + a.action)}</CimTag>,
    },
    { key: 'actor', titleKey: 'audit.colActor', width: 110 },
    { key: 'beforeState', titleKey: 'audit.colBefore', width: 110, mono: true },
    { key: 'afterState', titleKey: 'audit.colAfter', width: 110, mono: true },
    { key: 'detail', titleKey: 'audit.colDetail', ellipsis: true },
    {
      key: 'createdAt',
      titleKey: 'audit.colTime',
      width: 160,
      mono: true,
      render: (a) => fmtDate(a.createdAt),
    },
    {
      key: 'hash',
      titleKey: 'audit.colHash',
      width: 130,
      mono: true,
      render: (a) => <span title={a.hash}>{shortHash(a.hash, 12)}</span>,
    },
    {
      key: 'prevHash',
      titleKey: 'audit.colPrevHash',
      width: 130,
      mono: true,
      render: (a) => <span title={a.prevHash}>{shortHash(a.prevHash, 12)}</span>,
    },
  ];

  return (
    <Page>
      <Panel
        titleKey="audit.title"
        subKey="audit.sub"
        actions={
          <>
            <CimInput
              id="audit-bk"
              mono
              value={key}
              onChange={setKey}
              onEnter={search}
              placeholder="BK-2026-001"
              width={240}
            />
            <CimButton size="small" onClick={search} loading={auditRes.loading}>
              {t('audit.search')}
            </CimButton>
          </>
        }
        flush
      >
        {/* 链校验结论放表格上方：它是「这批数据整体可不可信」的判断，不是某一行的属性，
            塞进表格列会让人以为逐行判定。 */}
        {chain && (
          <InlineAlert tone={(chain === 'ok' ? 'ok' : 'err')} style={{ margin: '10px 12px 0' }}>
            {chain === 'ok' ? t('audit.chainOk') : t('audit.chainBroken')}
          </InlineAlert>
        )}

        {applied ? (
          <DataTable<AuditView>
            flat
            columns={columns}
            rows={rows}
            rowKey={(a) => a.id}
            loading={auditRes.loading}
            error={auditRes.error ? auditRes.error.message : null}
            onRetry={auditRes.refresh}
            emptyTitleKey="common.empty"
            scrollX="max-content"
          />
        ) : (
          <div className="empty" style={{ padding: '28px 0' }}>
            <IconShield width={26} height={26} />
            <span>{t('audit.empty')}</span>
          </div>
        )}
      </Panel>
    </Page>
  );
}
