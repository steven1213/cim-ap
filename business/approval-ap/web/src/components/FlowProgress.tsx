import { useTranslation } from 'react-i18next';
import type { FlowNodeTrace } from '../types';

/**
 * 审批进度节点条（详情页）。
 *
 * <p>按 BPMN 顺序展示全部审批节点：已完成=绿点亮起 ✓、进行中=主色高亮脉冲、
 * 未到达=灰空心、未经过（条件分支未走）=灰虚线。数据源为后端 detail.flowNodes。</p>
 */
export function FlowProgress({ nodes }: { nodes?: FlowNodeTrace[] | null }) {
  const { t } = useTranslation();
  if (!nodes || nodes.length === 0) return null;
  return (
    <div className="flow-progress" role="list" aria-label={t('detail.flow.progress')}>
      {nodes.map((n, i) => {
        const st = n.status === 'DONE' || n.status === 'ACTIVE' || n.status === 'SKIPPED'
          ? n.status.toLowerCase() : 'pending';
        const sub =
          st === 'done'
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
            <div className="fp-name" title={n.name}>{n.name}</div>
            <div className="fp-sub" title={sub}>{sub}</div>
          </div>
        );
      })}
    </div>
  );
}
