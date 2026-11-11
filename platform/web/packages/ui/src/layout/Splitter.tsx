// 分栏：左树右表、上概览下明细的分割布局。
//
// ⚠️ 这里踩过一个坑，写下来免得重犯：
//   antd 的 `Splitter.Panel` 是 `() => null`，`Splitter` 用 `useItems(children)`
//   把**直接子元素的 props 摊平**成 item 再自己渲染（见 antd/es/splitter/hooks/useItems）。
//   也就是说 Panel 自身永远不渲染 —— 中间如果隔一层自定义组件
//   （`<CimSplitterPanel>`），antd 看到的是那个组件的 props，拿到的是它的
//   `children`（可能是个函数），结果面板直接空白。
//   所以本组件用 **panels 配置数组**，由 CimSplitter 自己产出 `<Splitter.Panel>`，
//   保证 Panel 是 Splitter 的直接子元素。
//
// 尺寸上报（render prop）保留：图表必须知道容器尺寸才能重绘，这是参考项目
// GoatSplitterPanel 做对的地方。区别是它把尺寸塞进 Vue 的 slot props，
// 这里用函数 children，并用 rAF 节流避免拖拽时每帧重绘。

import { Splitter } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';

export interface SplitterPanelSize {
  width: number;
  height: number;
}

export interface SplitterPanelConfig {
  /** 唯一键。 */
  key: string;
  /** 内容；传函数可拿到面板实时尺寸。 */
  content: ReactNode | ((size: SplitterPanelSize) => ReactNode);
  /** 初始尺寸（数字 = px，字符串 = 百分比）。 */
  defaultSize?: number | string;
  /** 受控尺寸（配 onResizeEnd 使用；单给 size 而不给 onResize 会被 antd 警告）。 */
  size?: number | string;
  min?: number | string;
  max?: number | string;
  /** 是否允许拖拽该面板边界，默认 true。 */
  resizable?: boolean;
  className?: string;
}

export interface CimSplitterProps {
  /** 分栏方向：horizontal = 左右分，vertical = 上下分。 */
  direction?: 'horizontal' | 'vertical';
  panels: SplitterPanelConfig[];
  /** 拖拽结束回调（各面板实际像素尺寸）。 */
  onResizeEnd?: (sizes: number[]) => void;
  /** 高度；不传则撑满父容器。 */
  height?: number | string;
  className?: string;
}

export function CimSplitter({
  direction = 'horizontal',
  panels,
  onResizeEnd,
  height,
  className,
}: CimSplitterProps) {
  return (
    <div
      className={className ? `cim-splitter ${className}` : 'cim-splitter'}
      style={{ height: height ?? '100%', minHeight: 0, overflow: 'hidden' }}
    >
      <Splitter layout={direction} onResizeEnd={onResizeEnd}>
        {panels.map((p) => (
          // 必须是 Splitter.Panel 的直接元素：见文件头说明
          <Splitter.Panel
            key={p.key}
            size={p.size}
            defaultSize={p.defaultSize ?? p.size}
            min={p.min}
            max={p.max}
            resizable={p.resizable ?? true}
          >
            <PanelBody className={p.className}>{p.content}</PanelBody>
          </Splitter.Panel>
        ))}
      </Splitter>
    </div>
  );
}

/** 面板内容容器：负责测量自身尺寸并回调给 render prop。 */
function PanelBody({
  children,
  className,
}: {
  children: ReactNode | ((size: SplitterPanelSize) => ReactNode);
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [box, setBox] = useState<SplitterPanelSize>({ width: 0, height: 0 });
  const raf = useRef<number | null>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const obs = new ResizeObserver(([entry]) => {
      const rect = entry.contentRect;
      const width = Math.floor(rect.width);
      const height = Math.floor(rect.height);
      // rAF 节流：拖拽分栏时 ResizeObserver 每帧都触发，直接 setState 会掉帧
      if (raf.current !== null) cancelAnimationFrame(raf.current);
      raf.current = requestAnimationFrame(() => {
        setBox((prev) => (prev.width === width && prev.height === height ? prev : { width, height }));
      });
    });
    obs.observe(el);
    return () => {
      if (raf.current !== null) cancelAnimationFrame(raf.current);
      obs.disconnect();
    };
  }, []);

  return (
    <div
      ref={ref}
      className={className ? `cim-splitter-panel ${className}` : 'cim-splitter-panel'}
    >
      {typeof children === 'function' ? children(box) : children}
    </div>
  );
}
