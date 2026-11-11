// FormGrid：配置驱动的表单网格。
//
// 借鉴 industry.mes 的 GoatForm（`items: [{ prop, name, options }]`）+ GoatContentTop，
// 改成 React/TS 的做法：
//   - 字段用**判别联合**描述（kind），有类型提示，不再靠 `name: 'GoatInput'` 字符串；
//   - 受控（value + onChange(patch)），不藏 Form 实例：提交/校验时机由页面决定，
//     调试时状态一眼可见；
//   - 错误按字段传（errors），由页面把后端错误映射到字段上。
//
// 不做的事：不做校验规则 DSL。校验只有两处：前端必填（`required` 标 *）与后端返回的错误。
// 复杂的联动/异步校验请在页面里写，硬塞进配置会变成小型框架。

import { Input, InputNumber, Select, Switch } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface FormOption {
  value: string;
  labelKey?: string;
  label?: string;
  disabled?: boolean;
}

interface FieldBase<V> {
  key: Extract<keyof V, string>;
  labelKey: string;
  /** 占几列（默认 1）。窄屏由 CSS 的 auto-fit 兜底。 */
  span?: number;
  required?: boolean;
  disabled?: boolean;
  hintKey?: string;
  hint?: string;
}

export type FormField<V> =
  | (FieldBase<V> & { kind: 'text'; placeholderKey?: string; maxLength?: number; mono?: boolean })
  | (FieldBase<V> & { kind: 'password'; placeholderKey?: string })
  | (FieldBase<V> & { kind: 'textarea'; placeholderKey?: string; rows?: number })
  | (FieldBase<V> & { kind: 'number'; min?: number; max?: number; step?: number; precision?: number })
  | (FieldBase<V> & {
      kind: 'select';
      options: FormOption[];
      allowClear?: boolean;
      showSearch?: boolean;
      placeholderKey?: string;
    })
  | (FieldBase<V> & { kind: 'switch' })
  | (FieldBase<V> & {
      kind: 'custom';
      render: (value: string, setValue: (next: string) => void) => ReactNode;
    });

export interface FormGridProps<V> {
  fields: Array<FormField<V>>;
  value: V;
  onChange: (patch: Partial<V>) => void;
  /** 字段级错误（key 与字段 key 对齐）。 */
  errors?: Partial<Record<Extract<keyof V, string>, string>>;
  /** 列数，默认 2。 */
  columns?: number;
  disabled?: boolean;
}

export function FormGrid<V extends object>({
  fields,
  value,
  onChange,
  errors,
  columns = 2,
  disabled = false,
}: FormGridProps<V>) {
  const label = useUiLabel();
  const bag = value as Record<string, unknown>;

  const patch = (key: string, next: unknown) => {
    onChange({ [key]: next } as Partial<V>);
  };

  return (
    <form
      className="cim-form-grid"
      style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
      onSubmit={(e) => e.preventDefault()}
    >
      {fields.map((f) => {
        const key = f.key as string;
        const raw = bag[key];
        const err = (errors as Record<string, string | undefined> | undefined)?.[key];
        const span = Math.min(Math.max(f.span ?? 1, 1), columns);
        const labelText = label(f.labelKey, key);
        return (
          <div className="cim-field" key={key} style={{ gridColumn: `span ${span}` }}>
            <label className="cim-field-label" htmlFor={`f-${key}`}>
              {labelText}
              {f.required ? <span className="cim-req">*</span> : null}
            </label>
            <FieldControl
              field={f}
              raw={raw}
              disabled={disabled || !!f.disabled}
              patch={patch}
              id={`f-${key}`}
              label={label}
            />
            {f.hint || f.hintKey ? (
              <p className="cim-field-hint">{f.hintKey ? label(f.hintKey, f.hint ?? '') : f.hint}</p>
            ) : null}
            {err ? <p className="cim-field-error">{err}</p> : null}
          </div>
        );
      })}
    </form>
  );
}

function FieldControl<V>({
  field,
  raw,
  disabled,
  patch,
  id,
  label,
}: {
  field: FormField<V>;
  raw: unknown;
  disabled: boolean;
  patch: (key: string, next: unknown) => void;
  id: string;
  label: (key: string, fallback: string) => string;
}) {
  const key = field.key as string;
  const placeholder = (k?: string, fallback = '') => (k ? label(k, fallback) : fallback);
  const off = disabled;

  switch (field.kind) {
    case 'text':
      return (
        <Input
          id={id}
          value={(raw as string) ?? ''}
          disabled={off}
          maxLength={field.maxLength}
          className={field.mono ? 'cim-mono' : undefined}
          placeholder={placeholder(field.placeholderKey)}
          onChange={(e) => patch(key, e.target.value)}
        />
      );
    case 'password':
      return (
        <Input.Password
          id={id}
          value={(raw as string) ?? ''}
          disabled={off}
          placeholder={placeholder(field.placeholderKey)}
          onChange={(e) => patch(key, e.target.value)}
        />
      );
    case 'textarea':
      return (
        <Input.TextArea
          id={id}
          value={(raw as string) ?? ''}
          disabled={off}
          rows={field.rows ?? 3}
          placeholder={placeholder(field.placeholderKey)}
          onChange={(e) => patch(key, e.target.value)}
        />
      );
    case 'number':
      return (
        <InputNumber
          id={id}
          style={{ width: '100%' }}
          value={typeof raw === 'number' ? raw : raw === '' || raw == null ? null : Number(raw)}
          disabled={off}
          min={field.min}
          max={field.max}
          step={field.step}
          precision={field.precision}
          onChange={(v) => patch(key, v)}
        />
      );
    case 'select':
      return (
        <Select
          id={id}
          style={{ width: '100%' }}
          value={(raw as string) ?? undefined}
          disabled={off}
          allowClear={field.allowClear ?? true}
          showSearch={field.showSearch ?? false}
          optionFilterProp="label"
          placeholder={placeholder(field.placeholderKey)}
          options={field.options.map((o) => ({
            value: o.value,
            label: o.labelKey ? label(o.labelKey, o.label ?? o.value) : o.label ?? o.value,
            disabled: o.disabled,
          }))}
          onChange={(v) => patch(key, v ?? null)}
        />
      );
    case 'switch':
      return (
        <Switch id={id} checked={!!raw} disabled={off} onChange={(v) => patch(key, v)} />
      );
    case 'custom':
      return <>{field.render((raw as string) ?? '', (next) => patch(key, next))}</>;
    default:
      return null;
  }
}
