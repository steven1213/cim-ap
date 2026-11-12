// 开关：布尔字段的编辑控件。
//
// 增量在两点：
//   - 状态文案（`onTextKey` / `offTextKey`）直接在开关旁标注 ——
//     只有颜色没有文字的开关在产线屏幕上必须靠猜，色弱用户更读不出来；
//   - `onChange` 给 boolean + 可选 `confirm` 拦截（停用机台、下线配方这类操作
//     需要二次确认，确认逻辑不该散落在每个页面）。

import { Switch } from 'antd';
import { useUiLabel } from '../locale';

export interface CimSwitchProps {
  checked?: boolean;
  onChange?: (checked: boolean) => void;
  /** 返回 false 则不改值（用于二次确认）。 */
  confirm?: (next: boolean) => boolean | Promise<boolean>;
  onTextKey?: string;
  offTextKey?: string;
  onText?: string;
  offText?: string;
  disabled?: boolean;
  loading?: boolean;
  size?: 'default' | 'small';
  id?: string;
  /** 开关在左、文字在右（默认文字在右）。 */
  inlinePrompt?: boolean;
  /**
   * 静态说明文案（与 `onText`/`offText` 的**状态文案**不同：这里不随开关变化）。
   *
   * 用途：「只显示生效中」这类筛选开关，文字是开关的说明而不是状态。
   * 此前页面只能自己套一层 `<label className="check">`，把开关塞进复选框的排版里。
   */
  label?: string;
  labelKey?: string;
}

export function CimSwitch({
  checked = false,
  onChange,
  confirm,
  onTextKey,
  offTextKey,
  onText,
  offText,
  disabled = false,
  loading = false,
  size = 'default',
  id,
  inlinePrompt = false,
  label: labelText,
  labelKey,
}: CimSwitchProps) {
  const label = useUiLabel();
  const onLabel = onTextKey ? label(onTextKey, onText ?? '') : onText;
  const offLabel = offTextKey ? label(offTextKey, offText ?? '') : offText;

  const control = (
    <Switch
      id={id}
      checked={checked}
      loading={loading}
      disabled={disabled}
      size={size === 'small' ? 'small' : undefined}
      onChange={async (next) => {
        if (confirm) {
          const ok = await confirm(next);
          if (!ok) return;
        }
        onChange?.(next);
      }}
      checkedChildren={inlinePrompt ? onLabel : undefined}
      unCheckedChildren={inlinePrompt ? offLabel : undefined}
    />
  );

  // 静态说明文案：与开关同一行。
  // 这里用 span 而不是 label —— antd Switch 渲染的是 <button>，label 包裹
  // 不会带来「点文字切换」的效果，写成 label 只会误导使用者。
  if (labelKey || labelText) {
    const caption = labelKey ? label(labelKey, labelText ?? '') : labelText;
    return (
      <span className="cim-switch-row">
        {control}
        <span>{caption}</span>
      </span>
    );
  }

  return (
    <span className="cim-switch">
      {control}
      {!inlinePrompt && (onLabel || offLabel) ? (
        <span className="cim-switch-text">{checked ? onLabel : offLabel}</span>
      ) : null}
    </span>
  );
}
