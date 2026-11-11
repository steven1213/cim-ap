// 折叠面板：详情页把「不常看的字段」收起来，首屏只留关键信息。
//
// 参考项目有三个折叠组件：GoatCollapse + GoatCollapseItem（父子靠 provide/inject 通信）、
// GoatDirectCollapse（另一个 el-collapse 封装）、data/GoatDirectCollapse（同一件事）。
// 三个文件解决同一个问题。这里一个组件 + items 数组。
//
// 增量：
//   - `labelKey` 翻译；
//   - `badge`：折叠标题右侧显示计数（如「变更历史 12」），收起时也能看到有新内容；
//   - 受控/非受控都可，`accordion` 时自动只留一个。

import { Collapse } from 'antd';
import { useState, type ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CollapseItem {
  key: string;
  labelKey?: string;
  label?: string;
  /** 标题右侧计数（0 不显示）。 */
  badge?: number;
  /** 标题右侧额外内容（按钮、状态标签）。点击不会触发展开。 */
  extra?: ReactNode;
  children: ReactNode;
  disabled?: boolean;
}

export interface CimCollapseProps {
  items: CollapseItem[];
  /** 受控展开的 key 列表。 */
  activeKeys?: string[];
  /** 非受控默认展开。 */
  defaultActiveKeys?: string[];
  onChange?: (keys: string[]) => void;
  /** 手风琴模式：同时只展开一个。 */
  accordion?: boolean;
  bordered?: boolean;
  size?: 'small' | 'middle' | 'large';
  className?: string;
}

export function CimCollapse({
  items,
  activeKeys,
  defaultActiveKeys = [],
  onChange,
  accordion = false,
  bordered = false,
  size = 'small',
  className,
}: CimCollapseProps) {
  const label = useUiLabel();
  const [inner, setInner] = useState<string[]>(defaultActiveKeys);
  const active = activeKeys ?? inner;

  const antdItems = items.map((item) => ({
    key: item.key,
    label: (
      <span className="cim-collapse-label">
        {item.labelKey ? label(item.labelKey, item.labelKey) : item.label}
        {item.badge && item.badge > 0 ? <span className="cim-tab-badge">{item.badge}</span> : null}
      </span>
    ),
    extra: item.extra ? (
      // 阻止冒泡：点 extra 里的按钮不应展开/收起面板
      <span onClick={(e) => e.stopPropagation()}>{item.extra}</span>
    ) : undefined,
    children: item.children,
    disabled: item.disabled,
    showArrow: true,
  }));

  return (
    <Collapse
      className={className ? `cim-collapse ${className}` : 'cim-collapse'}
      items={antdItems}
      activeKey={active}
      onChange={(keys) => {
        const list = (Array.isArray(keys) ? keys : [keys]).map(String);
        setInner(list);
        onChange?.(list);
      }}
      accordion={accordion}
      bordered={bordered}
      size={size}
      ghost={!bordered}
    />
  );
}
