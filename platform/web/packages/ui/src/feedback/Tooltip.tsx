// 气泡提示：文字说明的悬停/聚焦浮层。
//
// 为什么值得单独包一层：Tooltip 的文案几乎总是要翻译，而直接写
// `<Tooltip title={t('x')}>` 的人会在这里直接算 t() —— 一旦这个 Tooltip
// 被包在 memo 组件里，切语言就不重算。统一走 textKey 避免这个坑。

import { Tooltip } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimTooltipProps {
  /** 提示文案的 i18n 键。 */
  textKey?: string;
  /** 已翻译文案（与 textKey 二选一）。 */
  text?: string;
  children: ReactNode;
  placement?: 'top' | 'bottom' | 'left' | 'right';
  /** 延迟出现（毫秒），默认 200；产线快速扫视时不要设太大。 */
  mouseEnterDelay?: number;
  /** 内容较长时限制宽度，默认 280。 */
  maxWidth?: number;
}

export function CimTooltip({
  textKey,
  text,
  children,
  placement = 'top',
  mouseEnterDelay = 0.2,
  maxWidth = 280,
}: CimTooltipProps) {
  const label = useUiLabel();
  const content = textKey ? label(textKey, text ?? '') : text;
  // 没有文案就不要包一层空 Tooltip（会吃掉鼠标事件且显示空框）
  if (!content) return <>{children}</>;
  return (
    <Tooltip
      title={<span style={{ display: 'inline-block', maxWidth }}>{content}</span>}
      placement={placement}
      mouseEnterDelay={mouseEnterDelay}
    >
      {children}
    </Tooltip>
  );
}
