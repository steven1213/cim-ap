// 二次确认气泡：删除、停用、下发这类操作的轻量确认。
//
// 现仓 `confirmDelete` 手写 20+ 次，文案散落成 `'确认删除？此操作不可撤销。'`
// 与 `'Delete this record?'` 两份，漏改一边就会中英混排。这里统一走键。
//
// 与 CimModal 的分工：影响面小（一条记录）用 Popconfirm；影响面大
// （整批、整条配方、会影响在制批次）用 Modal，因为 Modal 能承载说明文字与输入框。

import { Popconfirm } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimPopconfirmProps {
  /** 主问句的 i18n 键，默认「确认执行该操作？」。 */
  titleKey?: string;
  title?: string;
  /** 补充说明（后果描述），比主问句更适合放「不可撤销」。 */
  descriptionKey?: string;
  description?: string;
  onConfirm: () => void | Promise<void>;
  onCancel?: () => void;
  children: ReactNode;
  /** 确认按钮是否危险色（删除类为 true）。 */
  danger?: boolean;
  okKey?: string;
  cancelKey?: string;
  disabled?: boolean;
  placement?: 'top' | 'bottom' | 'left' | 'right' | 'topRight' | 'bottomRight';
}

export function CimPopconfirm({
  titleKey,
  title,
  descriptionKey,
  description,
  onConfirm,
  onCancel,
  children,
  danger = true,
  okKey,
  cancelKey,
  disabled = false,
  placement = 'topRight',
}: CimPopconfirmProps) {
  const label = useUiLabel();
  const main = titleKey ? label(titleKey, title ?? '') : (title ?? label('ui.confirm.title', '确认执行该操作？'));
  const desc = descriptionKey ? label(descriptionKey, description ?? '') : description;

  return (
    <Popconfirm
      title={main}
      description={desc}
      onConfirm={onConfirm}
      onCancel={onCancel}
      okText={okKey ? label(okKey, '确定') : label('ui.confirm.ok', '确定')}
      cancelText={cancelKey ? label(cancelKey, '取消') : label('ui.confirm.cancel', '取消')}
      okButtonProps={danger ? { danger: true } : undefined}
      disabled={disabled}
      placement={placement}
    >
      {children}
    </Popconfirm>
  );
}
