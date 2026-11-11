// 数字输入。
//
// 半导体参数的两个硬性要求，antd 的 InputNumber 都不直接表达：
//   1. **单位**：膜厚 nm、温度 ℃、时间 s、压力 Torr —— 输入框必须自带单位；
//   2. **精度与量程**：工艺参数有物理意义，`precision` 不给就会被浮点尾数污染
//      （0.1 + 0.2 = 0.30000000000000004 这种事进过一次配方库就是事故）。
//
// 另外把 `onChange` 语义收紧为 `number | null`（null = 清空），
// 避免调用方在 `value === '' ? null : Number(value)` 上反复纠结。

import { InputNumber } from 'antd';
import { useUiLabel } from '../locale';

export interface CimInputNumberProps {
  value?: number | null;
  onChange?: (value: number | null) => void;
  /** 单位后缀（nm / ℃ / s）。 */
  unit?: string;
  /** 小数位数；不传则不限制。 */
  precision?: number;
  min?: number;
  max?: number;
  step?: number;
  /** 千分位分隔（产量类数字用）。 */
  thousandSeparator?: boolean;
  placeholderKey?: string;
  placeholder?: string;
  disabled?: boolean;
  /** 超出量程时的提示（不阻断输入，只标红）。 */
  status?: 'error' | 'warning';
  width?: number | string;
  id?: string;
  onEnter?: () => void;
}

/** 千分位只加在整数部分：「1234.5678」→「1,234.5678」，不能把小数也切开。 */
function formatThousands(v: string | number | undefined): string {
  if (v === undefined || v === null || v === '') return '';
  const s = String(v);
  const dot = s.indexOf('.');
  const int = dot >= 0 ? s.slice(0, dot) : s;
  const dec = dot >= 0 ? s.slice(dot) : '';
  return int.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + dec;
}

function parseThousands(v: string | undefined): number | string {
  if (!v) return '';
  // 中间态（用户正在输入「1,2」）返回原串，否则 antd 会把它改成 NaN 并清空
  const n = Number(v.replace(/,/g, ''));
  return Number.isFinite(n) ? n : v;
}

export function CimInputNumber({
  value,
  onChange,
  unit,
  precision,
  min,
  max,
  step,
  thousandSeparator = false,
  placeholderKey,
  placeholder,
  disabled = false,
  status,
  width,
  id,
  onEnter,
}: CimInputNumberProps) {
  const label = useUiLabel();
  const ph = placeholderKey ? label(placeholderKey, placeholder ?? '') : placeholder;

  const input = (
    <InputNumber
      value={value ?? null}
      onChange={(v: string | number | null) => {
        // antd 在 stringMode 下会回字符串；统一转数字，空值转 null
        onChange?.(v === null || v === undefined || v === '' ? null : Number(v));
      }}
      precision={precision}
      min={min}
      max={max}
      step={step}
      disabled={disabled}
      status={status}
      id={id}
      placeholder={ph}
      style={{ width: unit ? '100%' : (width ?? '100%') }}
      formatter={thousandSeparator ? formatThousands : undefined}
      parser={thousandSeparator ? parseThousands : undefined}
      onPressEnter={onEnter}
    />
  );

  if (!unit) return input;
  return (
    <span className="cim-unit-input" style={{ width: width ?? '100%' }}>
      {input}
      <span className="cim-unit">{unit}</span>
    </span>
  );
}
