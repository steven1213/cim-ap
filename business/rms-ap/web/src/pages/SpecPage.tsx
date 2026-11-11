import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimInput,
  CimSelect,
  CimSwitch,
  CimTag,
  DataTable,
  EmptyState,
  Page,
  PageHead,
  Toolbar,
  useConfirm,
  useToast,
  type DataTableColumn,
  CimButton,
} from '@cim/ui';
import SpecEditModal from '@/components/SpecEditModal';
import * as api from '@/lib/rmsApi';
import { SPEC_MANAGE } from '@/lib/permCodes';
import type { ParamSpec, Recipe } from '@/types';

/** 模块级空数组：`data ?? []` 会每次渲染造新引用，进依赖数组后触发无谓的 effect。 */
const NO_RECIPES: Recipe[] = [];
const NO_SPECS: ParamSpec[] = [];

/**
 * 参数规格页（FR-S1~S4，Req 29/32/34/35/36）：四类规格 + 双轨容差 + 比对开关 + 模板应用/继承。
 *
 * 迁移要点：
 *   - 取数交给 `useResource`（切配方时的竞态与取消由 hook 保证；旧实现快速切配方会串数据）；
 *   - 「应用模板 / 继承规格」的结果从页顶一条常驻提示条改为 toast —— 提示条原来不会消失，
 *     下一次操作前一直挂在页面上，看起来像当前状态；
 *   - 比对开关从手写 `.led` 按钮改为 `CimSwitch`，并保留无权限时的纯文本回退。
 */
export default function SpecPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [recipeId, setRecipeId] = useState('');
  const [tplCode, setTplCode] = useState('');
  const [srcId, setSrcId] = useState('');
  const [editing, setEditing] = useState<ParamSpec | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const recipesRes = useResource(() => api.listRecipes());
  const recipes = recipesRes.data ?? NO_RECIPES;

  // 默认选中第一个配方：配方列表回来后才有值
  useEffect(() => {
    if (!recipeId && recipes.length) setRecipeId(recipes[0].id);
  }, [recipes, recipeId]);

  const { data, loading, error, refresh } = useResource(
    () => (recipeId ? api.listSpecs(recipeId) : Promise.resolve(NO_SPECS)),
    { deps: [recipeId], enabled: !!recipeId, keepPreviousData: true },
  );
  const rows = data ?? NO_SPECS;

  const recipeOptions = useMemo(
    () => recipes.map((r) => ({ value: r.id, label: `${r.code} · ${r.name}` })),
    [recipes],
  );

  const toggle = async (x: ParamSpec) => {
    try {
      await api.setSpecEnabled(x.id, !x.enabled);
      toast.success(t(x.enabled ? 'rms.common.disabled' : 'rms.common.enabled'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const doDelete = async (x: ParamSpec) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.spec.deleteConfirm',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteSpec(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const applyTpl = async () => {
    if (!tplCode) return;
    try {
      const n = await api.applySpecTemplate(recipeId, tplCode);
      toast.success(t('rms.spec.templateApplied', { n }));
      setTplCode('');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const inherit = async () => {
    if (!srcId) return;
    try {
      const n = await api.inheritSpec(recipeId, srcId);
      toast.success(t('rms.spec.inherited', { n }));
      setSrcId('');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const columns = useMemo<Array<DataTableColumn<ParamSpec>>>(
    () => [
      { key: 'paramKey', titleKey: 'rms.spec.paramKey', mono: true, width: 170 },
      {
        key: 'paramName',
        titleKey: 'rms.spec.paramName',
        width: 150,
        render: (r) => r.paramName ?? t('rms.common.dash'),
      },
      {
        key: 'specType',
        titleKey: 'rms.spec.specType',
        width: 100,
        render: (r) => <CimTag labelKey={`rms.spec.type.${r.specType}`} />,
      },
      {
        key: 'specValue',
        titleKey: 'rms.spec.value',
        mono: true,
        width: 160,
        render: (r) => specValue(r),
      },
      {
        key: 'tolerance',
        titleKey: 'rms.spec.type.TOLERANCE',
        mono: true,
        width: 150,
        render: (r) => tolText(r),
      },
      {
        key: 'unit',
        titleKey: 'rms.spec.unit',
        width: 80,
        render: (r) => r.unit ?? t('rms.common.dash'),
      },
      {
        key: 'enabled',
        titleKey: 'rms.spec.enabled',
        width: 96,
        align: 'center',
        render: (r) => (
          <Can perm={SPEC_MANAGE}
            fallback={<span className="muted">{r.enabled ? t('rms.common.yes') : t('rms.common.no')}</span>}
          >
            <CimSwitch size="small" checked={r.enabled} onChange={() => void toggle(r)} />
          </Can>
        ),
      },
      {
        key: 'templateCode',
        titleKey: 'rms.spec.template',
        width: 130,
        render: (r) => <span className="muted">{r.templateCode ?? t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={SPEC_MANAGE}>
              <CimButton size="small" onClick={() => setEditing(r)}>
                {t('rms.common.edit')}
              </CimButton>
              <CimButton size="small" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const toolbarInput = { width: 150 } as const;

  return (
    <Page>
      <PageHead
        titleKey="rms.spec.title"
        subtitleKey="rms.spec.sub"
        actions={
          <Can perm={SPEC_MANAGE}>
            <CimButton
              type="primary"
              size="small"
              disabled={!recipeId}
              onClick={() => setShowCreate(true)}
            >
              + {t('rms.spec.add')}
            </CimButton>
          </Can>
        }
      />

      <Toolbar
        left={
          <>
            <span className="muted">{t('rms.spec.pickRecipe')}</span>
            <CimSelect
              value={recipeId || null}
              onChange={(v) => setRecipeId(typeof v === 'string' ? v : '')}
              options={recipeOptions}
              allowClear={false}
              width={320}
            />
          </>
        }
        right={
          <Can perm={SPEC_MANAGE}>
            <div style={toolbarInput}>
              <CimInput
                value={tplCode}
                onChange={setTplCode}
                placeholderKey="rms.spec.applyTemplatePh"
                mono
              />
            </div>
            <CimButton size="small" onClick={() => void applyTpl()}>
              {t('rms.spec.applyTemplate')}
            </CimButton>
            <div style={toolbarInput}>
              <CimInput
                value={srcId}
                onChange={setSrcId}
                placeholderKey="rms.spec.inheritPh"
                mono
              />
            </div>
            <CimButton size="small" onClick={() => void inherit()}>
              {t('rms.spec.inherit')}
            </CimButton>
          </Can>
        }
      />

      {!recipeId ? (
        <EmptyState titleKey="rms.spec.noRecipe" />
      ) : (
        <DataTable<ParamSpec>
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          loading={loading}
          error={error?.message}
          onRetry={refresh}
          onRefresh={refresh}
          settingsKey="rms.spec"
          scrollX="max-content"
          emptyTitleKey="rms.spec.empty"
        />
      )}

      {(showCreate || editing) && (
        <SpecEditModal
          recipeId={recipeId}
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={() => {
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

function specValue(x: ParamSpec): string {
  switch (x.specType) {
    case 'RANGE':
      return `[${x.rangeMin ?? '-'}, ${x.rangeMax ?? '-'}]`;
    case 'LIST_IN':
      return `{${x.listValues ?? '-'}}`;
    default:
      return x.targetValue ?? '-';
  }
}

function tolText(x: ParamSpec): string {
  if (x.specType !== 'TOLERANCE') return '—';
  const p = x.tolerancePct != null ? `${x.tolerancePct}%` : '-';
  const a = x.toleranceAbs != null ? `±${x.toleranceAbs}` : '-';
  return `${p} / ${a}`;
}
