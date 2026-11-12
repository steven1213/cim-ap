import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimModal,
  CimSelect,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
  InlineAlert,
  CimSplitPane,
} from '@cim/ui';
import * as api from '@/lib/api';
import { changeLanguage, currentLang } from '@/lib/i18n';
import type { LocaleCoverageDto, SysI18nDto, SysLocaleDto } from '@/types';
import { useHasPermission } from '@/lib/usePermission';
import { I18N_LIST, I18N_CREATE, I18N_DELETE, I18N_LOCALE } from '@/lib/permCodes';
import { IconChevron, IconEdit, IconPlus, IconRefresh, IconSave, IconTrash } from '@/components/Icons';

/** 译文表客户端分页大小（服务端已按 locale/module/keyword 过滤，前端只做翻页）。 */
const PAGE_SIZE = 50;

/**
 * 多语言（`/i18n`）：语言目录与译文维护。
 *
 * <p><b>布局（主从式）</b>：左列语言目录（点行即过滤右侧译文），右列译文表
 * （module/keyword 过滤 + 客户端分页）+ 缺失键（默认收起，点开处理）。
 * 与菜单/权限/角色三页保持同一 master-detail 心智模型。</p>
 *
 * <p><b>数据面在 IAM 自己</b>（不像菜单/权限/角色那样复用平台端点）：管理端点
 * `GET/POST/DELETE /api/v1/admin/i18n/**` 由 `I18nAdminController` 提供，权限码是
 * 控制台自己的 `iam:i18n:*` —— 与平台 `sys:*` 无关，因此本页不需要双闸门。</p>
 *
 * <p><b>两级 scope</b>：`SYSTEM` 是程序内置种子（启动写入、不可删），`USER` 是业务/UI 文案。
 * 两者都按 `(localeCode, code)` upsert——在此覆盖 SYSTEM 行即可把内置文案改掉，
 * 且重启不会被种子冲回（种子口径是「已存在即跳过」）。</p>
 *
 * <p><b>保存后即时生效</b>：写库即 `cache.invalidate()`，前端下次拉取或刷新页面即拿到新译文。
 * 「刷新译文」按钮直接重拉当前语言的整包（`changeLanguage(lang, true)`）。</p>
 */
export default function I18nAdminPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canRead = useHasPermission(I18N_LIST);
  const canRemove = useHasPermission(I18N_DELETE);

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
    ({ signal }) => api.get<SysLocaleDto[]>('/admin/i18n/locales', undefined, { signal }),
    { enabled: canRead },
  );
  const messagesRes = useResource<SysI18nDto[]>(
    ({ signal }) =>
      api.get<SysI18nDto[]>(
        '/admin/i18n/messages',
        {
          localeCode: filterLocale || undefined,
          module: filterModule || undefined,
          keyword: appliedKw || undefined,
        },
        { signal },
      ),
    { deps: [filterLocale, filterModule, appliedKw], enabled: canRead, keepPreviousData: true },
  );
  const missingRes = useResource<Record<string, string>>(
    ({ signal }) => api.get<Record<string, string>>('/admin/i18n/missing', undefined, { signal }),
    { enabled: canRead },
  );
  const coverageRes = useResource<LocaleCoverageDto[]>(
    ({ signal }) => api.get<LocaleCoverageDto[]>('/admin/i18n/coverage', undefined, { signal }),
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
   * 「未译条目」面板跟踪的语言：优先左栏选中的那个；没选就挑**第一个真有缺口**的语言。
   * 不挑 `[0]`——列表首项通常是默认语言，恒 100%，面板会永远空着，看起来像坏了。
   */
  const gapLocale = useMemo(() => {
    if (filterLocale) return coverage.find((c) => c.locale === filterLocale);
    return coverage.find((c) => !c.base && c.missing.length > 0) ?? coverage.find((c) => !c.base);
  }, [coverage, filterLocale]);

  /** 过滤条件变化 → 回第一页（否则会停在一个不存在的页码上）。 */
  useEffect(() => setPage(1), [filterLocale, filterModule, appliedKw]);

  /**
   * 模块候选**只增不减**：直接按当前 `messages` 推导会让下拉在选中某模块后
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
      setLocaleErr({ code: t('iam.common.required') });
      return;
    }
    setLocaleErr({});
    setBusy(true);
    try {
      await api.post<SysLocaleDto>('/admin/i18n/locales', {
        id: localeDraft.id || undefined,
        code: localeDraft.code.trim(),
        name: localeDraft.name.trim() || null,
        isDefault: localeDraft.isDefault,
        sortNo: Number(localeDraft.sortNo) || 0,
        status: localeDraft.status,
      });
      toast.success(t('iam.common.saved'));
      setLocaleDraft(null);
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteLocale(l: SysLocaleDto) {
    const ok = await confirm({
      title: t('iam.common.delete'),
      content: `${t('iam.common.confirmDelete')}\n${l.code}`,
      danger: true,
      okKey: 'iam.common.delete',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.del<void>(`/admin/i18n/locales/${encodeURIComponent(l.id)}`);
      toast.success(t('iam.common.deleted'));
      if (filterLocale === l.code) setFilterLocale('');
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onSaveMessage() {
    if (!msgDraft) return;
    if (!msgDraft.code.trim() || !msgDraft.localeCode.trim()) {
      setMsgErr({ code: t('iam.common.required') });
      return;
    }
    setMsgErr({});
    setBusy(true);
    try {
      await api.post<SysI18nDto>('/admin/i18n/messages', {
        id: msgDraft.id || undefined,
        localeCode: msgDraft.localeCode.trim(),
        code: msgDraft.code.trim(),
        content: msgDraft.content,
        module: msgDraft.module.trim() || undefined,
        scope: msgDraft.scope,
      });
      toast.success(t('iam.common.saved'));
      setMsgDraft(null);
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteMessage(m: SysI18nDto) {
    const ok = await confirm({
      title: t('iam.common.delete'),
      content: `${t('iam.common.confirmDelete')}\n${m.localeCode} · ${m.code}`,
      danger: true,
      okKey: 'iam.common.delete',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.del<void>(`/admin/i18n/messages/${encodeURIComponent(m.id)}`);
      toast.success(t('iam.common.deleted'));
      reloadAll();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onClearMissing() {
    setBusy(true);
    try {
      await api.del<void>('/admin/i18n/missing');
      toast.success(t('iam.common.deleted'));
      missingRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  const localeColumns = useMemo<Array<DataTableColumn<SysLocaleDto>>>(
    () => [
      { key: 'code', titleKey: 'iam.field.locale', mono: true, width: 108 },
      {
        key: 'name',
        titleKey: 'iam.field.name',
        ellipsis: true,
        render: (l) => (
          <span className="cell-inline">
            <span className="dim">{l.name ?? '—'}</span>
            {l.isDefault && (
              <CimTag size="small" tone="run">
                {t('iam.i18n.default')}
              </CimTag>
            )}
            {l.status === 'DISABLED' && (
              <CimTag size="small" tone="err">
                {t('iam.common.disabled')}
              </CimTag>
            )}
          </span>
        ),
      },
      {
        key: 'coverage',
        titleKey: 'iam.field.coverage',
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
        titleKey: 'iam.common.actions',
        width: 76,
        render: (l) => (
          <span className="cell-actions" onClick={(e) => e.stopPropagation()}>
            <Can perm={I18N_LOCALE}>
              <CimButton
                bare
                className="icon-btn"
                title={t('iam.common.edit')}
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
            </Can>
            {!l.isDefault && (
              <Can perm={I18N_LOCALE}>
                <CimButton
                  bare
                  className="icon-btn danger"
                  title={t('iam.common.delete')}
                  onClick={() => onDeleteLocale(l)}
                >
                  <IconTrash width={13} height={13} />
                </CimButton>
              </Can>
            )}
          </span>
        ),
      },
    ],
    // onDeleteLocale 依赖 filterLocale，透传 t 与 filterLocale 即可
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, filterLocale, coverageByLocale],
  );

  const messageColumns = useMemo<Array<DataTableColumn<SysI18nDto>>>(
    () => [
      { key: 'localeCode', titleKey: 'iam.field.locale', mono: true, width: 96 },
      { key: 'code', titleKey: 'iam.field.code', mono: true, width: 240, ellipsis: true },
      {
        key: 'content',
        titleKey: 'iam.field.content',
        ellipsis: true,
        render: (m) =>
          m.content ? m.content : (
            <CimTag size="small" tone="err">
              {t('iam.common.none')}
            </CimTag>
          ),
      },
      {
        key: 'module',
        titleKey: 'iam.field.module',
        width: 96,
        render: (m) => <span className="cim-mono dim">{m.module ?? '—'}</span>,
      },
      {
        key: 'scope',
        titleKey: 'iam.admin.i18n.scope',
        width: 92,
        render: (m) => (
          <CimTag size="small" tone={m.scope === 'SYSTEM' ? 'run' : 'idle'}>
            {m.scope}
          </CimTag>
        ),
      },
      {
        key: 'actions',
        titleKey: 'iam.common.actions',
        width: 108,
        render: (m) => (
          <span className="cell-actions">
            <Can perm={I18N_CREATE}>
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
                {t('iam.common.edit')}
              </CimButton>
            </Can>
            <Can perm={I18N_DELETE}>
              <CimButton bare className="icon-btn danger" title={t('iam.common.delete')} onClick={() => onDeleteMessage(m)}>
                <IconTrash width={13} height={13} />
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const localeFields = useMemo<Array<FormField<LocaleDraft>>>(
    () =>
      localeDraft
        ? [
            {
              key: 'code',
              labelKey: 'iam.field.code',
              kind: 'text',
              mono: true,
              placeholder: 'zh-TW',
              required: true,
              disabled: !!localeDraft.id,
              hint: t('iam.admin.i18n.localeCodeHint'),
            },
            { key: 'name', labelKey: 'iam.field.name', kind: 'text', placeholder: '繁體中文' },
            { key: 'sortNo', labelKey: 'iam.field.sortNo', kind: 'number', min: 0 },
            {
              key: 'isDefault',
              labelKey: 'iam.i18n.default',
              kind: 'switch',
              hint: t('iam.admin.i18n.defaultLocked'),
            },
            {
              key: 'status',
              labelKey: 'iam.field.status',
              kind: 'select',
              allowClear: false,
              options: [
                { value: 'ENABLED', labelKey: 'iam.common.enabled' },
                { value: 'DISABLED', labelKey: 'iam.common.disabled' },
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
              labelKey: 'iam.field.locale',
              kind: 'select',
              allowClear: false,
              options: locales.map((l) => ({ value: l.code, label: l.code, hint: l.name ?? undefined })),
              required: true,
            },
            {
              key: 'code',
              labelKey: 'iam.field.code',
              kind: 'text',
              mono: true,
              placeholder: 'iam.menu.menus',
              required: true,
            },
            { key: 'module', labelKey: 'iam.field.module', kind: 'text', mono: true },
            {
              key: 'scope',
              labelKey: 'iam.admin.i18n.scope',
              kind: 'select',
              allowClear: false,
              options: [
                { value: 'USER', label: 'USER' },
                { value: 'SYSTEM', label: 'SYSTEM' },
              ],
              hint: t('iam.admin.i18n.systemScope'),
            },
            {
              key: 'content',
              labelKey: 'iam.field.content',
              kind: 'textarea',
              span: 2,
              rows: 3,
              hint: t('iam.admin.i18n.reloadHint'),
            },
          ]
        : [],
    [msgDraft, locales, t],
  );

  if (!canRead) {
    return (
      <Page>
        <Panel>
          <InlineAlert tone="warn">{t('iam.common.forbidden.desc')}</InlineAlert>
        </Panel>
      </Page>
    );
  }

  return (
    <Page>
      <CimSplitPane>
        <Panel
          className="scroll-y"
          title={t('iam.admin.i18n.locales')}
          sub={t('iam.admin.i18n.clickToFilter')}
          flush
          actions={
            <>
              <Can perm={I18N_LOCALE}>
                <CimButton
                  bare
                  className="icon-btn"
                  title={t('iam.admin.i18n.newLocale')}
                  onClick={() => {
                    setLocaleErr({});
                    setLocaleDraft({ id: '', code: '', name: '', isDefault: false, sortNo: 20, status: 'ENABLED' });
                  }}
                >
                  <IconPlus width={14} height={14} />
                </CimButton>
              </Can>
              <CimButton size="small" onClick={localesRes.refresh} disabled={localesRes.loading}>
                <IconRefresh width={13} height={13} />
                {localesRes.loading ? t('iam.common.loading') : t('iam.common.refresh')}
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
            emptyTitleKey="iam.common.none"
          />
        </Panel>

        <div>
          <Panel
            title={t('iam.admin.i18n.messages')}
            sub={
              pages > 1 ? t('iam.common.pagerInfo', { page: safePage, pages, total: messages.length }) : undefined
            }
            flush
            actions={
              <>
                <Can perm={I18N_CREATE}>
                  <CimButton
                    bare
                    className="icon-btn"
                    title={t('iam.admin.i18n.newMessage')}
                    onClick={() => {
                      setMsgErr({});
                      setMsgDraft({
                        id: '',
                        localeCode: filterLocale || currentLang(),
                        code: '',
                        content: '',
                        module: filterModule,
                        scope: 'USER',
                      });
                    }}
                  >
                    <IconPlus width={14} height={14} />
                  </CimButton>
                </Can>
                {currentLang() && (
                  <CimButton
                    size="small"
                    title={t('iam.admin.i18n.reloadHint')}
                    onClick={() => changeLanguage(currentLang(), true)}
                  >
                    <IconSave width={13} height={13} />
                    {t('iam.common.refresh')}
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
                placeholderKey: 'iam.admin.i18n.keyword',
                width: 220,
              }}
              actions={
                <>
                  {filterLocale && (
                    <CimButton
                      size="small"
                      htmlType="button"
                      title={t('iam.admin.i18n.clickToFilter')}
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
                    placeholder={t('iam.admin.i18n.filterModule')}
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
                      onChange: (p) => setPage(p),
                      sizeOptions: [PAGE_SIZE],
                    }
                  : undefined
              }
              emptyTitleKey="iam.common.none"
            />
          </Panel>

          <Panel
            title={t('iam.i18n.missing')}
            sub={missOpen ? t('iam.admin.i18n.missingHint') : undefined}
            actions={
              <>
                <CimTag tone={missingKeys.length ? 'warn' : 'ok'}>
                  {t('iam.admin.i18n.missingCount', { n: missingKeys.length })}
                </CimTag>
                {canRemove && missOpen && missingKeys.length > 0 && (
                  <CimButton size="small" onClick={onClearMissing} disabled={busy}>
                    <IconTrash width={13} height={13} />
                    {t('iam.admin.i18n.clearMissing')}
                  </CimButton>
                )}
                <CimButton
                  bare
                  className="icon-btn"
                  htmlType="button"
                  title={missOpen ? t('iam.common.collapseAll') : t('iam.common.expandAll')}
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
                      <CimButton
                        bare
                        className="chip-x"
                        title={t('iam.common.add')}
                        onClick={() => {
                          setMsgErr({});
                          setMsgDraft({
                            id: '',
                            localeCode: filterLocale || currentLang(),
                            code: k,
                            content: '',
                            module: '',
                            scope: 'USER',
                          });
                        }}
                      >
                        +
                      </CimButton>
                    </CimTag>
                  ))}
                </div>
              ) : (
                <div className="cim-empty">
                  <div className="cim-empty-title">{t('iam.common.none')}</div>
                </div>
              )
            ) : null}
          </Panel>

          {/*
            未译条目 ≠ 缺失译文：
            - 缺失译文 = 四级兜底全未命中（连中文都没有），前端会上报，量很小；
            - 未译条目 = 本语言没值、被默认语言兜底填满。**服务端合并后键是命中的**，
              英文界面显示中文，前端不会上报任何东西。只有按原始值比才看得见。
          */}
          <Panel
            title={t('iam.i18n.untranslated')}
            sub={
              gapLocale
                ? t('iam.admin.i18n.untranslatedHint', { locale: gapLocale.name ?? gapLocale.locale })
                : undefined
            }
            actions={
              <>
                {gapLocale && (
                  <CimTag tone={gapLocale.missing.length ? 'warn' : 'ok'}>
                    {t('iam.admin.i18n.untranslatedCount', {
                      n: gapLocale.missing.length + (gapLocale.truncated ? '+' : ''),
                    })}
                  </CimTag>
                )}
                <CimButton size="small" onClick={coverageRes.refresh} disabled={coverageRes.loading}>
                  <IconRefresh width={13} height={13} />
                  {coverageRes.loading ? t('iam.common.loading') : t('iam.common.refresh')}
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
                      <Can perm={I18N_CREATE}>
                        <CimButton
                          bare
                          className="chip-x"
                          title={t('iam.common.add')}
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
                      </Can>
                    </CimTag>
                  ))}
                </div>
                {gapLocale.truncated && <p className="dim">{t('iam.admin.i18n.untranslatedTruncated')}</p>}
              </>
            ) : (
              <div className="cim-empty">
                <div className="cim-empty-title">{t('iam.common.none')}</div>
                <div className="cim-empty-hint">{t('iam.admin.i18n.noUntranslated')}</div>
              </div>
            )}
          </Panel>
        </div>
      </CimSplitPane>

      <CimModal
        open={!!localeDraft}
        onClose={() => setLocaleDraft(null)}
        title={localeDraft?.id ? t('iam.common.edit') : t('iam.admin.i18n.newLocale')}
        onOk={onSaveLocale}
        okKey="iam.common.save"
        cancelKey="iam.common.cancel"
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
        title={msgDraft?.id ? t('iam.common.edit') : t('iam.admin.i18n.newMessage')}
        onOk={onSaveMessage}
        okKey="iam.common.save"
        cancelKey="iam.common.cancel"
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

// ------------------------------------------------------------------ 辅助

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
