// 多语言（共享）：语言目录与译文维护。
//
// 布局（主从式）：左列语言目录（点行即过滤右侧译文），右列译文表（module/keyword
// 过滤 + 客户端分页）+ 缺失键 + 未译条目两个面板。与菜单/权限/角色三页同一
// master-detail 心智模型。
//
// 数据面在宿主自己的 i18n 管理端点（IAM: /admin/i18n/**；业务系统: /{ap}/admin/i18n/**，
// 各 ap 落自己的 I18nAdminController 委托平台 I18nService），经 basePath 注入——
// 管理属 ap 内部权限（见 IamConsoleCatalog/identity-directory 既有决策），码走 sys:i18n:*。
//
// 保存后即时生效：写库即缓存失效，前端下次拉取即拿到新译文。「刷新译文」按钮
// 经 reloadTranslations 重拉当前语言整包（宿主传自己的 changeLanguage force 包装）。
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  CimButton,
  CimModal,
  CimSelect,
  CimSplitPane,
  CimTag,
  DataTable,
  DataTableColumn,
  FormField,
  FormGrid,
  IconChevron,
  IconEdit,
  IconPlus,
  IconRefresh,
  IconSave,
  IconTrash,
  InlineAlert,
  Page,
  Panel,
  useConfirm,
  useToast,
} from '@cim/ui';
import { useAdminHasAll, useAdminHttp } from '../context';
import { I18N_PERMS, type AdminPermOverrides } from '../perms';
import type { LocaleCoverageDto, SysI18nDto, SysLocaleDto } from '../types';

/** 译文表客户端分页大小（服务端已按 locale/module/keyword 过滤，前端只做翻页）。 */
const PAGE_SIZE = 50;

export interface I18nAdminPageProps {
  perms?: AdminPermOverrides;
  /** 管理端点前缀（缺省 /admin/i18n；业务系统传如 /mds/admin/i18n）。 */
  basePath?: string;
  /** 「刷新译文」动作（重拉当前语言整包）；不传则不渲染该按钮。 */
  reloadTranslations?: () => void;
}

/** 多语言页（六页共享）。 */
export default function I18nAdminPage({ perms, basePath = '/admin/i18n', reloadTranslations }: I18nAdminPageProps) {
  const { t, i18n } = useTranslation();
  const http = useAdminHttp();
  const toast = useToast();
  const confirm = useConfirm();
  const p = { ...I18N_PERMS, ...perms };
  const hasAll = useAdminHasAll();
  const canRead = hasAll(p.read);
  const canRemove = hasAll(p.remove);
  const canSave = hasAll(p.save);
  const canLocale = hasAll(p.locale);

  const [localeDraft, setLocaleDraft] = useState<LocaleDraft | null>(null);
  const [msgDraft, setMsgDraft] = useState<MsgDraft | null>(null);
  const [localeErr, setLocaleErr] = useState<{ code?: string }>({});
  const [msgErr, setMsgErr] = useState<{ code?: string }>({});

  const [filterLocale, setFilterLocale] = useState('');
  const [filterModule, setFilterModule] = useState('');
  const [keyword, setKeyword] = useState('');
  /** 输入 → 防抖 → 进 deps：取数与渲染用同一个滞后值，不存在「后端按 A 过滤、前端按 B 再滤」。 */
  const appliedKw = useDebounced(keyword, 350);
  const [page, setPage] = useState(1);
  const [missOpen, setMissOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const localesRes = useResource<SysLocaleDto[]>(
    async ({ signal }) => (await http.get<SysLocaleDto[]>(`${basePath}/locales`, undefined, { signal })) ?? [],
    { enabled: canRead },
  );
  const messagesRes = useResource<SysI18nDto[]>(
    async ({ signal }) =>
      (await http.get<SysI18nDto[]>(
        `${basePath}/messages`,
        {
          localeCode: filterLocale || undefined,
          module: filterModule || undefined,
          keyword: appliedKw || undefined,
        },
        { signal },
      )) ?? [],
    { deps: [filterLocale, filterModule, appliedKw], enabled: canRead, keepPreviousData: true },
  );
  const missingRes = useResource<Record<string, string>>(
    async ({ signal }) => (await http.get<Record<string, string>>(`${basePath}/missing`, undefined, { signal })) ?? {},
    { enabled: canRead },
  );
  const coverageRes = useResource<LocaleCoverageDto[]>(
    async ({ signal }) => (await http.get<LocaleCoverageDto[]>(`${basePath}/coverage`, undefined, { signal })) ?? [],
    { enabled: canRead },
  );

  const locales = useMemo(() => localesRes.data ?? [], [localesRes.data]);
  const messages = useMemo(() => messagesRes.data ?? [], [messagesRes.data]);
  const missing = useMemo(() => missingRes.data ?? {}, [missingRes.data]);
  const coverage = useMemo(() => coverageRes.data ?? [], [coverageRes.data]);

  /** 语言代码 → 覆盖率，左栏表格按行取。 */
  const coverageByLocale = useMemo(
    () => new Map(coverage.map((c) => [c.locale, c])),
    [coverage],
  );

  /**
   * 「未译条目」面板跟踪的语言：优先左栏选中的那个；没选就挑第一个真有缺口的语言。
   * 不挑 [0]——列表首项通常是默认语言，恒 100%，面板会永远空着，看起来像坏了。
   */
  const gapLocale = useMemo(() => {
    if (filterLocale) return coverage.find((c) => c.locale === filterLocale);
    return coverage.find((c) => !c.base && c.missing.length > 0) ?? coverage.find((c) => !c.base);
  }, [coverage, filterLocale]);

  /** 过滤条件变化 → 回第一页（否则会停在一个不存在的页码上）。 */
  useEffect(() => setPage(1), [filterLocale, filterModule, appliedKw]);

  /**
   * 模块候选只增不减：直接按当前 messages 推导会让下拉在选中某模块后
   * 只剩那一个选项（过滤后的结果里当然没有别的模块）。
   */
  const modulesRef = useRef<Set<string>>(new Set());
  const [modules, setModules] = useState<string[]>([]);
  useEffect(() => {
    let added = false;
    for (const m of messages) {
      if (m.module && !modulesRef.current.has(m.module)) {
        modulesRef.current.add(m.module);
        added = true;
      }
    }
    if (added) setModules(Array.from(modulesRef.current).sort());
  }, [messages]);

  const missingKeys = useMemo(() => Object.keys(missing).sort(), [missing]);

  const pages = Math.max(1, Math.ceil(messages.length / PAGE_SIZE));
  const safePage = Math.min(page, pages);
  const paged = useMemo(
    () => messages.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE),
    [messages, safePage],
  );

  function reloadAll() {
    localesRes.refresh();
    messagesRes.refresh();
    missingRes.refresh();
    coverageRes.refresh();
  }

  async function onSaveLocale() {
    if (!localeDraft) return;
    if (!localeDraft.code.trim()) {
      setLocaleErr({ code: t('admin.common.required') });
      return;
    }
    setLocaleErr({});
    setBusy(true);
    try {
      await http.post<SysLocaleDto>(`${basePath}/locales`, {
        id: localeDraft.id || undefined,
        code: localeDraft.code.trim(),
        name: localeDraft.name.trim() || null,
        isDefault: localeDraft.isDefault,
        sortNo: Number(localeDraft.sortNo) || 0,
        status: localeDraft.status,
      });
      toast.success(t('admin.common.saved'));
      setLocaleDraft(null);
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteLocale(l: SysLocaleDto) {
    const ok = await confirm({
      title: t('admin.common.delete'),
      content: `${t('admin.common.confirmDelete')}\n${l.code}`,
      danger: true,
      okKey: 'admin.common.delete',
      cancelKey: 'admin.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await http.del<void>(`${basePath}/locales/${encodeURIComponent(l.id)}`);
      toast.success(t('admin.common.deleted'));
      if (filterLocale === l.code) setFilterLocale('');
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onSaveMessage() {
    if (!msgDraft) return;
    if (!msgDraft.code.trim() || !msgDraft.localeCode.trim()) {
      setMsgErr({ code: t('admin.common.required') });
      return;
    }
    setMsgErr({});
    setBusy(true);
    try {
      await http.post<SysI18nDto>(`${basePath}/messages`, {
        id: msgDraft.id || undefined,
        localeCode: msgDraft.localeCode.trim(),
        code: msgDraft.code.trim(),
        content: msgDraft.content,
        module: msgDraft.module.trim() || undefined,
        scope: msgDraft.scope,
      });
      toast.success(t('admin.common.saved'));
      setMsgDraft(null);
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteMessage(m: SysI18nDto) {
    const ok = await confirm({
      title: t('admin.common.delete'),
      content: `${t('admin.common.confirmDelete')}\n${m.localeCode} · ${m.code}`,
      danger: true,
      okKey: 'admin.common.delete',
      cancelKey: 'admin.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await http.del<void>(`${basePath}/messages/${encodeURIComponent(m.id)}`);
      toast.success(t('admin.common.deleted'));
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onClearMissing() {
    setBusy(true);
    try {
      await http.del<void>(`${basePath}/missing`);
      toast.success(t('admin.common.deleted'));
      missingRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  const localeColumns = useMemo<Array<DataTableColumn<SysLocaleDto>>>(
    () => [
      { key: 'code', titleKey: 'admin.field.locale', mono: true, width: 108 },
      {
        key: 'name',
        titleKey: 'admin.field.name',
        ellipsis: true,
        render: (l) => (
          <span className="cell-inline">
            <span className="dim">{l.name ?? '—'}</span>
            {l.isDefault && (
              <CimTag size="small" tone="run">
                {t('admin.i18n.default')}
              </CimTag>
            )}
            {l.status === 'DISABLED' && (
              <CimTag size="small" tone="err">
                {t('admin.common.disabled')}
              </CimTag>
            )}
          </span>
        ),
      },
      {
        key: 'coverage',
        titleKey: 'admin.field.coverage',
        width: 84,
        render: (l) => {
          const c = coverageByLocale.get(l.code);
          if (!c || c.total === 0) return <span className="dim">—</span>;
          const pct = Math.round((c.translated * 100) / c.total);
          return (
            <span title={`${c.translated} / ${c.total}`}>
              <CimTag size="small" tone={pct >= 100 ? 'ok' : pct >= 80 ? 'warn' : 'err'}>
                {pct}%
              </CimTag>
            </span>
          );
        },
      },
      {
        key: 'actions',
        titleKey: 'admin.common.actions',
        width: 76,
        render: (l) => (
          <span className="cell-actions" onClick={(e) => e.stopPropagation()}>
            {canLocale && (
              <CimButton
                bare
                className="icon-btn"
                title={t('admin.common.edit')}
                onClick={() =>
                  setLocaleDraft({
                    id: l.id,
                    code: l.code,
                    name: l.name ?? '',
                    isDefault: l.isDefault,
                    sortNo: l.sortNo,
                    status: l.status,
                  })
                }
              >
                <IconEdit width={13} height={13} />
              </CimButton>
            )}
            {!l.isDefault && canLocale && (
              <CimButton
                bare
                className="icon-btn danger"
                title={t('admin.common.delete')}
                onClick={() => onDeleteLocale(l)}
              >
                <IconTrash width={13} height={13} />
              </CimButton>
            )}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, canLocale, filterLocale, coverageByLocale],
  );

  const messageColumns = useMemo<Array<DataTableColumn<SysI18nDto>>>(
    () => [
      { key: 'localeCode', titleKey: 'admin.field.locale', mono: true, width: 96 },
      { key: 'code', titleKey: 'admin.field.code', mono: true, width: 240, ellipsis: true },
      {
        key: 'content',
        titleKey: 'admin.field.content',
        ellipsis: true,
        render: (m) =>
          m.content ? m.content : (
            <CimTag size="small" tone="err">
              {t('admin.common.none')}
            </CimTag>
          ),
      },
      {
        key: 'module',
        titleKey: 'admin.field.module',
        width: 96,
        render: (m) => <span className="cim-mono dim">{m.module ?? '—'}</span>,
      },
      {
        key: 'scope',
        titleKey: 'admin.admin.i18n.scope',
        width: 92,
        render: (m) => (
          <CimTag size="small" tone={m.scope === 'SYSTEM' ? 'run' : 'idle'}>
            {m.scope}
          </CimTag>
        ),
      },
      {
        key: 'actions',
        titleKey: 'admin.common.actions',
        width: 108,
        render: (m) => (
          <span className="cell-actions">
            {canSave && (
              <CimButton
                size="small"
                onClick={() =>
                  setMsgDraft({
                    id: m.id,
                    localeCode: m.localeCode,
                    code: m.code,
                    content: m.content ?? '',
                    module: m.module ?? '',
                    scope: m.scope,
                  })
                }
              >
                {t('admin.common.edit')}
              </CimButton>
            )}
            {canRemove && (
              <CimButton bare className="icon-btn danger" title={t('admin.common.delete')} onClick={() => onDeleteMessage(m)}>
                <IconTrash width={13} height={13} />
              </CimButton>
            )}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, canSave, canRemove],
  );

  const localeFields = useMemo<Array<FormField<LocaleDraft>>>(
    () =>
      localeDraft
        ? [
            {
              key: 'code',
              labelKey: 'admin.field.code',
              kind: 'text',
              mono: true,
              placeholder: 'zh-TW',
              required: true,
              disabled: !!localeDraft.id,
              hint: t('admin.admin.i18n.localeCodeHint'),
            },
            { key: 'name', labelKey: 'admin.field.name', kind: 'text', placeholder: '繁體中文' },
            { key: 'sortNo', labelKey: 'admin.field.sortNo', kind: 'number', min: 0 },
            {
              key: 'isDefault',
              labelKey: 'admin.i18n.default',
              kind: 'switch',
              hint: t('admin.admin.i18n.defaultLocked'),
            },
            {
              key: 'status',
              labelKey: 'admin.field.status',
              kind: 'select',
              allowClear: false,
              options: [
                { value: 'ENABLED', labelKey: 'admin.common.enabled' },
                { value: 'DISABLED', labelKey: 'admin.common.disabled' },
              ],
            },
          ]
        : [],
    [localeDraft, t],
  );

  const msgFields = useMemo<Array<FormField<MsgDraft>>>(
    () =>
      msgDraft
        ? [
            {
              key: 'localeCode',
              labelKey: 'admin.field.locale',
              kind: 'select',
              allowClear: false,
              options: locales.map((l) => ({ value: l.code, label: l.code, hint: l.name ?? undefined })),
              required: true,
            },
            {
              key: 'code',
              labelKey: 'admin.field.code',
              kind: 'text',
              mono: true,
              placeholder: 'admin.menu.menus',
              required: true,
            },
            { key: 'module', labelKey: 'admin.field.module', kind: 'text', mono: true },
            {
              key: 'scope',
              labelKey: 'admin.admin.i18n.scope',
              kind: 'select',
              allowClear: false,
              options: [
                { value: 'USER', label: 'USER' },
                { value: 'SYSTEM', label: 'SYSTEM' },
              ],
              hint: t('admin.admin.i18n.systemScope'),
            },
            {
              key: 'content',
              labelKey: 'admin.field.content',
              kind: 'textarea',
              span: 2,
              rows: 3,
              hint: t('admin.admin.i18n.reloadHint'),
            },
          ]
        : [],
    [msgDraft, locales, t],
  );

  if (!canRead) {
    return (
      <Page>
        <Panel>
          <InlineAlert tone="warn">{t('admin.common.forbidden.desc')}</InlineAlert>
        </Panel>
      </Page>
    );
  }

  return (
    <Page>
      <CimSplitPane>
        <Panel
          className="scroll-y"
          title={t('admin.admin.i18n.locales')}
          flush
          actions={
            <>
              {canLocale && (
                <CimButton
                  bare
                  className="icon-btn"
                  title={t('admin.admin.i18n.newLocale')}
                  onClick={() => {
                    setLocaleErr({});
                    setLocaleDraft({ id: '', code: '', name: '', isDefault: false, sortNo: 20, status: 'ENABLED' });
                  }}
                >
                  <IconPlus width={14} height={14} />
                </CimButton>
              )}
              <CimButton size="small" onClick={localesRes.refresh} disabled={localesRes.loading}>
                <IconRefresh width={13} height={13} />
                {localesRes.loading ? t('admin.common.loading') : t('admin.common.refresh')}
              </CimButton>
            </>
          }
        >
          <DataTable
            flat
            size="small"
            rows={locales}
            columns={localeColumns}
            rowKey={(l) => l.id}
            loading={localesRes.loading}
            error={localesRes.error?.message}
            onRetry={localesRes.refresh}
            onRowClick={(l) => setFilterLocale(filterLocale === l.code ? '' : l.code)}
            rowClassName={(l) => (filterLocale === l.code ? 'ant-table-row-selected' : '')}
            emptyTitleKey="admin.common.none"
          />
        </Panel>

        <div>
          <Panel
            title={t('admin.admin.i18n.messages')}
            sub={
              pages > 1 ? t('admin.common.pagerInfo', { page: safePage, pages, total: messages.length }) : undefined
            }
            flush
            actions={
              <>
                {canSave && (
                  <CimButton
                    bare
                    className="icon-btn"
                    title={t('admin.admin.i18n.newMessage')}
                    onClick={() => {
                      setMsgErr({});
                      setMsgDraft({
                        id: '',
                        localeCode: filterLocale || i18n.language,
                        code: '',
                        content: '',
                        module: filterModule,
                        scope: 'USER',
                      });
                    }}
                  >
                    <IconPlus width={14} height={14} />
                  </CimButton>
                )}
                {reloadTranslations && (
                  <CimButton
                    size="small"
                    title={t('admin.admin.i18n.reloadHint')}
                    onClick={reloadTranslations}
                  >
                    <IconSave width={13} height={13} />
                    {t('admin.common.refresh')}
                  </CimButton>
                )}
              </>
            }
          >
            <DataTable
              flat
              size="small"
              rows={paged}
              columns={messageColumns}
              rowKey={(m) => m.id}
              loading={messagesRes.loading}
              error={messagesRes.error?.message}
              onRetry={messagesRes.refresh}
              search={{
                value: keyword,
                onChange: setKeyword,
                placeholderKey: 'admin.admin.i18n.keyword',
                width: 220,
              }}
              actions={
                <>
                  {filterLocale && (
                    <CimButton
                      size="small"
                      htmlType="button"
                      title={t('admin.admin.i18n.clickToFilter')}
                      onClick={() => setFilterLocale('')}
                    >
                      {filterLocale} ✕
                    </CimButton>
                  )}
                  <CimSelect
                    id="i18n-module"
                    width={170}
                    allowClear
                    value={filterModule || null}
                    onChange={(v) => setFilterModule((v as string) ?? '')}
                    placeholder={t('admin.admin.i18n.filterModule')}
                    options={modules.map((m) => ({ value: m, label: m }))}
                  />
                </>
              }
              pagination={
                pages > 1
                  ? {
                      page: safePage,
                      size: PAGE_SIZE,
                      total: messages.length,
                      onChange: (pg) => setPage(pg),
                      sizeOptions: [PAGE_SIZE],
                    }
                  : undefined
              }
              emptyTitleKey="admin.common.none"
            />
          </Panel>

          <Panel
            title={t('admin.i18n.missing')}
            sub={missOpen ? t('admin.admin.i18n.missingHint') : undefined}
            actions={
              <>
                <CimTag tone={missingKeys.length ? 'warn' : 'ok'}>
                  {t('admin.admin.i18n.missingCount', { n: missingKeys.length })}
                </CimTag>
                {canRemove && missOpen && missingKeys.length > 0 && (
                  <CimButton size="small" onClick={onClearMissing} disabled={busy}>
                    <IconTrash width={13} height={13} />
                    {t('admin.admin.i18n.clearMissing')}
                  </CimButton>
                )}
                <CimButton
                  bare
                  className="icon-btn"
                  htmlType="button"
                  title={missOpen ? t('admin.common.collapseAll') : t('admin.common.expandAll')}
                  aria-expanded={missOpen}
                  onClick={() => setMissOpen((v) => !v)}
                >
                  <span className={'tree-toggle' + (missOpen ? ' open' : '')} style={{ width: 14, height: 14 }}>
                    <IconChevron width={12} height={12} />
                  </span>
                </CimButton>
              </>
            }
          >
            {missOpen ? (
              missingKeys.length ? (
                <div className="tag-list">
                  {missingKeys.map((k) => (
                    <CimTag key={k} tone="warn" className="cim-mono">
                      <span title={missing[k]}>{k}</span>
                      {canSave && (
                        <CimButton
                          bare
                          className="chip-x"
                          title={t('admin.common.add')}
                          onClick={() => {
                            setMsgErr({});
                            setMsgDraft({
                              id: '',
                              localeCode: filterLocale || i18n.language,
                              code: k,
                              content: '',
                              module: '',
                              scope: 'USER',
                            });
                          }}
                        >
                          +
                        </CimButton>
                      )}
                    </CimTag>
                  ))}
                </div>
              ) : (
                <div className="cim-empty">
                  <div className="cim-empty-title">{t('admin.common.none')}</div>
                </div>
              )
            ) : null}
          </Panel>

          {/*
            未译条目 ≠ 缺失译文：缺失译文 = 四级兜底全未命中（连中文都没有）；
            未译条目 = 本语言没值、被默认语言兜底填满（英文界面显示中文）。
            只有按原始值比才看得见。
          */}
          <Panel
            title={t('admin.i18n.untranslated')}
            sub={
              gapLocale
                ? t('admin.admin.i18n.untranslatedHint', { locale: gapLocale.name ?? gapLocale.locale })
                : undefined
            }
            actions={
              <>
                {gapLocale && (
                  <CimTag tone={gapLocale.missing.length ? 'warn' : 'ok'}>
                    {t('admin.admin.i18n.untranslatedCount', {
                      n: gapLocale.missing.length + (gapLocale.truncated ? '+' : ''),
                    })}
                  </CimTag>
                )}
                <CimButton size="small" onClick={coverageRes.refresh} disabled={coverageRes.loading}>
                  <IconRefresh width={13} height={13} />
                  {coverageRes.loading ? t('admin.common.loading') : t('admin.common.refresh')}
                </CimButton>
              </>
            }
          >
            {gapLocale && gapLocale.missing.length ? (
              <>
                <div className="tag-list">
                  {gapLocale.missing.map((k) => (
                    <CimTag key={k} tone="warn" className="cim-mono">
                      <span title={k}>{k}</span>
                      {canSave && (
                        <CimButton
                          bare
                          className="chip-x"
                          title={t('admin.common.add')}
                          onClick={() => {
                            setMsgErr({});
                            setMsgDraft({
                              id: '',
                              localeCode: gapLocale.locale,
                              code: k,
                              content: '',
                              module: '',
                              scope: 'USER',
                            });
                          }}
                        >
                          +
                        </CimButton>
                      )}
                    </CimTag>
                  ))}
                </div>
                {gapLocale.truncated && <p className="dim">{t('admin.admin.i18n.untranslatedTruncated')}</p>}
              </>
            ) : (
              <div className="cim-empty">
                <div className="cim-empty-title">{t('admin.common.none')}</div>
                <div className="cim-empty-hint">{t('admin.admin.i18n.noUntranslated')}</div>
              </div>
            )}
          </Panel>
        </div>
      </CimSplitPane>

      <CimModal
        open={!!localeDraft}
        onClose={() => setLocaleDraft(null)}
        title={localeDraft?.id ? t('admin.common.edit') : t('admin.admin.i18n.newLocale')}
        onOk={onSaveLocale}
        okKey="admin.common.save"
        cancelKey="admin.common.cancel"
        okLoading={busy}
      >
        {localeDraft && (
          <FormGrid
            fields={localeFields}
            value={localeDraft}
            onChange={(patch) => setLocaleDraft({ ...localeDraft, ...patch })}
            errors={localeErr}
            columns={2}
            disabled={busy}
          />
        )}
      </CimModal>

      <CimModal
        open={!!msgDraft}
        onClose={() => setMsgDraft(null)}
        title={msgDraft?.id ? t('admin.common.edit') : t('admin.admin.i18n.newMessage')}
        onOk={onSaveMessage}
        okKey="admin.common.save"
        cancelKey="admin.common.cancel"
        okLoading={busy}
        width={640}
      >
        {msgDraft && (
          <FormGrid
            fields={msgFields}
            value={msgDraft}
            onChange={(patch) => setMsgDraft({ ...msgDraft, ...patch })}
            errors={msgErr}
            columns={2}
            disabled={busy}
          />
        )}
      </CimModal>
    </Page>
  );
}

// ---------------------------------------------------------------- 辅助

interface LocaleDraft {
  id: string;
  code: string;
  name: string;
  isDefault: boolean;
  sortNo: number;
  status: 'ENABLED' | 'DISABLED';
}

interface MsgDraft {
  id: string;
  localeCode: string;
  code: string;
  content: string;
  module: string;
  scope: 'SYSTEM' | 'USER';
}
