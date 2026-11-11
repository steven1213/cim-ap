// 日期 / 时间选择：对外统一用**字符串**，对内才用 dayjs。
//
// 这一条是刻意设计的。antd 的 DatePicker 收发 dayjs 对象，于是每个页面都要
//   `value={form.date ? dayjs(form.date) : null}` + `onChange={(d) => setForm({date: d?.format('YYYY-MM-DD')})}`
// 两处转换。转错的后果很具体：`toISOString()` 会把本地 00:00 变成前一天 16:00（东八区），
// 而配方生效日期差一天就是批次事故。
//
// 所以这里固定口径：
//   - `value` / `onChange` 一律是字符串（默认 `YYYY-MM-DD`，可改 `valueFormat`）；
//   - 传 `withTime` 时带时间（`YYYY-MM-DD HH:mm:ss`）；
//   - 清空一律返回 `null`（不是空字符串），跟后端可空字段对齐。
//
// ⚠️ 后端若要求 ISO-8601 带时区（2026-11-11T00:00:00Z），请把 valueFormat 设为
//    'YYYY-MM-DDTHH:mm:ss[Z]' 并在页面上确认时区，不要在这里隐式 toISOString。

import { DatePicker, TimePicker } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useUiLabel } from '../locale';

export type DateMode = 'date' | 'datetime' | 'month' | 'year' | 'week';

const DEFAULT_FORMAT: Record<DateMode, string> = {
  date: 'YYYY-MM-DD',
  datetime: 'YYYY-MM-DD HH:mm:ss',
  month: 'YYYY-MM',
  year: 'YYYY',
  week: 'YYYY-[W]ww',
};

function toDayjs(s: string | null | undefined): Dayjs | null {
  if (!s) return null;
  const d = dayjs(s);
  return d.isValid() ? d : null;
}

export interface CimDatePickerProps {
  /** 字符串值（口径见文件头）。 */
  value?: string | null;
  onChange?: (value: string | null) => void;
  mode?: DateMode;
  /** 值格式，默认按 mode 推导。 */
  valueFormat?: string;
  /** 显示格式，默认同 valueFormat。 */
  displayFormat?: string;
  placeholderKey?: string;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  status?: 'error' | 'warning';
  width?: number | string;
  id?: string;
  /** 禁用日期，返回 true 表示不可选。 */
  disabledDate?: (current: Dayjs) => boolean;
  onOpenChange?: (open: boolean) => void;
}

export function CimDatePicker({
  value,
  onChange,
  mode = 'date',
  valueFormat,
  displayFormat,
  placeholderKey,
  placeholder,
  disabled = false,
  allowClear = true,
  status,
  width,
  id,
  disabledDate,
  onOpenChange,
}: CimDatePickerProps) {
  const label = useUiLabel();
  const fmt = valueFormat ?? DEFAULT_FORMAT[mode];
  const ph = placeholderKey ? label(placeholderKey, placeholder ?? '') : placeholder;

  const common = {
    value: toDayjs(value),
    onChange: (d: Dayjs | null) => onChange?.(d ? d.format(fmt) : null),
    placeholder: ph,
    disabled,
    allowClear,
    status,
    id,
    format: displayFormat ?? fmt,
    style: { width: width ?? '100%' },
    onOpenChange,
  };

  if (mode === 'datetime') {
    return <DatePicker showTime {...common} />;
  }
  return (
    <DatePicker
      {...common}
      picker={mode === 'date' ? 'date' : mode === 'week' ? 'week' : mode}
      disabledDate={disabledDate}
    />
  );
}

/** 日期区间。值为 `[起, 止]` 字符串对，任一端可为 null。 */
export function CimDateRangePicker({
  value,
  onChange,
  valueFormat = 'YYYY-MM-DD',
  placeholderKey,
  disabled = false,
  allowClear = true,
  status,
  width = 260,
  id,
}: {
  value?: [string | null, string | null] | null;
  onChange?: (value: [string | null, string | null] | null) => void;
  valueFormat?: string;
  placeholderKey?: string;
  disabled?: boolean;
  allowClear?: boolean;
  status?: 'error' | 'warning';
  width?: number | string;
  id?: string;
}) {
  const label = useUiLabel();
  const pair: [Dayjs | null, Dayjs | null] | null = value
    ? [toDayjs(value[0]), toDayjs(value[1])]
    : null;
  return (
    <DatePicker.RangePicker
      id={id}
      value={pair as [Dayjs, Dayjs] | null}
      onChange={(v) =>
        onChange?.(
          v && v[0] && v[1]
            ? [v[0].format(valueFormat), v[1].format(valueFormat)]
            : null,
        )
      }
      placeholder={
        placeholderKey
          ? [label(placeholderKey, '开始'), label('ui.date.to', '结束')]
          : undefined
      }
      disabled={disabled}
      allowClear={allowClear}
      status={status}
      style={{ width }}
    />
  );
}

export interface CimTimePickerProps {
  /** `HH:mm:ss` 字符串。 */
  value?: string | null;
  onChange?: (value: string | null) => void;
  /** 是否带秒，默认 false（产线大多只到分）。 */
  withSeconds?: boolean;
  placeholderKey?: string;
  disabled?: boolean;
  allowClear?: boolean;
  status?: 'error' | 'warning';
  width?: number | string;
  id?: string;
}

export function CimTimePicker({
  value,
  onChange,
  withSeconds = false,
  placeholderKey,
  disabled = false,
  allowClear = true,
  status,
  width,
  id,
}: CimTimePickerProps) {
  const label = useUiLabel();
  const fmt = withSeconds ? 'HH:mm:ss' : 'HH:mm';
  return (
    <TimePicker
      id={id}
      value={value ? dayjs(value, fmt) : null}
      onChange={(d) => onChange?.(d ? d.format(fmt) : null)}
      format={fmt}
      placeholder={placeholderKey ? label(placeholderKey, '') : undefined}
      disabled={disabled}
      allowClear={allowClear}
      status={status}
      style={{ width: width ?? '100%' }}
    />
  );
}
