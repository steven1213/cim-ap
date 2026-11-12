# API 响应与消息分发规约

> 适用范围：`platform/server/**` 与 `business/*/server/**` 的全部 HTTP 端点。
> 本文是**契约层规约**，与 [design.md §4](./design.md) 的实现描述互补：design.md 讲"结构长什么样"，本文讲"什么必须做、什么允许例外、怎么强制"。
> 强制手段已落地为代码：`ApiContractVerifier`（启动期校验）、`@RawResponse`（例外出口）、`CimMessageDispatcher`（可选的分发模式）。

---

## 1. 三条铁律

1. **控制器方法的返回类型必须是 `Result<T>`。** 没有第二种数据接口形态。
2. **裸返回只允许四类例外**，且必须标注 `@RawResponse(reason = …)` 写明理由：文件下载、HTML/跳转、标准协议、流式。
3. **例外由框架校验，不靠自觉。** 违反者启动即失败（`cim.api.response.strict`，默认 `true`）。

这三条解决的是一个具体问题：**同一个前端工程里存在"要解包"和"不要解包"两种接口时，每个调用点都要先判断一次**。本仓库接入校验器前就有 9 个这样的端点（4 个其实该套信封、5 个属正当例外），其中 `POST /api/v1/internal/token-version/bump` 让 IAM 前端不得不保留一个 `postRaw()` 旁路——两套口径的成本会一直摊在每个新增接口上。

---

## 2. 统一信封

```java
public record Result<T>(
        int code,                    // 0 = 成功；非 0 见 BizCode
        String msg,                  // 已按 Accept-Language 解析的提示
        T data,                      // 业务数据；无数据为 null
        Map<String,String> errors,   // 字段级校验错误（field → 提示），无则 null
        String traceId,              // 取自 MDC，用于日志串联
        long timestamp               // 服务器毫秒时间戳
) { }
```

**成功**：`Result.ok(data)`；无返回体用 `Result.ok()`（`data = null`）。

**失败**：由 `GlobalExceptionHandler` 统一生成，控制器**不要**自己 `return Result.fail(...)`：

| 抛出 | 生成 | HTTP |
| --- | --- | --- |
| `BizException(BizCode.X, args…)` | `code = X`，`msg` 走 i18n | `X.httpStatus` |
| `ValidationException` | `PARAM_INVALID` + `errors` 字段级明细 | 400 |
| `SystemException` / 未捕获异常 | `SYSTEM_ERROR`，`msg` 只给通用语，**不含内部细节** | 500 |

**`msg` 一律由 `MessageSource` 解析**（`BizCode.i18nKey`）。自定义文案请用 `BizException(BizCode, String message, args…)`，不要拼接进 `i18nKey`。

### 2.1 HTTP 状态与业务码分工

- **HTTP 状态码表达"这次调用在传输/协议层发生了什么"**（200/400/401/403/404/409/500）。
- **`code` 表达"业务上具体是什么问题"**（1000 段认证、2000 段业务、3000 段集成、9000 段系统）。
- 前端判据：HTTP 2xx **且** `code === 0` 才算成功；其余一律进错误分支。

### 2.2 不要做的三件事

- ❌ 控制器里 `try { … } catch { return Result.fail(...) }` —— 异常交给全局处理器，否则 `traceId`、字段级错误、HTTP 状态三条都要自己维护一遍。
- ❌ 用 `Map<String,Object>` 当返回体（`bump` 这类临时结构应建 record/DTO）。`Map` 无法被契约校验、无法生成接口文档、改字段没人知道。
- ❌ 把 `PageResult` 直接当返回类型（见 §3）。

---

## 3. 分页

```java
Result<PageResult<T>> page(PageQuery query)   // ✅
PageResult<T> page(PageQuery query)           // ❌ 裸分页
```

- `PageResult` 是 `cim-core` 的值对象（`records / total / page / size`），**页号 1 起**；Spring Data 的 `Page` 用 `PageResults.from(page)` 转换（0 起 → 1 起在这一处收口）。
- 分页信息属于业务数据，放在 `data` 内；**不要**把 `total` 塞进信封顶层——信封是所有接口共用的，加字段就等于给每个接口加字段。
- 全量返回 + 前端本地分页是允许的（前端 `DataTable` 支持），但要在接口注释里写明"不分页"及其上限依据。

---

## 4. 例外白名单：`@RawResponse`

```java
@RawResponse(
        reason = RawResponse.Reason.DOWNLOAD,
        value = "配方导出附件：Content-Disposition 下载，套信封会破坏文件内容")
public ResponseEntity<byte[]> export(...) { }
```

`reason` 只有四个取值，**选不出一个就不要开例外**：

| Reason | 含义 | 典型形态 |
| --- | --- | --- |
| `DOWNLOAD` | 二进制/附件下载 | `ResponseEntity<byte[]>`、`ResponseEntity<Resource>` + `Content-Disposition: attachment` |
| `HTML` | 服务端渲染页面或 302 跳转 | `text/html`、`Location` 回跳 |
| `STANDARD_PROTOCOL` | 报文体由外部规范规定 | JWKS（RFC 7517） |
| `STREAM` | 长连接流式响应 | SSE / WebSocket |

**当前全部例外（4 处，改动需同步本表）**：

| 端点 | Reason | 消费方 | 不套信封的原因 |
| --- | --- | --- | --- |
| `GET /.well-known/jwks.json`（`JwksController`） | `STANDARD_PROTOCOL` | 各 ap 的 `JwtVerifier` 按 RFC 7517 解析 | 包一层 `keys` 就变成非标准 JWKS |
| `GET /api/v1/approval/attachments/{id}/download` | `DOWNLOAD` | 浏览器直接下载 | 信封会写进文件内容 |
| `GET /api/v1/rms/recipes/export` | `DOWNLOAD` | 前端 `downloadBlob` | 同上 |
| `GET /sys/notify/stream`（`SseController`） | `STREAM` | 各 ap 的 `SseClient` | 连接期内持续推事件帧，一次性信封不适用 |
| `GET /login`（`SsoLoginPageController`） | `HTML` | 浏览器 | 交回静态登录页（`static/login/index.html`）/ 302 回跳业务 ap |

> 标注在**类上**表示该控制器的所有映射方法都是例外（如整体流式端点）；通常标在方法上。

---

## 5. 强制手段

### 5.1 启动期校验（`ApiContractVerifier`）

`SmartInitializingSingleton` 收尾阶段反射扫描控制器：

```
cim.api.response.verify            # 默认 true；关闭会打 WARN
cim.api.response.strict            # 默认 true＝违规即启动失败；false＝只记 ERROR
cim.api.response.include-packages  # 默认 [com.cim]；限定校验范围
```

校验对象：`@RestController` 的 Bean，以及**仅有类型级 `@RequestMapping`** 的处理器 Bean。（后者只是「不是组件」的一种形态，**它对 MVC 不可见**——见 §7，别照着写端点。）

跳过项：`@ExceptionHandler` / `@InitBinder` / `@ModelAttribute`；框架自带控制器（`BasicErrorController` 等，默认被 `include-packages` 排除——它是错误页出口，不属于业务契约）。

违规输出形如：

```
统一响应契约违规（返回类型必须是 Result，或标注 @RawResponse 并给出理由）：
  - AttachmentController#download() -> ResponseEntity
口径见 docs/platform/server/api-convention.md
```

### 5.2 测试上下文

测试夹具（如 `cim-auth-starter` 的 `TestApi`）为了验证安全过滤链会故意返回裸字符串。**不要**为此给夹具加 `@RawResponse`（理由不成立），而是在该测试的 `application.yml` 里关掉：

```yaml
cim:
  api:
    response:
      verify: false
```

### 5.3 滚动发布兼容

内部 S2S 端点（`/api/v1/internal/**`）改形态时，**消费侧必须同时兼容新旧两种响应**，否则"先升级哪一侧"都会让安全校验静默退化：

- `IamTokenVersionChecker` / `IamTokenBlacklistChecker` 已同时接受信封与旧纯文本；
- 解析失败才判为"不可达"→ fail-open（版本/黑名单是增强，不应因 IAM 抖动拒登）。

### 5.4 前端闸门：渲染契约

后端契约保证"返回什么形状"，前端还得保证"在给定权限集下画出什么"。`business/iam-ap/web/test/render-contract.tsx` 用 `renderToStaticMarkup` 把页面渲成静态 HTML（零浏览器、零测试框架），断言三件事：**双闸门**（IAM 控制台码 ∧ 平台码）生效、无权限时写按钮**不挂载**、存量页无中文残留。

三个坑（都真踩过）：

1. **必须挂 `UiLocaleProvider`**（与 `main.tsx` 的 `Root` 同构）。`@cim/ui` 的组件文案走 `useUiLabel(key, fallback)`，未挂 Provider 时全部回落到组件内置中文兜底——12 个页面会同时报"含中文残留"，而产品代码并没有泄漏（2026-11-12 修）。
2. **`hasKey` 应恒为 `true`**。本测试断言的是"页面请求了哪些键"，缺键处理器已把 `t(key)` 变成 `⟪key⟫` 标记；若让 `hasKey` 如实返回 `false`，`useUiLabel` 会取中文兜底，标记永远出不来。
3. **页面迁到 `@cim/*` 后要给 `test/run.mjs` 补 `@cim/*` + `antd` 别名与 `nodePaths`**，否则 esbuild 报 `Could not resolve "react"`，闸门静默空转（看起来有测试，实际从未跑通）。

断言本身也要随组件层更新：空态类名已由旧手写页的 `.empty` 变为 `@cim/ui` 的 `.cim-empty`，树为 `.cim-tree`——按类名/文案写死的断言会随迁移失效，改的时候要对齐组件层的真实 DOM。

---

## 6. 消息分发模式（可选，默认关闭）

### 6.1 何时用

**适用**：同一入口承载多品类请求、且品类会持续增加——典型是 MES 侧"设备/工单/主数据各类消息"，或厂商定制扩展点。参考实现（`industry.mes` 的 `CustomAutoProcessorController` + `@AutoMsgToProcessor`）就是这一形态。

**不适用**（本仓库主路径）：REST 资源化接口。我们的 150 个映射方法都有明确资源路径与强类型 DTO，套通配入口会把 URL 语义换成"消息名"，得不偿失。**默认关闭**（`cim.api.dispatch.enabled=false`），按需开启。

### 6.2 结构

```
POST {base-path}/{message}          # base-path 默认 /api/v1/messages
        ↓
CimDispatchController → CimMessageDispatcher
        ↓ 查表（路径 → 请求类型 + 处理器）
CimMessageDispatcher → 反序列化 → 处理器.process(request) → Result.ok(data)
```

```java
@CimMessage(processor = LeaveApplyProcessor.class)      // 标在请求 DTO 上
public record LeaveApplyRequest(String userId, int days) { }

public class LeaveApplyProcessor implements CimMessageProcessor<LeaveApplyRequest> {
    @Override public Object process(LeaveApplyRequest req) { … }   // 返回业务数据，不返回 Result
}
```

路径缺省由 DTO 名派生：`LeaveApplyRequest → /leaveApply`（大小写不敏感，`/leaveapply` 同样命中）。

### 6.3 与参考实现的差异（为什么改）

| 维度 | 参考实现 | 本实现 | 理由 |
| --- | --- | --- | --- |
| 信封 | 处理器自己 `return Result` | 框架统一包 | 接口签名不返回 `Result`，**结构上不可能漏包**；参考实现靠 handler 运行期 `instanceof` 兜底，漏了就抛 |
| 注册时机 | 启动扫描 | 启动扫描（`afterSingletonsInstantiated`） | 相同；但路径重复**直接失败**并打印双方类名（照抄其优点） |
| 泛型一致性 | 不校验 | 注册期校验处理器泛型参数与 DTO 是否一致 | 绑错处理器在参考实现里要到运行期强转才炸 |
| 入口形态 | 两套通配（`/{x}Controller/{y}Request`、`/auto/{y}Request`） | 单段 `/messages/{name}` | 前者要求调用方拼内部类名，等于把实现细节写进对外契约 |
| 事务 | 控制器方法级 `@Transactional` | 处理器自行声明 | 同一入口既有查询也有写操作，一刀切会把只读消息拖进写事务 |
| 组件语义 | `@RestController`（可被宿主扫描） | `@RestController` + 自动装配 `@Bean` 注册（两条路都通，见 §7） | 参考实现把控制器留在宿主工程里，库只管处理器表；本仓库让 starter 自带端点，于是必须处理「宿主扫描」与「开关」的交互——结论见 §7 |
| 失败语义 | 自定义 `CoreException` + 错误码枚举 | 复用 `BizCode` + 全局异常处理器 | 一套错误码，前端一个判据 |

### 6.4 开启方式

```yaml
cim:
  api:
    dispatch:
      enabled: true
      base-path: /api/v1/messages
      base-packages: []        # 留空取应用主类所在包
```

**安全**：分发入口不自行放行，落在应用既有鉴权链内。开启前必须确认权限模型覆盖该前缀——通配入口意味着"一个 URL 前缀背后是多品类操作"，权限粒度要靠处理器上的 `@PreAuthorize` 或 `SecurityGuard` 补齐。

**日志**：每次分发写 `[cim-dispatch][START|DONE|FAILED]`，并把 `path` / `processor` 放进 MDC（`cim.msg.path` / `cim.msg.processor`），便于按消息类型过滤。

**失败语义**：路径未注册 → `PARAM_INVALID`（**不回显已注册清单**，避免探测）；报文无法反序列化 → `PARAM_INVALID` 并指明目标类型；处理器抛业务异常原样透传；其余 → `SystemException`。

### 6.5 库 jar 里的 HTTP 端点（starter 自带端点）

这条约定此前写反过，导致「把 `@RestController` 换成 `@RequestMapping` 就能避免宿主扫描」的尝试反复出现（并 404）。现在两条硬事实都实测过，按这个写。

**硬事实一：Spring 6 起只有 `@Controller` 能被 MVC 发现。**

`RequestMappingHandlerMapping.isHandler()` 在 Spring Framework **6.0** 起方法体只剩一句
`hasAnnotation(beanType, Controller.class)`；5.x 里「或 `@RequestMapping`」的分支已删（反编译
6.1.14 与 5.0.8 对比确认）。所以「类上只写 `@RequestMapping + @ResponseBody`、由自动装配 `@Bean` 注册」
在 Spring 6 上**永远 404**——405/500 都不是，就是找不到 handler。守门测试：
`cim-spring-support` 的 `LibraryControllerAssemblyTest`（A 形态 200 / B 形态 404 / 未注册 404）。

**硬事实二：`@Controller` 蕴含 `@Component`，宿主扫描一定会扫到。**

`cim-bootstrap` 用 `scanBasePackages="com.cim"`，会把所有 starter 的控制器扫进来。默认态看着没事——
Boot 的 `isOverriddenByExistingDefinition` 允许自动装配的 `@Bean` 覆盖扫描定义，而 `@ConditionalOnMissingBean`
在扫描先赢时会让位，最终只剩一个 Bean，谁赢都不报错。**但只要开关关闭**，`@Bean` 不注册，扫描的定义
成为唯一，而它依赖的 Bean 也不存在了：

```
cim.jpa.sql-log.admin-enabled=false
→ No qualifying bean of type 'java.lang.String' available
   （SqlLogAdminController 的构造器第一个参数是 loggerName）
```

报错完全不提「你关了一个开关」。同类隐患：`SseController`（依赖 `SseRegistry`/`NotifyProperties`）。

**规则（三件套，缺一不可）：**

```java
@RestController                                                     // ① MVC 才认
@ConditionalOnProperty(prefix = "cim.notify", name = "enabled",     // ② 与自动装配一字不差
        havingValue = "true", matchIfMissing = true)                //    扫描阶段即跳过
public class SseController { … }

@Bean @ConditionalOnMissingBean                                     // ③ 正常装配路径
public SseController sseController(SseRegistry r, NotifyProperties p) { … }
```

**不要**：① 去掉 `@RestController` 换 `@RequestMapping`（404）；② 只加 `@Bean` 不写类上条件
（开关一关启动失败）；③ 用 `@ConditionalOnBean` 代替 `@ConditionalOnProperty`——前者只在自动配置类里有
确定顺序，组件扫描阶段求值不可靠。

守门测试：`LibraryEndpointScanTest`（bootstrap，关开关真启动一次）、`SseControllerScanConditionTest`
（notify，扫描候选断言，毫秒级）。

---

## 7. 落地清单

| 项 | 位置 | 状态 |
| --- | --- | --- |
| `Result` / `BizCode` / `GlobalExceptionHandler` | `cim-spring-support/web` | ✅ 既有 |
| `@RawResponse` + 四类 Reason | `cim-spring-support/web` | ✅ 新增 |
| `ApiContractVerifier` + `ApiResponseProperties` | `cim-spring-support/web` | ✅ 新增 |
| 消息分发（`CimMessage` / `CimMessageProcessor` / `Registry` / `Dispatcher` / `Controller`） | `cim-spring-support/dispatch` | ✅ 新增（默认关闭） |
| 9 个非 `Result` 端点整改（4 改信封 / 5 标例外） | `iam-ap` / `rms-ap` / `approval-ap` / `cim-notify-starter` | ✅ 已完成，见 §4 例外表 |
| 前端 `postRaw()` 旁路 | `iam-ap` / `rms-ap` 的 `lib/api.ts` | ✅ 已删除 |
| `cim.api.response` / `cim.api.dispatch` 写入配置模板 | `cim-bootstrap` + `iam-ap` / `rms-ap` / `approval-ap` 的 `application.yml.example` | ✅ 已补（6 个键均已在 `spring-configuration-metadata.json` 中） |
| 前端渲染契约闸门 | `business/iam-ap/web/test/render-contract.tsx` | ✅ 33/33（已按组件层现状重置，见 §5.4） |

### 待办

- 消息分发三应用均未启用：等出现「一类报文 N 个近似端点」的真实场景再开，避免为模式而模式。
- 内部 S2S 端点（`/api/v1/internal/**`）下次改形态时，记得同步检查消费侧的双兼容分支是否还需要保留（旧版客户端下线后可删）。
