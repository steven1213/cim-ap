import type { i18n as I18nInstance } from 'i18next';

/** 语言目录项。后端 `SysLocale` 是多字段 DTO，取其子集即可赋值（多余字段不参与判定）。 */
export interface LocaleInfo {
  code: string;
  /** 语言的自称（如「简体中文」），直接用译文里的写法，不再过 i18n */
  name: string;
  isDefault?: boolean;
  sortNo?: number;
  /** `DISABLED` 的项在 `loadLocales()` 里被过滤掉 */
  status?: string;
}

/** 服务端返回的译文整包。 */
export interface BundlePayload {
  /** 译文版本号（服务端按库内容算）；缺省表示该来源不参与版本比对 */
  version?: string;
  /** 实际生效语言——服务端可能归一化（如 `zh` → `zh-CN`），以它为准 */
  lang?: string;
  /** 键 → 译文。**版本未变时为空对象**，此时前端必须保留本地缓存 */
  messages: Record<string, string>;
}

/**
 * 译文来源（远端实现）。
 *
 * <p>不提供此对象即为「纯内置包」应用：译文随前端发版，`localStorage` 缓存与
 * 版本比对全部空转，行为等同于静态资源表。</p>
 */
export interface RemoteSource {
  /** 拉取整包。抛错表示网络/服务异常，调用方退回本地缓存。 */
  loadBundle(ctx: { lang: string; version?: string }): Promise<BundlePayload | null>;
  /** 语言目录；缺省或抛错时退回 `CreateI18nOptions.locales` */
  loadLocales?(): Promise<LocaleInfo[]>;
  /** 缺键上报；缺省则只做开发态提示（不攒批、不请求） */
  reportMissing?(keys: string[], lang: string): Promise<unknown>;
}

export interface CreateI18nOptions {
  /**
   * 存储命名空间，同时是 `localStorage` 前缀：`{app}-lang` / `{app}-i18n-bundle:{lang}`。
   *
   * <p>必须各 ap 唯一：三个应用可能同域部署（不同 path），共用一个前缀会导致
   * A 应用的语言偏好串到 B 应用。</p>
   */
  app: string;
  /** 默认语言（四级兜底的第二级），缺省 `zh-CN` */
  defaultLng?: string;
  /**
   * 内置包：语言 → 键值表。
   *
   * <p>它同时承担两个角色：① 纯内置包应用的正文；② 远端应用的最小基线
   * （远端只回覆盖项时，其余键仍能从内置包取到）。远端包以 `overwrite` 方式
   * 叠在内置包之上，故**内置包不会被远端抹掉，只会被逐键覆盖**。</p>
   */
  builtin?: Record<string, Record<string, string>>;
  remote?: RemoteSource;
  /** 语言目录兜底（远端不可用 / 未提供 `loadLocales` 时使用） */
  locales?: LocaleInfo[];
  /** 关掉缺键**上报**（即使 `remote.reportMissing` 存在）；默认开启。不影响开发态提示 */
  missingReport?: boolean;
  /** 上报去抖，缺省 3000ms（一次页面渲染会连续缺十几个键，必须攒批） */
  missingDebounceMs?: number;
  /** 上报失败的重试上限，缺省 3 次；超限后丢弃，避免端点 404 时每 3s 空打一枪 */
  missingRetryLimit?: number;
  /**
   * 语言变化钩子。应用在此同步 `Accept-Language` 请求头——
   * 本包不认识 axios，也不该认识。
   */
  onLanguageChange?: (lang: string) => void;
  /**
   * 开发态：缺键打到控制台（每键一次），且缺键占位用 `⟦key⟧` 而非 humanize。
   *
   * <p>为什么占位也分环境：humanize 的产物「看起来像译文」（`Desc` / `Callback failed`），
   * 开发时反而看不出来；生产又不能让终端用户看方括号。两边要的东西不一样，只能分开。</p>
   */
  dev?: boolean;
}

/** `createI18n()` 的返回值。各应用把 `lib/i18n.ts` 收成一层薄适配即可。 */
export interface CimI18n {
  /** 底层 i18next 实例（同一个单例，`react-i18next` 的 `useTranslation` 直接可用） */
  instance: I18nInstance;
  /**
   * 启动引导：先用本地缓存同步渲染（防闪白），再后台比对版本。
   *
   * @return 生效语言
   */
  bootstrap(): Promise<string>;
  /**
   * 切换语言（不刷新页面）。
   *
   * @param lang  目标语言（BCP-47）
   * @param opts.force 忽略缓存版本强制重拉（语言目录变更后使用）
   */
  changeLanguage(lang: string, opts?: { force?: boolean }): Promise<void>;
  currentLang(): string;
  loadLocales(): Promise<LocaleInfo[]>;
  /** 键在当前语言（含兜底链）里是否存在——供 `@cim/ui` 的 `UiLocaleProvider.hasKey` 使用 */
  hasKey(key: string): boolean;
  /** 已灌入内存的语言（调试用） */
  loadedLanguages(): string[];
}
