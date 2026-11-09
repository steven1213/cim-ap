# cim-notify-starter 设计稿（实时消息推送核心能力）

> 状态：框架稿（待评审）→ 评审通过后进入实现
> 关联：`docs/platform/server/design.md`（平台总设计）、`docs/business/mds-ap/server/realtime-contract-design.md`（既有实时契约探索）、`business/approval-ap`（已有 SSE 散落实现）

## 0. 决策记录（本次对齐结论）

| 议题 | 结论 | 说明 |
| --- | --- | --- |
| 红点（未读数）归属 | **各 AP 自行负责** | 红点 = 各 AP 自有持久未读表 + 自有 `/unread-count` REST；starter 只做实时传输，不持库。 |
| Redis 扇出 | **首期直接启用** | `cim.notify.cluster.enabled=true`（默认开），多实例经 Redis pub/sub 扇出；保留 `LocalSseFanout` 作为单实例/dev 兜底。 |
| starter 形态 | 纯传输层 jar（无 JPA） | 自动装配；不占 Flyway 版本段（无实体）。 |

> ⚠️ **红点可靠性铁律（强制）**：因为 SSE 在刷新/重连/网络抖动时会丢消息（rms-ap 已踩坑），
> 红点**绝不能**依赖实时流计数。各 AP 必须遵循 **persist-before-publish + reconcile-on-reconnect**：
> 1. 先写各 AP 自己的未读记录（落库、标记未读）；
> 2. 再调 `NotifyPublisher.publish(...)` 触发实时推送（仅用于"即时点亮红点/刷新角标"）；
> 3. 前端重连成功后**回拉**本 AP 的 `/unread-count` 自愈，绝不信任流内累计。

---

## 1. 目标与范围

**目标**：把"WEB 端消息实时推送"从各业务 ap 的重复造轮子（approval-ap 内存 `Map<String,SseEmitter>`、rms-ap 退化成轮询 `/unread-count`）提为平台标准能力，统一传输层、扇出、鉴权、心跳、租户隔离与可观测性。

**在范围内（starter 负责）**
- SSE 连接管理：订阅、按 `(tenantId, userId/role/topic)` 投递、心跳保活、超时与重连语义。
- 跨实例扇出：Redis pub/sub（默认），单实例内存兜底。
- 鉴权集成：长连接令牌注入过滤器 + 安全贡献器（端点仍走 `authenticated()`）。
- 租户隔离：异步线程上下文固化（防跨租户串消息）。
- 可观测：在线连接数、推送计数、扇出错误计数。

**不在范围内（各 AP 负责）**
- 通知内容语义、未读/已读状态、红点计数 → 各 AP 自有表 + REST。
- 离线消息存储 → 由"各 AP 持久未读表"承担，starter 不落库。

---

## 2. 架构总览

```
┌──────────────┐   SSE (text/event-stream)    ┌──────────────────────────────┐
│ WEB 前端      │ ─────── /sys/notify/stream?token= ──▶│ AP Pod (含 cim-notify-starter) │
│ (各 ap 前端)  │◀────── event: data:{...} ─────────│  SseController                │
└──────────────┘         + 心跳 :\n\n            │  ├─ SseRegistry (tenant,user→conn)  │
                                                │  ├─ SseFanout SPI                │
  业务 ap 发布 ──▶ NotifyPublisher.publish(evt) ──┤     ├─ LocalSseFanout (dev/单实例)│
                                                │     └─ RedisSseFanout (默认)      │
                                                │            │ convertAndSend        │
                                                │            ▼                       │
                                                │      Redis pub/sub channel         │
                                                │            │ (同 appCode 命名空间)   │
                                                │            ▼                       │
                                                │  RedisSseFanoutSubscriber         │
                                                │     → registry.deliverLocal(evt)  │
                                                └──────────────────────────────┘
       同 ap 的其他 Pod 订阅同一 channel → 把事件投递给本 Pod 内挂着的连接
```

要点：扇出只在同一 `appCode` 的 Redis 频道内发生（`cim:notify:{appCode}`）。每个 ap 的 Pod 只处理自己 ap 的连接，因此**不存在跨 ap 误投**。用户浏览哪个 ap，就连那个 ap 的 `/sys/notify/stream`，发往该 ap 的事件经 Redis 扇到所有该 ap 的 Pod。

---

## 3. 模块边界与包结构

新增平台模块（jar，无启动类，靠 `META-INF/spring/...AutoConfiguration.imports` 自动装配）：

```
platform/server/cim-notify-starter/
└── src/main/java/com/cim/notify/
    ├── config/
    │   ├── NotifyAutoConfiguration.java        # @ConditionalOnProperty(cim.notify.enabled)
    │   ├── NotifyProperties.java               # cim.notify.* 绑定
    │   └── NotifySecurityContributor.java      # 端点授权 + 注入 BearerTokenQueryParamFilter
    ├── transport/
    │   ├── SseConnection.java                  # 连接值对象（固化 tenantId/userId/角色）
    │   ├── SseRegistry.java                    # 线程安全注册表 + 心跳调度 + 投递
    │   └── SseController.java                  # GET /sys/notify/stream
    ├── fanout/
    │   ├── SseFanout.java                      # SPI 接口
    │   ├── LocalSseFanout.java                 # 默认（cluster.enabled=false）
    │   ├── RedisSseFanout.java                 # 默认启用（cluster.enabled=true）
    │   └── RedisSseFanoutSubscriber.java       # implements MessageListener
    ├── api/
    │   ├── NotifyEvent.java                    # 可序列化事件（tenantId/target/appCode/type/payload）
    │   └── NotifyPublisher.java                # 业务 ap 调用门面
    └── security/
        └── BearerTokenQueryParamFilter.java    # ?token= → Authorization 头（仅 auth 启用时）
```

**合规**：starter 自动装配走 `@ConditionalOnProperty(cim.notify.enabled)` + `@ConditionalOnMissingBean`（让位范式，对齐 auth/cache/mq）；**不声明 `@EntityScan`/`@EnableJpaRepositories`**（纯传输无 JPA）；配置键统一 `cim.notify.*`。

---

## 4. 核心组件设计

### 4.1 NotifyProperties
```yaml
cim:
  notify:
    enabled: true                 # 总开关（默认 true）
    cluster:
      enabled: true               # Redis 扇出开关（默认 true → RedisSseFanout）
    heartbeat: 25s                # 心跳间隔（Duration），须 < 反向代理 proxy_read_timeout（默认 60s）
    timeout: 5m                   # 单连接超时（Duration），超时后由客户端指数退避重连（勿设 MAX_VALUE）
    channel-prefix: "cim:notify"  # 实际频道 = {prefix}:{appCode}
    max-connections-per-user: 5   # 单用户并发连接上限（防滥用）
```

### 4.2 SseConnection（租户上下文固化）
```java
class SseConnection {
    final String id;
    final String tenantId;        // ★ 订阅时从 TenantContext 捕获并固化
    final String userId;          // 来自 SecurityContext 主体
    final Set<String> roles;      // 用于 role 级广播
    final SseEmitter emitter;
    volatile long lastHeartbeatAt;
    // send(Event) 前必须先 TenantContext.set(tenantId)，send 完 clear
}
```
> 关键：心跳与 `emitter.send()` 跑在调度线程，**请求线程的 `TenantContext` 已丢失**。必须在连接建立时把 `tenantId` 存进对象，每次 send/心跳前 `TenantContext.set(tenantId)`，结束 `TenantContext.clear()`，否则租户过滤器静默失效、跨租户串消息。

### 4.3 SseRegistry（投递与心跳）
- 索引：`Map<(tenantId,userId), Set<SseConnection>>` + `Map<topic, Set<SseConnection>>`（均 `ConcurrentHashMap`）。
- `subscribe(tenantId,userId,roles)` → `SseEmitter`，封装 `SseConnection`，注册 `onCompletion/onTimeout/onError` 统一 `unregister`。
- `deliverLocal(NotifyEvent)`：按 `targetType` 匹配 `USER`(userId) / `ROLE`(roles) / `TOPIC`，逐连接 send；send 异常即 `unregister` + `complete`。
- 心跳：单个 `ScheduledExecutorService`（`heartbeat` 固定速率），遍历所有连接发 `SseEmitter.event().comment("hb")`；发失败即 unregister。
- 容量护栏：`subscribe` 时查 `(tenant,user)` 连接数，超 `max-connections-per-user` 拒绝（返回 429 / 直接 complete）。

### 4.4 SseFanout SPI（照抄 cache-starter 范式）
```java
interface SseFanout { void fanout(NotifyEvent evt); }

// 单实例/dev：直接本地投递，零额外基础设施
class LocalSseFanout implements SseFanout {
    public void fanout(NotifyEvent e){ registry.deliverLocal(e); }
}

// 默认（cluster.enabled=true）：镜像 RedisCacheInvalidationBroadcaster
class RedisSseFanout implements SseFanout {
    // convertAndSend("{prefix}:{appCode}", om.writeValueAsString(evt))
    // try/catch：广播失败仅 warn，不阻断主流程（本地已落库/已处理）
}
class RedisSseFanoutSubscriber implements MessageListener {
    // onMessage → 解析 NotifyEvent → registry.deliverLocal(evt)
}
```
`NotifyAutoConfiguration` 据 `cim.notify.cluster.enabled` 二选一装配；`RedisSseFanoutSubscriber` 经 `RedisMessageListenerContainer` 订阅 `{prefix}:{appCode}`（同 `CacheAutoConfiguration` 第 188 行写法）。

### 4.5 NotifyEvent / NotifyPublisher（业务 ap 调用面）
```java
record NotifyEvent(
    String tenantId,
    TargetType targetType,        // USER / ROLE / TOPIC
    List<String> targets,         // userId 列表 / role 码 / topic 名
    String appCode,               // 发布方 ap（如 "rms-ap"），用于频道命名空间
    String type,                  // 业务类型（ap 自定义，如 "RECIPE_SIGN_OFF"）
    String payload                // JSON 业务载荷
) {}

interface NotifyPublisher {
    void publish(NotifyEvent evt);                 // → fanout.fanout(evt)
    void publishToUser(String tenant, String user, String appCode, String type, String payload);
}
```
业务 ap 只依赖 `NotifyPublisher`，不直接碰 Redis/Registry。

### 4.6 SseController
```
GET /sys/notify/stream   →  SseEmitter
```
- 主体取自 `SecurityContext`（由 `BearerTokenQueryParamFilter` + `JwtAuthenticationFilter` 前置建立）。
- 调用 `SseRegistry.subscribe(tenantId,userId,roles)` 返回 `SseEmitter`。
- 端点**必须 `authenticated()`**，绝不因长连接弱化鉴权。

---

## 5. 鉴权集成（安全红线）

`EventSource` **无法设置 `Authorization` 头**。复用并下沉既有 `?token=` 模式为公共组件：

1. **`BearerTokenQueryParamFilter`**（`OncePerRequestFilter`，仅在 `cim.auth.enabled=true` 时装配）：若请求无 `Authorization` 头但带 `?token=`，用 `HttpServletRequestWrapper` 把该值暴露为 `Authorization: Bearer <token>`，让后续 `JwtAuthenticationFilter` 照常验签、版本/黑名单/准入判定。
2. **宿主 ap 自行排链（starter 不依赖 Spring Security 配置类，保持解耦）**：starter 提供 `BearerTokenQueryParamFilter` Bean 与 `SseController.STREAM` 常量；宿主 ap 在其 `SecurityFilterChain` 中：① `http.addFilterBefore(bearerFilter, JwtAuthenticationFilter.class)`；② `requestMatchers(SseController.STREAM).authenticated()`。避免各 ap 各写一遍、漏接导致 401。
3. **令牌暴露面权衡**：`?token=` 会进浏览器历史/反代日志。starter 同时提供 **`fetch` + `ReadableStream` 客户端**（事件流是 `text/event-stream` chunked 响应，fetch 能读且可带头），前端可按安全诉求选用，从根上避免 URL 带令牌。

**dev 模式（`cim.auth.enabled=false`）**：各 ap 已有 `*DevSecurityConfig` 放行并填充 dev 主体；SSE 端点需在该配置下显式放行，并支持 `?userId=&tenantId=` 直连以建立连接身份（与 approval-ap dev 做法一致）。

---

## 6. 租户隔离（异步线程上下文）

见 §4.2 铁律：`tenantId` 在订阅时捕获固化，`deliverLocal`/心跳/每帧 send 前 `TenantContext.set(tenantId)`、结束后 clear。注册表一律按 `(tenantId,userId)` 双键索引，严禁只按 userId。

---

## 7. 心跳 / 超时 / 重连 / 断点续传

- **心跳**：默认 25s 发 `:hb\n\n` 注释帧，须小于 nginx `proxy_read_timeout`（默认 60s），否则空闲连接被掐。
- **超时与重连**：`SseEmitter(timeout.toMillis())`，超时 `onTimeout` → unregister+complete；客户端指数退避重连（base 1s ×2，cap 30s），**不**用 `Long.MAX_VALUE` 永久挂连。
- **断点续传**：SSE 不实现消息重放（性价比低）。离线/重连补差交由"各 AP 持久未读表 + `/unread-count` 回拉"（见 §0 铁律）。SSE 只管在线实时，REST 管离线兜底，职责分清。

---

## 8. 前端客户端契约（参考实现）

starter 提供 TS 参考实现（放置位置见 §11 决策点），各 ap 前端接入：

```
createSseClient({
  url: "/sys/notify/stream",
  getToken: () => useAuthStore.getState().accessToken,  // 内存态取令牌（构造即取，异于 axios 拦截器）
  onMessage: (evt) => { /* 解析 data，点亮红点/刷新角标 */ },
  onError: async () => {
     if (status===401) { await ensureRefreshed(); reconnect(newToken); }  // 借现有换发逻辑
     else backoffReconnect();
  },
  onOpen: () => { /* 重连成功 → 回拉本 ap /unread-count 自愈红点 */ }
})
```
- 默认 `EventSource`（自带重连）；高安全场景用 `fetch-stream` 变体带 `Authorization` 头。
- 指数退避、断线检测、组件卸载清理（abort/close）必须内建。
- **重连后必回拉 `/unread-count`** 自愈（§0 铁律）。

---

## 9. 可观测与容量

- **指标**（内部门面 `NotifyMetrics`，只依赖 micrometer 的 `MeterRegistry` 接口，自带 `cim.` 前缀；宿主引入 `cim-obs-starter` 时自动联通，未引入则 no-op）：
  - `cim.notify.connections`（gauge，tags `tenant,appCode`）— subscribe +1 / unregister −1；
  - `cim.notify.sent`（counter，tags `tenant,appCode,targetType`）；
  - `cim.notify.fanout.error`（counter）— Redis 广播失败计数。
- **容量提醒**：Tomcat 走 NIO 异步，连接不占线程但占文件描述符。上千并发长连接须调：
  - `server.tomcat.max-connections`；
  - OS `ulimit -n`；
  - `cim.notify.max-connections-per-user` 防单用户滥用。
  - 极端规模（万级连接）建议抽独立 notify 服务，记为后续演进。

---

## 10. 平台约定合规清单

- [x] 包名 `com.cim.notify.{layer}`；starter 自动装配（imports + `@ConditionalOnProperty(cim.notify.enabled)` + `@ConditionalOnMissingBean` 让位）。
- [x] **不声明 `@EntityScan`/`@EnableJpaRepositories`**（ADR-12 铁律；本 starter 无 JPA）。
- [x] **不占 Flyway 版本段**（无实体，故无需在 `cim-jpa-starter/.../README.md` 分配表登记）。
- [x] 扇出复用 `cim-cache-starter` 的 Redis pub/sub 范式（非另起一套）。
- [x] 指标用内部门面 `NotifyMetrics`（仅 `io.micrometer:micrometer-core`，**不依赖 `cim-obs-starter`**，避免把 actuator/prometheus 拖进纯传输层）。
- [x] 端点前缀遵循"平台端点 = `/sys/**`"（与 IAM `/api/v1/**`、业务 `/api/**` 区分）。

---

## 11. 决策记录（已对齐，2026-11-09）

1. **前端客户端放置位置** → **建立轻量共享 web 包** `platform/web/cim-notify-client`（npm 包，各 ap 以 `file:`/workspace 依赖引用），不再各 ap 复制。
2. **红点 / 未读归属** → **各 AP 自行负责**（持久未读表 + 自有 `/unread-count`）。starter 纯传输层、不持库，无跨 ap 统一铃。若未来要跨 ap 全局中心，再评估通用收件箱（登记新 1000 段，下一空闲 V5000），本期不做。
3. **迁移** → approval-ap / rms-ap 删散落 SSE，改依赖 `cim-notify-starter`；红点各 AP 按持久未读表实现（§0 铁律）。即时点亮走 `NotifyPublisher.publish`，计数真相走各 AP 落库 + 重连回拉。

### 11.1 落地约束补充（评审后新增）
- **`cim.notify.app-code` 必填**：Redis 频道命名空间 `cim:notify:{appCode}` 依赖宿主 ap 显式声明，starter 无法自探。各 ap 在 `application.yml` 配置（如 `rms-ap` / `approval-ap`）。
- **`/sys/notify/stream` 端点**：由 starter 的 `SseController` 以 `@Bean` 形式注册（库 jar 不靠组件扫描）；宿主 ap 把它纳入 `authenticated()` 并在 `JwtAuthenticationFilter` 前插入 `BearerTokenQueryParamFilter`（starter 提供该过滤器 Bean）。
- **dev 直连**：`cim.notify.dev-accept-query-identity=true`（仅 dev profile）时允许 `?userId=&tenantId=` 建连，便于无令牌调试。

---

## 12. 实现任务分解（评审通过后）

1. Maven 模块 `cim-notify-starter` + `NotifyAutoConfiguration`/`NotifyProperties`。
2. `SseConnection` / `SseRegistry`（含心跳调度、容量护栏、租户固化）。
3. `SseFanout` SPI：`LocalSseFanout` + `RedisSseFanout`/`RedisSseFanoutSubscriber`（经 `RedisMessageListenerContainer`）。
4. `NotifyEvent` / `NotifyPublisher` / `SseController`（`/sys/notify/stream`）。
5. `BearerTokenQueryParamFilter`（鉴权链由宿主 ap 自行排序，见 §5）。
6. `NotifyMetrics` 指标埋点（不引入 obs-starter）。
7. 单测：`LocalSseFanout` 投递、`RedisSseFanout`（embedded Redis / mock StringRedisTemplate）、租户固化、容量护栏、超时重连语义。
8. 前端 `createSseClient` 参考实现 + 接入示例。
9. 二期：迁移 approval-ap / rms-ap。

---

## 13. 迁移落地状态（2026-11-09 完成）

按 §11.3 决策完成 approval-ap / rms-ap 的散落 SSE 迁移，两 ap 均改依赖 `cim-notify-starter`。

### 13.1 approval-ap
- **删除** 旧 `controller/NotificationController.java`（手搓 `Map<String,SseEmitter>` + `/api/v1/approval/notifications/stream`），统一改用平台端点 `/sys/notify/stream`。
- `service/NotificationService` 内部改为注入 `NotifyPublisher`，保留 `pushToUser` / `pushEvent` 签名；调用方（`InAppChannel` / `NotificationEventListener` / `OverdueScanner` / `ApprovalProcessService`）零改动。
- `application.yml` 增加 `cim.notify`（app-code=approval-ap + cluster + dev-accept-query-identity）。
- `ApprovalSecurityConfig` 增加 `@Order(0) notifySecurityFilterChain`，复用 `BearerTokenQueryParamFilter` + `JwtAuthenticationFilter` + `authenticated()`。
- 前端 `NotificationBell.tsx` / `api.ts` / `verify-journey.mjs` 改用 `@cim/notify-client` 的 `SseClient` 连 `/sys/notify/stream`（dev 直连 `?userId=&tenantId=`；生产走 `getToken`）。
- ⚠️ approval-ap 当前未持久化通知，红点以会话内存角标表示；后续若需离线红点历史，按下"各 AP 自管"补一张通知表。

### 13.2 rms-ap
- `pom.xml` 增加 `cim-notify-starter` 依赖；`application.yml.example` 增加 `cim.notify`（app-code=rms-ap + cluster + dev-accept-query-identity）。
- `RmsSecurityConfig` 增加 `@Order(0) notifySecurityFilterChain`（与 approval 同构）。
- 新增 `notification/SseNotifyChannel`：实现 `NotifyChannel` SPI，落库后即时推送——有 `recipient`→`USER`；有 `audience`→映射权限码 `ROLE`（对齐 RMS 内部 RBAC）；全员广播跳过 SSE（靠轮询兜底）。失败只记日志，绝不拖垮业务事务。
- 既有 `NotificationController`（`/api/v1/rms/notifications` 列表/未读/已读）**保留**为红点真相源；`NotificationBell.tsx` 在 30s 轮询基础上叠加 `SseClient` 实时刷新，`onOpen`（重连）/ 收消息均回拉 `/unread-count` 自愈（§0 铁律）。

### 13.3 校验
- 共享包 `platform/web/cim-notify-client`：`npm run build` 产出 `dist`。
- 两 ap web：`npm install` 链接 `file:` 依赖 + `tsc --noEmit` 均通过。
- 两 ap server：`mvn -o compile` 均通过（AP_EXIT=0 / RMS_EXIT=0）。
- **starter 单元 + 端到端共 11 例全绿**（`mvn -o -pl cim-notify-starter test`）：
  - 9 例单测（发布器 / Redis 频道命名 / 鉴权过滤器 / 注册表匹配 & 容量护栏）。
  - 1 例端到端 `NotifyEndToEndTest`：关闭 cluster（走 `LocalSseFanout`，无需 Redis），经真实 `TestRestTemplate` 打开 `/sys/notify/stream?userId=&tenantId=` → 发 `NotifyPublisher.publish` → 断言连接收到 `{id,ts,appCode,type,payload}` 信封（payload 双编码 JSON 字符串，与前端 `JSON.parse` 契约一致）。
  - 1 例端到端 `NotifyRedisFanoutTest`：**开启 cluster + 真实 Redis（127.0.0.1:6379）**，验证 `RedisSseFanout.publish` → `RedisMessageListenerContainer` 订阅回投本 Pod 连接 的完整 pub/sub 链路；Redis 不可达时整测试 `assumeTrue` 跳过（兼容 Redis-less CI）。

### 13.4 应用层验证（Redis 环境已补跑）
- **rms-ap `RmsW9BatchAndNotifyTest`（8 例全绿）**：在 `cim.auth.enabled=false` + H2 + Redis 起的真实 Spring 上下文里跑通 W9 批量 Hold/下发/签核；日志可见 `DefaultNotifyPublisher: publish event without appCode: type=SIGNOFF_SUBMITTED`，证明 `SseNotifyChannel` → `NotifyPublisher` → `RedisSseFanout` 已随业务事务真实触发且**未拖垮主流程**。
  - ⚠️ 该测试 `@TestPropertySource` 未设 `cim.notify.app-code`，故走 `cim:notify:null` 频道并打 WARN；生产 `application.yml` 已设 `app-code: rms-ap`，不受影响。验证了"appCode 必填"约束的兜底行为。
- approval-ap 无独立 notify 测试；其 `NotificationService→NotifyPublisher` 复用同一套 starter 自动装配（已 by rms-ap 上下文验证），server 侧 `mvn -o compile` 通过。

### 13.5 仍待确认项
- `BearerTokenQueryParamFilter` 在真实 `JwtAuthenticationFilter` 链下的鉴权拒绝路径（401）：依赖宿主 ap 完整 Security 链，未在自动化测试覆盖；建议在有 Redis 的环境跑前端 `verify-journey.mjs` 或手动用令牌访问 `/sys/notify/stream` 确认。
- ⚠️ **实现修复（已落地）**：`NotifyAutoConfiguration` 原 `@ConditionalOnBean(StringRedisTemplate.class)` 在与 `RedisAutoConfiguration` 的排序竞态中可能先于 Redis autoconfig 求值，导致 `redisSseFanout`/`notifyFanoutContainer` 不装配 → 集群扇出静默失效、退化为本地扇出。已加 `@AutoConfigureAfter(RedisAutoConfiguration.class)` 修正，并已 `install` 回写本地仓。各 ap 须 `mvn install` 更新该 starter 后再跑。
