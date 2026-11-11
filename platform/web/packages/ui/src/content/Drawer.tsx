// 抽屉：右侧滑出的详情/表单容器（antd Drawer 的受控封装）。
//
// 与 CimModal 的分工（这条约定要写死，否则各页面会乱用）：
//   Modal  —— 短流程、需要「确认」的动作（新建、删除确认、批量操作），宽度 ≤ 640；
//   Drawer —— 长内容、可滚动、需要参照原列表的查看/编辑（配方详情、审批轨迹）。
//
// 参考项目的 GoatDrawer 把 `size="60%"` 写死，且直接 `v-model` 绑在 options 对象上
// （外部改 options.isOpen 才生效），这里改成标准受控。

import { Drawer, Space } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimDrawerProps {
  open: boolean;
  onClose: () => void;
  titleKey?: string;
  title?: ReactNode;
  children: ReactNode;
  width?: number | string;
  /** 底部操作区；不传则不渲染 footer。 */
  footer?: ReactNode;
  /** 点遮罩关闭（填写中的表单建议 false，防误触丢失内容）。 */
  maskClosable?: boolean;
  /** 关闭前确认（有未保存修改时返回 false 拦截）。 */
  onBeforeClose?: () => boolean | Promise<boolean>;
  extra?: ReactNode;
}

export function CimDrawer({
  open,
  onClose,
  titleKey,
  title,
  children,
  width = 720,
  footer,
  maskClosable = true,
  onBeforeClose,
  extra,
}: CimDrawerProps) {
  const label = useUiLabel();
  const heading = titleKey ? label(titleKey, titleKey) : title;

  return (
    <Drawer
      open={open}
      onClose={async () => {
        if (onBeforeClose) {
          const ok = await onBeforeClose();
          if (!ok) return;
        }
        onClose();
      }}
      title={heading}
      width={width}
      placement="right"
      maskClosable={maskClosable}
      destroyOnClose
      extra={extra ? <Space size={8}>{extra}</Space> : undefined}
      footer={
        footer ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>{footer}</div>
        ) : undefined
      }
      styles={{ body: { paddingTop: 12 } }}
    >
      {children}
    </Drawer>
  );
}
