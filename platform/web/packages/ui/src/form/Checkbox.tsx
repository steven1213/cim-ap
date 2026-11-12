// 单复选框 + 内联文案。
//
// 为什么需要它（`CimChoice` 为什么不够）：
//   `CimChoice` 是「一组选项」的组件，入参是 `options: CimOption[]`。而站内
//   页面上的高频用法是**一个**布尔值配一句说明（「设为必填」「记住我」），
//   套进 options 数组要写 `options={[{ value: 'x', label: ... }]}` 再取
//   `value[0]`，读起来比手写 `<label><input/></label>` 还绕。
//
// 用原生 `<input type="checkbox">` 而不是 antd `Checkbox`：
//   全局 `input { width:100%; padding; border; background }` 会把复选框拉成
//   一个输入框，必须自绘。站内原来的 `.check` 就是干这件事的，这里把同一套
//   画法收进组件，页面不再需要引 CSS 类。

import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimCheckboxProps {
  checked: boolean;
  onChange?: (checked: boolean) => void;
  disabled?: boolean;
  /** 文案（与 `labelKey` 二选一，`labelKey` 优先）。 */
  label?: string;
  labelKey?: string;
  /** 富文案（例如文案里带 <code>）：给了就忽略 label/labelKey。 */
  children?: ReactNode;
  id?: string;
  /** 纵向排列（文案较长、需要换行时用）。默认横排。 */
  block?: boolean;
  /** 悬停提示（长文案 / 编码类文案的补充说明）。 */
  title?: string;
  /** 额外类名（合并而非覆盖），供页面挂布局类。 */
  className?: string;
}

export function CimCheckbox({
  checked,
  onChange,
  disabled = false,
  label,
  labelKey,
  children,
  id,
  block = false,
  title,
  className,
}: CimCheckboxProps) {
  const t = useUiLabel();
  const text = children ?? (labelKey ? t(labelKey, label ?? '') : label);

  return (
    <label
      className={['cim-checkbox', block && 'cim-checkbox-block', className].filter(Boolean).join(' ')}
      title={title}
    >
      <input
        id={id}
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange?.(e.target.checked)}
      />
      {text ? <span>{text}</span> : null}
    </label>
  );
}
