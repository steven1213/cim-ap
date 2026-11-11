// 状态标签：把后端枚举值渲染成带语义色的标签。
//
// 与参考项目（GoatTag）的差别：
//   GoatTag 只接收调用方算好的 `type`（success/warning/…），也就是「颜色名」；
//   同一次「设备停机」在 A 页取 danger、B 页取 warning，语义就分叉了。
//   这里只接收**语义档**（由 @cim/theme/status 的映射表决定），调用方无法自选颜色。
//
// ⚠️ 曾经还有一个 `ToneTag`（「调用方已确定语义档时用」），已删除：
//    它就是 `CimTag tone=…` 少了 `outline`/`size` 的子集，两个名字做同一件事
//    只会让人在两者之间犹豫。需要「纯语义档」时直接用 `<CimTag tone>`。
//
// ⚠️ 本组件**不加** `cim-status-tag` 类：那个类带 `font-size: var(--fs-xs)` /
//    `padding-inline: 7px`，而现有调用点普遍传 `size="small"`（11px / padding 5px）。
//    两者叠加会改变存量标签的尺寸。需要「标准状态标签观感」（无 size 覆盖）时
//    由调用方显式传 `className="cim-status-tag"`。

import { toneOf, type StatusTone } from '@cim/theme';
import { CimTag } from '../basic/Tag';
import { useUiLabel } from '../locale';

/**
 * 枚举状态标签：`dict` 是该枚举所属的域（与 @cim/theme/status 的 STATUS_TONES 键一致）。
 * 文案走 `enum.{dict}.{value}`，缺键时回落为原始枚举值（便于发现漏翻，而不是显示 key）。
 */
export function StatusTag({
  dict,
  value,
  label,
  tone,
  size,
  outline,
}: {
  dict: string;
  value: string | null | undefined;
  /** 覆盖译文（一般不需要，除非后端已回中文）。 */
  label?: string;
  /** 覆盖语义档（一般不需要；未登记的域会回落 idle）。 */
  tone?: StatusTone;
  size?: 'default' | 'small';
  outline?: boolean;
}) {
  const uiLabel = useUiLabel();
  const realTone = tone ?? toneOf(dict, value);
  const raw = value ?? '—';
  const text = label ?? uiLabel(`enum.${dict}.${raw}`, raw);
  return (
    <CimTag tone={realTone} size={size} outline={outline}>
      {text}
    </CimTag>
  );
}

