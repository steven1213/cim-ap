import { useTranslation } from 'react-i18next';
import type { FlowNodeTrace } from '../types';

/** 首尾哨兵节点 id（后端 FlowNodeTrace 约定）。 */
const START_ID = '__start__';
const END_ID = '__end__';

/** 结束节点结果 → i18n 键后缀。 */
const RESULT_KEY: Record<string, string> = {
  APPROVED: 'resultApproved',
  REJECTED: 'resultRejected',
  WITHDRAWN: 'resultWithdrawn',
};

/**
 * 审批进度节点条（详情页）：完整链路「发起 → 各审批节点 → 结束」。
 *
 * <p>已到达节点亮起（完成=绿✓、进行中=主色光晕），未到达置灰，未经过（条件分支未走）虚线。
 * 数据源为后端 detail.flowNodes（含 __start__/__end__ 哨兵节点）。</p>
 */
export function FlowProgress({ nodes }: { nodes?: FlowNodeTrace[] | null }) {
  const { t } = useTranslation();
  if (!nodes || nodes.length === 0) return null;
  return (
    <div className="flow-progress" role="list" aria-label={t('detail.flow.progress')}>
      {nodes.map((n, i) => {
        const st = n.status === 'DONE' || n.status === 'ACTIVE' || n.status === 'SKIPPED'
          ? n.status.toLowerCase() : 'pending';
        const name = n.nodeId === START_ID
          ? t('detail.flow.start')
          : n.nodeId === END_ID
            ? t('detail.flow.end')
            : n.name;
        const sub =
          n.nodeId === START_ID
            ? n.actor || t('detail.flow.done')
            : n.nodeId === END_ID
              ? (n.outcome && RESULT_KEY[n.outcome] ? t(`detail.flow.${RESULT_KEY[n.outcome]}`) : t('detail.flow.done'))
              : st === 'done'
                ? [n.actor, n.outcome].filter(Boolean).join(' · ') || t('detail.flow.done')
                : st === 'active'
                  ? t('detail.flow.active')
                  : st === 'skipped'
                    ? t('detail.flow.skipped')
                    : t('detail.flow.pending');
        return (
          <div key={n.nodeId + '-' + i} className={`fp-step fp-${st}`} role="listitem">
            {i > 0 && <div className="fp-line" aria-hidden="true" />}
            <div className="fp-dot" aria-hidden="true">
              {st === 'done' && (
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path d="M1.5 5.5L4 8L8.5 2.5" stroke="currentColor" strokeWidth="1.6"
                    strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
            </div>
            <div className="fp-name" title={name}>{name}</div>
            <div className="fp-sub" title={sub}>{sub}</div>
          </div>
        );
      })}
    </div>
  );
}
