import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CimChoice, CimField, CimInput, CimModal, useToast } from '@cim/ui';
import type { CompleteTaskRequest, TaskView } from '@/types';

/**
 * 办理弹窗（通过/驳回 + 意见）。TodoPage 与 DetailModal 共用——
 * 详情内直达审批时也由此弹窗收集结论与意见。
 */
export default function CompleteModal({
  task,
  busy,
  onClose,
  onSubmit,
  defaultOutcome = 'APPROVE',
}: {
  task: TaskView;
  busy: boolean;
  onClose: () => void;
  onSubmit: (r: CompleteTaskRequest) => void;
  /** 预选结论（详情内「通过/驳回」直达时使用）。 */
  defaultOutcome?: 'APPROVE' | 'REJECT';
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [outcome, setOutcome] = useState<'APPROVE' | 'REJECT'>(defaultOutcome);
  const [comment, setComment] = useState('');

  function submit() {
    // 驳回必须给意见：否则办理人无法知道要改什么，只能再问一遍
    if (outcome === 'REJECT' && !comment.trim()) {
      toast.warning(t('todo.rejectCommentRequired'));
      return;
    }
    // actor 由调用方补（详情内与列表页的身份来源不同）
    onSubmit({ outcome, comment, actor: '' });
  }

  return (
    <CimModal
      open
      onClose={onClose}
      title={`${t('todo.completeTitle')} · ${task.name}`}
      onOk={submit}
      okKey="common.submit"
      cancelKey="common.cancel"
      okLoading={busy}
      okDanger={outcome === 'REJECT'}
      maskClosable={false}
    >
      <div className="cim-form-grid" style={{ gridTemplateColumns: '1fr' }}>
        <CimField label={t('todo.outcome')} required>
          <CimChoice
            mode="radio"
            value={outcome}
            onChange={(v) => setOutcome(v === 'REJECT' ? 'REJECT' : 'APPROVE')}
            options={[
              { value: 'APPROVE', labelKey: 'todo.outcome.approve' },
              { value: 'REJECT', labelKey: 'todo.outcome.reject' },
            ]}
          />
        </CimField>
        <CimField
          label={t('todo.comment')}
          required={outcome === 'REJECT'}
          hint={outcome === 'REJECT' ? t('todo.rejectCommentRequired') : undefined}
        >
          <CimInput rows={3} value={comment} onChange={(v) => setComment(v)} />
        </CimField>
      </div>
    </CimModal>
  );
}
