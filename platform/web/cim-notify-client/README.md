# @cim/notify-client

CIM 实时消息推送前端客户端（SSE），对应后端 `cim-notify-starter`。

轻量、零运行时依赖的共享 web 包；各业务 ap 以 `file:` 依赖引用，不再各抄一份 SSE 逻辑。

## 安装（各 ap 的 web 工程）

```jsonc
// business/<ap>/web/package.json
{
  "dependencies": {
    "@cim/notify-client": "file:../../../platform/web/cim-notify-client"
  }
}
```

> 路径相对关系：业务 ap 在 `business/<ap>/web`，本包在 `platform/web/cim-notify-client`，故用 `../../../`。

## 用法

### 1. React Hook（推荐）

```tsx
import { useSseStream } from '@cim/notify-client';

useSseStream({
  url: `${import.meta.env.VITE_API_BASE}/sys/notify/stream`,  // 注意：连接的是本 ap 自己的 /sys 端点
  getToken: () => useAuthStore.getState().accessToken,         // 内存态令牌（构造即取）
  mode: 'event-source',                                        // 默认；高安全场景用 'fetch' 避免 URL 带令牌
  onMessage: (m) => bumpBadge(m.data),                         // 即时点亮红点 / 刷新角标
  onOpen: () => refreshUnread(),                               // ★ 重连成功 → 回拉本 ap /unread-count 自愈红点
  onError: async (e) => {
    if (e.type === 'unauthorized') {
      await ensureRefreshed();                                  // 借现有令牌刷新逻辑换发；下次重连用新令牌
      // 客户端会自动指数退避重连
    }
  },
});
```

### 2. 直接使用 SseClient（非 React / 自定义）

```ts
import { SseClient } from '@cim/notify-client';

const client = new SseClient({ url, getToken, onMessage, onOpen, onError });
client.connect();   // 挂载/登录后
// client.close();  // 卸载/登出时
```

## 与后端约定

- 后端端点：`GET /sys/notify/stream`（平台端点前缀 `/sys/**`，由各 ap 暴露）。
- 推送信封：`{ id, ts, appCode, type, payload }`（JSON），`payload` 内为业务自定义 JSON。
- 红点真相在**各 ap 自有持久未读表**：SSE 仅做在线实时提醒；`onOpen`（重连）必须回拉 `/unread-count` 自愈，因为刷新/重连必然丢消息。

## 关键行为

- 指数退避重连（base 1s ×2，上限 30s），连接成功即重置。
- 死连接探测：超过 `heartbeatTimeoutMs`（默认 70s）未收到任何帧即强制重连（防反代静默掐断，服务端心跳 25s）。
- `fetch` 模式可读 HTTP 状态码，401 以 `onError({type:'unauthorized', status:401})` 上报；`event-source` 模式无法读状态码，仅在未成功打开时兜底按 unauthorized 处理。
