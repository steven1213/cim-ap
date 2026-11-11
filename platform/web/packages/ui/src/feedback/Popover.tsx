// 气泡卡片：点开的小面板（列设置、快照详情、筛选条件）。
//
// 与 CimTooltip 的分工：Tooltip 只放纯文字且悬停即消失；Popover 可放交互内容。
// 参考项目的 GoatPopover 把 trigger 默认设为 click，但内部又给了 `content`
// 字符串入口 —— 两种用法混在一个组件里，容易写出既不能点也不能悬停的东西。

import { Popover } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimPopoverProps {
  /** 触发方式，默认 click（内容里有按钮时必须用 click）。 */
  trigger?: 'click' | 'hover' | 'focus';
  titleKey?: string;
  title?: ReactNode;
  content: ReactNode;
  children: ReactNode;
  placement?: 'top' | 'bottom' | 'left' | 'right' | 'bottomRight' | 'bottomLeft';
  /** 面板宽度，默认自适应（最小 160）。 */
  width?: number;
  /** 受控开关。 */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

export function CimPopover({
  trigger = 'click',
  titleKey,
  title,
  content,
  children,
  placement = 'bottom',
  width,
  open,
  onOpenChange,
}: CimPopoverProps) {
  const label = useUiLabel();
  const heading = titleKey ? label(titleKey, titleKey) : title;
  return (
    <Popover
      trigger={trigger}
      title={heading}
      // antd 5.21 的 PopoverProps 还没有 `styles`（5.23+ 才有），
      // 用内容区外层包装设最小宽度，避免依赖版本升级
      content={<div style={{ minWidth: width ?? 160 }}>{content}</div>}
      placement={placement}
      open={open}
      onOpenChange={onOpenChange}
    >
      {children}
    </Popover>
  );
}
