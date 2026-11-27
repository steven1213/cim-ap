// 树形下拉。组织架构、工艺路线这类带层级的选项共用：
//   - 数据由调用方建好树（children 嵌套），组件只管渲染与过滤；
//   - `disabled` 节点渲染为「仅层级展示、不可选中」（selectable=false，可展开）；
//   - label 支持 ReactNode（名称 + 右侧 hint），搜索按原始文本命中（title）。
//
// 与 CimSelect 同一套 { value, label } 语义：value 落库，label 显示。

import { TreeSelect } from 'antd';
import { useMemo } from 'react';
import { useUiLabel } from '../locale';

export interface CimTreeOption {
  /** 落库值。 */
  value: string;
  /** 显示文案（已翻译）。 */
  label?: string;
  /** 显示文案的 i18n 键（优先级高于 label）。 */
  labelKey?: string;
  disabled?: boolean;
  /** 右侧补充说明（如编码后面跟名称），同时参与搜索命中。 */
  hint?: string;
  children?: CimTreeOption[];
}

export interface CimTreeSelectProps {
  value?: string | null;
  onChange?: (value: string | null) => void;
  options?: CimTreeOption[];
  allowClear?: boolean;
  /** 默认开启搜索（按节点文本过滤）。 */
  showSearch?: boolean;
  placeholderKey?: string;
  placeholder?: string;
  disabled?: boolean;
  loading?: boolean;
  status?: 'error' | 'warning';
  id?: string;
}

interface AntdTreeNode {
  value: string;
  label: React.ReactNode;
  title: string;
  disabled?: boolean;
  selectable?: boolean;
  children?: AntdTreeNode[];
}

export function CimTreeSelect({
  value,
  onChange,
  options,
  allowClear = true,
  showSearch = true,
  placeholderKey,
  placeholder,
  disabled = false,
  loading = false,
  status,
  id,
}: CimTreeSelectProps) {
  const label = useUiLabel();

  const toAntd = useMemo(() => {
    const walk = (opts: CimTreeOption[]): AntdTreeNode[] =>
      opts.map((o) => {
        const text = o.labelKey ? label(o.labelKey, o.labelKey) : (o.label ?? o.value);
        return {
          value: o.value,
          disabled: o.disabled,
          // 不可选节点仅作层级上下文展示（可展开看子级）
          selectable: !o.disabled,
          label: (
            <span className="cim-select-opt">
              <span>{text}</span>
              {o.hint ? <span className="cim-select-opt-hint">{o.hint}</span> : null}
            </span>
          ),
          // 搜索按原始文本命中（label 是 ReactNode，过滤走 title）
          title: `${text} ${o.hint ?? ''}`.trim(),
          children: o.children?.length ? walk(o.children) : undefined,
        };
      });
    return walk(options ?? []);
  }, [options, label]);

  const ph = placeholderKey ? label(placeholderKey, placeholder ?? '') : placeholder;

  return (
    <TreeSelect
      id={id}
      value={value ?? undefined}
      onChange={(v) => onChange?.(v ?? null)}
      treeData={toAntd}
      allowClear={allowClear}
      showSearch={showSearch}
      filterTreeNode={(input, node) =>
        String((node as AntdTreeNode)?.title ?? '')
          .toLowerCase()
          .includes(input.toLowerCase())
      }
      treeNodeFilterProp="title"
      placeholder={ph}
      disabled={disabled}
      loading={loading}
      status={status}
      style={{ width: '100%' }}
    />
  );
}
