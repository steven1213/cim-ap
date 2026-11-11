import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  CimModal,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  type FormOption,
} from '@cim/ui';
import Perms from '@/components/Perms';
import DeviceDetailDrawer from '@/components/DeviceDetailDrawer';
import RecipeDetailDrawer from '@/components/RecipeDetailDrawer';
import * as api from '@/lib/rmsApi';
import type {
  Device,
  DeviceArea,
  DeviceStatus,
  DeviceType,
  DeviceRecipeRow,
  RecipeVersion,
} from '@/types';

/** 模块级常量：`data ?? []` 每次渲染都会造新引用，进依赖数组后会触发无谓的 effect。 */
const NO_DEVICES: Device[] = [];
const NO_TYPES: DeviceType[] = [];
const NO_AREAS: DeviceArea[] = [];

const STATUS_TONE: Record<DeviceStatus, 'ok' | 'warn'> = {
  ENABLED: 'ok',
  DISABLED: 'warn',
};

const STATUS_OPTIONS: FormOption[] = [
  { value: 'ENABLED', labelKey: 'rms.device.status.ENABLED' },
  { value: 'DISABLED', labelKey: 'rms.device.status.DISABLED' },
];

interface AreaGroup {
  root: DeviceArea;
  items: { area: DeviceArea; depth: number }[];
}

/**
 * 把扁平区域压成「工厂为组头、其下区域缩进」的分组，供设备归属选择。
 * 工厂（parentId 为空或指向不存在节点）= 顶层组头，但不可作为设备父节点；
 * 设备只能选工厂下的区域，从而把「工厂父节点」在录入时显式呈现。
 */
function buildAreaGroups(areas: DeviceArea[]): AreaGroup[] {
  const map = new Map<string, DeviceArea>();
  areas.forEach((a) => map.set(a.id, a));
  const childrenOf = new Map<string | null, DeviceArea[]>();
  for (const a of areas) {
    const pid = a.parentId && a.parentId.trim() ? a.parentId : null;
    const resolved = pid && map.has(pid) ? pid : null; // 悬空父 → 视为顶层（与后端 areaTree 一致）
    if (!childrenOf.has(resolved)) childrenOf.set(resolved, []);
    childrenOf.get(resolved)!.push(a);
  }
  const roots = childrenOf.get(null) ?? [];
  return roots.map((root) => {
    const items: { area: DeviceArea; depth: number }[] = [];
    const walk = (pid: string, depth: number) => {
      for (const c of childrenOf.get(pid) ?? []) {
        items.push({ area: c, depth });
        walk(c.id, depth + 1);
      }
    };
    walk(root.id, 1);
    return { root, items };
  });
}

/**
 * 区域下拉选项：扁平化 + 层级缩进 + 工厂项禁用。
 *
 * <p>旧实现用原生 `&lt;optgroup&gt;` 表达工厂分组，但原生 select 不能搜索——设备一多
 * 就得靠肉眼滚。改成扁平选项后 `CimSelect` 的搜索框可以按区域名、编码、工厂名命中
 * （`hint` 参与搜索），工厂本身仍保留为禁用项，避免把设备直接挂到工厂根
 * （后端会拒绝）。</p>
 */
function areaOptions(areas: DeviceArea[]): FormOption[] {
  const out: FormOption[] = [];
  for (const g of buildAreaGroups(areas)) {
    out.push({ value: g.root.id, label: g.root.name, hint: g.root.code, disabled: true });
    for (const { area, depth } of g.items) {
      out.push({
        value: area.id,
        label: `${'　'.repeat(depth - 1)}${area.name}`,
        hint: `${g.root.name} / ${area.code}`,
      });
    }
  }
  return out;
}

/**
 * 设备台账页（Req 46/47/48）。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>取数交给 `useResource`（关键字防抖后作为 deps，竞态与取消由 hook 保证）；</li>
 *   <li>关键字只走服务端：旧实现「服务端按 keyword 过滤 + 前端再按**即时值**滤一遍」，
 *       两套值不同步时会闪一行空态，而且前端那段还按 `ip` 过滤——服务端根本不认 ip，
 *       命中行已经被丢掉，这段过滤从来没生效过（ip 搜索已补到后端查询里）；</li>
 *   <li>删除从 `confirm()` 换 `useConfirm`，失败有 toast（旧实现 `await` 之后既没有
 *       catch 也没有提示，接口 500 时页面毫无反应）；</li>
 *   <li>弹窗换 `CimModal` + `FormGrid`：必填校验按字段提示，不再是页脚一行拼起来的字符串。</li>
 * </ul>
 */
export default function DevicePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounced(keyword, 200);

  const [editing, setEditing] = useState<Device | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  // 机台详情抽屉（枢纽）+ 配方详情抽屉（互斥渲染：开配方时隐藏机台抽屉，关闭后回到机台）
  const [detail, setDetail] = useState<Device | null>(null);
  const [recipeDetail, setRecipeDetail] = useState<{ row: DeviceRecipeRow; versions: RecipeVersion[] } | null>(null);

  const { data, loading, error, refresh } = useResource(
    () => api.listDevices({ keyword: debouncedKeyword || undefined }),
    { deps: [debouncedKeyword] },
  );
  const rows = data ?? NO_DEVICES;

  const typesRes = useResource(() => api.listDeviceTypes());
  const areasRes = useResource(() => api.listDeviceAreas());
  const types = typesRes.data ?? NO_TYPES;
  const areas = areasRes.data ?? NO_AREAS;

  const typeName = useMemo(() => Object.fromEntries(types.map((x) => [x.id, x.name])), [types]);
  const areaName = useMemo(() => Object.fromEntries(areas.map((x) => [x.id, x.name])), [areas]);
  const areaById = useMemo(() => Object.fromEntries(areas.map((x) => [x.id, x])), [areas]);

  /** 设备所属区域的全路径（工厂 / 区域 …），强化列表里的工厂归属上下文。 */
  const deviceAreaPath = (id: string | null | undefined): string => {
    if (!id) return '';
    const parts: string[] = [];
    const seen = new Set<string>();
    let cur: DeviceArea | undefined = areaById[id];
    while (cur && !seen.has(cur.id)) {
      seen.add(cur.id);
      parts.unshift(cur.name);
      cur = cur.parentId ? areaById[cur.parentId] : undefined;
    }
    return parts.join(' / ');
  };

  const openRecipe = async (row: DeviceRecipeRow) => {
    if (!row.recipeId) return;
    try {
      const versions = await api.listVersions(row.recipeId);
      setRecipeDetail({ row, versions });
    } catch {
      // 版本拉取失败时仍打开抽屉（抽屉内会展示错误）
      setRecipeDetail({ row, versions: [] });
    }
  };

  const doDelete = async (x: Device) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteDevice(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const columns = useMemo<Array<DataTableColumn<Device>>>(
    () => [
      { key: 'code', titleKey: 'rms.device.code', mono: true, width: 140 },
      { key: 'name', titleKey: 'rms.device.name', width: 200 },
      {
        key: 'deviceTypeName',
        titleKey: 'rms.device.type',
        width: 140,
        render: (r) => r.deviceTypeName ?? (r.deviceTypeId ? typeName[r.deviceTypeId] ?? '?' : t('rms.common.dash')),
      },
      {
        key: 'areaId',
        titleKey: 'rms.device.area',
        width: 220,
        render: (r) => {
          const path = r.areaId ? deviceAreaPath(r.areaId) || r.areaName : '';
          return path ? path : <span className="muted">{t('rms.common.dash')}</span>;
        },
      },
      {
        key: 'ip',
        titleKey: 'rms.device.ip',
        width: 140,
        render: (r) => <span className="cim-mono">{r.ip ?? t('rms.common.dash')}</span>,
      },
      {
        key: 'status',
        titleKey: 'rms.common.status',
        width: 96,
        render: (r) => <CimTag labelKey={`rms.device.status.${r.status}`} tone={STATUS_TONE[r.status] ?? 'idle'} />,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 150,
        render: (r) => (
          <span className="cell-actions">
            <Perms code="rms:device:update">
              <button className="btn btn-ghost btn-sm" onClick={() => setEditing(r)}>
                {t('rms.common.edit')}
              </button>
            </Perms>
            <Perms code="rms:device:delete">
              <button className="btn btn-ghost btn-sm danger" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </button>
            </Perms>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, typeName, areaById],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.device.title"
        actions={
          <Perms code="rms:device:create">
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
              + {t('rms.device.createNew')}
            </button>
          </Perms>
        }
      />

      <DataTable<Device>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="rms.device"
        scrollX="max-content"
        search={{ value: keyword, onChange: setKeyword, placeholderKey: 'rms.common.search' }}
        onRowClick={(r) => setDetail(r)}
        rowClassName={() => 'clickable'}
        emptyTitleKey="rms.common.noData"
      />

      {/* 机台枢纽（W7 体验整合）：点开一台机 = 一处维护腔体 + 装载配方 + 绑定/解绑。
          配方详情打开时与机台详情互斥渲染（关闭配方详情回到机台详情）。 */}
      {detail && !recipeDetail && (
        <DeviceDetailDrawer
          device={detail}
          typeName={detail.deviceTypeId ? typeName[detail.deviceTypeId] : undefined}
          areaName={detail.areaId ? areaName[detail.areaId] : undefined}
          onClose={() => setDetail(null)}
          onChanged={refresh}
          onOpenRecipe={(row) => void openRecipe(row)}
          onEdit={() => setEditing(detail)}
        />
      )}

      {recipeDetail && (
        <RecipeDetailDrawer
          recipeId={recipeDetail.row.recipeId!}
          recipeCode={recipeDetail.row.code}
          recipeName={recipeDetail.row.name}
          versions={recipeDetail.versions}
          initialVersionId={recipeDetail.row.activeVersionId}
          onClose={() => setRecipeDetail(null)}
          onChanged={refresh}
        />
      )}

      {(showCreate || editing) && (
        <DeviceModal
          types={types}
          areas={areas}
          typeError={typesRes.error?.message}
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={async () => {
            const wasEdit = !!editing;
            setShowCreate(false);
            setEditing(null);
            toast.success(t(wasEdit ? 'rms.common.updated' : 'rms.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

interface DeviceForm {
  code: string;
  name: string;
  deviceTypeId: string;
  areaId: string;
  ip: string;
  status: DeviceStatus;
  remark: string;
}

function DeviceModal({
  types,
  areas,
  typeError,
  initial,
  onClose,
  onDone,
}: {
  types: DeviceType[];
  areas: DeviceArea[];
  /** 机型列表加载失败时的提示：不传就是「下拉是空的但不说为什么」。 */
  typeError?: string;
  initial: Device | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<DeviceForm>({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    deviceTypeId: initial?.deviceTypeId ?? '',
    areaId: initial?.areaId ?? '',
    ip: initial?.ip ?? '',
    status: initial?.status ?? 'ENABLED',
    remark: initial?.remark ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof DeviceForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const typeOptions = useMemo(
    () => types.map((x) => ({ value: x.id, label: x.name, hint: x.code })),
    [types],
  );
  const areaOpts = useMemo(() => areaOptions(areas), [areas]);

  const fields = useMemo<Array<FormField<DeviceForm>>>(
    () => [
      { kind: 'text', key: 'code', labelKey: 'rms.device.code', required: true, mono: true, disabled: !!initial },
      { kind: 'text', key: 'name', labelKey: 'rms.device.name', required: true },
      {
        kind: 'select',
        key: 'deviceTypeId',
        labelKey: 'rms.device.type',
        required: true,
        options: typeOptions,
        allowClear: false,
        hint: typeError,
      },
      {
        kind: 'select',
        key: 'areaId',
        labelKey: 'rms.device.area',
        options: areaOpts,
        placeholderKey: 'rms.device.areaEmpty',
        hintKey: 'rms.device.areaHint',
      },
      { kind: 'text', key: 'ip', labelKey: 'rms.device.ip', placeholder: '192.168.x.x' },
      {
        kind: 'select',
        key: 'status',
        labelKey: 'rms.common.status',
        options: STATUS_OPTIONS,
        allowClear: false,
      },
      { kind: 'text', key: 'remark', labelKey: 'rms.common.remark', span: 2 },
    ],
    [initial, typeOptions, areaOpts, typeError],
  );

  async function submit() {
    const next: Partial<Record<keyof DeviceForm, string>> = {};
    if (!form.code) next.code = t('rms.common.required');
    if (!form.name) next.name = t('rms.common.required');
    if (!form.deviceTypeId) next.deviceTypeId = t('rms.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      const payload = {
        code: form.code,
        name: form.name,
        deviceTypeId: form.deviceTypeId,
        areaId: form.areaId || null,
        ip: form.ip || null,
        status: form.status,
        remark: form.remark || null,
      };
      if (initial) await api.updateDevice(initial.id, payload);
      else await api.createDevice(payload);
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? 'rms.common.edit' : 'rms.device.createNew'}
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={620}
    >
      <FormGrid<DeviceForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      {err ? (
        <p className="cim-field-error" style={{ marginTop: 10 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}
