// 树：组织架构、设备层级（厂区 → 区域 → 机台）、权限树、配方目录。
//
// 对照参考项目 GoatTree / GoatTableSettingTree：两个文件 95% 相同，只是
// `check-on-click-node` 一个 true 一个 false —— 就是「没抽参数，复制了一份」。
// 这里一个组件 + `checkOnClick` 参数。
//
// 增量：
//   - `labelKey` 自动翻译（节点的 title 常来自后端，但层级名往往还是配置项）；
//   - 受控 checked / selected / expanded 三套状态都可选，不传就走非受控；
//   - `search` 高亮过滤（设备树节点上百个时必须有）。

import { Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useMemo, useState, type ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimTreeNode {
  key: string;
  labelKey?: string;
  label?: string;
  /**
   * 自定义标题节点（优先级最高）。用于「名称 + 编码 + 语义标签」这类富节点
   * —— 例如设备树的一个机台节点既要显示 `EQP-01 · 刻蚀机`，右侧还要跟状态标签，
   * 纯字符串表达不了；用字符串拼又拿不到样式。
   */
  labelNode?: ReactNode;
  /**
   * 搜索用的纯文本。`labelNode` 是 ReactNode，`search` 无法从中取文案，
   * 这类节点必须显式给出可搜索文本（如 `EQP-01 刻蚀机`），否则过滤时会被漏掉。
   */
  searchText?: string;
  children?: CimTreeNode[];
  disabled?: boolean;
  /** 叶子节点（无 children 时自动判定，不必手填）。 */
  isLeaf?: boolean;
  /** 节点前的图标。 */
  icon?: ReactNode;
}

export interface CimTreeProps {
  nodes: CimTreeNode[];
  /** 复选。 */
  checkable?: boolean;
  checkedKeys?: string[];
  onCheck?: (keys: string[]) => void;
  selectedKeys?: string[];
  onSelect?: (key: string) => void;
  /** 点节点即勾选（参考项目两个组件正是在这一点上分叉）。 */
  checkOnClick?: boolean;
  expandedKeys?: string[];
  onExpand?: (keys: string[]) => void;
  defaultExpandAll?: boolean;
  /** 拖拽排序。 */
  draggable?: boolean;
  onDrop?: (dragKey: string, dropKey: string, position: 'before' | 'after' | 'inside') => void;
  /** 关键词过滤（按显示文案包含匹配；命中节点的祖先会一并显示）。 */
  search?: string;
  showLine?: boolean;
  height?: number;
  className?: string;
}

/** 把业务节点转成 antd 的 DataNode（翻译 title）。 */
function toDataNode(
  node: CimTreeNode,
  label: (k: string, fb: string) => string,
): DataNode & { children?: DataNode[] } {
  const title = node.labelNode
    ? node.labelNode
    : node.labelKey
      ? label(node.labelKey, node.labelKey)
      : node.label;
  return {
    key: node.key,
    title,
    icon: node.icon,
    disabled: node.disabled,
    isLeaf: node.isLeaf ?? (!node.children || node.children.length === 0),
    children: node.children?.map((c) => toDataNode(c, label)),
  };
}

/** 过滤：命中自身或任一后代命中则保留。 */
function filterTree(nodes: CimTreeNode[], kw: string, label: (k: string, fb: string) => string): CimTreeNode[] {
  if (!kw) return nodes;
  const lower = kw.toLowerCase();
  const walk = (list: CimTreeNode[]): CimTreeNode[] => {
    const out: CimTreeNode[] = [];
    list.forEach((n) => {
      const text = (
        n.searchText ??
        (n.labelKey ? label(n.labelKey, n.labelKey) : n.label ?? '')
      ).toLowerCase();
      const kids = n.children ? walk(n.children) : [];
      if (text.includes(lower) || kids.length > 0) {
        out.push({ ...n, children: kids.length > 0 ? kids : n.children });
      }
    });
    return out;
  };
  return walk(nodes);
}

/** 收集所有分支节点 key（用于过滤后自动展开）。 */
function branchKeys(nodes: CimTreeNode[]): string[] {
  const out: string[] = [];
  const walk = (list: CimTreeNode[]) => {
    list.forEach((n) => {
      if (n.children && n.children.length > 0) {
        out.push(n.key);
        walk(n.children);
      }
    });
  };
  walk(nodes);
  return out;
}

export function CimTree({
  nodes,
  checkable = false,
  checkedKeys,
  onCheck,
  selectedKeys,
  onSelect,
  checkOnClick = false,
  expandedKeys,
  onExpand,
  defaultExpandAll = false,
  draggable = false,
  onDrop,
  search,
  showLine = false,
  height,
  className,
}: CimTreeProps) {
  const label = useUiLabel();
  const [innerExpanded, setInnerExpanded] = useState<string[]>(() =>
    defaultExpandAll ? branchKeys(nodes) : [],
  );

  const filtered = useMemo(() => filterTree(nodes, search ?? '', label), [nodes, search, label]);
  const treeData = useMemo(() => filtered.map((n) => toDataNode(n, label)), [filtered, label]);

  // 搜索时强制展开全部命中路径，否则用户看到空树
  const expanded = expandedKeys ?? (search ? branchKeys(filtered) : innerExpanded);

  return (
    <Tree
      className={className ? `cim-tree ${className}` : 'cim-tree'}
      treeData={treeData}
      checkable={checkable}
      checkStrictly={false}
      checkedKeys={checkedKeys}
      onCheck={(keys) => {
        const list = Array.isArray(keys) ? keys : keys.checked;
        onCheck?.(list.map(String));
      }}
      selectedKeys={selectedKeys}
      onSelect={(_keys, info) => {
        onSelect?.(String(info.node.key));
        if (checkOnClick && checkable) {
          const k = String(info.node.key);
          const cur = checkedKeys ?? [];
          onCheck?.(cur.includes(k) ? cur.filter((x) => x !== k) : [...cur, k]);
        }
      }}
      expandedKeys={expanded}
      onExpand={(keys) => {
        setInnerExpanded(keys.map(String));
        onExpand?.(keys.map(String));
      }}
      autoExpandParent={!!search}
      showLine={showLine}
      height={height}
      draggable={draggable ? { icon: false } : false}
      blockNode
      onDrop={(info) => {
        if (!onDrop) return;
        const dragKey = String(info.dragNode.key);
        const dropKey = String(info.node.key);
        // antd 的 dropPosition 是绝对位置，需减去节点自身 pos 才能得到相对方向：
        // -1 = 放到该节点之前，0 = 放进去（成为子节点），1 = 放到之后
        const posArr = info.node.pos.split('-');
        const rel = info.dropPosition - Number(posArr[posArr.length - 1]);
        const where: 'before' | 'after' | 'inside' = rel < 0 ? 'before' : rel > 0 ? 'after' : 'inside';
        onDrop(dragKey, dropKey, where);
      }}
    />
  );
}
