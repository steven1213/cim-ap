// 分隔线。参考项目 GoatDivider 只是 el-divider 的别名，这里同样薄，
// 但补了 `labelKey`（文案走 i18n）与 `spacing`（产线密集布局里默认间距偏大）。

import { Divider } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimDividerProps {
  labelKey?: string;
  children?: ReactNode;
  /** 文案位置。 */
  orientation?: 'left' | 'right' | 'center';
  direction?: 'horizontal' | 'vertical';
  dashed?: boolean;
  /** 上下留白（px）。表单分区之间一般用 8～12。 */
  spacing?: number;
}

export function CimDivider({
  labelKey,
  children,
  orientation = 'left',
  direction = 'horizontal',
  dashed = false,
  spacing,
}: CimDividerProps) {
  const label = useUiLabel();
  const text = labelKey ? label(labelKey, labelKey) : children;
  return (
    <Divider
      orientation={orientation}
      type={direction === 'vertical' ? 'vertical' : 'horizontal'}
      dashed={dashed}
      style={spacing !== undefined ? { marginBlock: spacing } : undefined}
    >
      {text}
    </Divider>
  );
}
