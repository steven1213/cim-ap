// Modal：确认/编辑弹窗（antd Modal 的受控封装）。
//
// 对照 industry.mes：他们的弹窗是每页手写 `.modal-backdrop/.modal-head/.modal-body/.modal-foot`
// （现仓 25 处 backdrop、27 处 foot），焦点管理、Esc 关闭、滚动锁定都没有。
// 这里用 antd Modal 拿到这些行为，视觉仍由 @cim/theme 的 token 决定。

import { Modal as AntModal, Button } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimModalProps {
  open: boolean;
  onClose: () => void;
  titleKey?: string;
  title?: ReactNode;
  children: ReactNode;
  width?: number;
  /** 自定义底部；不传则渲染「取消 / 确定」。 */
  footer?: ReactNode;
  onOk?: () => void;
  okKey?: string;
  cancelKey?: string;
  okLoading?: boolean;
  okDanger?: boolean;
  okDisabled?: boolean;
  /** 点遮罩是否关闭（强制流程弹窗传 false）。 */
  maskClosable?: boolean;
}

export function CimModal({
  open,
  onClose,
  titleKey,
  title,
  children,
  width = 520,
  footer,
  onOk,
  okKey,
  cancelKey,
  okLoading = false,
  okDanger = false,
  okDisabled = false,
  maskClosable = true,
}: CimModalProps) {
  const label = useUiLabel();
  const heading = titleKey ? label(titleKey, titleKey) : title;

  const defaultFooter = onOk ? (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
      <Button onClick={onClose}>{cancelKey ? label(cancelKey, '取消') : label('ui.modal.cancel', '取消')}</Button>
      <Button
        type="primary"
        danger={okDanger}
        loading={okLoading}
        disabled={okDisabled}
        onClick={onOk}
      >
        {okKey ? label(okKey, '确定') : label('ui.modal.ok', '确定')}
      </Button>
    </div>
  ) : (
    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
      <Button onClick={onClose}>{cancelKey ? label(cancelKey, '取消') : label('ui.modal.cancel', '取消')}</Button>
    </div>
  );

  return (
    <AntModal
      open={open}
      onCancel={onClose}
      title={heading}
      width={width}
      footer={footer ?? defaultFooter}
      maskClosable={maskClosable}
      centered
      destroyOnClose
    >
      {children}
    </AntModal>
  );
}
