import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '@/components/Modal';
import { IconCheck } from '@/components/Icons';
import { toast } from '@/store/toastStore';
import type { CompleteTaskRequest, TaskView } from '@/types';

/**
 * 办理弹窗（通过/驳回 + 意见）。TodoPage 与 DetailModal 共用——
 * 详情内直达审批时也由此弹窗收集结论与意见。
 */
export default function CompleteModal({ task, busy, onClose, onSubmit, defaultOutcome = 'APPROVE' }: {
  task: TaskView;
  busy: boolean;
  onClose: () => void;
  onSubmit: (r: CompleteTaskRequest) => void;
  /** 预选结论（详情内「通过/驳回」直达时使用）。 */
  defaultOutcome?: 'APPROVE' | 'REJECT';
}) {
  const { t } = useTranslation();
  const [outcome, setOutcome] = useState<'APPROVE' | 'REJECT'>(defaultOutcome);
  const [comment, setComment] = useState('');
  return (
    <Modal
      title={`${t('todo.completeTitle')} · ${task.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button
            className="btn btn-sm"
            disabled={busy}
            onClick={() => {
              if (outcome === 'REJECT' && !comment.trim()) {
                toast(t('todo.rejectCommentRequired'), 'warn');
                return;
              }
              onSubmit({ outcome, comment, actor: '' });
            }}
          >
            <IconCheck width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field-label">{t('todo.outcome')}</label>
        <div className="row">
          <label className="check"><input type="radio" name="outcome" checked={outcome === 'APPROVE'} onChange={() => setOutcome('APPROVE')} /> {t('todo.outcome.approve')}</label>
          <label className="check"><input type="radio" name="outcome" checked={outcome === 'REJECT'} onChange={() => setOutcome('REJECT')} /> {t('todo.outcome.reject')}</label>
        </div>
      </div>
      <div className="field">
        <label className="field-label">{t('todo.comment')}</label>
        <textarea rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
      </div>
    </Modal>
  );
}
