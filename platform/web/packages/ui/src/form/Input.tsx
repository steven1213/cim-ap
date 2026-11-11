// 文本输入。
//
// 相比直接用 antd Input，这里的增量只有三条，但每条都在现仓重复过几十次：
//   1. `onChange` 直接给字符串（不是 event）—— 页面里 `setX(e.target.value)` 出现 100+ 次；
//   2. `unit` 单位后缀（nm / Å / ℃ / s / 片 …）—— 半导体参数没有一个是无量纲的；
//   3. `onEnter` 回车提交 —— 搜索框、扫码枪输入（扫码枪就是发一个回车）。
//
// 扫码枪这条不是花活：产线上大量输入靠扫工单号/批次号，回车必须能用。

import { Input } from 'antd';
import type { ChangeEvent, ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimInputProps {
  value?: string;
  onChange?: (value: string) => void;
  /** 单位后缀，右对齐灰字（nm、℃、s）。 */
  unit?: string;
  /** 前置图标。 */
  prefix?: ReactNode;
  /** 前置固定文案（如设备编码前缀）。 */
  addonBefore?: string;
  placeholderKey?: string;
  placeholder?: string;
  disabled?: boolean;
  readOnly?: boolean;
  /** 默认允许清空。 */
  allowClear?: boolean;
  /** 等宽字体（编码、哈希、SQL）。 */
  mono?: boolean;
  maxLength?: number;
  /** 显示字数统计（maxLength 存在时才生效）。 */
  showCount?: boolean;
  /** 多行（传了就变 textarea）。 */
  rows?: number;
  /** 密码框。 */
  password?: boolean;
  /** 回车回调（扫码枪、搜索框）。 */
  onEnter?: () => void;
  onBlur?: () => void;
  /** 失焦即触发（表格内联编辑常用）。 */
  autoFocus?: boolean;
  status?: 'error' | 'warning';
  width?: number | string;
  id?: string;
}

export function CimInput({
  value,
  onChange,
  unit,
  prefix,
  addonBefore,
  placeholderKey,
  placeholder,
  disabled = false,
  readOnly = false,
  allowClear = true,
  mono = false,
  maxLength,
  showCount = false,
  rows,
  password = false,
  onEnter,
  onBlur,
  autoFocus = false,
  status,
  width,
  id,
}: CimInputProps) {
  const label = useUiLabel();
  const ph = placeholderKey ? label(placeholderKey, placeholder ?? '') : placeholder;

  const common = {
    value: value ?? '',
    onChange: (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange?.(e.target.value),
    placeholder: ph,
    disabled,
    readOnly,
    allowClear,
    maxLength,
    autoFocus,
    status,
    id,
    onBlur,
    onPressEnter: onEnter,
    style: {
      width: width ?? (unit ? undefined : '100%'),
      ...(mono ? { fontFamily: 'var(--font-mono)' } : null),
    },
    className: mono ? 'cim-mono-input' : undefined,
  };

  // 有单位时不加 allowClear（清空图标会和单位挤在一起），改由外层包一层
  const core =
    rows !== undefined ? (
      <Input.TextArea {...common} rows={rows} showCount={showCount && !!maxLength} />
    ) : password ? (
      <Input.Password {...common} prefix={prefix} />
    ) : (
      <Input {...common} prefix={prefix} />
    );

  const withAddon = addonBefore ? (
    <Input {...common} addonBefore={addonBefore} prefix={prefix} />
  ) : (
    core
  );

  if (!unit) return withAddon;

  return (
    <span className="cim-unit-input" style={{ width: width ?? '100%' }}>
      {addonBefore ? (
        <Input {...common} addonBefore={addonBefore} prefix={prefix} style={{ width: '100%' }} />
      ) : rows !== undefined ? (
        <Input.TextArea {...common} rows={rows} style={{ width: '100%' }} />
      ) : password ? (
        <Input.Password {...common} prefix={prefix} style={{ width: '100%' }} />
      ) : (
        <Input {...common} prefix={prefix} allowClear={false} style={{ width: '100%' }} />
      )}
      <span className="cim-unit">{unit}</span>
    </span>
  );
}
