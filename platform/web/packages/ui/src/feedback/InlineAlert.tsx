// 提示块：错误/成功/警告/信息四种色调的一行提示（可带图标，可多行）。
//
// 为什么值得做成组件：三应用里 `.alert <span>文案</span>` 这种结构手写了 22 处，
// 其中「错误提示 + 下方重试按钮」的组合重复得最多。样式已随 `.alert` 段上提到 `ui.css`
// （原三份 styles.css 逐字节相同），组件只是把结构固定下来，避免出现
// `.alert` 里套 `<div>` 再套 `<span>` 之类的变体。
//
// 刻意**不含重试按钮**：按钮该放块内还是块下，各页面现状不同（有的 `marginTop:12`，
// 有的包在 `.row` 里），猜错就是改版。组件只负责提示本身 —— 需要重试时由调用方
// 在块后自行放按钮，不要在这里猜。
//
// `style` 与其余原生属性透传：存量调用点里 `margin: '0 14px 12px'`、
// `marginTop: 10` 这类「就地微调」共 14 处，全都因为外层容器不同（抽屉内、面板内、
// 页头下）。要求调用方为此包一层 div 只会让 DOM 更深，故直接透传。
import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { IconAlert } from '../icons';

export type AlertTone = 'err' | 'ok' | 'warn' | 'info';

export interface InlineAlertProps
  extends Omit<ComponentPropsWithoutRef<'div'>, 'children' | 'className'> {
  /** 色调，默认 `err`。 */
  tone?: AlertTone;
  /** 自定义图标；`err`/`warn` 默认给警示图标，`ok`/`info` 默认无图标。 */
  icon?: ReactNode;
  /** 提示文案（通常已是翻译后的文本）。 */
  children: ReactNode;
  /** 追加类名（与 `.alert` 同级）。 */
  className?: string;
}

/** 默认图标：仅错误与警告需要视觉强调，成功/信息靠颜色区分即可。 */
function defaultIcon(tone: AlertTone): ReactNode {
  if (tone === 'err' || tone === 'warn') {
    return <IconAlert width={15} height={15} />;
  }
  return null;
}

export function InlineAlert({ tone = 'err', icon, children, className, ...rest }: InlineAlertProps) {
  const leading = icon === undefined ? defaultIcon(tone) : icon;
  return (
    <div {...rest} className={className ? `alert ${tone} ${className}` : `alert ${tone}`}>
      {leading}
      <span>{children}</span>
    </div>
  );
}
