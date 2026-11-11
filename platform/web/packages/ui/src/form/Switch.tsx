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
}: CimSwitchProps) {
  const label = useUiLabel();
  const onLabel = onTextKey ? label(onTextKey, onText ?? '') : onText;
  const offLabel = offTextKey ? label(offTextKey, offText ?? '') : offText;

  return (
    <span className="cim-switch">
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
      {!inlinePrompt && (onLabel || offLabel) ? (
        <span className="cim-switch-text">{checked ? onLabel : offLabel}</span>
      ) : null}
    </span>
  );
}
