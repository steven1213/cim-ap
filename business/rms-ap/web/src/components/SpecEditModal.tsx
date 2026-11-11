import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CimModal, FormGrid, type FormField } from '@cim/ui';
import * as api from '@/lib/rmsApi';
import type { ParamSpec, SpecType } from '@/types';

const SPEC_TYPES: SpecType[] = ['TARGET', 'RANGE', 'LIST_IN', 'TOLERANCE'];
const DATA_TYPES = ['NUMBER', 'TEXT', 'BOOL'];

interface SpecForm {
  paramKey: string;
  paramName: string;
  specType: SpecType;
  dataType: string;
  targetValue: string;
  rangeMin: string;
  rangeMax: string;
  listValues: string;
  tolerancePct: string;
  toleranceAbs: string;
  unit: string;
}

/**
 * 参数规格编辑弹窗（配方详情抽屉与规格参数页共用）。
 *
 * <p>「一处搞定」原则：用户在配方抽屉里即可维护规格，不必跳页后重新定位配方。</p>
 *
 * <p>迁移要点：手写 `.modal-backdrop` 三件套换成 `CimModal`（Esc / 焦点 / 滚动锁定由 antd 负责），
 * 表单换成 `FormGrid`。字段随 `specType` 变化——不是「渲染后隐藏」，而是**按当前类型构造字段数组**，
 * 这样切换类型时不会留下上一次类型的值参与提交（旧实现的隐藏字段仍会随 payload 一起发出）。</p>
 */
export default function SpecEditModal({ recipeId, initial, onClose, onDone }: {
  recipeId: string;
  initial: ParamSpec | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<SpecForm>({
    paramKey: initial?.paramKey ?? '',
    paramName: initial?.paramName ?? '',
    specType: initial?.specType ?? 'TARGET',
    dataType: initial?.dataType ?? 'NUMBER',
    targetValue: initial?.targetValue ?? '',
    rangeMin: initial?.rangeMin ?? '',
    rangeMax: initial?.rangeMax ?? '',
    listValues: initial?.listValues ?? '',
    tolerancePct: initial?.tolerancePct?.toString() ?? '',
    toleranceAbs: initial?.toleranceAbs?.toString() ?? '',
    unit: initial?.unit ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof SpecForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const fields = useMemo<Array<FormField<SpecForm>>>(() => {
    const list: Array<FormField<SpecForm>> = [
      {
        kind: 'text',
        key: 'paramKey',
        labelKey: 'rms.spec.paramKey',
        required: true,
        mono: true,
        // 参数键是比对与下发的锚点，改键等于换一条规格
        disabled: !!initial,
      },
      { kind: 'text', key: 'paramName', labelKey: 'rms.spec.paramName' },
      {
        kind: 'select',
        key: 'specType',
        labelKey: 'rms.spec.specType',
        options: SPEC_TYPES.map((s) => ({ value: s, labelKey: `rms.spec.type.${s}` })),
      },
      {
        kind: 'select',
        key: 'dataType',
        labelKey: 'rms.spec.dataType',
        options: DATA_TYPES.map((d) => ({ value: d, label: d })),
      },
    ];

    if (form.specType === 'TARGET' || form.specType === 'TOLERANCE') {
      list.push({ kind: 'text', key: 'targetValue', labelKey: 'rms.spec.target', mono: true });
    }
    if (form.specType === 'RANGE') {
      list.push({ kind: 'text', key: 'rangeMin', labelKey: 'rms.spec.rangeMin', mono: true });
      list.push({ kind: 'text', key: 'rangeMax', labelKey: 'rms.spec.rangeMax', mono: true });
    }
    if (form.specType === 'LIST_IN') {
      list.push({ kind: 'text', key: 'listValues', labelKey: 'rms.spec.list', mono: true, span: 2 });
    }
    if (form.specType === 'TOLERANCE') {
      list.push({ kind: 'text', key: 'tolerancePct', labelKey: 'rms.spec.tolPct', unit: '%' });
      list.push({ kind: 'text', key: 'toleranceAbs', labelKey: 'rms.spec.tolAbs', unit: '±' });
    }
    list.push({ kind: 'text', key: 'unit', labelKey: 'rms.spec.unit' });
    return list;
  }, [initial, form.specType]);

  async function submit() {
    if (!form.paramKey) {
      setFieldErrors({ paramKey: t('rms.common.required') });
      return;
    }
    setFieldErrors({});
    setBusy(true);
    setErr('');
    // 只提交当前类型相关的取值：切换过类型时，界面上已不存在的旧值不应进入 payload
    const only = (k: keyof SpecForm) => (fields.some((f) => f.key === k) ? form[k] : '');
    try {
      await api.upsertSpec(recipeId, {
        paramKey: form.paramKey,
        paramName: form.paramName || null,
        specType: form.specType,
        dataType: form.dataType,
        targetValue: only('targetValue') || null,
        rangeMin: only('rangeMin') || null,
        rangeMax: only('rangeMax') || null,
        listValues: only('listValues') || null,
        tolerancePct: only('tolerancePct') ? Number(only('tolerancePct')) : null,
        toleranceAbs: only('toleranceAbs') ? Number(only('toleranceAbs')) : null,
        unit: form.unit || null,
        enabled: initial?.enabled ?? true,
      });
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? 'rms.spec.editSpec' : 'rms.spec.add'}
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={600}
    >
      <FormGrid<SpecForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      {err ? (
        <p className="cim-field-error" style={{ marginTop: 10 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}
