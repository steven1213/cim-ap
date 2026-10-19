import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '@/components/Modal';
import { IconRefresh } from '@/components/Icons';
import { getProcessDetail, resubmitInstance } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { toast } from '@/store/toastStore';
import type { FormSchemaField, ProcessDetailView } from '@/types';

/**
 * 重新提交弹窗（退回到发起人后的重提态）：
 * 加载实例当前表单数据，按 formSchema 渲染可编辑字段，提交后审批链从头重走。
 * 无 schema 时回退为键值文本编辑，保证不丢数据。
 */
export default function ResubmitModal({
  processInstanceId,
  onClose,
  onDone,
}: {
  processInstanceId: string;
  onClose: () => void;
  /** 重提成功后通知父页面刷新。 */
  onDone?: () => void;
}) {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();
  const [detail, setDetail] = useState<ProcessDetailView | null>(null);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getProcessDetail(processInstanceId, userId)
      .then((d) => {
        setDetail(d);
        setValues({ ...(d.formData ?? {}) });
      })
      .catch((e) => {
        toast((e as Error).message, 'err');
        onClose();
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [processInstanceId]);

  /** schema 字段按 group/order 排序；未声明键按原顺序追加。 */
  const orderedKeys: { key: string; label: string; type: string; unit: string }[] = [];
  if (detail) {
    const schema: FormSchemaField[] = [...(detail.formSchema ?? [])].sort(
      (a, b) => (a.order ?? 0) - (b.order ?? 0),
    );
    const used = new Set<string>();
    for (const f of schema) {
      if (f.key && f.key in (detail.formData ?? {})) {
        used.add(f.key);
        orderedKeys.push({
          key: f.key,
          label: f.label || f.key,
          type: f.type ?? 'text',
          unit: f.unit ?? '',
        });
      }
    }
    for (const [k] of Object.entries(detail.formData ?? {})) {
      if (!used.has(k)) orderedKeys.push({ key: k, label: k, type: 'text', unit: '' });
    }
  }

  function setValue(key: string, type: string, raw: string | boolean) {
    setValues((prev) => {
      const next = { ...prev };
      if (type === 'number' || type === 'money') {
        const n = Number(raw);
        next[key] = raw === '' || Number.isNaN(n) ? null : n;
      } else if (type === 'boolean') {
        next[key] = raw === true || raw === 'true';
      } else {
        next[key] = raw;
      }
      return next;
    });
  }

  async function submit() {
    setBusy(true);
    try {
      await resubmitInstance(processInstanceId, {
        formData: values,
        formSchema: detail?.formSchema,
      });
      toast(t('todo.resubmitDone'), 'ok');
      onDone?.();
      onClose();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={t('todo.resubmitTitle')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-sm" disabled={busy || !detail} onClick={submit}>
            <IconRefresh width={14} height={14} /> {t('todo.resubmitSubmit')}
          </button>
        </>
      }
    >
      {!detail ? (
        <div className="empty" style={{ padding: '24px 0' }}>
          <span>{t('common.loading')}</span>
        </div>
      ) : (
        <>
          <p className="dim" style={{ marginTop: 0 }}>
            {t('todo.resubmitHint', { n: detail.round ?? 1 })}
          </p>
          {orderedKeys.length === 0 && <p className="dim">{t('detail.formDataEmpty')}</p>}
          {orderedKeys.map(({ key, label, type, unit }) => (
            <div className="field" key={key}>
              <label className="field-label">
                {label}
                {unit ? `（${unit}）` : ''}
              </label>
              {type === 'textarea' ? (
                <textarea
                  rows={3}
                  value={values[key] == null ? '' : String(values[key])}
                  onChange={(e) => setValue(key, 'text', e.target.value)}
                />
              ) : type === 'boolean' ? (
                <select
                  value={values[key] === true ? 'true' : 'false'}
                  onChange={(e) => setValue(key, 'boolean', e.target.value)}
                >
                  <option value="true">是</option>
                  <option value="false">否</option>
                </select>
              ) : (
                <input
                  type={type === 'number' || type === 'money' ? 'number' : type === 'date' ? 'date' : type === 'datetime' ? 'datetime-local' : 'text'}
                  value={values[key] == null ? '' : String(values[key])}
                  onChange={(e) => setValue(key, type, e.target.value)}
                />
              )}
            </div>
          ))}
        </>
      )}
    </Modal>
  );
}
