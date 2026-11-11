// 标签页：详情页的分区切换（基本信息 / 配方体 / 变更历史 / 签核记录）。
//
// 三个增量（都是现仓手写实现里反复出现的问题）：
//   1. **懒挂载**：未访问过的 tab 不渲染（配方详情里「变更历史」可能要查几百条）；
//   2. **保持挂载**：访问过的 tab 切走后不销毁，切回来不重新请求、不丢表单输入；
//   3. **徽标**：`slot.badge` 直接吃数字，省掉各页面自己在 label 后面拼 `(3)`。
//
// 文案走 labelKey，与 DataTable 的列标题同一套规则（切语言必须跟着变）。

import { Tabs } from 'antd';
import { useState, type CSSProperties, type ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface TabItem {
  key: string;
  labelKey?: string;
  label?: string;
  disabled?: boolean;
  /** 右上角计数徽标（0 不显示）。 */
  badge?: number;
  children: ReactNode;
}

export interface CimTabsProps {
  items: TabItem[];
  /** 受控当前 tab。 */
  activeKey?: string;
  defaultActiveKey?: string;
  onChange?: (key: string) => void;
  /** 未访问的 tab 不挂载，默认 true。 */
  lazy?: boolean;
  /** 访问过的 tab 切走后保留，默认 true。 */
  keepAlive?: boolean;
  type?: 'line' | 'card' | 'editable-card';
  size?: 'small' | 'middle' | 'large';
  className?: string;
  /** 内容区额外样式（如固定高度滚动）。 */
  contentStyle?: CSSProperties;
}

export function CimTabs({
  items,
  activeKey,
  defaultActiveKey,
  onChange,
  lazy = true,
  keepAlive = true,
  type = 'line',
  size = 'small',
  className,
  contentStyle,
}: CimTabsProps) {
  const label = useUiLabel();
  const [inner, setInner] = useState(defaultActiveKey ?? items[0]?.key ?? '');
  const active = activeKey ?? inner;
  // 访问过的 tab（lazy 模式下用来决定是否挂载）
  const [visited, setVisited] = useState<Set<string>>(() => new Set([active]));

  const handleChange = (key: string) => {
    setInner(key);
    setVisited((prev) => {
      if (prev.has(key)) return prev;
      const next = new Set(prev);
      next.add(key);
      return next;
    });
    onChange?.(key);
  };

  const antdItems = items.map((item) => {
    const show = !lazy || visited.has(item.key);
    const isActive = item.key === active;
    // keepAlive=false 时只渲染当前 tab，其余卸载
    const rendered = show && (keepAlive || isActive);
    const text = item.labelKey ? label(item.labelKey, item.labelKey) : item.label;
    return {
      key: item.key,
      label:
        item.badge && item.badge > 0 ? (
          <span className="cim-tab-label">
            {text}
            <span className="cim-tab-badge">{item.badge}</span>
          </span>
        ) : (
          text
        ),
      disabled: item.disabled,
      children: rendered ? <div style={contentStyle}>{item.children}</div> : null,
    };
  });

  return (
    <Tabs
      className={className}
      items={antdItems}
      activeKey={active}
      onChange={handleChange}
      type={type}
      size={size}
      // 内容区由我们自己控制挂载，关掉 antd 的销毁策略避免二次卸载
      destroyInactiveTabPane={false}
    />
  );
}
