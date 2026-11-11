// 状态标签：把后端枚举值渲染成带语义色的标签。
//
// 与参考项目（GoatTag）的差别：
//   GoatTag 只接收调用方算好的 `type`（success/warning/…），也就是「颜色名」；
//   同一次「设备停机」在 A 页取 danger、B 页取 warning，语义就分叉了。
//   这里只接收**语义档**（由 @cim/theme/status 的映射表决定），调用方无法自选颜色。

import { Tag } from 'antd';
import { TONE_SOFT_VAR, TONE_VAR, toneOf, type StatusTone } from '@cim/theme';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

/** 纯语义标签：调用方已确定语义档时用（如「启用/停用」）。 */
export function ToneTag({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return (
    <Tag
      className="cim-status-tag"
      style={{
        color: TONE_VAR[tone],
        background: TONE_SOFT_VAR[tone],
        borderColor: 'transparent',
      }}
    >
      {children}
    </Tag>
  );
}

/**
 * 枚举状态标签：`dict` 是该枚举所属的域（与 @cim/theme/status 的 STATUS_TONES 键一致）。
 * 文案走 `enum.{dict}.{value}`，缺键时回落为原始枚举值（便于发现漏翻，而不是显示 key）。
 */
export function StatusTag({
  dict,
  value,
  label,
  tone,
}: {
  dict: string;
  value: string | null | undefined;
  /** 覆盖译文（一般不需要，除非后端已回中文）。 */
  label?: string;
  /** 覆盖语义档（一般不需要；未登记的域会回落 idle）。 */
  tone?: StatusTone;
}) {
  const uiLabel = useUiLabel();
  const realTone = tone ?? toneOf(dict, value);
  const raw = value ?? '—';
  const text = label ?? uiLabel(`enum.${dict}.${raw}`, raw);
  return <ToneTag tone={realTone}>{text}</ToneTag>;
}
