import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimChoice,
  CimDrawer,
  CimModal,
  CimSwitch,
  CimTag,
  CimUpload,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
  InlineAlert,
  KpiCard,
  CimField,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import type {
  BodyFormat,
  BodyTemplate,
  DeviceArea,
  DeviceType,
  PreviewResult,
  Recipe,
  RecipeVersion,
} from '@/types';

async function sha256Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/** UTF-8 安全的文本⇄Base64（btoa 仅支持 Latin1，多字节需先编码）。 */
function b64Utf8(s: string): string {
  return btoa(unescape(encodeURIComponent(s)));
}

function atobUtf8(b64: string): string {
  return decodeURIComponent(escape(atob(b64)));
}

const BODY_FORMAT_OPTIONS = [
  { value: 'TEXT', label: 'TEXT' },
  { value: 'JSON_STRUCTURED', label: 'JSON_STRUCTURED' },
  { value: 'BINARY', label: 'BINARY' },
];

const NO_RECIPES: Recipe[] = [];
const NO_VERSIONS: RecipeVersion[] = [];
const NO_TEMPLATES: BodyTemplate[] = [];

/** 把 FormGrid 回传的 `null`（清空下拉）归一成空串，避免 payload 里出现 null 与 '' 两套空值。 */
function denull<T extends object>(patch: Partial<T>): Partial<T> {
  const out: Record<string, unknown> = { ...patch };
  for (const k of Object.keys(out)) if (out[k] === null) out[k] = '';
  return out as Partial<T>;
}

/**
 * 配方库（Req 5~12、FR-A1~A5 + W8 导入导出 + W10 新建向导）。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>四处手写 `.modal-backdrop` 全部换成 `CimModal`：拿到 Esc / 焦点 / 滚动锁定；
 *       旧实现点遮罩即关闭，输到一半误点一次全丢。</li>
 *   <li>版本抽屉换成 `CimDrawer`，内部版本表用 `DataTable flat`（不画第二层框）。</li>
 *   <li>激活/删除从 `window.confirm` 改为 `useConfirm`，并把失败原因用 toast 说出来
 *       —— 旧实现 `await api.activateVersion()` 失败时 Promise 抛出无人接，界面毫无反应。</li>
 *   <li>导入改用 `CimUpload`（大小/类型在前端拦），策略选择用 `CimChoice`。</li>
 * </ul>
 */
export default function RecipeLibraryPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();

  const [keyword, setKeyword] = useState('');
  const kw = useDebounced(keyword, 250);
  const [goldenOnly, setGoldenOnly] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [showImport, setShowImport] = useState(false);
  const [busyExport, setBusyExport] = useState(false);
  const [active, setActive] = useState<Recipe | null>(null);

  const listRes = useResource(
    () => api.listRecipes({ ...(kw ? { keyword: kw } : {}), ...(goldenOnly ? { golden: true } : {}) }),
    { deps: [kw, goldenOnly], keepPreviousData: true },
  );
  const metaRes = useResource(async () => {
    const [types, areas] = await Promise.all([api.listDeviceTypes(), api.listDeviceAreas()]);
    return { types, areas };
  });
  const types = metaRes.data?.types ?? [];
  const areas = metaRes.data?.areas ?? [];

  const typeName = useMemo(() => new Map(types.map((x) => [x.id, x.name])), [types]);
  const areaName = useMemo(() => new Map(areas.map((x) => [x.id, x.name])), [areas]);

  const doExport = async () => {
    setBusyExport(true);
    try {
      const blob = await api.exportRecipes();
      const ts = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
      api.saveBlob(blob, `rms-recipes-${ts}.json`);
      toast.success(t('rms.recipe.exportDone'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusyExport(false);
    }
  };

  const doActivate = async (recipe: Recipe, v: RecipeVersion) => {
    const ok = await confirm({
      titleKey: 'rms.recipe.activate',
      contentKey: 'rms.common.confirm',
      okKey: 'rms.recipe.activate',
    });
    if (!ok) return;
    try {
      await api.activateVersion(recipe.id, v.id);
      toast.success(t('rms.common.updated'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setActive(null);
    listRes.refresh();
  };

  const doDelete = async (r: Recipe) => {
    const ok = await confirm({
      titleKey: 'rms.common.delete',
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteRecipe(r.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    listRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<Recipe>>>(
    () => [
      { key: 'code', titleKey: 'rms.recipe.code', mono: true, width: 170 },
      { key: 'name', titleKey: 'rms.recipe.name', width: 200 },
      {
        key: 'deviceTypeId',
        titleKey: 'rms.recipe.deviceType',
        width: 130,
        render: (r) => (r.deviceTypeId ? typeName.get(r.deviceTypeId) ?? '?' : t('rms.common.dash')),
      },
      {
        key: 'areaId',
        titleKey: 'rms.recipe.area',
        width: 120,
        render: (r) => (r.areaId ? areaName.get(r.areaId) ?? '?' : t('rms.common.dash')),
      },
      {
        key: 'golden',
        titleKey: 'rms.recipe.golden',
        width: 90,
        render: (r) =>
          r.golden ? (
            <CimTag size="small" tone="run">
              {t('rms.recipe.golden')}
            </CimTag>
          ) : (
            <span className="muted">{t('rms.common.dash')}</span>
          ),
      },
      {
        key: 'activeVersionNo',
        titleKey: 'rms.recipe.activeVersion',
        mono: true,
        width: 100,
        render: (r) => r.activeVersionNo ?? t('rms.common.dash'),
      },
      { key: 'versionCount', titleKey: 'rms.recipe.versionCount', width: 90, align: 'right' },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 150,
        render: (r) => (
          <span className="cell-actions">
            <CimButton size="small" onClick={() => setActive(r)}>
              {t('rms.recipe.version')}
            </CimButton>
            <Can perm="rms:recipe:delete">
              <CimButton size="small" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, typeName, areaName],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.recipe.title"
        actions={
          <>
            <Can perm="rms:recipe:create">
              <CimButton size="small" onClick={() => setShowImport(true)}>
                {t('rms.recipe.importBtn')}
              </CimButton>
            </Can>
            <Can perm="rms:body:export">
              <CimButton size="small" disabled={busyExport} onClick={() => void doExport()}>
                {busyExport ? t('rms.common.loading') : t('rms.recipe.exportAll')}
              </CimButton>
            </Can>
            <Can perm="rms:recipe:create">
              <CimButton type="primary" size="small" onClick={() => setShowCreate(true)}>
                + {t('rms.recipe.createNew')}
              </CimButton>
            </Can>
          </>
        }
      />

      <DataTable<Recipe>
        columns={columns}
        rows={listRes.data ?? NO_RECIPES}
        rowKey={(r) => r.id}
        loading={listRes.loading}
        error={listRes.error?.message}
        onRetry={listRes.refresh}
        onRefresh={listRes.refresh}
        settingsKey="rms.recipe"
        scrollX="max-content"
        emptyTitleKey="rms.common.noData"
        search={{
          value: keyword,
          onChange: setKeyword,
          placeholderKey: 'rms.common.search',
          width: 220,
        }}
        actions={
          <label className="check">
            <CimSwitch size="small" checked={goldenOnly} onChange={setGoldenOnly} />
            {t('rms.recipe.goldenOnly')}
          </label>
        }
        onRowDoubleClick={(r) => setActive(r)}
      />

      {showCreate && (
        <CreateModal
          types={types}
          areas={areas}
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            toast.success(t('rms.common.created'));
            listRes.refresh();
          }}
        />
      )}

      {showImport && (
        <ImportModal
          onClose={() => setShowImport(false)}
          onDone={() => listRes.refresh()}
        />
      )}

      <CimDrawer
        open={!!active}
        onClose={() => setActive(null)}
        title={active ? `${active.code} · ${active.name}` : ''}
        width={780}
      >
        {active ? (
          <VersionsPanel
            recipe={active}
            onActivate={(v) => void doActivate(active, v)}
            onSavedAs={() => {
              setActive(null);
              listRes.refresh();
            }}
          />
        ) : null}
      </CimDrawer>
    </Page>
  );
}

/** 版本列表（抽屉内容）：新版本 / 另存为 / 激活都在这里。 */
function VersionsPanel({
  recipe,
  onActivate,
  onSavedAs,
}: {
  recipe: Recipe;
  onActivate: (v: RecipeVersion) => void;
  onSavedAs: () => void;
}) {
  const { t } = useTranslation();
  const [showNewVer, setShowNewVer] = useState(false);
  const [showSaveAs, setShowSaveAs] = useState(false);

  const versionsRes = useResource(() => api.listVersions(recipe.id), { deps: [recipe.id] });

  const columns = useMemo<Array<DataTableColumn<RecipeVersion>>>(
    () => [
      { key: 'versionNo', titleKey: 'rms.recipe.version', mono: true, width: 90, render: (v) => `v${v.versionNo}` },
      { key: 'bodyFormat', titleKey: 'rms.recipe.bodyFormat', width: 150 },
      {
        key: 'status',
        titleKey: 'rms.recipe.status',
        width: 120,
        render: (v) => (
          <CimTag
            size="small"
            tone={v.status === 'ACTIVE' ? 'ok' : v.status === 'DRAFT' ? 'run' : 'warn'}
          >
            {t('rms.recipe.status.' + v.status, { defaultValue: v.status })}
          </CimTag>
        ),
      },
      {
        key: 'changeSummary',
        titleKey: 'rms.common.remark',
        ellipsis: true,
        render: (v) => <span className="muted">{v.changeSummary ?? t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 100,
        render: (v) => (
          <Can perm="rms:recipe:activate">
            <CimButton
              size="small"
              disabled={v.status !== 'DRAFT'}
              title={v.status !== 'DRAFT' ? t('rms.recipe.activateBlocked') : ''}
              onClick={() => onActivate(v)}
            >
              {t('rms.recipe.activate')}
            </CimButton>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div className="cell-actions">
        <Can perm="rms:recipe:version">
          <CimButton type="primary" size="small" onClick={() => setShowNewVer(true)}>
            + {t('rms.recipe.newVersion')}
          </CimButton>
        </Can>
        <Can perm="rms:recipe:create">
          <CimButton size="small" onClick={() => setShowSaveAs(true)}>
            {t('rms.recipe.saveAs')}
          </CimButton>
        </Can>
      </div>

      <DataTable<RecipeVersion>
        flat
        columns={columns}
        rows={versionsRes.data ?? NO_VERSIONS}
        rowKey={(v) => v.id}
        loading={versionsRes.loading}
        error={versionsRes.error?.message}
        onRetry={versionsRes.refresh}
        scrollX="max-content"
        emptyTitleKey="rms.common.noData"
      />

      {showNewVer && (
        <NewVersionModal
          recipeId={recipe.id}
          onClose={() => setShowNewVer(false)}
          onDone={() => {
            setShowNewVer(false);
            versionsRes.refresh();
          }}
        />
      )}
      {showSaveAs && (
        <SaveAsModal
          recipeId={recipe.id}
          defaultName={recipe.name + ' (copy)'}
          onClose={() => setShowSaveAs(false)}
          onDone={() => {
            setShowSaveAs(false);
            onSavedAs();
          }}
        />
      )}
    </div>
  );
}

// ----------------------------------------------------------------- 新建配方

interface CreateForm {
  code: string;
  name: string;
  deviceTypeId: string;
  areaId: string;
  bodyFormat: BodyFormat;
  templateId: string;
  body: string;
  remark: string;
  golden: boolean;
}

const EMPTY_CREATE: CreateForm = {
  code: '',
  name: '',
  deviceTypeId: '',
  areaId: '',
  bodyFormat: 'TEXT',
  templateId: '',
  body: '',
  remark: '',
  golden: false,
};

/** 新建配方（W10 向导）：选 Body 模板 → 自动填骨架 → 保存前预检 → 提交。 */
function CreateModal({
  types,
  areas,
  onClose,
  onCreated,
}: {
  types: DeviceType[];
  areas: DeviceArea[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<CreateForm>(EMPTY_CREATE);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [previewing, setPreviewing] = useState(false);

  const templatesRes = useResource(() => api.listBodyTemplates());
  const templates = templatesRes.data ?? NO_TEMPLATES;
  const matchedTemplates = useMemo(
    () => templates.filter((x) => !x.scopeDeviceTypeId || x.scopeDeviceTypeId === form.deviceTypeId),
    [templates, form.deviceTypeId],
  );

  const applyTemplate = async (id: string) => {
    if (!id) return;
    try {
      const sk = await api.templateSkeleton(id);
      // 骨架 bodyBase64 → 明文填入（与 create 契约一致：提交时再算 hash）
      setForm((prev) => ({ ...prev, bodyFormat: sk.bodyFormat as BodyFormat, body: atobUtf8(sk.bodyBase64) }));
      setPreview(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const fields = useMemo<Array<FormField<CreateForm>>>(
    () => [
      { key: 'code', labelKey: 'rms.recipe.code', kind: 'text', required: true, mono: true },
      { key: 'name', labelKey: 'rms.recipe.name', kind: 'text', required: true },
      {
        key: 'deviceTypeId',
        labelKey: 'rms.recipe.deviceType',
        kind: 'select',
        options: types.map((x) => ({ value: x.id, label: x.name, hint: x.code })),
      },
      {
        key: 'areaId',
        labelKey: 'rms.recipe.area',
        kind: 'select',
        options: areas.map((x) => ({ value: x.id, label: x.name, hint: x.code })),
      },
      { key: 'bodyFormat', labelKey: 'rms.recipe.bodyFormat', kind: 'select', options: BODY_FORMAT_OPTIONS, allowClear: false },
      {
        key: 'templateId',
        labelKey: 'rms.btpl.applyTemplate',
        kind: 'select',
        options: matchedTemplates.map((tp) => ({
          value: tp.id,
          label: `${tp.code} · ${tp.name}${tp.enforced ? '（' + t('rms.btpl.enforcedYes') + '）' : ''}`,
        })),
      },
      { key: 'body', labelKey: 'rms.recipe.body', kind: 'textarea', rows: 8, required: true, span: 2 },
      { key: 'remark', labelKey: 'rms.common.remark', kind: 'text', span: 2 },
      { key: 'golden', labelKey: 'rms.recipe.golden', kind: 'switch' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [types, areas, matchedTemplates, t],
  );

  const onChange = (p: Partial<CreateForm>) => {
    const patch = denull(p);
    setForm((prev) => ({ ...prev, ...patch }));
    if (patch.body !== undefined) setPreview(null);
    if (patch.templateId) void applyTemplate(patch.templateId as string);
  };

  const runPreview = async () => {
    if (!form.body.trim()) return;
    setPreviewing(true);
    try {
      setPreview(
        await api.previewValidate({
          deviceTypeId: form.deviceTypeId || null,
          bodyFormat: form.bodyFormat,
          bodyBase64: b64Utf8(form.body),
        }),
      );
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setPreviewing(false);
    }
  };

  const submit = async () => {
    if (!form.code.trim() || !form.name.trim() || !form.body) {
      toast.warning(t('rms.recipe.requiredHint'));
      return;
    }
    setBusy(true);
    try {
      const expectedBodyHash = await sha256Hex(form.body);
      await api.createRecipe({
        code: form.code.trim(),
        name: form.name.trim(),
        deviceTypeId: form.deviceTypeId || null,
        areaId: form.areaId || null,
        golden: form.golden,
        remark: form.remark || null,
        bodyFormat: form.bodyFormat,
        body: form.body,
        expectedBodyHash,
      });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onCreated();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.recipe.createNew"
      width={820}
      onOk={submit}
      okLoading={busy}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      <FormGrid<CreateForm> fields={fields} value={form} onChange={onChange} columns={2} disabled={busy} />

      <div className="cell-actions" style={{ marginTop: 10 }}>
        <CimButton size="small" disabled={previewing || !form.body.trim()} onClick={() => void runPreview()}>
          {previewing ? t('rms.common.loading') : t('rms.recipe.previewValidate')}
        </CimButton>
      </div>

      {preview ? (
        <InlineAlert tone={(preview.ok ? 'ok' : 'err')} style={{ marginTop: 10 }}>
          {preview.ok ? (
            <span>{t('rms.recipe.previewOk')}</span>
          ) : (
            <span>
              {preview.errors.join('；')}
              {preview.templateViolations.length > 0 ? (
                <>
                  {' · '}
                  {t('rms.recipe.previewTplMissing')}:{' '}
                  {preview.templateViolations.map((v) => v.paramKey).join(', ')}
                </>
              ) : null}
            </span>
          )}
        </InlineAlert>
      ) : null}
    </CimModal>
  );
}

// ----------------------------------------------------------------- 新版本

interface NewVersionForm {
  bodyFormat: BodyFormat;
  body: string;
  remark: string;
}

function NewVersionModal({
  recipeId,
  onClose,
  onDone,
}: {
  recipeId: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<NewVersionForm>({ bodyFormat: 'TEXT', body: '', remark: '' });
  const [busy, setBusy] = useState(false);

  const fields: Array<FormField<NewVersionForm>> = [
    { key: 'bodyFormat', labelKey: 'rms.recipe.bodyFormat', kind: 'select', options: BODY_FORMAT_OPTIONS, allowClear: false },
    { key: 'body', labelKey: 'rms.recipe.body', kind: 'textarea', rows: 10, required: true, span: 2 },
    { key: 'remark', labelKey: 'rms.common.remark', kind: 'text', span: 2 },
  ];

  const submit = async () => {
    if (!form.body) {
      toast.warning(t('rms.recipe.requiredHint'));
      return;
    }
    setBusy(true);
    try {
      const expectedBodyHash = await sha256Hex(form.body);
      await api.newVersion(recipeId, {
        bodyFormat: form.bodyFormat,
        body: form.body,
        expectedBodyHash,
        remark: form.remark || null,
      });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onDone();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.recipe.newVersion"
      width={820}
      onOk={submit}
      okLoading={busy}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      <FormGrid<NewVersionForm>
        fields={fields}
        value={form}
        onChange={(p) => setForm((prev) => ({ ...prev, ...denull(p) }))}
        columns={2}
        disabled={busy}
      />
    </CimModal>
  );
}

// ----------------------------------------------------------------- 另存为

interface SaveAsForm {
  code: string;
  name: string;
}

function SaveAsModal({
  recipeId,
  defaultName,
  onClose,
  onDone,
}: {
  recipeId: string;
  defaultName: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<SaveAsForm>({ code: '', name: defaultName });
  const [busy, setBusy] = useState(false);

  const fields: Array<FormField<SaveAsForm>> = [
    { key: 'code', labelKey: 'rms.recipe.code', kind: 'text', required: true, mono: true },
    { key: 'name', labelKey: 'rms.recipe.name', kind: 'text', required: true },
  ];

  const submit = async () => {
    if (!form.code.trim() || !form.name.trim()) {
      toast.warning(t('rms.recipe.requiredHint'));
      return;
    }
    setBusy(true);
    try {
      // 另存为沿用源版本 body，hash 由后端按源内容校验
      const expectedBodyHash = await sha256Hex('');
      await api.saveAs(recipeId, {
        code: form.code.trim(),
        name: form.name.trim(),
        expectedBodyHash,
        remark: null,
      });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onDone();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.recipe.saveAs"
      width={560}
      onOk={submit}
      okLoading={busy}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      <FormGrid<SaveAsForm>
        fields={fields}
        value={form}
        onChange={(p) => setForm((prev) => ({ ...prev, ...denull(p) }))}
        columns={2}
        disabled={busy}
      />
    </CimModal>
  );
}

// ==================== W8：导入（文件级备份恢复） ====================

const STRATEGY_OPTIONS = [
  { value: 'SKIP', labelKey: 'rms.recipe.strategySkip' },
  { value: 'NEW_VERSION', labelKey: 'rms.recipe.strategyNewVer' },
];

function ImportModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [content, setContent] = useState('');
  const [fileName, setFileName] = useState('');
  const [strategy, setStrategy] = useState<'SKIP' | 'NEW_VERSION'>('SKIP');
  const [busy, setBusy] = useState(false);
  const [report, setReport] = useState<import('@/types').RecipeImportReport | null>(null);

  const pickFile = (files: File[]) => {
    const f = files[0];
    if (!f) {
      setContent('');
      setFileName('');
      return;
    }
    setFileName(f.name);
    const reader = new FileReader();
    reader.onload = () => setContent(String(reader.result || ''));
    reader.onerror = () => toast.error(t('rms.recipe.importReadFail'));
    reader.readAsText(f, 'utf-8');
  };

  const run = async () => {
    setBusy(true);
    try {
      const r = await api.importRecipes(content, strategy);
      setReport(r);
      onDone();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.recipe.importTitle"
      width={720}
      footer={
        report ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <CimButton type="primary" onClick={onClose}>
              {t('rms.common.close')}
            </CimButton>
          </div>
        ) : undefined
      }
      onOk={report ? undefined : run}
      okKey="rms.recipe.importRun"
      okDisabled={busy || !content}
      okLoading={busy}
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      {!report ? (
        <>
          <CimField label={t('rms.recipe.importFile')} required>
            <CimUpload
              files={[]}
              onChange={pickFile}
              accept=".json,application/json"
              maxCount={1}
              multiple={false}
              maxSizeMb={32}
              buttonKey="rms.recipe.importFile"
            />
            {fileName ? <div className="muted" style={{ marginTop: 4 }}>{fileName}</div> : null}
          </CimField>
          <CimField label={t('rms.recipe.importStrategy')} style={{ marginTop: 12 }}>
            <CimChoice
              mode="radio"
              value={strategy}
              onChange={(v) => setStrategy(v as 'SKIP' | 'NEW_VERSION')}
              options={STRATEGY_OPTIONS}
              direction="vertical"
            />
          </CimField>
          <div className="muted" style={{ marginTop: 10 }}>
            {t('rms.recipe.importHint')}
          </div>
        </>
      ) : (
        <div>
          <div className="kpi-grid">
            <KpiCard label={t('rms.recipe.importCreated')} value={report.created} />
            <KpiCard tone="info" label={t('rms.recipe.importUpdated')} value={report.updated} />
            <KpiCard tone="warn" label={t('rms.recipe.importSkipped')} value={report.skipped} />
          </div>
          {report.errors.length > 0 ? (
            <Panel titleKey="rms.recipe.importErrors" style={{ marginTop: 12 }}>
              <ul className="muted" style={{ margin: 0, paddingLeft: 18 }}>
                {report.errors.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            </Panel>
          ) : null}
        </div>
      )}
    </CimModal>
  );
}
