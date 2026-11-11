// Toast / Confirm：平台统一的消息与二次确认。
//
// 现状（改造前）：4 个前端各有各的写法 ——
//   · RMS / approval 页面用 `const [msg, setMsg] = useState('')` 在页顶挂一条提示条
//     （样式各异、不会自动消失、成功失败同色、切页即丢）；
//   · 二次确认直接 `window.confirm()` 或手写 `.modal` 三件套 —— 前者阻塞主线程且样式不可控，
//     后者 25 处 backdrop / 27 处 foot 各写一遍，焦点与 Esc 都没接。
//
// 这里统一到 antd 的 **上下文版** message / modal（`App.useApp()`）：
//   · 不用 `message.success()` 这类静态方法 —— 静态方法拿不到 ConfigProvider 的主题，
//     暗色主题下弹出来仍是浅色，且 z-index 与项目其它浮层不共享；
//   · 不用 `window.confirm` —— 阻塞、不可主题化、无法带富文本与危险态按钮。
//
// 铁律照旧：组件不碰文案。内置按钮（确定/取消）走 useUiLabel；
// 业务消息（「已创建 3 条」）由调用方 t() 后传入 —— 那是数据，不是组件文案。

import { App as AntApp } from 'antd';
import { useCallback, useMemo, type ReactNode } from 'react';
import { useUiLabel } from '../locale';

/** 消息提示。返回值：`loading` 返回关闭函数，其余返回 void。 */
export interface CimToast {
  success: (content: ReactNode, duration?: number) => void;
  error: (content: ReactNode, duration?: number) => void;
  warning: (content: ReactNode, duration?: number) => void;
  info: (content: ReactNode, duration?: number) => void;
  /** 常用于「请求中…」，拿到 close 后在 finally 里调用。 */
  loading: (content: ReactNode, duration?: number) => () => void;
  /** 一次性清空所有消息（登出、切租户时用）。 */
  destroy: () => void;
}

export interface CimConfirmOptions {
  /** 标题走 key —— 切语言后重开弹窗才是新语言。 */
  titleKey?: string;
  title?: ReactNode;
  contentKey?: string;
  content?: ReactNode;
  /** 危险动作：确定按钮飘红。 */
  danger?: boolean;
  okKey?: string;
  cancelKey?: string;
  width?: number;
}

/** resolve(true) = 用户点了确定；resolve(false) = 取消 / Esc / 点遮罩关闭。 */
export type CimConfirm = (options: CimConfirmOptions) => Promise<boolean>;

/**
 * 消息层容器。必须放在 `CimProvider` **内部**（否则拿不到主题），
 * 放在业务 `<App />` **外部**。
 *
 * `component={false}` 是关键：antd 的 App 默认会渲染一层 `<div class="ant-app">`，
 * 套在应用外层会把页面多包一层块级容器（影响 `.app` 的 100vh 布局），
 * 关掉它只保留上下文，DOM 结构零变化。
 */
export function UiToastProvider({ children }: { children: ReactNode }) {
  return <AntApp component={false}>{children}</AntApp>;
}

export function useToast(): CimToast {
  const { message } = AntApp.useApp();
  return useMemo<CimToast>(
    () => ({
      success: (content, duration) => message.success(content, duration),
      error: (content, duration) => message.error(content, duration),
      warning: (content, duration) => message.warning(content, duration),
      info: (content, duration) => message.info(content, duration),
      loading: (content, duration) => {
        const close = message.loading(content, duration ?? 0);
        return () => close();
      },
      destroy: () => message.destroy(),
    }),
    [message],
  );
}

export function useConfirm(): CimConfirm {
  const { modal } = AntApp.useApp();
  const label = useUiLabel();

  return useCallback<CimConfirm>(
    (options) =>
      new Promise<boolean>((resolve) => {
        modal.confirm({
          title: options.titleKey ? label(options.titleKey, options.titleKey) : options.title,
          content: options.contentKey ? label(options.contentKey, options.contentKey) : options.content,
          okText: label(options.okKey ?? 'ui.confirm.ok', '确定'),
          cancelText: label(options.cancelKey ?? 'ui.confirm.cancel', '取消'),
          okButtonProps: options.danger ? { danger: true } : undefined,
          width: options.width,
          centered: true,
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      }),
    [modal, label],
  );
}
