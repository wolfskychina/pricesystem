# 全链路追踪补全 — 实施步骤文档

> 本文档配套 [design.md](./design.md) 4.7 节「全链路追踪」设计，给出从当前状态到设计目标落地的完整实施步骤、文件清单与具体逻辑。

---

## 一、背景与目标

### 1.1 设计目标（design.md 4.7 节）

```
- 每个外部请求生成 traceId
- REST 调用通过 HTTP header 透传
- Kafka 通过消息 header 透传
- 日志 MDC 打印 traceId
- 审计可还原单笔订单全链路
```

### 1.2 当前实现状态

| 设计目标 | 实际状态 | 说明 |
|---|---|---|
| 每个外部请求生成 traceId | 部分实现 | 仅 gateway 生成 UUID，注入 `X-Trace-Id` 头 |
| REST 调用通过 HTTP header 透传 | 未实现 | 下游服务不读头；同步 REST 客户端不透传 |
| Kafka 通过消息 header 透传 | 未实现 | 所有 producer 用 `send(topic,key,value)`，无 header |
| 日志 MDC 打印 traceId | 未实现 | 全代码库无 `MDC.put`；无 logback.xml；yml 无 pattern |
| 审计可还原单笔订单全链路 | 未实现 | event_store/orders 表有 trace_id 列但写入 null |

### 1.3 链路断点示意

```
[Gateway] 生成 traceId=abc ✅
   │ (注入 X-Trace-Id 头)
   ▼
[OMS] 不读头 ❌ → order.traceId=null
   ├──REST──> [Risk] 不透传头 ❌
   ▼
[OMS] 发 TradeEvent(经Outbox) → event.traceId=null ❌
   ▼
[Outbox Relay] send(topic,key,payload) 无 header ❌
   ▼
[Execution] 消费，不提取 traceId 写 MDC ❌
   ├──REST──> [sim-exchange] 不透传头 ❌
   ▼ (Webhook 回调)
[Execution] 接收回调，无法关联 ❌
   ▼
[Execution] 发 HedgeFillEvent → event.traceId=null ❌
   ▼
[Position/Account/Notify] 消费，日志无 traceId ❌
```

**结论：出了网关 traceId 就丢了。**

---

## 二、总体设计原则

### 2.1 核心策略：拦截器 + 自动装配，最小化业务代码改动

不在 16 个服务里逐个改业务代码，而是把追踪能力做成**公共组件**放在 `common-core`，通过 Spring Boot 自动装配一次性覆盖所有服务：

| 传播通道 | 实现机制 | 是否改业务代码 |
|---|---|---|
| HTTP 入站（接收请求） | `TraceIdFilter`（Servlet `OncePerRequestFilter`） | 否，自动装配 |
| HTTP 出站（REST 调用） | `RestTemplateCustomizer` + `ClientHttpRequestInterceptor` | 否，自动装配 |
| Kafka 出站（发送消息） | `ProducerInterceptor` | 否，自动装配 |
| Kafka 入站（消费消息） | `ConsumerInterceptor` | 否，自动装配 |
| 定时任务 / 线程池 | `TaskDecorator` | 否，自动装配 |
| 日志输出 | `logback-spring.xml` pattern `[%X{traceId}]` | 各服务加配置文件 |
| 业务事件源头 | 关键节点 `event.setTraceId(MDC.get("traceId"))` | 少量业务代码 |

### 2.2 traceId 传递双通道

为兼顾"Kafka header 透传"（设计目标，不污染事件体）与"DB 审计"（已有 trace_id 列），采用双通道：

- **传输通道**：Kafka `RecordHeaders`（key=`trace-id`），由 ProducerInterceptor/ConsumerInterceptor 自动处理
- **持久化通道**：事件体 `BaseEvent.traceId` 字段（已存在），在事件源头由 MDC 赋值，落 event_store / outbox 表

### 2.3 包结构规划

```
common/common-core/src/main/java/com/bank/trading/common/core/trace/
├── TraceContext.java              # MDC 工具类（get/set/clear/generate）
├── TraceIdFilter.java             # Servlet 入站 Filter（提取/生成 traceId → MDC）
├── TraceRestTemplateCustomizer.java  # 出站 RestTemplate 拦截器（注入 X-Trace-Id 头）
├── TraceKafkaProducerInterceptor.java   # 出站 Kafka 注入 header
├── TraceKafkaConsumerInterceptor.java   # 入站 Kafka 提取 header → MDC
├── MdcTaskDecorator.java          # 线程池 MDC 复制装饰器
└── TraceAutoConfiguration.java    # 自动装配类（注册上述组件）

common/common-core/src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 2.4 为什么必须用自动装配

各服务启动类包名是 `com.bank.trading.{service}`（如 `com.bank.trading.oms`），而 common-core 的包是 `com.bank.trading.common.core`。两者是**兄弟包**而非父子包，`@SpringBootApplication` 默认扫描不会覆盖到 `com.bank.trading.common.core.trace`。因此必须用 `AutoConfiguration.imports`（Spring Boot 2.7+ 推荐方式）注册。

---

## 三、实施步骤（分阶段）

### 阶段 1：公共组件建设（common-core）

#### 1.1 新建 `TraceContext` 工具类

**文件**：`common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceContext.java`（新建）

**职责**：封装 traceId 的 MDC 操作，统一入口。

**逻辑**：
```java
public final class TraceContext {
    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String Kafka_TRACE_HEADER = "trace-id";

    private TraceContext() {}

    /** 生成 32 位无横线 UUID */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        } else {
            MDC.put(TRACE_ID_KEY, generate());
        }
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
```

#### 1.2 新建 `TraceIdFilter`（HTTP 入站）

**文件**：`common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceIdFilter.java`（新建）

**职责**：每个 HTTP 请求进来，从 `X-Trace-Id` 头提取 traceId（缺失则生成），写入 MDC；请求结束清理 MDC。

**逻辑**：
```java
@Component
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")  // 仅 Servlet 服务装配
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        TraceContext.setTraceId(traceId);  // 缺失自动生成
        // 回写响应头，便于客户端排查
        response.setHeader(TraceContext.TRACE_ID_HEADER, TraceContext.getTraceId());
        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
```

**注意**：此 Filter 仅装配到 Servlet 服务（oms/risk/execution/account/position/pricing/notify/refdata/reconciliation）。gateway 是响应式（WebFlux），需单独处理（见阶段 3）。

#### 1.3 新建 `TraceRestTemplateCustomizer`（HTTP 出站）

**文件**：`common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceRestTemplateCustomizer.java`（新建）

**职责**：所有 RestTemplate 出站调用自动注入 `X-Trace-Id` 头。

**逻辑**：
```java
public class TraceRestTemplateCustomizer implements RestTemplateCustomizer {
    @Override
    public void customize(RestTemplate restTemplate) {
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String traceId = TraceContext.getTraceId();
            if (traceId != null) {
                request.getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
            }
            return execution.execute(request, body);
        });
    }
}
```

**覆盖范围**：Spring Boot 自动把 `RestTemplateCustomizer` Bean 应用到所有 `RestTemplateBuilder` 构建的实例。需改造的客户端：

| 文件 | 当前状态 | 改造方式 |
|---|---|---|
| [oms/RiskServiceClient.java](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/client/RiskServiceClient.java) | 手动设 Content-Type | Customizer 自动注入头 |
| [oms/PricingServiceClient.java](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/client/PricingServiceClient.java) | 无 header | Customizer 自动注入 |
| [execution/ExchangeSessionClient.java](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/client/ExchangeSessionClient.java) | 手动设 Content-Type | Customizer 自动注入 |
| [execution/DefaultHedgeCapacityChecker.java](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/DefaultHedgeCapacityChecker.java) | 无 header | Customizer 自动注入 |
| [reconciliation/DownstreamClient.java](file:///workspace/services/reconciliation-service/src/main/java/com/bank/trading/reconciliation/client/DownstreamClient.java) | 无 header | Customizer 自动注入 |
| [sim-client/SimClientService.java](file:///workspace/sim/sim-client/src/main/java/com/bank/trading/simclient/service/SimClientService.java) | 手动设 Content-Type | Customizer 自动注入 |
| [sim-exchange/CallbackRegistry.java](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/callback/CallbackRegistry.java) | 手动设 Content-Type | Customizer 自动注入 |

**特殊处理 — oms 的 RestTemplate**：[OmsApplication.java#L18-L22](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/OmsApplication.java#L18-L22) 用 `new RestTemplate()` 而非 `RestTemplateBuilder`，Customizer 不会自动应用。**需改为 `RestTemplateBuilder` 注入方式**：
```java
// 修改前
@Bean
@LoadBalanced
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// 修改后
@Bean
@LoadBalanced
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

#### 1.4 新建 `TraceKafkaProducerInterceptor`（Kafka 出站）

**文件**：`common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceKafkaProducerInterceptor.java`（新建）

**职责**：发送 Kafka 消息时自动把 MDC 中的 traceId 注入 `RecordHeaders`。

**逻辑**：
```java
public class TraceKafkaProducerInterceptor implements ProducerInterceptor<String, String> {
    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        String traceId = TraceContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            Headers headers = record.headers();
            // 避免重复添加
            if (headers.lastHeader(TraceContext.KAFKA_TRACE_HEADER) == null) {
                headers.add(TraceContext.KAFKA_TRACE_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
            }
        }
        return record;
    }
    @Override public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}
    @Override public void close() {}
    @Override public void configure(Map<String, ?> configs) {}
}
```

**注册方式**：在 `TraceAutoConfiguration` 中通过 `KafkaProperties` 注入，或修改各服务 `application.yml`：
```yaml
spring:
  kafka:
    producer:
      properties:
        interceptor.classes: com.bank.trading.common.core.trace.TraceKafkaProducerInterceptor
```
推荐 yml 方式（无需改 Java 代码，且对 outbox-relay 也生效）。

#### 1.5 新建 `TraceKafkaConsumerInterceptor`（Kafka 入站）

**文件**：`common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceKafkaConsumerInterceptor.java`（新建）

**职责**：消费 Kafka 消息时从 `RecordHeaders` 提取 traceId 写入 MDC；消费结束清理。

**逻辑**：
```java
public class TraceKafkaConsumerInterceptor implements ConsumerInterceptor<String, String> {
    @Override
    public ConsumerRecords<String, String> onConsume(ConsumerRecords<String, String> records) {
        // 注意：ConsumerInterceptor 在 poll 线程执行，每批消息只触发一次
        // 无法为每条消息单独设 MDC（@KafkaListener 才是每条触发）
        // 因此此拦截器仅作辅助，主要靠下方 ListenerAdapter
        return records;
    }
    // ...
}
```

**关键问题**：`ConsumerInterceptor.onConsume` 在 poll 线程批量触发，无法为每条消息设 MDC。`@KafkaListener` 方法接收 `String message` 不含 header。**两种解法**：

- **方案 A（推荐）**：改消费者方法签名为 `ConsumerRecord<String, String>`，在 AOP 或手动提取 header。需改 7 个监听点。
- **方案 B**：用 `@KafkaListener` 的 `batch=false` + 自定义 `RecordInterceptor`（Spring Kafka 2.8+），在 `ConcurrentKafkaListenerContainerFactory` 上注册。

**采用方案 B**（不改业务方法签名）：在 `TraceAutoConfiguration` 中注册：
```java
@Bean
@ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
public TraceKafkaListenerMdcInterceptor traceKafkaListenerMdcInterceptor() {
    return new TraceKafkaListenerMdcInterceptor();
}
```
`TraceKafkaListenerMdcInterceptor implements RecordInterceptor<String,String>`（Spring Kafka 接口），在 `record` 方法里从 `ConsumerRecord.headers()` 提取 traceId → MDC，在 `afterRecord` 清理。然后通过 `ContainerCustomizer` 注册到 factory。

#### 1.6 新建 `MdcTaskDecorator`（线程池 / 定时任务）

**文件**：`common/common-core/src/main/java/com/bank/trading/common/core/trace/MdcTaskDecorator.java`（新建）

**职责**：`@Scheduled` / `@Async` / 线程池执行任务时，为每个任务生成新 traceId（定时任务无父请求，生成新 ID）并复制父线程 MDC。

**逻辑**：
```java
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        // 定时任务无父 MDC，生成新 traceId
        String parentTraceId = TraceContext.getTraceId();
        return () -> {
            try {
                if (parentTraceId != null) {
                    TraceContext.setTraceId(parentTraceId);  // 异步继承父
                } else {
                    TraceContext.setTraceId(TraceContext.generate());  // 定时任务新建
                }
                runnable.run();
            } finally {
                TraceContext.clear();
            }
        };
    }
}
```

**注册**：在 `TraceAutoConfiguration` 中配置 `TaskExecutor` / `TaskScheduler`：
```java
@Bean
@ConditionalOnMissingBean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new MdcTaskDecorator());
    return executor;
}
```
对 `@Scheduled`，需配置 `ScheduledTaskRegistrar` 的 `TaskScheduler`，或在 yml 中通过 `spring.task.scheduling.simple.task-decorator`（Spring Boot 3.x）。

**覆盖的 8 个定时任务**（无需改业务代码）：
- [ReconciliationScheduler.java#L31](file:///workspace/services/reconciliation-service/src/main/java/com/bank/trading/reconciliation/scheduler/ReconciliationScheduler.java#L31)
- [HedgeBatcher.java#L144](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/HedgeBatcher.java#L144)
- [DefaultHedgeCapacityChecker.java#L117](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/DefaultHedgeCapacityChecker.java#L117)
- [HedgeRecoveryScheduler.java#L55](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/HedgeRecoveryScheduler.java#L55)
- [SimExchangeWebSocketClient.java#L72](file:///workspace/services/market-data-service/src/main/java/com/bank/trading/marketdata/client/SimExchangeWebSocketClient.java#L72)
- [OutboxRelayRunner.java#L56](file:///workspace/services/outbox-relay-service/src/main/java/com/bank/trading/outbox/relay/OutboxRelayRunner.java#L56)
- [MarketDataWebSocketHandler.java#L109](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/ws/MarketDataWebSocketHandler.java#L109)
- [MarketDataScheduler.java#L71](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/config/MarketDataScheduler.java#L71)

**自定义线程池特殊处理**（需手动包装 Runnable）：
- [MatchingEngine.java#L72](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/engine/MatchingEngine.java#L72)：`Executors.newSingleThreadExecutor` — 在提交任务处用 `MdcTaskDecorator` 包装
- [SimClientService.java#L44](file:///workspace/sim/sim-client/src/main/java/com/bank/trading/simclient/service/SimClientService.java#L44)：`Executors.newFixedThreadPool(8)` — 同上

#### 1.7 新建 `TraceAutoConfiguration`

**文件**：`common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceAutoConfiguration.java`（新建）

**职责**：集中注册上述组件，条件装配。

**逻辑**：
```java
@AutoConfiguration
@ConditionalOnProperty(name = "common.trace.enabled", havingValue = "true", matchIfMissing = true)
public class TraceAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnMissingBean(TraceIdFilter.class)
    public TraceIdFilter traceIdFilter() { return new TraceIdFilter(); }

    @Bean
    @ConditionalOnClass(RestTemplate.class)
    public TraceRestTemplateCustomizer traceRestTemplateCustomizer() {
        return new TraceRestTemplateCustomizer();
    }

    @Bean
    @ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
    public ContainerCustomizer<?,?,?> traceKafkaContainerCustomizer() {
        return new TraceKafkaContainerCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean
    public MdcTaskDecorator mdcTaskDecorator() { return new MdcTaskDecorator(); }
}
```

#### 1.8 新建自动装配注册文件

**文件**：`common/common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（新建）

**内容**：
```
com.bank.trading.common.core.trace.TraceAutoConfiguration
```

---

### 阶段 2：数据库 migration（outbox 表加 trace_id）

outbox 表只在 common db 定义，**只需在 common db 加一个 V2 migration**，不需要每个服务都加。

#### 2.1 SQLite migration

**文件**：`db/sqlite/V2__add_trace_id.sql`（新建）

**逻辑**：
```sql
-- outbox 表增加 trace_id 列，用于审计追溯
ALTER TABLE outbox ADD COLUMN trace_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_outbox_trace ON outbox(trace_id);
```

#### 2.2 PostgreSQL migration

**文件**：`db/postgres/V2__add_trace_id.sql`（新建）

**逻辑**：
```sql
SET client_encoding TO 'UTF8';
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_outbox_trace ON outbox(trace_id);
COMMENT ON COLUMN outbox.trace_id IS '分布式链路追踪 ID，串联一次请求跨服务的所有事件';
```

> **说明**：event_store 表已有 trace_id 列（[V1__init_common.sql#L32](file:///workspace/db/sqlite/V1__init_common.sql#L32)），orders 表也已有（[oms V1#L19](file:///workspace/services/oms-service/src/main/resources/db/sqlite/V1__init_oms.sql#L19)），无需再加。hedge_orders / hedge_trades / hedge_batch_items 表是否加 trace_id 列见阶段 8（可选）。

---

### 阶段 3：网关层（gateway）

gateway 是响应式 WebFlux，Servlet 的 `TraceIdFilter` 不适用。现有 [RequestLogFilter.java](file:///workspace/services/gateway/src/main/java/com/bank/trading/gateway/filter/RequestLogFilter.java) 已实现生成 + 注入头，但缺 MDC（响应式 MDC 需用 `Hooks`/`Context`）。

#### 3.1 修改 RequestLogFilter 补 MDC

**文件**：[services/gateway/src/main/java/com/bank/trading/gateway/filter/RequestLogFilter.java](file:///workspace/services/gateway/src/main/java/com/bank/trading/gateway/filter/RequestLogFilter.java)（修改）

**当前问题**：Javadoc 声称"写入 MDC"但代码没做。

**修改逻辑**：响应式场景 MDC 传播复杂，建议**保持现有手动拼接日志**（已可用），并确保 header 注入正确（已正确）。可选增强：在 `doFinally` 里也打印 traceId（已做）。

**评估结论**：gateway 当前实现已满足"生成 + 透传头"目标，**本次不改**，仅补一行注释说明"响应式场景不写 MDC，靠 header 透传给下游 Servlet 服务由下游写 MDC"。

---

### 阶段 4：REST 调用透传

由阶段 1.3 的 `TraceRestTemplateCustomizer` 自动处理，**唯一需改的 Java 代码**是 oms 的 RestTemplate Bean 定义。

#### 4.1 修改 OmsApplication RestTemplate Bean

**文件**：[services/oms-service/src/main/java/com/bank/trading/oms/OmsApplication.java#L18-L22](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/OmsApplication.java#L18-L22)（修改）

**修改**：
```java
// 修改前
@Bean
@LoadBalanced
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// 修改后
@Bean
@LoadBalanced
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

其余 6 个 REST 客户端**无需改 Java 代码**，Customizer 自动生效（前提是它们用 `RestTemplateBuilder`，已确认 reconciliation/execution/sim-client/sim-exchange 均用 Builder）。

---

### 阶段 5：Kafka 消息透传

#### 5.1 各服务 application.yml 增加 interceptor 配置

**需修改的文件**（所有发/收 Kafka 的服务，共 8 个 yml）：

| 服务 | 文件 |
|---|---|
| execution | [application.yml](file:///workspace/services/execution-service/src/main/resources/application.yml) |
| position | [application.yml](file:///workspace/services/position-service/src/main/resources/application.yml) |
| account | [application.yml](file:///workspace/services/account-service/src/main/resources/application.yml) |
| notify | [application.yml](file:///workspace/services/notify-service/src/main/resources/application.yml) |
| pricing | [application.yml](file:///workspace/services/pricing-service/src/main/resources/application.yml) |
| market-data | [application.yml](file:///workspace/services/market-data-service/src/main/resources/application.yml) |
| oms | [application.yml](file:///workspace/services/oms-service/src/main/resources/application.yml) |
| outbox-relay | [application.yml](file:///workspace/services/outbox-relay-service/src/main/resources/application.yml) |

**增加配置**：
```yaml
spring:
  kafka:
    producer:
      properties:
        interceptor.classes: com.bank.trading.common.core.trace.TraceKafkaProducerInterceptor
    consumer:
      properties:
        interceptor.classes: com.bank.trading.common.core.trace.TraceKafkaConsumerInterceptor
```

> 若 ProducerInterceptor + ConsumerInterceptor 在所有服务统一注册有副作用（如无 Kafka 的服务报错），可只给有 Kafka 的服务加。refdata/risk/gateway/reconciliation/sim-exchange/sim-client 可不加。

#### 5.2 Kafka Listener MDC 注入（RecordInterceptor）

由阶段 1.5 的 `TraceKafkaContainerCustomizer` 自动注册 `RecordInterceptor`，**7 个 @KafkaListener 方法无需改签名**：

| 服务 | 文件 | 行号 |
|---|---|---|
| execution | [TradeEventConsumer.java#L41](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/consumer/TradeEventConsumer.java#L41) |
| account | [TradeEventConsumer.java#L45](file:///workspace/services/account-service/src/main/java/com/bank/trading/account/consumer/TradeEventConsumer.java#L45) |
| pricing | [MarketDataConsumer.java#L22](file:///workspace/services/pricing-service/src/main/java/com/bank/trading/pricing/consumer/MarketDataConsumer.java#L22) |
| notify | [BusinessEventConsumer.java#L43, L68](file:///workspace/services/notify-service/src/main/java/com/bank/trading/notify/consumer/BusinessEventConsumer.java#L43) |
| position | [HedgeFillEventConsumer.java#L42](file:///workspace/services/position-service/src/main/java/com/bank/trading/position/consumer/HedgeFillEventConsumer.java#L42), [TradeEventConsumer.java#L42](file:///workspace/services/position-service/src/main/java/com/bank/trading/position/consumer/TradeEventConsumer.java#L42) |

---

### 阶段 6：Outbox 链路透传

#### 6.1 OutboxMessage 实体加字段

**文件**：[common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMessage.java](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMessage.java)（修改）

**修改**：增加 `private String traceId;` 字段。

#### 6.2 OutboxMapper INSERT 加列

**文件**：[common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMapper.java#L41-L43](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMapper.java#L41-L43)（修改）

**修改**：INSERT 语句增加 `trace_id` 列与 `#{traceId}` 参数。

#### 6.3 OutboxServiceImpl 赋值

**文件**：[common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxServiceImpl.java#L33-L45](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxServiceImpl.java#L33-L45)（修改）

**修改**：在构造 `OutboxMessage` 时增加 `message.setTraceId(TraceContext.getTraceId())`。

#### 6.4 OutboxRelayRunner 发送时带 header

**文件**：[services/outbox-relay-service/src/main/java/com/bank/trading/outbox/relay/OutboxRelayRunner.java#L88-L93](file:///workspace/services/outbox-relay-service/src/main/java/com/bank/trading/outbox/relay/OutboxRelayRunner.java#L88-L93)（修改）

**修改方式**：优先依赖阶段 5.1 的 ProducerInterceptor 自动注入 header（从 MDC 取）。但 OutboxRelay 是定时任务，MDC 需由 `MdcTaskDecorator` 生成新 traceId —— 这会导致每批 outbox 消息共享一个 traceId，**无法关联原始请求 traceId**。

**正确方案**：从 `OutboxMessage.traceId`（阶段 6.1 新增字段）取值，显式构造 `ProducerRecord` 带 header：
```java
// 修改前
kafkaTemplate.send(msg.getTopic(), msg.getPartitionKey(), msg.getPayload()).get();

// 修改后
ProducerRecord<String, String> record = new ProducerRecord<>(
        msg.getTopic(), null, null, msg.getPartitionKey(), msg.getPayload());
if (msg.getTraceId() != null) {
    record.headers().add(TraceContext.KAFKA_TRACE_HEADER,
            msg.getTraceId().getBytes(StandardCharsets.UTF_8));
}
kafkaTemplate.send(record).get();
```

---

### 阶段 7：定时任务与自定义线程池 MDC 传播

#### 7.1 @Scheduled（由 MdcTaskDecorator 自动覆盖，无需改业务代码）

见阶段 1.6。

#### 7.2 自定义线程池手动包装

**文件 A**：[sim/sim-exchange/src/main/java/com/bank/trading/simexchange/engine/MatchingEngine.java#L72](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/engine/MatchingEngine.java#L72)（修改）

**修改**：提交任务时用 `MdcTaskDecorator` 包装 Runnable，或在 ThreadFactory 中复制 MDC。sim-exchange 是模拟交易所，trace 价值低，**可标记为可选**。

**文件 B**：[sim/sim-client/src/main/java/com/bank/trading/simclient/service/SimClientService.java#L44](file:///workspace/sim/sim-client/src/main/java/com/bank/trading/simclient/service/SimClientService.java#L44)（修改）

**修改**：同上。sim-client 是压测客户端，**可标记为可选**。

---

### 阶段 8：业务事件源头赋值（关键！）

拦截器只解决"传输"，但 traceId 的**源头**必须由业务代码在创建事件时从 MDC 取值赋给 `event.setTraceId(...)`。这是让 event_store / outbox 表 trace_id 列不再为 null 的关键。

#### 8.1 OMS 发 TradeEvent

**文件**：[services/oms-service/src/main/java/com/bank/trading/oms/service/OrderService.java#L183-L205](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/service/OrderService.java#L183-L205) `publishTradeEvent`（修改）

**修改**：构造 TradeEvent 后加 `event.setTraceId(TraceContext.getTraceId())`。

#### 8.2 OMS 落订单 traceId

**文件**：[services/oms-service/src/main/java/com/bank/trading/oms/service/OrderService.java#L82](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/service/OrderService.java#L82)（修改）

**修改**：
```java
// 修改前
order.setTraceId(createDTO.getTraceId());

// 修改后：优先用 DTO 传入，回退到 MDC
String traceId = createDTO.getTraceId();
if (traceId == null || traceId.isBlank()) {
    traceId = TraceContext.getTraceId();
}
order.setTraceId(traceId);
```

#### 8.3 market-data 发 MarketDataEvent

**文件**：[services/market-data-service/src/main/java/com/bank/trading/marketdata/service/MarketDataService.java#L46-L66](file:///workspace/services/market-data-service/src/main/java/com/bank/trading/marketdata/service/MarketDataService.java#L46-L66) `publishEvent`（修改）

**修改**：构造事件后加 `event.setTraceId(TraceContext.getTraceId())`。

#### 8.4 execution 发 HedgeFillEvent（2 处）

**文件**：[services/execution-service/src/main/java/com/bank/trading/execution/service/ExecutionService.java#L531-L532, L594-L595](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/ExecutionService.java#L531-L532)（修改）

**修改**：构造 HedgeFillEvent 后加 `event.setTraceId(TraceContext.getTraceId())`。

> **Webhook 回调的 traceId 续接问题**：execution 接收 sim-exchange 回调时（[CallbackController.java#L47-L81](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/controller/CallbackController.java#L47-L81)），sim-exchange 不透传 traceId（见阶段 4，CallbackRegistry 已由 Customizer 自动注入头，但 execution→sim-exchange 提交对冲单时 traceId 已写入头，sim-exchange 需回传）。需在 sim-exchange [CallbackRegistry](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/callback/CallbackRegistry.java) 推送回调时把入站请求的 `X-Trace-Id` 头存起来并回传。**可选增强**：execution 在提交对冲单时把 hedgeOrderId 作为关联键，回调时用 hedgeOrderId 反查原 traceId（从 hedge_orders 表 trace_id 列，需阶段 8.5 加列）。

#### 8.5（可选）hedge_orders 表加 trace_id 列

若要解决 Webhook 回调 traceId 续接，需：

**文件**：`services/execution-service/src/main/resources/db/sqlite/V5__add_trace_id.sql`（新建）+ `db/postgres/V5__add_trace_id.sql`（新建）

**逻辑**：
```sql
ALTER TABLE hedge_orders ADD COLUMN trace_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_trace ON hedge_orders(trace_id);
```

**ExecutionService.submitBatchedOrder / submitOrder** 落 hedge_orders 时写 traceId。回调时 `onTradeNotification` 从 hedgeOrder.traceId 恢复 MDC。此项为**可选增强**，本期可不做（hedge 链路靠 hedgeOrderId 关联也可审计）。

---

### 阶段 9：日志格式配置

#### 9.1 各服务增加 logback-spring.xml

**需新增的文件**（每个 Servlet 服务一个，共 11 个）：

| 服务 | 文件路径 |
|---|---|
| oms | `services/oms-service/src/main/resources/logback-spring.xml` |
| risk | `services/risk-service/src/main/resources/logback-spring.xml` |
| execution | `services/execution-service/src/main/resources/logback-spring.xml` |
| account | `services/account-service/src/main/resources/logback-spring.xml` |
| position | `services/position-service/src/main/resources/logback-spring.xml` |
| pricing | `services/pricing-service/src/main/resources/logback-spring.xml` |
| notify | `services/notify-service/src/main/resources/logback-spring.xml` |
| refdata | `services/refdata-service/src/main/resources/logback-spring.xml` |
| reconciliation | `services/reconciliation-service/src/main/resources/logback-spring.xml` |
| outbox-relay | `services/outbox-relay-service/src/main/resources/logback-spring.xml` |
| gateway | `services/gateway/src/main/resources/logback-spring.xml` |

**统一内容**（pattern 关键是 `[%X{traceId:-}]`）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

`%X{traceId:-}` 表示从 MDC 取 traceId，缺失时显示空（`:-` 是默认值分隔符）。

> **注意**：gateway 是响应式，MDC 不生效，其日志靠 RequestLogFilter 手动拼接的 `[traceId]`（已实现）。gateway 的 logback-spring.xml 可不加 `%X{traceId}`，仅统一格式。

---

## 四、文件清单总表

### 新增文件（共 ~25 个）

| # | 文件路径 | 类型 |
|---|---|---|
| 1 | `common/common-core/.../trace/TraceContext.java` | 新建 |
| 2 | `common/common-core/.../trace/TraceIdFilter.java` | 新建 |
| 3 | `common/common-core/.../trace/TraceRestTemplateCustomizer.java` | 新建 |
| 4 | `common/common-core/.../trace/TraceKafkaProducerInterceptor.java` | 新建 |
| 5 | `common/common-core/.../trace/TraceKafkaConsumerInterceptor.java` | 新建 |
| 6 | `common/common-core/.../trace/TraceKafkaListenerMdcInterceptor.java` | 新建 |
| 7 | `common/common-core/.../trace/TraceKafkaContainerCustomizer.java` | 新建 |
| 8 | `common/common-core/.../trace/MdcTaskDecorator.java` | 新建 |
| 9 | `common/common-core/.../trace/TraceAutoConfiguration.java` | 新建 |
| 10 | `common/common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 新建 |
| 11 | `db/sqlite/V2__add_trace_id.sql` | 新建 |
| 12 | `db/postgres/V2__add_trace_id.sql` | 新建 |
| 13-23 | `services/*/src/main/resources/logback-spring.xml`（11 个） | 新建 |
| 24 | `services/execution-service/src/main/resources/db/sqlite/V5__add_trace_id.sql` | 新建（可选） |
| 25 | `services/execution-service/src/main/resources/db/postgres/V5__add_trace_id.sql` | 新建（可选） |

### 修改文件（共 ~20 个）

| # | 文件 | 修改内容 |
|---|---|---|
| 1 | [common-persistence/.../outbox/OutboxMessage.java](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMessage.java) | 加 traceId 字段 |
| 2 | [common-persistence/.../outbox/OutboxMapper.java](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMapper.java) | INSERT 加 trace_id |
| 3 | [common-persistence/.../outbox/OutboxServiceImpl.java](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxServiceImpl.java) | saveEvent 赋 MDC traceId |
| 4 | [outbox-relay/.../OutboxRelayRunner.java](file:///workspace/services/outbox-relay-service/src/main/java/com/bank/trading/outbox/relay/OutboxRelayRunner.java) | send 带 header |
| 5 | [oms/.../OmsApplication.java](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/OmsApplication.java) | RestTemplate 改 Builder |
| 6 | [oms/.../OrderService.java](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/service/OrderService.java) | L82 order.traceId 回退 MDC；publishTradeEvent 设 event.traceId |
| 7 | [market-data/.../MarketDataService.java](file:///workspace/services/market-data-service/src/main/java/com/bank/trading/marketdata/service/MarketDataService.java) | publishEvent 设 event.traceId |
| 8 | [execution/.../ExecutionService.java](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/ExecutionService.java) | 2 处发 HedgeFillEvent 设 traceId |
| 9-16 | 8 个服务 `application.yml` | 加 Kafka interceptor 配置 |
| 17 | [sim-exchange/.../MatchingEngine.java](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/engine/MatchingEngine.java) | 线程池 MDC 包装（可选） |
| 18 | [sim-client/.../SimClientService.java](file:///workspace/sim/sim-client/src/main/java/com/bank/trading/simclient/service/SimClientService.java) | 线程池 MDC 包装（可选） |
| 19 | [sim-exchange/.../CallbackRegistry.java](file:///workspace/sim/sim-exchange/src/main/java/com/bank/trading/simexchange/callback/CallbackRegistry.java) | 回调回传 traceId 头（可选） |
| 20 | [gateway/.../RequestLogFilter.java](file:///workspace/services/gateway/src/main/java/com/bank/trading/gateway/filter/RequestLogFilter.java) | 补注释（响应式不写 MDC） |

---

## 五、验证方案

### 5.1 单元测试

- `TraceContextTest`：验证 generate/set/get/clear
- `TraceIdFilterTest`：MockHttpServletRequest，验证从头提取、缺失生成、响应头回写、MDC 清理
- `TraceRestTemplateCustomizerTest`：Mock RestTemplate，验证出站请求带 X-Trace-Id 头
- `TraceKafkaProducerInterceptorTest`：Mock ProducerRecord，验证 header 注入
- `MdcTaskDecoratorTest`：验证子线程能继承/生成 traceId

### 5.2 集成测试

在 [tests/integration-tests/](file:///workspace/tests/integration-tests/) 新增 `TracePropagationIntegrationTest`：
1. sim-client 带 `X-Trace-Id: test-trace-001` 下单
2. 验证 oms 日志含 `[test-trace-001]`
3. 验证 oms 调用 risk 的请求头含 `X-Trace-Id: test-trace-001`
4. 验证 TradeEvent 的 Kafka header 含 `trace-id: test-trace-001`
5. 验证 execution 消费后日志含 `[test-trace-001]`
6. 验证 HedgeFillEvent 的 Kafka header 含 `trace-id: test-trace-001`
7. 验证 position/account 消费后日志含 `[test-trace-001]`
8. 查询 event_store 表 trace_id 列 = `test-trace-001`
9. 查询 outbox 表 trace_id 列 = `test-trace-001`

### 5.3 端到端验证命令

```bash
# 带 traceId 下单
curl -X POST http://localhost:8080/oms/orders \
  -H "X-Trace-Id: manual-trace-abc" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C1","symbol":"AU2406","side":"BUY","type":"MARKET","qty":"1"}'

# 在各服务日志中搜索
grep "manual-trace-abc" services/*/logs/*.log
```

---

## 六、实施顺序与风险

### 6.1 建议实施顺序

1. **阶段 1**（common-core 公共组件）— 基础，其他都依赖它
2. **阶段 9**（logback-spring.xml）— 与阶段 1 并行，先有日志才能验证
3. **阶段 2**（DB migration）— 与阶段 1 并行
4. **阶段 6**（Outbox 链路）— 依赖阶段 1、2
5. **阶段 8**（业务源头赋值）— 依赖阶段 1
6. **阶段 4、5、7**（REST/Kafka/线程池透传）— 依赖阶段 1
7. **阶段 3**（gateway 注释）— 收尾

### 6.2 风险与缓解

| 风险 | 缓解 |
|---|---|
| AutoConfiguration 未生效 | 启动日志加 `--debug` 看 `TraceAutoConfiguration matched`；加单元测试 |
| Kafka Interceptor 导致消费失败 | 先在测试环境验证；`common.trace.enabled=false` 可一键禁用 |
| MDC 线程泄露（未清理） | Filter/TaskDecorator 都用 try-finally clear |
| 响应式 gateway MDC 不生效 | 已说明靠 header 透传，不依赖 gateway MDC |
| outbox relay traceId 断裂 | 阶段 6.4 显式从 OutboxMessage.traceId 取值构造 header |
| 现有测试失败 | 拦截器对 Mock 环境可能无效，加 `@MockBean` 或条件装配 |

### 6.3 回滚方案

- 配置开关 `common.trace.enabled=false` 一键禁用所有追踪组件
- DB migration 是加列（ALTER ADD），回滚需写 V3 drop column（Flyway 不支持回滚，需手动）
- logback-spring.xml 删除即回退默认 pattern

---

## 七、附录：关键设计决策记录

### 决策 1：为什么用 AutoConfiguration 而非 @ComponentScan
各服务启动类包名 `com.bank.trading.{service}` 与 common-core 包 `com.bank.trading.common.core` 是兄弟包，默认扫描不覆盖。AutoConfiguration 是 Spring Boot 官方推荐的公共组件装配方式。

### 决策 2：为什么用 Kafka Header 而非事件体传递 traceId
设计文档明确"Kafka 通过消息 header 透传"。Header 不污染业务事件 JSON，且 BaseEvent.traceId 字段已存在用于落库审计，两者各司其职。

### 决策 3：为什么 ConsumerInterceptor 不够，还要 RecordInterceptor
`ConsumerInterceptor.onConsume` 在 poll 线程批量触发，无法为每条消息单独设 MDC。`RecordInterceptor`（Spring Kafka）在每条记录消费前后触发，正好匹配 `@KafkaListener` 单条消费模型。

### 决策 4：为什么 outbox relay 要显式构造 ProducerRecord
OutboxRelay 是定时任务，MDC 的 traceId 是定时任务新建的，不是原始请求的。原始 traceId 存在 `OutboxMessage.traceId`（落 outbox 表时写入），必须显式取出放进 header 才能续接。

### 决策 5：Webhook 回调 traceId 续接为何标记可选
sim-exchange 是模拟交易所，回调链路 trace 续接价值有限。真实交易所对接时再处理。本期靠 hedgeOrderId 关联审计即可。
