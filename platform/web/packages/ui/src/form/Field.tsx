// 表单项包装：标签 + 必填标记 + 错误 + 提示。
//
// 存在的理由：FormGrid 是「配置驱动」，但详情页里经常要「半自定义」——
// 一个字段需要下拉联动、另一个要挂个按钮。这时用 FormGrid 会束缚，
// 直接写裸 antd 又会丢掉必填星号、错误位置、字号这些一致性。
// CimField 就是这两者之间的那一层：只负责排版，控件由调用方给。
//
// ```tsx
// <CimField labelKey="rms.recipe.code" required error={errors.code}>
//   <CimInput value={code} onChange={setCode} />
// </CimField>
// ```

import type { CSSProperties, ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimFieldProps {
  labelKey?: string;
  label?: ReactNode;
  required?: boolean;
  /** 字段级错误（后端返回的也会映射到这里）。 */
  error?: string | null;
  hintKey?: string;
  hint?: string;
  /** 标签在左（横向）还是在上（纵向），默认在上。 */
  layout?: 'top' | 'left';
  /**
   * 紧凑档：密集工具条里的元数据字段（标签字号 10.5、间距 3）。
   *
   * <p>原 approval 设计器 `.designer-meta` 里手写的 `.field.field-inline` 就是这个形态——
   * 名字与 `layout="left"` 的 `.cim-field-inline` 撞了却语义相反（那个是横向），
   * 所以这里另起 `compact`，别按名字猜。</p>
   */
  compact?: boolean;
  /** layout=left 时的标签宽度，默认 96。 */
  labelWidth?: number;
  /** 占几列（配合 .cim-form-grid 使用）。 */
  span?: number;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
  /**
   * 就地微调（`marginTop` / `padding`）。存量调用点里常见
   * `style={{ marginTop: 10 }}`、`style={{ padding: '0 10px' }}`，
   * 都是因为外层容器不同；要求为此包一层 div 只会让 DOM 更深，故透传。
   */
  style?: CSSProperties;
}

export function CimField({
  labelKey,
  label,
  required = false,
  error,
  hintKey,
  hint,
  layout = 'top',
  compact = false,
  labelWidth = 96,
  span,
  htmlFor,
  children,
  className,
  style,
}: CimFieldProps) {
  const t = useUiLabel();
  const text = labelKey ? t(labelKey, labelKey) : label;
  const hintText = hintKey ? t(hintKey, hintKey) : hint;

  return (
    <div
      className={[
        'cim-field',
        layout === 'left' ? 'cim-field-inline' : '',
        compact ? 'cim-field-compact' : '',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ ...style, ...(span ? { gridColumn: `span ${span}` } : null) }}
    >
      {text ? (
        <label
          className="cim-field-label"
          htmlFor={htmlFor}
          style={layout === 'left' ? { width: labelWidth, flex: `0 0 ${labelWidth}px` } : undefined}
        >
          {text}
          {required ? (
            <span className="cim-req" aria-hidden="true">
              *
            </span>
          ) : null}
        </label>
      ) : null}
      <div className="cim-field-control">
        {children}
        {error ? (
          <div className="cim-field-error" role="alert">
            {error}
          </div>
        ) : hintText ? (
          <div className="cim-field-hint">{hintText}</div>
        ) : null}
      </div>
    </div>
  );
}
