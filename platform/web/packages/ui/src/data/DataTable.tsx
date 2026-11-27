// DataTable：平台唯一的表格组件（antd Table 的受控封装）。
//
// 设计来源与取舍（对照 industry.mes 的 GoatTable / GoatViewTable）：
//   借鉴 —— 工具栏（动作 + 搜索 + 计数 + 列设置）、列显示/顺序可配、行语义色、
//           双击/单击行、选择集合、服务端分页；
//   不改 —— 原组件把「取数 + 分页 + 导出 + 右键菜单 + 单元格编辑器」全塞进一个
//           1057 行的文件（GoatViewTable 更是 1927 行），业务侧无法单独替换任一部分。
//           这里只做**渲染层**：数据由调用方通过 `rows` 传入，加载/错误由 `loading`/`error` 表达；
//   改掉 —— 原组件的文案全部硬编码中文（无 i18n），这里一律走 `titleKey` / useUiLabel，
//           切语言时表头随之重算。
//
// 铁律：本组件不 fetch、不 t() 具体文案、不读 store。数据与翻译都在外层。

import { Button, Checkbox, Input, Popover, Table } from 'antd';
import type { TableProps } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type ReactNode,
  type ThHTMLAttributes,
} from 'react';
import { useUiLabel } from '../locale';
import { getPaginationDefaults } from './paginationDefaults';
import { useColumns } from './useColumns';
import { EmptyState } from '../content/Page';

export type SortDir = 'asc' | 'desc';

export interface DataTableColumn<T> {
  /** 数据字段名（同时作为列唯一键）。 */
  key: string;
  /** 列标题的 i18n key —— 刻意不支持传 `title: t(...)`：t() 在构造时求值，
   *  切语言不会重算，是表头停在上一种语言的最常见原因。 */
  titleKey: string;
  width?: number;
  align?: 'left' | 'center' | 'right';
  /** 编码 / 时间戳用等宽字体。 */
  mono?: boolean;
  /** 出厂默认隐藏（用户可在列设置里打开）。 */
  hiddenByDefault?: boolean;
  /** 单元格自定义渲染（优先级高于默认取值渲染）。 */
  render?: (row: T) => ReactNode;
  /** 是否可排序。**必须**同时提供 DataTable 的 `onSortChange`，否则不生效：
   *  服务端分页下客户端排序只会对当前页排序，属于会骗人的功能。 */
  sortable?: boolean;
  /** 超长内容省略并显示 title。 */
  ellipsis?: boolean;
}

export interface DataTableProps<T> {
  columns: Array<DataTableColumn<T>>;
  rows: T[];
  rowKey: (row: T) => string;
  loading?: boolean;
  /** 请求失败信息。非空时整块替换为错误态（不吞错）。 */
  error?: string | null;
  onRetry?: () => void;

  /** 工具栏：左侧动作（新建/批量/导出等）。 */
  actions?: ReactNode;
  /** 工具栏：搜索框。 */
  search?: {
    value: string;
    onChange: (value: string) => void;
    placeholderKey?: string;
    width?: number;
  };
  onRefresh?: () => void;
  /** 传了才启用「列设置」（显示/顺序），并以此为 localStorage 键做持久化。 */
  settingsKey?: string;

  /** 行选择（受控）。 */
  selectedKeys?: string[];
  onSelectionChange?: (keys: string[]) => void;
  selectable?: (row: T) => boolean;

  /** 服务端分页（受控）。不传则不分页。 */
  pagination?: {
    page: number;
    size: number;
    total: number;
    onChange: (page: number, size: number) => void;
    sizeOptions?: number[];
  };

  onRowClick?: (row: T) => void;
  onRowDoubleClick?: (row: T) => void;
  rowClassName?: (row: T) => string;

  /** 排序回调（与列上的 `sortable` 配合）。 */
  sort?: { key: string; dir: SortDir };
  onSortChange?: (key: string, dir: SortDir | null) => void;

  emptyTitleKey?: string;
  emptyHintKey?: string;
  /**
   * 表格标题（i18n key / 已翻译文本）。
   *
   * <p>为什么放在组件里：此前「标题 + 表格」的做法是外层包一个 `<Panel titleKey flush>`，
   * 而 DataTable 自己也有一行工具栏 → **两层头**（标题行 + 只有右侧「共 n / 刷新」的空行）。
   * 传 `titleKey` 后计数/刷新/列设置并入标题行右侧，工具栏只在有动作/搜索时才出现。</p>
   */
  titleKey?: string;
  title?: ReactNode;
  /** 标题下方的小号说明。 */
  subKey?: string;
  sub?: ReactNode;
  /**
   * 标题行右侧的动作（如「查看全部」链接）。
   *
   * <p>与 `actions` 的区别：`actions` 在工具栏左侧（新建/导入这类**操作表格**的按钮），
   * `headActions` 在标题行右侧（跳转到其它页面的入口）。此前这类入口只能靠
   * `<Panel title actions>` 外包一层，于是又回到「标题行 + 空工具栏行」的两层头。</p>
   */
  headActions?: ReactNode;
  sticky?: boolean;
  size?: 'small' | 'middle';
  /**
   * 横向滚动的最小宽度下限（px）。默认行为已统一内置：
   * 列宽合计超出容器 → 自动出横向滚动条；未超出 → 表格铺满容器。
   * 数字仅在需要给表格一个「比列宽合计更宽」的下限时才传。
   */
  scrollX?: number | 'max-content';
  /**
   * 虚拟滚动：传视口高度（px）启用，只渲染视口内的行。
   *
   * 适用：日志、审计、参数明细这类**一次给全量**的长列表（数千行）。
   * 不适用：服务端分页的列表 —— 一页才 20/50 行，虚拟滚动没有收益却丢失分页语义。
   *
   * 前置条件：每列都要有 `width`。antd 的虚拟表格按列宽定位列，任一列缺宽会塌成 0
   * （开发态会打印告警，不静默降级）。
   */
  virtual?: number;
  /** 计数展示：默认显示「已选 n / 共 m」。 */
  showCount?: boolean;
  /**
   * 去掉外框（边框 / 圆角 / 背景），用于**嵌在已有面板里**的第二张表。
   *
   * 场景：一面板内「工具栏 + 主表 + 明细表」这类分区，外层 `Panel` 已经画了框，
   * 内层再画一层会出现框套框（历史上各页为此各写一遍 `table-wrap`）。
   */
  flat?: boolean;
}

/** 未显式声明 width 的列的默认宽度：仅虚拟滚动模式使用（虚拟要求每列定宽）。
 *  非虚拟模式下未声明 width 的列保持**弹性**——吸收剩余空间，表不必要地撑出滚动条。 */
const DEFAULT_COL_W = 180;
/** 列宽拖拽的下限，防止拖没。 */
const MIN_COL_W = 60;
/** 弹性列在溢出态的保底宽度：固定列合计吃满表格宽时，弹性列至少留这么多，防止塌 0。 */
const FLEX_MIN_W = 80;
/** 行选择列（antd 自动加的 checkbox 列）占位宽度，计入横向滚动宽度。 */
const SELECTION_COL_W = 32;

interface ResizableHeaderProps extends ThHTMLAttributes<HTMLTableCellElement> {
  width?: number;
  onResize?: (width: number) => void;
  onReset?: () => void;
}

/**
 * 表头单元格包装：右缘挂列宽拖拽手柄。
 *
 * <p>拖拽以 th 的实际渲染宽度为起点（对未声明 width 的弹性列同样成立），
 * mousemove 期间实时回调 onResize → DataTable 更新列宽状态 → antd colgroup 重排，
 * 列宽合计超过容器时横向滚动条自动出现。双击手柄恢复该列出厂宽度。</p>
 */
function ResizableHeaderCell({ onResize, onReset, children, ...rest }: ResizableHeaderProps) {
  const startRef = useRef<{ x: number; w: number } | null>(null);
  const handleRef = useRef<HTMLSpanElement | null>(null);

  const onMouseDown = (e: ReactMouseEvent<HTMLSpanElement>) => {
    if (!onResize) return;
    e.preventDefault();
    e.stopPropagation();
    const th = e.currentTarget.parentElement as HTMLElement | null;
    if (!th) return;
    startRef.current = { x: e.clientX, w: th.offsetWidth };
    handleRef.current?.classList.add('is-dragging');
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    const move = (ev: MouseEvent) => {
      if (!startRef.current) return;
      onResize(Math.max(MIN_COL_W, startRef.current.w + (ev.clientX - startRef.current.x)));
    };
    const up = () => {
      startRef.current = null;
      handleRef.current?.classList.remove('is-dragging');
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };

  return (
    <th {...rest}>
      {children}
      {onResize ? (
        <span
          ref={handleRef}
          className="cim-col-resizer"
          title="拖拽调整列宽，双击恢复默认"
          onMouseDown={onMouseDown}
          onDoubleClick={() => onReset?.()}
          onClick={(e) => e.stopPropagation()}
        />
      ) : null}
    </th>
  );
}

function GearIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path
        d="M8 10.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4Z"
        stroke="currentColor"
        strokeWidth="1.3"
      />
      <path
        d="M13.2 8c0-.4-.05-.8-.13-1.17l1.2-.9-1.3-2.25-1.42.55a5.3 5.3 0 0 0-2.02-1.17L9.3 1.5H6.7l-.23 1.56c-.75.25-1.44.65-2.02 1.17l-1.42-.55-1.3 2.25 1.2.9a5.4 5.4 0 0 0 0 2.34l-1.2.9 1.3 2.25 1.42-.55c.58.52 1.27.92 2.02 1.17l.23 1.56h2.6l.23-1.56a5.3 5.3 0 0 0 2.02-1.17l1.42.55 1.3-2.25-1.2-.9c.08-.37.13-.77.13-1.17Z"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function DataTable<T>(props: DataTableProps<T>) {
  const {
    columns,
    rows,
    rowKey,
    loading = false,
    error,
    onRetry,
    actions,
    search,
    onRefresh,
    settingsKey,
    selectedKeys,
    onSelectionChange,
    selectable,
    pagination,
    onRowClick,
    onRowDoubleClick,
    rowClassName,
    sort,
    onSortChange,
    emptyTitleKey,
    emptyHintKey,
    titleKey,
    title,
    subKey,
    sub,
    headActions,
    sticky = false,
    size = 'small',
    scrollX,
    virtual,
    showCount = true,
    flat = false,
  } = props;

  const label = useUiLabel();
  const [settingsOpen, setSettingsOpen] = useState(false);

  const colPrefs = useColumns(
    columns.map((c) => ({ key: c.key, hiddenByDefault: c.hiddenByDefault })),
    settingsKey,
  );

  const visibleColumns = useMemo(() => {
    const byKey = new Map(columns.map((c) => [c.key, c]));
    return colPrefs.order
      .filter((o) => !o.hidden)
      .map((o) => byKey.get(o.key))
      .filter((c): c is DataTableColumn<T> => !!c);
  }, [columns, colPrefs.order]);

  const virtualEnabled = typeof virtual === 'number' && virtual > 0;

  // —— 列宽拖拽 + 按需横向滚动（统一行为，调用方零配置）——
  // 每列有效宽度：拖拽值 > 出厂 width > 弹性（非虚拟模式下未声明 width 的列不占固定宽，
  // 在固定布局里吸收剩余空间——多数表因此正好铺满容器，不出滚动条）。
  // scroll.x = max(容器宽, 显式列宽合计 + 弹性列保底)——只有真放不下才出横向滚动条。
  const tableWrapRef = useRef<HTMLDivElement | null>(null);
  const [containerW, setContainerW] = useState(0);
  useEffect(() => {
    const el = tableWrapRef.current;
    if (!el) return;
    setContainerW(el.clientWidth);
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) setContainerW(entry.contentRect.width);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const [widths, setWidths] = useState<Record<string, number>>({});
  const resizeColumn = useCallback((key: string, w: number) => {
    setWidths((prev) => ({ ...prev, [key]: Math.max(MIN_COL_W, w) }));
  }, []);
  const resetColumn = useCallback((key: string) => {
    setWidths((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }, []);

  // 显式定宽合计 + 弹性列数（虚拟模式没有弹性列：缺 width 的列按 DEFAULT_COL_W 兜底，
  // antd 虚拟表格按列宽定位列，任一列缺宽会塌成 0）。
  const { fixedSum, flexCount } = useMemo(() => {
    let fixed = 0;
    let flex = 0;
    for (const c of visibleColumns) {
      const w = widths[c.key] ?? c.width ?? (virtualEnabled ? DEFAULT_COL_W : undefined);
      if (w == null) flex += 1;
      else fixed += w;
    }
    return { fixedSum: fixed, flexCount: flex };
  }, [visibleColumns, widths, virtualEnabled]);

  const scroll = useMemo(() => {
    // scrollX 语义收敛为「最小宽度下限」：仅在显式传数字时参与 max。
    const floor = typeof scrollX === 'number' ? scrollX : 0;
    const fixedAll = fixedSum + (onSelectionChange ? SELECTION_COL_W : 0);
    // 容器宽取整：ResizeObserver 给的是小数（如 710.453），antd 照设后
    // scrollWidth 向上取整会比 clientWidth 多出 ~3px 伪滚动，滚动条凭空出现。
    const target = Math.max(floor, Math.floor(containerW));
    let x: number;
    if (fixedAll + flexCount * FLEX_MIN_W <= target) {
      // 固定列 + 弹性列保底都放得下 → 铺满容器，弹性列吸收剩余空间，不出滚动条
      x = target;
    } else {
      // 固定列都放不下 → 出横向滚动条（弹性列按保底宽计入）
      x = Math.max(floor, fixedAll + flexCount * FLEX_MIN_W);
    }
    if (!x) return undefined;
    return virtualEnabled ? { x, y: virtual } : { x };
  }, [scrollX, fixedSum, flexCount, containerW, virtualEnabled, virtual, onSelectionChange]);

  useEffect(() => {
    if (!virtualEnabled) return;
    const missing = visibleColumns.filter((c) => !c.width).map((c) => c.key);
    if (missing.length) {
      console.warn(
        `[DataTable] 虚拟滚动要求每列都有 width，以下列缺失（已按 160 兜底）：${missing.join(', ')}`,
      );
    }
  }, [virtualEnabled, visibleColumns]);

  const antdColumns = useMemo<TableProps<T>['columns']>(() => {
    return visibleColumns.map((c) => {
      const w = widths[c.key] ?? c.width ?? (virtualEnabled ? DEFAULT_COL_W : undefined);
      return {
        key: c.key,
        dataIndex: c.key,
        title: label(c.titleKey, c.key),
        width: w,
        align: c.align,
        ellipsis: c.ellipsis,
        sorter: c.sortable && onSortChange ? true : undefined,
        sortOrder: c.sortable && sort?.key === c.key ? (sort.dir === 'asc' ? 'ascend' : 'descend') : null,
        // 列宽拖拽：把宽度与回调塞进表头单元格（ResizableHeaderCell 消费）
        onHeaderCell: () =>
          ({ width: w, onResize: (nw: number) => resizeColumn(c.key, nw), onReset: () => resetColumn(c.key) }) as ThHTMLAttributes<HTMLTableCellElement>,
        render: (_value: unknown, row: T) => {
          if (c.render) return c.render(row);
          const raw = (row as Record<string, unknown>)[c.key];
          const text = raw === null || raw === undefined || raw === '' ? '—' : String(raw);
          return c.mono ? <span className="cim-mono">{text}</span> : text;
        },
      };
    });
  }, [visibleColumns, label, sort, onSortChange, widths, resizeColumn, resetColumn]);

  const total = pagination?.total ?? rows.length;
  const selectedCount = selectedKeys?.length ?? 0;

  // 表格自带的标题头（替代外层 `<Panel titleKey>`，见 props 注释）
  const headTitle = titleKey ? label(titleKey, titleKey) : title;
  const headSub = subKey ? label(subKey, subKey) : sub;
  const hasHead = !!(headTitle || headSub);

  // 右侧工具（计数 / 刷新 / 列设置）：有标题头就并进头部，没有再单占一行工具栏
  const hasRightTools = !!onRefresh || (!!settingsKey && columns.length > 1);
  const hasToolbar = !!actions || !!search || (!hasHead && hasRightTools);

  const countNode =
    showCount && (hasHead || hasToolbar) ? (
      <span className="cim-toolbar-count">
        {onSelectionChange && selectedCount > 0
          ? `${label('ui.table.selected', '已选')} ${selectedCount} / ${
              label('ui.table.total', '共')
            } ${total}`
          : `${label('ui.table.total', '共')} ${total}`}
      </span>
    ) : null;

  const settingsNode =
    settingsKey && columns.length > 1 ? (
      <Popover
        open={settingsOpen}
        onOpenChange={setSettingsOpen}
        trigger="click"
        placement="bottomRight"
        content={
          <div className="cim-colset">
            <div className="cim-colset-head">
              <span>{label('ui.table.columns', '列设置')}</span>
              <Button type="link" size="small" onClick={colPrefs.reset} disabled={!colPrefs.isCustomized}>
                {label('ui.table.reset', '重置')}
              </Button>
            </div>
            {colPrefs.order.map((o, i) => {
              const col = columns.find((c) => c.key === o.key);
              if (!col) return null;
              return (
                <div className="cim-colset-row" key={o.key}>
                  <Checkbox
                    checked={!o.hidden}
                    onChange={(e) => colPrefs.setHidden(o.key, !e.target.checked)}
                  >
                    {label(col.titleKey, col.key)}
                  </Checkbox>
                  <span className="cim-colset-ops">
                    <Button
                      type="text"
                      size="small"
                      disabled={i === 0}
                      onClick={() => colPrefs.move(o.key, -1)}
                      aria-label="up"
                    >
                      ↑
                    </Button>
                    <Button
                      type="text"
                      size="small"
                      disabled={i === colPrefs.order.length - 1}
                      onClick={() => colPrefs.move(o.key, 1)}
                      aria-label="down"
                    >
                      ↓
                    </Button>
                  </span>
                </div>
              );
            })}
          </div>
        }
      >
        <Button size="small" title={label('ui.table.columns', '列设置')}>
          <GearIcon />
        </Button>
      </Popover>
    ) : null;

  const refreshNode = onRefresh ? (
    <Button size="small" onClick={onRefresh} title={label('ui.table.refresh', '刷新')}>
      {label('ui.table.refresh', '刷新')}
    </Button>
  ) : null;

  const searchNode = search ? (
    <Input
      allowClear
      value={search.value}
      onChange={(e) => search.onChange(e.target.value)}
      placeholder={
        search.placeholderKey
          ? label(search.placeholderKey, label('ui.table.search', '搜索'))
          : label('ui.table.search', '搜索')
      }
      style={{ width: search.width ?? 220 }}
    />
  ) : null;

  const emptyNode = (
    <EmptyState
      titleKey={emptyTitleKey}
      title={emptyTitleKey ? undefined : label('ui.table.empty', '暂无数据')}
      hintKey={emptyHintKey}
    />
  );

  // 工具栏与表体在**同一个** .panel 内：此前工具栏在面板外、直接坐在页面底色上，
  // 于是「搜索框 + 共 n / 刷新」是一条无框的悬浮行，下面才开始一张白卡；
  // 且工具栏 14px 内边距与单元格 12px 不一致，控件左缘比表头文字多缩进 2px。
  // 现在共用一张卡：工具栏在卡内、带底部分隔线，内边距与单元格对齐。
  return (
    <div className={'cim-datatable' + (flat ? ' is-flat' : '')}>
      <div className="panel">
        {hasHead ? (
          <header className="panel-head">
            <div className="panel-heading">
              {headTitle ? <h2 className="panel-title">{headTitle}</h2> : null}
              {headSub ? <p className="panel-sub">{headSub}</p> : null}
            </div>
            <div className="panel-actions">
              {headActions}
              {countNode}
              {refreshNode}
              {settingsNode}
            </div>
          </header>
        ) : null}

        {hasToolbar ? (
          <div className="cim-toolbar">
            <div className="cim-toolbar-left">
              {actions}
              {searchNode}
            </div>
            <div className="cim-toolbar-spacer" />
            {hasHead ? null : (
              <div className="cim-toolbar-right">
                {countNode}
                {refreshNode}
                {settingsNode}
              </div>
            )}
          </div>
        ) : null}

        {error ? (
          <div className="cim-datatable-error" role="alert">
            <span className="cim-datatable-error-msg">{error}</span>
            {onRetry ? (
              <Button size="small" danger onClick={onRetry}>
                {label('ui.table.retry', '重试')}
              </Button>
            ) : null}
          </div>
        ) : (
          <div ref={tableWrapRef} style={{ width: '100%' }}>
            <Table<T>
              size={size}
              columns={antdColumns}
              dataSource={rows}
              rowKey={(row) => rowKey(row)}
              loading={loading}
              sticky={sticky ? { offsetHeader: 0 } : undefined}
              scroll={scroll}
              virtual={virtualEnabled || undefined}
              components={{ header: { cell: ResizableHeaderCell } }}
              locale={{ emptyText: emptyNode }}
            rowClassName={(row) => (rowClassName ? rowClassName(row) : '')}
            rowSelection={
              onSelectionChange
                ? {
                    selectedRowKeys: selectedKeys ?? [],
                    onChange: (keys) => onSelectionChange(keys.map((k) => String(k))),
                    getCheckboxProps: selectable ? (row: T) => ({ disabled: !selectable(row) }) : undefined,
                    preserveSelectedRowKeys: true,
                  }
                : undefined
            }
            onRow={(row) => ({
              onClick: onRowClick ? () => onRowClick(row) : undefined,
              onDoubleClick: onRowDoubleClick ? () => onRowDoubleClick(row) : undefined,
            })}
            onChange={(_pg, _filters, sorter) => {
              if (!onSortChange) return;
              const s = Array.isArray(sorter) ? sorter[0] : sorter;
              const key = s?.columnKey ? String(s.columnKey) : undefined;
              if (!key) return;
              onSortChange(key, s?.order === 'ascend' ? 'asc' : s?.order === 'descend' ? 'desc' : null);
            }}
            pagination={
              pagination
                ? {
                    current: pagination.page,
                    pageSize: pagination.size,
                    total: pagination.total,
                    showSizeChanger: true,
                    pageSizeOptions: (pagination.sizeOptions ?? getPaginationDefaults().sizeOptions).map(String),
                    size: 'small',
                    showTotal: undefined,
                    onChange: (page, pageSize) => pagination.onChange(page, pageSize),
                  }
                : false
            }
            />
          </div>
        )}
      </div>
    </div>
  );
}
