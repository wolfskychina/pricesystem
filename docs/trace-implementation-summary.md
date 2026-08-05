# 全链路追踪改造变更总结

> 文档版本：v1.0 ｜ 完成日期：2026-08-05
> 关联设计：[trace-implementation-plan.md](file:///workspace/docs/trace-implementation-plan.md)

## 1. 背景与目标

本次改造为做市商交易系统引入**全链路追踪（Distributed Tracing）**能力，通过 `traceId` 串联一次客户请求跨服务、跨消息、跨线程的全部事件与日志，实现：

- 请求链路可视化，快速定位跨服务异常；
- 事件溯源体系下 `event_store` / `outbox` 表的 `trace_id` 列不再为空；
- 聚合对冲等异步链路（定时任务出桶）仍能关联回原始客户请求。

核心机制：以 **MDC（Mapped Diagnostic Context）** 作为线程级上下文载体，在请求入口（HTTP Filter / Kafka Listener / 定时任务）初始化 traceId，在出口（RestTemplate / Kafka Producer）透传，业务代码在创建事件时从 MDC 取值赋给 `event.setTraceId(...)`。

## 2. 改造范围与分阶段交付

本次共完成 4 个阶段，对应 4 次提交：

| 阶段 | 主题 | 提交 | 文件数 | 净增行 |
|---|---|---|---|---|
| 阶段 1 | 全链路追踪公共组件建设（common-core trace 包） | `0cb5946` | 11 | +541 |
| 阶段 2 | DB migration —— outbox 表增加 trace_id 列 | `acc76fb` | 2 | +15 |
| 阶段 6 | Outbox 链路 traceId 透传 | `5715b7a` | 6 | +138 / -5 |
| 阶段 8 | 业务事件源头赋值（OMS + execution） | `5b906ba` | 7 | +47 / -5 |

## 3. 各阶段详细变更

### 3.1 阶段 1：common-core trace 公共组件（`0cb5946`）

在 [common/common-core](file:///workspace/common/common-core) 下新建 `trace` 包，提供与传输层无关的追踪基础设施，通过 Spring Boot 自动装配应用到所有依赖 common-core 的服务，无需各服务显式配置。

**新增文件**（[common/common-core/src/main/java/com/bank/trading/common/core/trace/](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/)）：

| 文件 | 职责 |
|---|---|
| [TraceContext.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceContext.java) | 追踪上下文工具类，封装 traceId 的 MDC 读写；统一定义常量 `TRACE_ID_KEY`、`TRACE_ID_HEADER="X-Trace-Id"`、`KAFKA_TRACE_HEADER="trace-id"` |
| [TraceIdFilter.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceIdFilter.java) | HTTP 入站过滤器，从 `X-Trace-Id` 头提取 traceId 写入 MDC，无则自动生成；请求结束 finally 清理 MDC |
| [TraceRestTemplateCustomizer.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceRestTemplateCustomizer.java) | RestTemplate 出站定制器，自动把 MDC 中的 traceId 注入 `X-Trace-Id` 请求头 |
| [TraceKafkaProducerInterceptor.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceKafkaProducerInterceptor.java) | Kafka 生产者拦截器，发送时注入 `trace-id` RecordHeader（由 Kafka 客户端反射实例化，非 Spring Bean） |
| [TraceKafkaListenerMdcInterceptor.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceKafkaListenerMdcInterceptor.java) | Kafka 消费侧 RecordInterceptor，从 RecordHeader 提取 traceId 写入 MDC，消费完成清理 |
| [TraceKafkaContainerCustomizer.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceKafkaContainerCustomizer.java) | BeanPostProcessor，把上述拦截器注册到所有 `AbstractKafkaListenerContainerFactory` |
| [MdcTaskDecorator.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/MdcTaskDecorator.java) | TaskDecorator，`@Async` / `@Scheduled` / 线程池跨线程传播 MDC |
| [TraceAutoConfiguration.java](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceAutoConfiguration.java) | 自动装配类，集中注册上述组件（带 `@ConditionalOnClass` 按需装配） |

**装配入口**：[META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports](file:///workspace/common/common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) 注册 `TraceAutoConfiguration`。

**开关**：`common.trace.enabled=false` 可一键禁用所有追踪组件（默认开启）。

**测试**：新增 `TraceContextTest`，8 个用例覆盖 generate/set/get/clear/常量；common-core 全部 16 个测试通过。

> 说明：Kafka 生产者拦截器需在各服务 `application.yml` 配置 `interceptor.classes` 注册（非 Spring Bean）。pom.xml 新增 `spring-kafka`（provided）依赖供拦截器类编译。

### 3.2 阶段 2：DB migration —— outbox 表 trace_id 列（`acc76fb`）

为 outbox 表增加 `trace_id` 列，供 OutboxRelay 投递时构造 Kafka RecordHeader。

**新增文件**：

- [db/sqlite/V2__add_trace_id.sql](file:///workspace/db/sqlite/V2__add_trace_id.sql) —— `ALTER TABLE outbox ADD COLUMN trace_id VARCHAR(64)` + 索引
- [db/postgres/V2__add_trace_id.sql](file:///workspace/db/postgres/V2__add_trace_id.sql) —— 同上，含 `COMMENT ON COLUMN` 与 UTF-8 编码设置

> 说明：`event_store`、`orders` 表在 V1 初始化脚本中已含 `trace_id` 列，无需重复添加。`hedge_orders` 表的 trace_id 列为可选增强（见第 7 节）。

### 3.3 阶段 6：Outbox 链路 traceId 透传（`5715b7a`）

打通"业务写 outbox → Relay 投递 Kafka → 下游消费"的 traceId 透传链路。

**修改文件**：

| 文件 | 变更 |
|---|---|
| [OutboxMessage.java](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMessage.java) | 新增 `traceId` 字段及 getter/setter |
| [OutboxMapper.java](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxMapper.java) | INSERT 语句增加 `trace_id` 列 |
| [OutboxServiceImpl.java](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxServiceImpl.java) | `saveEvent` 中 `message.setTraceId(TraceContext.getTraceId())`，从 MDC 写入 |
| [OutboxRelayRunner.java](file:///workspace/services/outbox-relay-service/src/main/java/com/bank/trading/outbox/relay/OutboxRelayRunner.java) | `processMessage` 显式构造 `ProducerRecord`，从 `msg.getTraceId()` 注入 `trace-id` RecordHeader |

**关键代码**（[OutboxServiceImpl.java#L45-L47](file:///workspace/common/common-persistence/src/main/java/com/bank/trading/common/persistence/outbox/OutboxServiceImpl.java#L45-L47)）：
```java
// 从 MDC 写入 traceId，供 OutboxRelay 投递时构造 Kafka RecordHeader
message.setTraceId(TraceContext.getTraceId());
```

**测试**：新增 `OutboxServiceImplTraceTest`，2 个用例验证 MDC 有值/无值时的写入逻辑。因 JDK25 + Mockito Byte Buddy 兼容问题，改用手写桩 Mapper。

### 3.4 阶段 8：业务事件源头赋值（`5b906ba`）

拦截器只解决"传输"，traceId 的**源头**必须由业务代码在创建事件时从 MDC 取值赋给 `event.setTraceId(...)`。本阶段让 `event_store` / `outbox` 表 trace_id 列不再为空。

#### 8.1 OMS 模块

**[OrderService.java](file:///workspace/services/oms-service/src/main/java/com/bank/trading/oms/service/OrderService.java)**：

- `createOrder`：优先从 MDC 取 traceId 赋值 order，回退到 DTO
  ```java
  // 优先从 MDC 获取 traceId（由 TraceIdFilter 从 X-Trace-Id 头注入），回退到 DTO
  String traceId = TraceContext.getTraceId();
  order.setTraceId(traceId != null ? traceId : createDTO.getTraceId());
  ```
- `publishTradeEvent`：从 MDC 赋值 `event.setTraceId(TraceContext.getTraceId())`，串联跨服务的对冲执行链路

#### 8.2 execution 模块

**实体与持久化**：

- [HedgeBatchItem.java](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/entity/HedgeBatchItem.java) 新增 `traceId` 字段及 getter/setter
- [HedgeBatchItemMapper.java](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/mapper/HedgeBatchItemMapper.java) `insert` / `update` SQL 增加 `trace_id` 列
- 新建 [V5__add_batch_item_trace_id.sql (sqlite)](file:///workspace/services/execution-service/src/main/resources/db/sqlite/V5__add_batch_item_trace_id.sql) 与 [postgres](file:///workspace/services/execution-service/src/main/resources/db/postgres/V5__add_batch_item_trace_id.sql)，为 `hedge_batch_items` 加 `trace_id` 列 + 索引

**入桶赋值**（[HedgeBatcher.java](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/HedgeBatcher.java) `enqueue`）：
```java
// 入桶时从 MDC 写入 traceId（关联原始客户请求）。
// 出桶发 hedge-fill-event 时取值赋给 event，保证聚合模式下每条事件仍能关联回
// 原始客户请求的 traceId（定时任务的 MDC 是新建的，无法续接）。
item.setTraceId(TraceContext.getTraceId());
```

**出桶发事件**（[ExecutionService.java](file:///workspace/services/execution-service/src/main/java/com/bank/trading/execution/service/ExecutionService.java)）：

- `publishHedgeFillEventForItem`（聚合模式）：从 `item.getTraceId()` 赋值 event——定时任务出桶时 MDC 是新建的、无法续接原客户请求 traceId，故从子项取值
- `publishHedgeFillEvent`（单笔模式）：从 MDC 取值 `event.setTraceId(TraceContext.getTraceId())`，由 Webhook 回调链路的 TraceIdFilter 注入

**设计要点**：聚合模式下，traceId 在入桶时持久化到 `hedge_batch_items`，出桶发 `hedge-fill-event` 时从子项取值，保证每条事件仍能关联回原始客户请求。

## 4. 全链路 traceId 流转图

以"客户下单 → OMS 成交 → execution 对冲 → 持仓更新"为例：

```
客户请求 (X-Trace-Id: t1)
   │
   ▼ TraceIdFilter 写入 MDC(t1)
[OMS] OrderService.createOrder
   │  order.traceId = MDC(t1)               ← 阶段 8.1
   │  publishTradeEvent: event.traceId = MDC(t1)   ← 阶段 8.1
   │  OutboxServiceImpl.saveEvent: outbox.trace_id = MDC(t1)   ← 阶段 6
   ▼
[outbox 表] trace_id=t1
   │
   ▼ OutboxRelayRunner 取 trace_id 构造 Kafka header(trace-id: t1)   ← 阶段 6
[Kafka: trade-event]  header trace-id=t1
   │
   ▼ TraceKafkaListenerMdcInterceptor 从 header 写入 MDC(t1)   ← 阶段 1
[execution] TradeEventConsumer → HedgeBatcher.enqueue
   │  item.traceId = MDC(t1)               ← 阶段 8.2
   ▼
[hedge_batch_items 表] trace_id=t1
   │
   ▼ 定时任务 flushBucket（MDC 新建 t2，但 item.traceId=t1 保留）
[execution] publishHedgeFillEventForItem
   │  event.traceId = item.traceId(t1)     ← 阶段 8.2（从子项取值，不取 MDC）
   ▼
[Kafka: hedge-fill-event]
   │
   ▼ 下游 position-service 消费，MDC(t1) 续接
```

## 5. 数据模型变更汇总

| 表 | 列 | 来源 | 阶段 |
|---|---|---|---|
| `outbox` | `trace_id VARCHAR(64)` + 索引 | V2 migration | 阶段 2 |
| `hedge_batch_items` | `trace_id VARCHAR(64)` + 索引 | V5 migration | 阶段 8 |
| `event_store` | `trace_id` | V1 已有（无需改） | — |
| `orders` | `trace_id` | V1 已有（无需改） | — |

## 6. 配置与开关

- **总开关**：`common.trace.enabled=false` 可一键禁用全部追踪组件（默认开启）
- **HTTP 头**：`X-Trace-Id`（[TraceContext.TRACE_ID_HEADER](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceContext.java#L26)）
- **Kafka header**：`trace-id`（[TraceContext.KAFKA_TRACE_HEADER](file:///workspace/common/common-core/src/main/java/com/bank/trading/common/core/trace/TraceContext.java#L29)）
- **Kafka 生产者拦截器**：需在各服务 `application.yml` 显式配置 `interceptor.classes` 注册 `TraceKafkaProducerInterceptor`（非 Spring Bean，自动装配不覆盖）

## 7. 已知限制与后续工作

1. **阶段 9（日志格式配置）尚未落地**：各服务 `logback-spring.xml` 未创建，日志 pattern 中尚无 `[%X{traceId:-}]`。MDC 已写入 traceId，但日志未打印，需补齐 11 个服务的 logback 配置后才能在日志中看到 traceId。
2. **`hedge_orders` 表 trace_id 列（可选增强）未做**：单笔对冲路径由 Webhook 回调触发，当前 `publishHedgeFillEvent` 取当前 MDC。若 sim-exchange 不回传 `X-Trace-Id`，单笔 hedge-fill-event 的 traceId 会丢失。如需续接原客户请求 traceId，需给 `hedge_orders` 加 trace_id 列，提交对冲单时写入，回调时反查恢复 MDC。
3. **Webhook 回调 traceId 续接**：sim-exchange 推送回调时未透传入站请求的 `X-Trace-Id`，单笔对冲链路 traceId 在 execution→sim-exchange→execution 回调环节断裂（聚合链路因 `hedge_batch_items` 持久化 traceId 不受影响）。
4. **Kafka 生产者拦截器需手动注册**：当前各服务 `application.yml` 未配置 `interceptor.classes`，直接走 `kafkaTemplate.send` 的路径（如 execution 发 hedge-fill-event）依赖 OutboxRelay 显式构造 header；非 Outbox 路径需补拦截器配置。

## 8. 测试与验证

| 模块 | 测试类 | 用例数 | 结果 |
|---|---|---|---|
| common-core | `TraceContextTest` | 8 | 全部通过 |
| common-persistence | `OutboxServiceImplTraceTest` | 2 | 全部通过 |
| execution-service | `ExecutionServiceTest` + `HedgeBatcherTest` + `ExchangeSessionClientTest` | 44 | 全部通过 |
| oms-service | — | — | 编译通过 |

回归验证：阶段 8 改动后 execution-service + oms-service 共 44 个测试全部通过（0 失败 0 错误），净额对冲、聚合分摊、入桶幂等等既有逻辑无回归。

## 9. 提交记录

| 提交 | 阶段 | 说明 |
|---|---|---|
| `0cb5946` | 阶段 1 | feat(trace): 阶段1 全链路追踪公共组件建设（common-core trace 包） |
| `acc76fb` | 阶段 2 | feat(trace): 阶段2 DB migration - outbox 表增加 trace_id 列 |
| `5715b7a` | 阶段 6 | feat(trace): 阶段6 Outbox 链路 traceId 透传 |
| `5b906ba` | 阶段 8 | feat: 业务事件源头赋值（OMS + execution 发事件时从 MDC 取 traceId） |

> 注：阶段 8 提交时使用通用提交信息，实际内容为业务事件源头 traceId 赋值（见 [trace-implementation-plan.md](file:///workspace/docs/trace-implementation-plan.md) 阶段 8）。
