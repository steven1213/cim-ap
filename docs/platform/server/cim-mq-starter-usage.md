# cim-mq-starter 使用指南

> 面向**业务 ap（如 `mds-ap` / `mes-ap` / `iam-ap` 的 `server` 工程）接入方**：本文讲清楚 starter 作为 jar 包如何**打包、引入、配置、写发送/消费代码、切换 MQ、扩展新 MQ、运维排查**。
> 架构设计、分层与"扩展点在哪里"见 [`design.md` §2.6](design.md#26-cim-mq-starter消息与发件箱)；三难题的系统性解法见 [`design.md` §2.6.2](design.md#262-三难题的系统性解法与-121316-协同)。本文是**使用手册**，不重复架构论证。

---

## 0. 产物形态与坐标

- **产物**：普通 library jar（**不是** Spring Boot 可执行 fat jar）。业务 ap 的 `cim-bootstrap` 才是可执行包；本 starter 作为依赖被它引入。
- **坐标**：

  ```text
  groupId   = com.cim
  artifactId = cim-mq-starter
  version    = 0.0.1-SNAPSHOT
  packaging  = jar
  ```

- **核心承诺**：业务只依赖 `MqTemplate` 与 `@MqListener` + `IntegrationEvent`。**切换 MQ（Kafka/Pulsar/RabbitMQ）不改业务代码**，只改配置与一个 Maven 依赖。

---

## 1. 打包（产出 jar）

在 `platform/server/` 下执行（父 POM 即 `platform/server/pom.xml`）：

```bash
# 1) 安装父 POM（cim-ap-parent）
mvn -N install

# 2) 安装上游依赖（cim-spring-support 等）
mvn -pl cim-spring-support -am install

# 3) 打包并安装 cim-mq-starter 到本地仓库
mvn -pl cim-mq-starter -am install
```

- 产物：`platform/server/cim-mq-starter/target/cim-mq-starter-0.0.1-SNAPSHOT.jar`
- 同时安装到本地仓库：`~/.m2/repository/com/cim/cim-mq-starter/0.0.1-SNAPSHOT/`
- 发布到私有 Nexus/Artifactory 时，把上述 jar + pom 上传即可（CI 通常用 `mvn deploy`）。

> ⚠️ **关键事实**：`cim-mq-starter` 内部把 `kafka-clients` / `pulsar-client` / `amqp-client` 三个厂商客户端都声明为 `<optional>true</optional>`。因此 **starter jar 不会包含、也不会向业务 ap 传递任何 MQ 客户端**。业务 ap 必须按自己选用的 `cim.mq.type` **显式引入**对应客户端（见 §2）。这是"易扩展、互不传染"的设计落点，也是最常见的接入踩坑点。

---

## 2. 业务 ap 引入依赖

### 2.1 公共依赖（必引）

```xml
<dependency>
    <groupId>com.cim</groupId>
    <artifactId>cim-mq-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

`cim-spring-support` 会作为编译依赖自动传递，无需手动引入。

### 2.2 按使用的 MQ 引入客户端（**必引其一，版本对齐 starter**）

`cim.mq.type` 取什么，就引哪个：

```xml
<!-- Kafka -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <!-- 版本由 cim-ap-parent BOM 收敛；若业务工程未继承该 BOM，请自行指定兼容版本 -->
</dependency>

<!-- RabbitMQ -->
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>5.21.0</version>
</dependency>

<!-- Pulsar -->
<dependency>
    <groupId>org.apache.pulsar</groupId>
    <artifactId>pulsar-client</artifactId>
    <version>3.3.1</version>
</dependency>
```

> 若业务工程未继承 `cim-ap-parent` BOM，请**显式写出版本**并与 starter 中声明的版本对齐（pulsar-client 3.3.1 / amqp-client 5.21.0 / kafka-clients 跟随父 BOM），避免版本冲突。

### 2.3 消费幂等用 Redis 去重（可选但推荐生产启用）

starter 内 `spring-boot-starter-data-redis` 也是 `<optional>true</optional>`，**不会传递**。若要用 Redis 做幂等去重（`cim.mq.idempotent.store=redis`），业务 ap 需自引：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

未引入时，框架自动降级为**内存去重**（单实例可用，集群/重启后失效）——仅适合开发或单实例部署。

---

## 3. 启用与配置（`application.yml`）

总开关默认 **关闭**，必须显式打开，并按所选 `type` 填连接信息。

### 3.1 通用骨架（三种 MQ 都一样的部分）

```yaml
cim:
  mq:
    enabled: true                 # 总开关，默认 false（务必打开）
    type: rabbit                  # kafka | pulsar | rabbit
    default-topic: cim-events     # 未显式指定 topic 时的兜底

    consumer:
      max-attempts: 3             # 消费最大重试次数，超出转死信
      concurrency: 1              # 单消费者并发线程数

    idempotent:
      enabled: true               # 消费幂等（去重）总开关
      store: redis                # redis | memory（无 Redis 时降级 memory）
      ttl-sec: 86400              # 去重键 TTL（秒），过期后允许重新处理（自愈）
      prefix: "mq:idem:"

    resilience:
      retry-max-attempts: 5
      retry-initial-backoff-ms: 500
      retry-factor: 2.0
      retry-max-backoff-ms: 30000
      cb-failure-threshold: 5     # 熔断器触发阈值
      cb-cooldown-ms: 30000

    outbox:
      enabled: true               # 发件箱：业务与消息同事务，保证"不丢"
      relay-enabled: true         # 中继定时轮询投递开关
      table: sys_outbox           # 发件箱表名
      batch-size: 100             # 每轮轮询批量大小
      interval-ms: 1000           # 轮询间隔
      init-schema: true           # 启动时自动 CREATE TABLE IF NOT EXISTS
```

### 3.2 差异段：按 `type` 二选一/三选一

```yaml
    # —— type: kafka ——
    kafka:
      bootstrap-servers: localhost:9092
      acks: all                   # 全副本确认（最高不丢保障）
      retries: 10
      enable-idempotence: true    # 生产端幂等（去重生产重发）
      extras: {}                  # 透传给 Kafka 的额外 producer/consumer 配置

    # —— type: rabbit ——
    rabbit:
      addresses: localhost:5672
      username: guest
      password: guest
      virtual-host: /
      exchange: cim-mq-exchange   # 统一的 topic 类型交换机名

    # —— type: pulsar ——
    pulsar:
      service-url: pulsar://localhost:6650
      enable-batching: true       # 是否批量发送（按业务吞吐权衡）
      extras: {}
```

> 只有与当前 `type` 匹配的 provider 会读取对应配置块；其余块可留空或不写。

---

## 4. 发送集成事件

有两种发送路径，按"是否要求业务与消息严格同事务、绝对不丢"选择。

### 4.1 发件箱事务路径（**推荐用于关键业务事件**）

业务在 `@Transactional` 内调用 `OutboxIntegrationEventPublisher.publish(event)`，事件信封被写入 `sys_outbox` 表（与你的业务表**同一本地事务**）；事务提交后，`OutboxRelay` 异步轮询 `sys_outbox` 并投递到 MQ。**这是"业务成功 ⇔ 消息存在"、从而"不丢消息"的关键**。

```java
@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final OutboxIntegrationEventPublisher outbox;   // 框架自动装配的 bean

    public OrderService(OrderRepository orderRepo,
                        OutboxIntegrationEventPublisher outbox) {
        this.orderRepo = orderRepo;
        this.outbox = outbox;
    }

    @Transactional
    public void create(Order order) {
        orderRepo.save(order);                       // ① 业务落库

        IntegrationEvent event = IntegrationEvent.builder()
                .topic("order-created")              // 订阅方按此 topic 收
                .type("OrderCreated")                // 事件类型（语义标识）
                .aggregateType("Order")
                .aggregateId(order.getId())          // 聚合根 ID（路由 + 去重键）
                .version(order.getVersion())         // 乐观版本号（状态类事件去重）
                .payloadType(OrderCreatedPayload.class.getName())
                .payload(JSON.toJson(order.toPayload()))  // JSON 字符串
                .build();

        outbox.publish(event);                       // ② 与 ① 同事务写入 sys_outbox
    }
}
```

- `publish` **必须在 `@Transactional` 内调用**，否则抛 `IllegalStateException`。
- 之后无需手动发 MQ，中继自动完成投递；投递成功后记录从 `sys_outbox` 清除。

### 4.2 直接发送 / 有序发送

非事务关键、或不需要"业务-消息同事务"的场景，直接注入 `MqTemplate`：

```java
@Service
public class NotificationService {

    private final MqTemplate mq;   // 框架自动装配的 bean

    public NotificationService(MqTemplate mq) { this.mq = mq; }

    public void notify(IntegrationEvent event) {
        mq.send(event);                       // 无序投递，默认以 aggregateId 路由
    }

    public void notifyOrdered(IntegrationEvent event, String orderId) {
        mq.sendOrdered(event, orderId);       // 保证同 orderId 内顺序（按 key 分区/队列）
    }
}
```

- `send(event)`：以 `event.aggregateId()` 作为路由键（同聚合落到同分区/队列，利于顺序与局部性）。
- `sendOrdered(event, key)`：以你传入的 `key`（通常是 `aggregateId`）保证该 key 内顺序。
- 直接发送仍是 **at-least-once** + 消费端幂等收口，不丢；区别是它**不保证与你的业务表同事务**。

### 4.3 事件信封字段说明（`IntegrationEvent`）

| 字段 | 含义 | 备注 |
| --- | --- | --- |
| `id` | 事件唯一 ID | 缺省自动生成 UUID；即幂等去重键（`eventId`） |
| `topic` | 订阅主题 | 订阅方 `@MqListener(topic=...)` 对应 |
| `type` | 事件类型语义标识 | 如 `OrderCreated` |
| `aggregateType` / `aggregateId` | 聚合类型 / 聚合根 ID | 路由键 + 去重维度 |
| `version` | 聚合版本号 | 状态类事件用 `(aggregateId, version)` 乐观去重 |
| `payloadType` | 负载目标类型全限定名 | 消费端据此反序列化 `payload` |
| `payload` | 业务负载 | **JSON 字符串** |
| `headers` | 透传头 | `traceId` / `tenantId` / `userId` 全链路透传（框架自动注入 traceId） |
| `occurredAt` | 发生时间 | `Instant` |

构建一律用 `IntegrationEvent.builder()`，字段均有 fluent setter，最后 `.build()`。

---

## 5. 消费集成事件

用 `@MqListener` 标注在 Spring Bean 的方法上即可，**容器刷新后框架自动注册**，无需手动 `registerListener`。框架会自动在外层包裹 **幂等过滤 + 重试 + 死信**。

```java
@Component
public class InventoryListener {

    @MqListener(topic = "order-created", group = "inventory",
                concurrency = 4, maxAttempts = 5)
    public void on(IntegrationEvent event, Acknowledgment ack) {
        try {
            handle(event);          // 你的业务处理
            ack.acknowledge();      // ✅ 处理成功才 ack → broker 不再重投
        } catch (Exception ex) {
            ack.negativeAcknowledge(); // ❌ 失败 → 触发框架重试，超次数转死信
        }
    }

    private void handle(IntegrationEvent event) {
        OrderCreatedPayload p = JSON.fromJson(
                event.payload(), OrderCreatedPayload.class);
        // ... 扣减库存等业务
    }
}
```

### 5.1 注解与签名约束

- `@MqListener(topic=, group=, concurrency=, maxAttempts=)`：`group` 相同则负载均衡，不同则广播。
- 方法签名**仅支持两种**（不符抛 `IllegalStateException`）：
  - `(IntegrationEvent event)`
  - `(IntegrationEvent event, Acknowledgment ack)` —— 需要手动 ack 时用。
- **手动 ack 语义**：成功 `ack.acknowledge()`，失败 `ack.negativeAcknowledge()`。配合 at-least-once，处理成功前不确认即避免"消费端丢消息"。

### 5.2 重试 / 死信（框架自动）

- 消费抛异常 → 按 `resilience.retry-*` 指数退避重试，最多 `consumer.max-attempts` 次。
- 仍失败 → 转发**死信队列**（由框架自动声明，命名随 provider 约定），业务可另行监控/人工处理。
- 去重：同 `event.id` 重复投递会被 `IdempotencyStore` 命中跳过；状态类事件用 `(aggregateId, version)` 去重。

---

## 6. 三难题保障速查

| 问题 | 框架机制 | 各 provider 落点 |
| --- | --- | --- |
| **丢失** | 发件箱同事务 + 中继轮询 + 同步等 broker 确认 + 消费手动 ack + 重试 | Kafka `acks=all`；Pulsar `durable`；Rabbit `persistent` + publisher confirm |
| **重复** | at-least-once + 消费幂等（`eventId` / `(aggregateId, version)` 去重）+ 生产端幂等 | Kafka `enable.idempotence`；Rabbit/Pulsar 靠 outbox + 消费幂等收口 |
| **顺序** | `sendOrdered(event, key)` 按 key 路由 | Kafka 按 `aggregateId` 分区；Pulsar 按 key 路由；Rabbit 一致哈希 exchange 同 key 同队列 |

**ProviderFeatures 能力声明（实测，驱动默认值与启动期校验）**：

| Provider | 按 key 有序 | 生产端幂等 | 原生死信 |
| --- | --- | --- | --- |
| Kafka | ✅ | ✅ | ✅ |
| Pulsar | ✅ | ❌ | ✅ |
| RabbitMQ | ❌（一致哈希兜底） | ❌ | ✅ |

> 说明：RabbitMQ 的有序性不在 `ProviderFeatures` 中标记为真（其原生模型为队列级），框架通过 consistent-hash exchange 把同 key 落到同队列、单队列内保序兜底。**若你严格要求"按 aggregateId 顺序"，优先选 Kafka / Pulsar**，或确认 Rabbit 路由满足你的顺序 SLA。

---

## 7. 切换 MQ（业务代码零改动）

因为业务只依赖 `MqTemplate` / `@MqListener` / `IntegrationEvent`，切换只需：

1. 改 `cim.mq.type`（与连接配置块）；
2. 业务 ap 的 `pom.xml` 把旧客户端依赖换成新客户端依赖（§2.2）；
3. 重启。

业务发送/消费代码、事件定义**完全不动**。

---

## 8. 扩展新 MQ（实现 `MqProvider` SPI）

新增任意 MQ（RocketMQ / ActiveMQ / Redis Streams / AWS SQS …）**核心代码零改动**，只需：

1. 新建 `com.cim.mq.provider.<x>/`，实现 `MqProvider`（含 `MqProducer` / `MqConsumer` / `MqAdmin` 三个适配类），把该 MQ 的概念（topic/partition/queue/exchange、ack 模式、有序语义）翻译为 `core` 的中立契约（`MqTemplate` 已定义）。
2. 在 `META-INF/services/com.cim.mq.spi.MqProvider` 注册（ServiceLoader 自动发现），或在 `MqProviderFactory` 注册表登记；`cim.mq.type=x` 即被选中。
3. 在 `ProviderFeatures` 如实声明该 MQ 的真实能力（有序 / 生产幂等 / 死信 …），框架据其在启动期做能力校验。
4. **核心层（`core` / `outbox` / `idempotent` / `resilience`）永不 import 任何厂商 SDK**，因此新增 MQ 不会动到其它 provider 的代码。

---

## 9. 运维与排查要点

- **发件箱表**：默认 `sys_outbox`（`outbox.table` 可改）。`outbox.init-schema=true` 时框架启动时自动建表（`CREATE TABLE IF NOT EXISTS`）。生产环境若由 Flyway 管理 DDL，可设 `init-schema=false` 并自行建表（字段见 `OutboxRecord`）。
- **中继**：`outbox.relay-enabled=true` 时由单线程 `Scheduler` 每 `interval-ms` 轮询一次，批量 `batch-size` 投递后删除。监控该表堆积即可发现投递瓶颈。
- **幂等 TTL**：`idempotent.ttl-sec` 控制去重键存活；过期后允许重复处理（自愈），状态类事件配合 `version` 仍可去重。
- **死信**：消费超限转死信队列，建议对死信做监控告警与人工/补偿处理。
- **Redis 降级**：未引入 `spring-boot-starter-data-redis` 时 `store=redis` 会自动降级为内存去重，日志可见；生产集群务必引入 Redis。

---

## 10. 常见坑（FAQ）

1. **启动后无消息收发 / 报类找不到** → 99% 是忘了在业务 ap 引入对应 MQ 客户端（§2.2）。starter 的客户端依赖是 optional，不会传递。
2. **`cim.mq.enabled` 没设或设 false** → 自动装配不生效，所有 bean 不存在，注入 `MqTemplate` 报 `NoSuchBeanDefinitionException`。务必 `enabled: true`。
3. **消费幂等没生效 / 重复处理** → 检查是否引入了 Redis（`store=redis` 时）；未引入则降级内存，集群下各实例独立去重导致仍重复。
4. **`@MqListener` 方法不生效** → 方法签名必须是 `(IntegrationEvent)` 或 `(IntegrationEvent, Acknowledgment)`；类须是 Spring Bean（`@Component` 等）。
5. **`OutboxIntegrationEventPublisher.publish` 抛 IllegalStateException** → 没在 `@Transactional` 内调用，或事务未被 Spring 管理（自调用、非 public、缺 `@Transactional`）。
6. **切换 MQ 后报错** → 确认业务 ap pom 已替换客户端依赖，且 `cim.mq.type` 与配置块一致。

---

## 11. 真集成测试（连 Docker broker）

`src/test/java/com/cim/mq/` 下有两套**连真实 broker** 的集成测试，验证 design.md §2.6 的「不丢 / 不重」两大保证：

| 测试类 | 连的 broker | 连接参数 |
|---|---|---|
| `RabbitMqIntegrationTest` | Docker RabbitMQ | `localhost:5672`，`admin/admin123`，vhost `/` |
| `PulsarMqIntegrationTest` | Docker Pulsar standalone | `pulsar://localhost:6650` |

两套测试均复用 `demo/` 下的 `OrderService`/`InventoryListener`/`OrderCreatedEvent`：下单在 `@Transactional` 内写业务表 + `sys_outbox` 同事务 → 中继轮询 → 真实 broker → 消费者，并用内存幂等去重验证「同一 eventId 重复投递仅处理一次」。

**运行方式**（`mvn` 见前文 `-pl cim-mq-starter`）：

```bash
# 只跑 RabbitMQ（需本机 RabbitMQ 在跑）
-Dtest=RabbitMqIntegrationTest
# 只跑 Pulsar（需本机 Pulsar 在跑）
-Dtest=PulsarMqIntegrationTest
# 全量（两个 broker 都要在跑，否则对应用例会因连不上收 0 条而失败）
# 不带 -Dtest 即全量
```

**隔离要点**：两套测试各自使用**独立的 H2 内存库名**（`cimrabbit` / `cimpulsar`，均带 `DB_CLOSE_DELAY=-1`）。若共用同名库，同 JVM 内前一个测试的 `sys_outbox` 表与残留行会污染后一个测试的 outbox 中继，导致收不到消息（典型的「共享内存库污染集成测试」）。新增真集成测试时务必用独立库名。

> Kafka 真 broker 集成测试尚未补（本机未起 Kafka）。其 provider 已按相同范式实现（`KafkaMqConsumer` 已修批量提交丢消息、`KafkaMqProducer` 已加 `ensureTopic` 惰性建 topic），建议起 Kafka 容器后参照上面两套测试补一个等价真集成测试。

---

> 文档版本：对齐 `cim-mq-starter` `0.0.1-SNAPSHOT` 实现（自动装配 `MqAutoConfiguration`、配置 `CimMqProperties`）。如实现变更，请以 `platform/server/cim-mq-starter/src/main` 源码为最终事实来源。
