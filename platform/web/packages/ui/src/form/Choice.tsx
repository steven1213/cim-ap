// 多选 / 单选组（复选框组、单选框组、按钮组）。
//
// 参考项目把它们拆成 6 个文件：GoatCheckbox、GoatCheckboxGroup、GoatRadio、
// GoatRadioGroup、GoatRadioButtonGroup、GoatCascader —— 而 Checkbox 与
// CheckboxGroup 的区别只是「一个 vs 一组」。这里用 `mode` 收敛成一个组件。
//
// 选项契约与 CimSelect 完全一致（`{ value, label, labelKey, disabled }`），
// 所以同一份枚举在「按钮组 / 下拉 / 标签组」之间切换只需改一个属性，
// 不用重写选项数组 —— 这是现仓最容易出现的不一致来源。

import { Checkbox, Radio, Space } from 'antd';
import { useUiLabel } from '../locale';
import type { CimOption } from './Select';

export type ChoiceMode = 'checkbox' | 'radio' | 'button';

export interface CimChoiceProps {
  options: CimOption[];
  /** checkbox 时为 string[]，radio / button 时为 string。 */
  value?: string | string[];
  onChange?: (value: string | string[]) => void;
  mode?: ChoiceMode;
  disabled?: boolean;
  /** 排列方向。 */
  direction?: 'horizontal' | 'vertical';
  /** 间距，默认 8。 */
  gap?: number;
  id?: string;
}

export function CimChoice({
  options,
  value,
  onChange,
  mode = 'radio',
  disabled = false,
  direction = 'horizontal',
  gap = 8,
  id,
}: CimChoiceProps) {
  const label = useUiLabel();

  const textOf = (o: CimOption) => (o.labelKey ? label(o.labelKey, o.labelKey) : (o.label ?? o.value));

  if (mode === 'button') {
    return (
      <Radio.Group
        id={id}
        value={typeof value === 'string' ? value : undefined}
        onChange={(e) => onChange?.(e.target.value as string)}
        disabled={disabled}
        optionType="button"
        buttonStyle="solid"
        options={options.map((o) => ({ value: o.value, label: textOf(o), disabled: o.disabled }))}
      />
    );
  }

  if (mode === 'checkbox') {
    const list = Array.isArray(value) ? value : [];
    return (
      <Checkbox.Group value={list} onChange={(v) => onChange?.(v.map(String))} disabled={disabled}>
        <Space size={gap} direction={direction === 'vertical' ? 'vertical' : 'horizontal'} wrap>
          {options.map((o) => (
            <Checkbox key={o.value} value={o.value} disabled={o.disabled}>
              {textOf(o)}
            </Checkbox>
          ))}
        </Space>
      </Checkbox.Group>
    );
  }

  const single = typeof value === 'string' ? value : undefined;
  return (
    <Radio.Group
      id={id}
      value={single}
      onChange={(e) => onChange?.(e.target.value as string)}
      disabled={disabled}
    >
      <Space size={gap} direction={direction === 'vertical' ? 'vertical' : 'horizontal'} wrap>
        {options.map((o) => (
          <Radio key={o.value} value={o.value} disabled={o.disabled}>
            {textOf(o)}
          </Radio>
        ))}
      </Space>
    </Radio.Group>
  );
}
