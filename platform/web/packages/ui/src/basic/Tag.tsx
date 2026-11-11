// 标签：普通标签（CimTag）与可选中标签（CimCheckTag）。
//
// 与 StatusTag 的分工：
//   StatusTag / EnumTag —— 输入是**后端枚举**，语义档由映射表决定，调用方不能指定颜色；
//   CimTag            —— 输入是**调用方给的文案**（如「PC」「实验」），语义档由调用方声明。
// 两条路径都用 @cim/theme 的 TONE_VAR，色板仍只有一份。

import { Tag } from 'antd';
import type { ReactNode } from 'react';
import { TONE_SOFT_VAR, TONE_VAR, type StatusTone } from '@cim/theme';
import { useUiLabel } from '../locale';

export interface CimTagProps {
  labelKey?: string;
  children?: ReactNode;
  /** 语义档，默认 idle（中性灰）。只有 5 档，没有「任意颜色」的口子。 */
  tone?: StatusTone;
  /** 空心样式（无底色，仅描边与文字色）。 */
  outline?: boolean;
  size?: 'default' | 'small';
  className?: string;
}

export function CimTag({
  labelKey,
  children,
  tone = 'idle',
  outline = false,
  size = 'default',
  className,
}: CimTagProps) {
  const label = useUiLabel();
  const text = labelKey ? label(labelKey, labelKey) : children;
  return (
    <Tag
      className={className}
      style={{
        marginInlineEnd: 0,
        ...(outline
          ? { color: TONE_VAR[tone], background: 'transparent', borderColor: TONE_VAR[tone] }
          : { color: TONE_VAR[tone], background: TONE_SOFT_VAR[tone], borderColor: 'transparent' }),
        ...(size === 'small' ? { fontSize: 11, lineHeight: '16px', padding: '0 5px' } : null),
      }}
    >
      {text}
    </Tag>
  );
}

export interface CimCheckTagProps {
  labelKey?: string;
  children?: ReactNode;
  /** 是否选中。 */
  checked: boolean;
  onChange?: (checked: boolean) => void;
  /** 选中时的语义档，默认 primary（对应 run 档的蓝）。 */
  tone?: StatusTone;
  disabled?: boolean;
}

/**
 * 可选中标签：筛选栏里用一排标签做多选/单选，比下拉少一次点击。
 * 参考项目的 GoatCheckTag 把 `checked` 写死为 true（等于只是个带边框的标签），
 * 无法表达选中态 —— 这里做成真正受控的开关。
 */
export function CimCheckTag({
  labelKey,
  children,
  checked,
  onChange,
  tone = 'run',
  disabled = false,
}: CimCheckTagProps) {
  const label = useUiLabel();
  const text = labelKey ? label(labelKey, labelKey) : children;
  return (
    <Tag.CheckableTag
      checked={checked}
      onChange={(next) => {
        if (!disabled) onChange?.(next);
      }}
      style={
        checked
          ? {
              color: TONE_VAR[tone],
              background: TONE_SOFT_VAR[tone],
              border: `1px solid ${TONE_VAR[tone]}`,
              opacity: disabled ? 0.5 : 1,
            }
          : {
              color: 'var(--text-2)',
              background: 'transparent',
              border: '1px solid var(--line)',
              opacity: disabled ? 0.5 : 1,
              cursor: disabled ? 'not-allowed' : 'pointer',
            }
      }
    >
      {text}
    </Tag.CheckableTag>
  );
}
