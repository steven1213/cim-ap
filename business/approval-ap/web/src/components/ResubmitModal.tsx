import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimDatePicker,
  CimField,
  CimInput,
  CimInputNumber,
  CimModal,
  CimSelect,
  EmptyState,
  useToast,
} from '@cim/ui';
import { getProcessDetail, resubmitInstance } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import type { FormSchemaField, ProcessDetailView } from '@/types';

/** 可重提的字段描述（schema 与未声明键归一到同一形状）。 */
interface EditField {
  key: string;
  label: string;
  type: string;
  unit: string;
}

/** 按类型渲染控件。日期/时间与后端口径对齐：值一律 ISO（日期 `YYYY-MM-DD`、时间带 `T`）。 */
function FieldControl({
  field,
  value,
  onChange,
}: {
  field: EditField;
  value: unknown;
  onChange: (next: unknown) => void;
}) {
  const common = { id: `resubmit-${field.key}`, disabled: false };
  switch (field.type) {
    case 'textarea':
      return <CimInput {...common} rows={3} value={str(value)} onChange={(v) => onChange(v)} />;
    case 'boolean':
      return (
        <CimSelect
          {...common}
          value={value === true || value === 'true' ? 'true' : 'false'}
          allowClear={false}
          showSearch={false}
          options={[
            { value: 'true', labelKey: 'common.yes' },
            { value: 'false', labelKey: 'common.no' },
          ]}
          onChange={(v) => onChange(v === 'true')}
        />
      );
    case 'number':
    case 'money':
      return (
        <CimInputNumber
          {...common}
          value={num(value)}
          unit={field.unit || undefined}
          thousandSeparator
          onChange={(v) => onChange(v)}
        />
      );
    case 'date':
      return (
        <CimDatePicker
          {...common}
          mode="date"
          value={str(value)}
          valueFormat="YYYY-MM-DD"
          onChange={(v) => onChange(v)}
        />
      );
    case 'datetime':
      return (
        <CimDatePicker
          {...common}
          mode="datetime"
          value={str(value)}
          valueFormat="YYYY-MM-DDTHH:mm:ss"
          onChange={(v) => onChange(v)}
        />
      );
    default:
      return (
        <CimInput
          {...common}
          value={str(value)}
          unit={field.unit || undefined}
          onChange={(v) => onChange(v)}
        />
      );
  }
}

function str(v: unknown): string {
  return v == null ? '' : String(v);
}

function num(v: unknown): number | null {
  if (typeof v === 'number') return v;
  if (v == null || v === '') return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

/**
 * 重新提交弹窗（退回到发起人后的重提态）：
 * 加载实例当前表单数据，按 formSchema 渲染可编辑字段，提交后审批链从头重走。
 * 无 schema 时回退为键值文本编辑，保证不丢数据。
 *
 * <p>标签来自 schema 的**字面**文案（不是 i18n 键），所以这里用 `CimField` 的 `label`
 * 而不是 `FormGrid` —— FormGrid 的 labelKey 缺键时回退成字段 key，会把中文标签变成
 * `amount` 这类原始键名。</p>
 */
export default function ResubmitModal({
  processInstanceId,
  onClose,
  onDone,
  initial,
}: {
  processInstanceId: string;
  onClose: () => void;
  /** 重提成功后通知父页面刷新。 */
  onDone?: () => void;
  /**
   * 调用方已经拿到的详情（详情弹窗内打开时传）。
   * 传了就**不再自己取一次**：同一份详情重复打一次接口只是多一个失败点。
   */
  initial?: ProcessDetailView;
}) {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();
  const toast = useToast();
  const [values, setValues] = useState<Record<string, unknown> | null>(
    initial ? { ...(initial.formData ?? {}) } : null,
  );
  const [busy, setBusy] = useState(false);

  const detailRes = useResource<ProcessDetailView>(
    ({ signal }) => getProcessDetail(processInstanceId, userId, { signal }),
    { deps: [processInstanceId, userId], enabled: !initial },
  );
  const detail = initial ?? detailRes.data;

  // 回包 → 可编辑副本：一条 effect 落地，不在 render 里派生（否则每次重渲染都重置用户的编辑）
  useEffect(() => {
    if (detail) setValues({ ...(detail.formData ?? {}) });
  }, [detail]);

  /** schema 字段按 order 排序；未声明键按原顺序追加。 */
  const fields = useMemo<EditField[]>(() => {
    if (!detail) return [];
    const formData = detail.formData ?? {};
    const schema: FormSchemaField[] = [...(detail.formSchema ?? [])].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
    const used = new Set<string>();
    const out: EditField[] = [];
    for (const f of schema) {
      if (f.key && f.key in formData) {
        used.add(f.key);
        out.push({ key: f.key, label: f.label || f.key, type: f.type ?? 'text', unit: f.unit ?? '' });
      }
    }
    for (const k of Object.keys(formData)) {
      if (!used.has(k)) out.push({ key: k, label: k, type: 'text', unit: '' });
    }
    return out;
  }, [detail]);

  function patch(key: string, next: unknown) {
    setValues((prev) => ({ ...(prev ?? {}), [key]: next }));
  }

  async function submit() {
    setBusy(true);
    try {
      await resubmitInstance(processInstanceId, {
        formData: values ?? undefined,
        formSchema: detail?.formSchema,
      });
      toast.success(t('todo.resubmitDone'));
      onDone?.();
      onClose();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      title={t('todo.resubmitTitle')}
      width={640}
      onOk={submit}
      okKey="todo.resubmitSubmit"
      cancelKey="common.cancel"
      okLoading={busy}
      okDisabled={!detail || !values}
      maskClosable={false}
    >
      {detailRes.error && !detail ? (
        <div className="detail-load-error">
          <EmptyState title={detailRes.error.message} hint={t('detail.loadFailedHint')} />
          <CimButton size="small" onClick={detailRes.refresh}>
            {t('common.refresh')}
          </CimButton>
        </div>
      ) : !detail || !values ? (
        <EmptyState title={t('common.loading')} />
      ) : (
        <>
          <p className="detail-resubmit-hint">{t('todo.resubmitHint', { n: detail.round ?? 1 })}</p>
          {fields.length === 0 ? (
            <p className="detail-empty">{t('detail.formDataEmpty')}</p>
          ) : (
            <div className="cim-form-grid" style={{ gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' }}>
              {fields.map((f) => (
                <CimField
                  key={f.key}
                  label={f.unit ? `${f.label}（${f.unit}）` : f.label}
                  span={f.type === 'textarea' ? 2 : 1}
                  htmlFor={`resubmit-${f.key}`}
                >
                  <FieldControl field={f} value={values[f.key]} onChange={(v) => patch(f.key, v)} />
                </CimField>
              ))}
            </div>
          )}
        </>
      )}
    </CimModal>
  );
}
