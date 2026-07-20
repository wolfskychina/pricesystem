# 银行做市商交易系统 - 最终设计方案

## 一、系统概述

### 1.1 项目背景
银行在金融市场领域扮演做市商角色，跟踪主流期货市场商品报价，向客户提供双边报价，接受客户订单并成交，同时在期货市场进行风险对冲。

### 1.2 设计目标
- 低耦合、高内聚的微服务架构
- 事件驱动 + 同步调用混合，一致性可保障
- 数据库可切换（SQLite ↔ PostgreSQL）
- 可水平扩展（逻辑分片预留，未来可物理分片）
- 可测试（模拟交易所 + 模拟客户端）

### 1.3 行业参考说明

以下是本方案参考的主流开源项目和行业实践，各项目在本系统中的参考点会在对应章节用【参考来源】标记说明。

| 参考项目 | 是什么 | 本系统参考了什么 | 对应章节 |
|---|---|---|---|
| **QuickFIX/J** | 金融行业标准 FIX 协议的 Java 实现，用于交易所/券商间的订单与行情通信 | 订单消息模型设计、行情消息格式、订单状态机思想 | 3.3、3.1、第十一章 |
| **LMAX Disruptor** | LMAX 交易所开源的高性能无锁环形缓冲区，用于极低延迟的事件处理 | 事件驱动架构思想、单消费者单分区保序、环形缓冲解耦生产者消费者 | 4.3、整体架构 |
| **Event Sourcing（事件溯源）** | DDD 中的架构模式，用事件的 append-only 日志作为状态的唯一真实来源 | event_store 表设计、状态可重放、审计追溯 | 4.5 |
| **Transactional Outbox 模式** | 微服务架构中解决"数据库更新 + 消息发送"一致性的经典模式 | outbox 表 + Relay 进程，保证事件不丢 | 4.2 |
| **Esper / Siddhi（CEP）** | 复杂事件处理引擎，用于实时流数据的模式匹配和聚合 | 实时风控、敞口监控的事件流处理思想（本期暂不引入，风控用纯 Java） | 第十章 |
| **Drools** | 开源业务规则引擎，将规则从代码中剥离，支持热更新 | 风控规则外置的设计思想，预留规则引擎接口 | 第十章 |
| **OpenMAMA** | 开源中间件无关的行情 API，屏蔽底层不同消息中间件的差异 | 行情归一化思想：不同交易所格式 → 统一 MarketData 模型 | 3.1 |
| **ShardingSphere** | 开源分库分表中间件，提供多种分片策略 | 分片路由、按 shard_id 水平扩展的设计思想 | 第五章 |
| **几何布朗运动（GBM）** | 金融数学中描述资产价格波动的经典模型（Black-Scholes 模型的基础） | 模拟交易所行情生成，使价格波动接近真实市场 | 3.10 |

> **说明**：以上参考项目中，QuickFIX/J、LMAX Disruptor、Esper、Drools、OpenMAMA、ShardingSphere 都是金融/互联网行业广泛使用的开源项目或经典架构模式。本方案**不直接引入这些框架**（除 Drools 预留接口外），而是借鉴其设计思想，用更轻量的方式实现，降低学习和维护成本。

---

## 二、整体架构

### 2.1 架构总览

```
                        ┌─────────────────────────────┐
                        │     前端：报价监控面板        │
                        │  (实时行情/报价表格,可调刷新)  │
                        └──────────────┬──────────────┘
                                       │ REST/WebSocket
                                ┌──────▼──────┐
                                │ API Gateway │
                                │ (traceId注入│
                                │  路由转发)   │
                                └──────┬──────┘
       ┌──────────────────────────────┼──────────────────────────────┐
       │                              │                              │
┌──────▼───────┐  ┌────────────┐  ┌──▼─────────┐  ┌────────────┐  ┌──▼──────────┐
│ Quote Service│  │ OMS Service│  │Pricing Svc │  │ Risk Svc   │  │Account Svc  │
│  (报价查询)  │  │ (订单管理) │  │ (做市报价) │  │ (风控)     │  │ (客户账户)  │
└──────┬───────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └──────┬───────┘
       │                │               │               │              │
       └────────────────┴───────┬───────┴───────────────┘              │
                               │                                       │
                   ┌───────────▼────────────┐                          │
                   │   Kafka 消息总线        │                          │
                   └───────────┬────────────┘                          │
                               │                                       │
        ┌──────────────────────┼──────────────────────┐               │
        │                      │                      │               │
┌───────▼────────┐  ┌──────────▼─────────┐  ┌────────▼────────┐       │
│ MarketData Svc │  │ Execution Svc      │  │ Position Svc    │       │
│ (行情接入)     │  │ (对冲执行)         │  │ (持仓管理)      │       │
└───────┬────────┘  └──────────┬─────────┘  └────────┬────────┘       │
        │                      │                      │               │
        │           ┌──────────▼────────────┐         │               │
        │           │  Simulated Exchange   │ ← 模拟期货交易所         │
        └──────────►│  (独立进程,随机波动)   │         │               │
                    └───────────────────────┘         │               │
                                                       │               │
        ┌──────────────────────────────────────────────┼───────────────┘
        │                                              │
┌───────▼────────┐                          ┌──────────▼─────────┐
│ Notify Svc     │                          │ Reconciliation Svc │
│ (WebSocket推送)│                          │ (定时跨服务对账)    │
└───────┬────────┘                          └───────────────────┘
        │
        │ 消费 trade-event / hedge-fill-event
        │
        ▼
┌────────────────────┐
│  客户端 WebSocket  │
│  (按customer订阅)   │
└────────────────────┘
```

辅助服务：
- **Eureka**（注册发现）、**Config Server**（配置中心）、**RefData Service**（基础数据）
- **Outbox Relay Service**：独立进程，轮询各服务 outbox 表，投递到 Kafka
- **Sim Client**：模拟客户下单，用于压测和联调验证

### 2.2 服务清单

| 服务 | 职责 | 优先级 |
|---|---|---|
| eureka-server | 服务注册与发现 | P0 |
| config-server | 配置中心 | P0 |
| gateway | API 网关（traceId 注入、路由转发、CORS） | P3 |
| refdata-service | 基础数据（合约/交易日历） | P0 |
| market-data-service | 行情接入与分发 | P1 |
| pricing-service | 做市报价计算 | P1 |
| oms-service | 订单管理 | P1 |
| risk-service | 风控 | P1 |
| execution-service | 对冲执行 | P2 |
| position-service | 持仓管理 | P2 |
| account-service | 客户账户 | P2 |
| notify-service | 消息推送（WebSocket + Kafka 多主题消费） | P3 |
| reconciliation-service | 定时跨服务对账（敞口/额度/对冲挂单） | P3 |
| outbox-relay-service | Outbox 消息投递（轮询 outbox 表 → Kafka） | P2 |
| sim-exchange | 模拟期货交易所（异步两阶段撮合 + GBM 行情） | P0 |
| sim-client | 模拟客户下单（批量/单笔，用于压测和联调） | P3 |
| frontend | 报价监控面板 | P2 |

---

## 三、模块详细设计

### 3.1 market-data-service（行情接入服务）

【参考来源：**OpenMAMA**】— 借鉴"屏蔽底层行情源差异，对外提供统一行情模型"的思想。OpenMAMA 是开源的中间件无关行情 API，支持接入不同交易所的行情数据，向上提供统一格式。本系统的 market-data-service 类似：接入 sim-exchange 的行情（未来可接入真实交易所），归一化后统一对外发布。

- 连接模拟交易所 WebSocket，订阅行情
- 归一化为统一 `MarketData` 模型（屏蔽不同交易所数据格式差异）
- 缓存最新行情到 Redis
- 通过 Kafka 发布 `market-data` 主题
- REST: `GET /marketdata/{symbol}` 查最新价

### 3.2 pricing-service（做市报价服务）
- 订阅 `market-data` 主题
- 根据期货行情 + spread 计算客户买卖价
- spread 按合约、客户等级配置
- 发布 `customer-quote` 主题
- REST: `GET /quotes/{symbol}`、`POST /quotes/rfq`

### 3.3 oms-service（订单管理服务）

【参考来源：**QuickFIX/J**】— 借鉴 FIX 协议的订单管理思想。QuickFIX/J 是金融行业标准 FIX（Financial Information eXchange）协议的 Java 实现，定义了标准的订单消息字段、订单状态流转（订单生命周期）、成交回报格式等。本系统的 OMS 订单状态机和订单数据模型参考了 FIX 协议的设计，如 New → Filled → Cancelled 的状态流转、ClOrdID（客户端订单号）的幂等设计等。

- 接收客户订单（市价/限价）
- 订单状态机：NEW → PENDING_RISK → ACCEPTED → PARTIALLY_FILLED → FILLED / REJECTED / CANCELLED
- 调用 risk-service 做事前风控（同步 REST）
- 做市商即对手方，撮合即接受
- 成交后发布 `trade-event` 事件（经 Outbox）

### 3.4 risk-service（风控服务）

【参考来源：**Drools + Esper/Siddhi**】— 借鉴两个方向的思想：
- **Drools**：开源业务规则引擎，核心价值是"规则与代码分离，业务人员可配置规则，规则可热更新"。本系统风控模块预留了 `RiskRuleEngine` 接口，未来可切换为 Drools 实现，实现规则外置。
- **Esper / Siddhi**：复杂事件处理（CEP）引擎，擅长从实时事件流中检测模式、计算聚合（如"5分钟内累计成交超过限额"）。本期风控用纯 Java 实现，不直接引入 CEP，但事件流处理的设计思想（消费 trade-event 做事后风控、敞口实时监控）借鉴了 CEP 的思路。

- 事前风控：信用额度、单笔限额、持仓限额、价格偏离度
- 事中风控：实时敞口监控
- 纯 Java 实现，预留 Drools 接入接口 `RiskRuleEngine`
- REST: `POST /risk/pre-trade` 同步调用

### 3.5 execution-service（对冲执行服务）
- 消费 `trade-event`（Kafka topic: trade-event）
- 计算对冲方向（与客户成交相反）和数量（× hedge-ratio，默认全额对冲）
- 通过 ExchangeSessionClient 调用 sim-exchange 下单（同步受理，返回 NEW）
- 接收 sim-exchange Webhook 回调（订单状态回报 + 成交通知），更新对冲订单状态为 FILLED
- 发布 `hedge-fill-event`（Kafka topic: hedge-fill-event）到 Kafka
- REST: `GET /execution/orders`、`GET /execution/orders/{id}/trades`
- 端口 8086

**与 sim-exchange 的异步两阶段交互**：
```
1. TradeEventConsumer 收到 trade-event → ExecutionService.onTradeEvent
2. ExecutionService → ExchangeSessionClient.submitOrder (POST /exchange/orders)
   - sim-exchange 同步返回订单受理（NEW）
3. sim-exchange 异步撮合后通过 Webhook 回调：
   - POST /execution/callback/order  → ExecutionService.onOrderNotification（状态回报）
   - POST /execution/callback/trade  → ExecutionService.onTradeNotification（成交通知）
4. ExecutionService 发布 HedgeFillEvent 到 Kafka hedge-fill-event topic
```

**数据表**：hedge_orders（对冲订单）、hedge_trades（对冲成交流水）、hedge_batch_items（聚合子项）

**对冲聚合（Hedge Batching）**：
短时间内同合约、同方向的多笔客户成交合并为一笔对冲单提交交易所，减少订单数量、降低手续费与市场冲击成本。
- 聚合粒度：按 `symbol + side` 分组（同一合约同一对冲方向合并）
- 双触发机制：
  - **时间窗口**：每 `batching-window-ms` 毫秒定时出桶（开发环境 1000ms，生产环境建议 200ms）
  - **数量阈值**：单桶累计数量 ≥ `batching-size-threshold`（默认 50 手）立即出桶
- 分摊规则（市价单）：所有子项成交价相同，按数量比例分摊，最后一项吸收尾差
- 事件发布：聚合订单成交后，按原始成交笔数逐笔发布 hedge-fill-event（position-service 消费模型不变）
- 可配置开关：`execution.batching-enabled`，关闭时回退为一笔成交一笔对冲

**对冲聚合数据流**：
```
trade-event → HedgeBatcher.enqueue
  ├─ batchingEnabled=false → ExecutionService.onTradeEventImmediate（单笔立即对冲）
  └─ batchingEnabled=true  → 入桶（hedge_batch_items 状态=PENDING）
       ├─ 时间窗口触发（@Scheduled fixedDelay）
       └─ 数量阈值触发（入桶时检查）
            → ExecutionService.submitBatchedOrder（聚合提交）
                 → hedge_orders.isBatched=1 + 子项状态=SUBMITTED
                 → ExchangeSessionClient.submitOrder（同步受理）
                 → sim-exchange 异步撮合
                      → Webhook /execution/callback/trade
                           → ExecutionService.onTradeNotification
                                → 按子项数量比例分摊
                                → 逐笔发布 hedge-fill-event（每原始成交1条）
```

### 3.6 position-service（持仓管理服务）
- 消费 `trade-event` 更新客户头寸
- 消费 `hedge-fill-event` 更新对冲头寸
- 计算净敞口 = 客户头寸 - 对冲头寸
- REST: `GET /positions/{customerId}`、`GET /positions/exposure`

### 3.7 account-service（客户账户服务）
- 客户主数据、信用额度、保证金
- 消费 `trade-event` 扣减可用额度
- REST: CRUD + `GET /accounts/{id}/credit`

### 3.8 refdata-service（基础数据服务）
- 合约定义（代码、乘数、最小变动价位）
- 交易所信息、交易日历
- REST 查询

### 3.9 notify-service（推送服务）
- 订阅 Kafka 多主题（trade-event / hedge-fill-event）
- WebSocket 推送给连接的客户，支持按 customerId + type 双重过滤订阅
- 会话注册表模式：ConcurrentHashMap + CopyOnWriteArraySet 维护连接与订阅关系
- REST: `GET /notify/health`、`GET /notify/stats`
- 端口 8087

### 3.10 reconciliation-service（对账服务）
- 定时（@Scheduled cron，默认每 5 分钟）跨服务对账
- 三维度对账：
  - **敞口对账**：遍历每个 symbol 的净敞口，|netExposure| > threshold 则记录不一致
  - **额度对账**：sum(usedCredit) vs sum(|customerPosition|)，差异超过阈值则告警
  - **对冲挂单对账**：检查 status=NEW 的对冲订单数（说明对冲延迟）
- 下游依赖：position-service（敞口）、account-service（额度）、execution-service（对冲订单）
- 对账结果缓存在内存中，供 REST 接口查询
- REST: `GET /reconciliation/last`（最近结果）、`POST /reconciliation/trigger`（手动触发）、`GET /reconciliation/health`
- 端口 8089

### 3.11 sim-exchange（模拟期货交易所）

【参考来源：**几何布朗运动（GBM）模型**】— 金融数学中描述资产价格随时间波动的经典随机过程，是 Black-Scholes 期权定价模型的基础假设。真实市场中股票、期货等资产的价格走势大致符合对数正态分布，GBM 模型通过漂移率（μ）和波动率（σ）两个参数来刻画价格的长期趋势和短期波动幅度，生成的行情比简单随机游走更接近真实市场。

【参考来源：**CTP / CME Globex FIX 协议**】— 真实期货交易所（国内 CTP、国际 CME Globex）的下单接口均采用**异步两阶段模型**：
- **国内 CTP**：`ReqOrderInsert` 同步返回仅表示"请求已送达前置机"，随后通过 `OnRspOrderInsert`（受理确认）、`OnRtnOrder`（订单状态回报）、`OnRtnTrade`（成交通知）三个异步回调推送结果。
- **国际 CME Globex（FIX 4.4）**：`NewOrderSingle (D)` 发送后，通过异步 `ExecutionReport (8)` 推送，Tag 150 (ExecType) 区分 New / Partial Fill / Filled / Cancelled / Rejected 等状态。
- **通信通道**：做市商系统（EMS）与交易所之间通过 **TCP 长连接 + SDK 回调**（CTP）或 **FIX 会话回调**（Globex）通信，**不使用消息队列**。Kafka 仅用于做市商内部微服务之间的事件分发。

本模拟系统据此实现：sim-exchange 与 execution-service 之间通过 **Webhook 回调**模拟 SDK/FIX 的异步推送语义，对齐真实交易所行为。

- **独立进程** Spring Boot 应用
- 从配置文件读取初始报价
- 按几何布朗运动（GBM）生成连续随机波动行情，接近真实市场
- WebSocket 广播行情
- REST: `POST /exchange/orders` 接受对冲单，**同步仅返回订单受理（状态=NEW）或参数校验拒绝（REJECTED）**，不返回成交
- REST: `GET /exchange/orders/{orderId}` 查询订单状态（受理后异步撮合，状态会变为 ACCEPTED → FILLED/PARTIALLY_FILLED/REJECTED）
- REST: `POST /exchange/callbacks/register` 注册 Webhook 回调地址，撮合完成后异步推送订单状态变更与成交通知（模拟 CTP `OnRtnOrder` / `OnRtnTrade`）
- REST: `GET /exchange/marketdata/{symbol}` 拉取最新价
- 内存存储，重启丢失（本期模拟用）

**异步两阶段撮合流程**：
```
1. EMS → sim-exchange: POST /exchange/orders（下单）
2. sim-exchange: 参数校验 → 通过则入订单表(状态=NEW) → 同步返回订单对象
3. sim-exchange: 异步撮合线程延迟 N ms 后撮合
   - 更新订单状态为 ACCEPTED → FILLED/PARTIALLY_FILLED/REJECTED
4. sim-exchange → EMS: POST {ems_webhook}/execution/callback/order（订单状态回报，模拟 OnRtnOrder）
5. sim-exchange → EMS: POST {ems_webhook}/execution/callback/trade（成交通知，模拟 OnRtnTrade）
```

**与真实交易所的语义对齐**：
| 真实交易所 | 模拟实现 |
|---|---|
| CTP `ReqOrderInsert` / FIX `NewOrderSingle` | `POST /exchange/orders`（同步受理） |
| CTP `OnRtnOrder` 订单状态回报 | Webhook `POST /execution/callback/order` |
| CTP `OnRtnTrade` 成交通知 | Webhook `POST /execution/callback/trade` |
| TCP 长连接 + SDK 回调 | HTTP Webhook 回调 |
| 做市商内部 Kafka 分发 | Kafka trade-event / hedge-fill-event（不变） |

**行情波动模型**：几何布朗运动（Geometric Brownian Motion）
```
S_{t+Δt} = S_t * exp((μ - 0.5σ²)Δt + σ√Δt * Z)
Z ~ N(0,1) 标准正态分布
μ: 漂移率（日收益率，配置项）
σ: 波动率（年化波动率，配置项）
Δt: 时间步长（配置项，如 1秒 = 1/86400 天）
```

**配置文件示例**：
```yaml
sim-exchange:
  symbols:
    - code: "AU2406"
      name: "黄金2406"
      initialPrice: 520.50
      volatility: 0.15    # 年化波动率 15%
      drift: 0.05         # 年化漂移率 5%
      tickSize: 0.02
      multiplier: 1000
    - code: "AG2406"
      name: "白银2406"
      initialPrice: 6280.0
      volatility: 0.25
      drift: 0.03
      tickSize: 1.0
      multiplier: 15
  intervalMs: 1000  # 行情推送间隔
```

### 3.12 前端：报价监控面板
- **最简实现**：单页 HTML + JavaScript（或 Vue 最简版）
- 功能：实时展示交易所报价 + 做市商报价表格
- 可调刷新频率（1s / 5s / 10s / 30s）
- 数据来源：REST 轮询或 WebSocket 推送
- 字段：合约代码、交易所买价、交易所卖价、银行买价、银行卖价、涨跌、涨跌幅

---

## 四、一致性方案（重点章节）

### 4.1 一致性分层策略

| 业务场景 | 一致性要求 | 机制 |
|---|---|---|
| 客户下单 → 风控 → 受理 | 强一致 | 同步 REST + 本地事务 |
| 成交 → 持仓/额度/对冲 | 最终一致 + 可审计 | Outbox + Kafka + 幂等消费 |
| 行情分发 | 允许少量丢/乱序 | 普通 Kafka |
| 报价推送 | 最终一致 | At-Most-Once |

### 4.2 Transactional Outbox 模式

【参考来源：**Transactional Outbox 模式**】— 微服务架构中解决"数据库更新与消息发送一致性"的经典设计模式，是企业集成模式（EIP）和微服务设计模式中的标准方案。核心思想是：把"要发的消息"当作业务数据的一部分，和业务数据在同一个本地事务里写入数据库，然后由一个独立的 Relay 进程异步读取并发送到消息队列。这样利用数据库的本地事务保证"业务变更和事件记录要么都成功，要么都失败"，彻底避免事件丢失。

**核心思想**：业务状态变更和事件记录放在同一个本地事务里，保证要么都成，要么都败。

```
OMS 处理成交（单次本地事务）：
  1. UPDATE orders SET status='FILLED' WHERE id=? AND version=?
  2. INSERT INTO positions ...
  3. INSERT INTO outbox(event_id, topic, key, payload, status, created_at)
     VALUES('evt-001','trade-event','C001',{...},'PENDING',now)
  4. INSERT INTO event_store(...)   -- 同事务写入事件溯源库
  -- 事务提交

Outbox Relay（定时任务轮询）：
  5. SELECT * FROM outbox WHERE status='PENDING' ORDER BY id LIMIT 100
     FOR UPDATE SKIP LOCKED
  6. 发送到 Kafka（带重试）
  7. UPDATE outbox SET status='SENT', sent_at=now WHERE id=?
```

### 4.3 Kafka 顺序保证

【参考来源：**LMAX Disruptor**】— LMAX 交易所开源的高性能无锁环形缓冲区框架，其核心设计思想是"单线程消费 + 环形缓冲 + 按序处理"，用极简的方式实现了极低延迟的事件处理。本系统借鉴了这一思想：同一客户的事件通过分区键路由到同一 Kafka 分区（类比环形缓冲的一个槽位序列），单消费者单分区处理（类比单线程消费），从而在分布式环境下保证同一客户事件的严格有序，同时通过多分区实现整体并行。

**分区策略**：
- `trade-event` topic：32 分区，`partitionKey = customerId`
- 同一客户的事件路由到同一分区 → 天然有序
- 单消费者单分区 → 处理顺序 = 入队顺序

**事件序列号**：
```
TradeEvent {
  eventId: "evt-xxxx"
  customerId: "C001"
  eventSeq: 7           ← 该客户第N个事件，单调递增
  occurredAt: timestamp
  payload: {...}
}
```
消费端发现跳号 → 告警 + 主动从 event_store 补偿。

**跨客户时序说明**：
做市商对客户是"对手方成交"（principal-to-principal），而非交易所撮合模式。
客户 A 与客户 B 的成交是相互独立的双边交易，做市商的义务是"持续报价 + 按报价成交"，
而非"按请求到达顺序先后处理"。因此：
- 同客户事件需严格保序（先买先扣持仓、先成交先扣信用）—— 通过 customerId 分区保证 ✅
- 跨客户状态相互独立，可并行处理 —— 多分区并行消费即可
- 做市商全局敞口是"最终一致"的聚合读模型，毫秒级延迟可接受，无需全局 FIFO

### 4.4 幂等消费

每个消费端维护 `processed_events` 表：
```sql
CREATE TABLE processed_events (
  event_id VARCHAR(64) PRIMARY KEY,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

消费处理逻辑：
```
BEGIN;
  -- 执行业务操作
  INSERT INTO positions ... ;
  -- 写入幂等表
  INSERT INTO processed_events(event_id) VALUES(?);
COMMIT;
```
重复消费时 INSERT 主键冲突 → 整个事务回滚 → 副作用回滚 → 幂等。

### 4.5 EventStore 事件溯源

【参考来源：**Event Sourcing（事件溯源）**】— 领域驱动设计（DDD）中的核心架构模式，由 Martin Fowler 等大师推广。核心思想是：不直接保存当前状态，而是把所有导致状态变更的事件以 append-only 的方式记录下来；当前状态可以通过从头回放所有事件来重建。这种模式的优势是：完整的审计追溯能力、可任意时间点重建状态、事件可重放用于测试和对账。本系统的 event_store 表就是事件溯源的实现，同时保留了当前状态表（orders/positions 等）以提高查询性能，是"事件溯源 + CQRS"的简化版。

所有业务事件写入 `event_store` 表（append-only）：
```sql
CREATE TABLE event_store (
  event_id        VARCHAR(64) PRIMARY KEY,
  topic           VARCHAR(64),
  partition_key   VARCHAR(64),   -- customerId
  event_seq       BIGINT,        -- 该客户的事件序号
  aggregate_type  VARCHAR(32),   -- Order / Position / Account
  aggregate_id    VARCHAR(64),   -- orderId / customerId
  event_type      VARCHAR(32),   -- OrderCreated / TradeFilled / ...
  payload         TEXT,          -- JSON
  occurred_at     TIMESTAMP,
  produced_by     VARCHAR(64),   -- 服务实例ID
  trace_id        VARCHAR(64)    -- 链路ID
);
CREATE INDEX idx_key_seq ON event_store(partition_key, event_seq);
CREATE INDEX idx_aggregate ON event_store(aggregate_type, aggregate_id);
```

**用途**：
- 审计追溯：按客户/订单查完整事件链
- 状态重建：重放事件重建任意时刻的状态
- 对账兜底：重放计算值 vs 实际状态表比对

### 4.6 对账与自愈

定时对账任务（每小时 / 每日）：
1. 从 event_store 重放计算理论持仓、理论额度
2. 与 positions / accounts 实际表比对
3. 与 sim-exchange 对冲成交记录比对
4. 不一致 → 告警 + 生成补偿事件

### 4.7 全链路追踪

- 每个外部请求生成 `traceId`
- REST 调用通过 HTTP header 透传
- Kafka 通过消息 header 透传
- 日志 MDC 打印 traceId
- 审计可还原单笔订单全链路

### 4.8 Kafka 关键配置

```yaml
spring:
  kafka:
    producer:
      acks: all
      enable-idempotence: true
      retries: 2147483647
      max-in-flight-requests-per-connection: 5
    consumer:
      enable-auto-commit: false
      isolation-level: read_committed
      auto-offset-reset: earliest
```

---

## 五、分片策略

【参考来源：**ShardingSphere**】— 开源的分库分表中间件，提供了完整的分片策略（哈希分片、范围分片、复合分片等）、读写分离、分布式事务等能力。本系统借鉴其"分片路由 + 逻辑分片到物理分片映射"的设计思想：通过 `ShardRouter` 接口计算分片，通过 `DataSourceProvider` 路由到物理数据源。不同的是，本期不引入 ShardingSphere 框架，而是自己实现轻量级分片路由，保持简单可控，未来如需更复杂的分片能力可平滑迁移到 ShardingSphere。

### 5.1 本期：逻辑分片（单库）
- 所有业务表带 `shard_id INT NOT NULL` 字段
- 所有查询/更新 SQL 必须带 `WHERE shard_id = ?`
- `ShardRouter` 接口：`int shardOf(String key)`
- 默认实现：`SingleShardRouter`，恒返 0
- `DynamicDataSource`：单数据源

### 5.2 未来：物理分片扩展路径
```
阶段1：单库逻辑分片（本期）
  ↓
阶段2：物理分片（同一数据库类型，多数据源）
  - 替换 ShardRouter → HashShardRouter（64分片）
  - 替换 DataSourceProvider → MultiDataSourceProvider
  - 数据迁移：按 shard_id 拆分到各物理库
  ↓
阶段3：跨数据库类型切换（SQLite → PostgreSQL）
  - 替换 datasource 配置 + Driver
  - MyBatis databaseId 处理方言差异
  - 分片逻辑完全不变
```

### 5.3 分片扩展接口

```java
// common-core 中定义
public interface ShardRouter {
    int shardOf(String key);
    int totalShards();
}

// common-persistence 中定义
public interface ShardDataSourceProvider {
    DataSource getDataSource(int shardId);
    int totalShards();
}
```

---

## 六、数据库可切换方案

### 6.1 实现策略

1. **Spring Profile 切换数据源**
   - `application-sqlite.yml`：SQLite Driver + jdbc:sqlite:trading.db
   - `application-postgres.yml`：PostgreSQL Driver + jdbc:postgresql://...

2. **MyBatis databaseId 处理方言**
   - 配置 `DatabaseIdProvider`
   - Mapper XML 中用 `databaseId="sqlite"` / `databaseId="postgresql"` 区分

3. **SQL 兼容性约束**
   - 主键自增：SQLite 用 `AUTOINCREMENT`，PG 用 `SERIAL/IDENTITY`
   - 时间字段：统一用 Java 生成后写入，避免数据库函数差异
   - 字符串类型：统一用 `VARCHAR` / `TEXT`

4. **Schema 迁移**
   - 用 Flyway，按 `db/sqlite/`、`db/postgres/` 分目录维护脚本

5. **依赖隔离**
   - 两个 JDBC Driver 都在 pom 中
   - 按 Profile 决定连接哪个

### 6.2 默认数据库
- 本地开发 / 模拟测试：SQLite
- 生产 / 压测：PostgreSQL

---

## 七、技术栈

| 层次 | 选型 |
|---|---|
| 微服务框架 | Spring Boot 3.x + Spring Cloud 2023.x |
| 服务注册 | Eureka |
| 配置中心 | Spring Cloud Config |
| 网关 | Spring Cloud Gateway |
| 持久层 | MyBatis-Spring-Boot-Starter 3.x |
| 数据库 | SQLite（默认） / PostgreSQL（可切换） |
| 缓存 | Redis（行情/会话） |
| 消息总线 | Kafka |
| 实时推送 | Spring WebSocket |
| 风控 | 纯 Java（预留 Drools 接口） |
| 前端 | 最简 HTML/JS 或 Vue 轻量版 |
| 构建工具 | Maven 多模块 |

---

## 八、工程结构

```
trading-system/
├── pom.xml                              (parent)
├── docs/
│   └── design.md                        (本文档)
├── common/
│   ├── common-core/                     (DTO/枚举/异常/ShardRouter/Result)
│   └── common-persistence/              (MyBatis基类/DataSource/Outbox/EventStore)
├── infra/
│   ├── eureka-server/
│   └── config-server/
├── services/
│   ├── refdata-service/
│   ├── market-data-service/
│   ├── pricing-service/
│   ├── oms-service/
│   ├── risk-service/
│   ├── execution-service/
│   ├── position-service/
│   ├── account-service/
│   ├── outbox-relay-service/            (Outbox 消息投递)
│   ├── notify-service/                  (WebSocket 推送 + Kafka 消费)
│   ├── reconciliation-service/          (定时跨服务对账)
│   └── gateway/                         (API 网关)
├── sim/
│   ├── sim-exchange/                    (模拟期货交易所，独立进程)
│   └── sim-client/                      (模拟客户，独立进程)
├── tests/
│   └── integration-tests/               (端到端集成测试)
├── frontend/                            (报价监控面板)
└── db/
    ├── sqlite/                          (SQLite Flyway 迁移脚本)
    └── postgres/                        (PostgreSQL Flyway 迁移脚本)
```

---

## 九、关键业务流程

### 9.1 客户下单成交主流程

```
1. 客户 → Gateway: POST /api/orders {symbol, side, qty, type, clientOrderId}
2. Gateway → OMS: 路由到 oms-service
3. OMS: 幂等校验（clientOrderId 唯一索引）
4. OMS → Risk: POST /risk/pre-trade (同步事前风控)
   4.1 Risk → Account: 查信用额度
   4.2 Risk → RefData: 查合约参数
   4.3 Risk → Position: 查当前持仓
   4.4 Risk 返回通过/拒绝
5. OMS: 风控通过 → 做市商以报价与客户立即成交（做市商即对手方，订单状态=FILLED）
6. OMS 本地事务:
   - 更新 orders 表状态为 FILLED
   - 写入 trades 表（客户成交记录）
   - 写入 outbox 表
   - 写入 event_store 表
7. OMS 返回客户: 订单已成交（FILLED，含成交价与成交量）
8. Outbox Relay: 轮询 outbox → 发送到 Kafka trade-event
9. 并发消费者（幂等处理）:
   - Position: 更新客户头寸
   - Account: 扣减可用额度
   - Risk: 事后敞口检查
   - Execution: 生成对冲单
10. Execution → Sim-Exchange: POST /exchange/orders (对冲下单)
    - sim-exchange 同步返回订单受理（NEW），不返回成交
    - sim-exchange 异步撮合后通过 Webhook 回调推送成交
11. Execution 收到 Webhook 成交通知 → Kafka: 发布 hedge-fill-event
12. Position: 更新对冲头寸，计算净敞口
13. Notify → 客户: WebSocket 推送成交回报
```

### 9.2 行情到客户报价流程

```
1. Sim-Exchange: GBM 模型生成行情 → WebSocket 广播
2. MarketData: 接收行情 → 归一化 → 缓存 Redis
3. MarketData → Kafka: 发布 market-data
4. Pricing: 消费行情 → 计算 spread → 生成客户双边报价
5. Pricing → Kafka: 发布 customer-quote
6. Notify: 消费 customer-quote → WebSocket 推送给客户
7. 前端: 轮询 REST 或 WebSocket 展示报价
```

---

## 十、风控模块设计

【参考来源：**Drools + Esper/Siddhi**】— 风控是金融系统的核心模块，行业通常采用"规则引擎 + CEP 引擎"的组合：
- **Drools** 负责可配置的规则（如限额、阈值），业务人员可调整
- **Esper/Siddhi** 负责实时事件流的模式匹配（如"5分钟内3次异常交易"）

本系统本期用纯 Java 实现，但通过 `RiskRuleEngine` 接口和策略模式，将规则逻辑与业务逻辑解耦，为未来接入 Drools 或 CEP 引擎预留了扩展点。

### 10.1 风控规则列表

| 规则 | 类型 | 说明 |
|---|---|---|
| 客户信用额度检查 | 事前 | 订单金额 ≤ 可用信用额度 |
| 单笔订单限额 | 事前 | 单笔数量 ≤ 合约单笔上限 |
| 单日累计限额 | 事前 | 当日累计成交 ≤ 客户日限额 |
| 持仓限额 | 事前 | 持仓 + 新增 ≤ 客户持仓上限 |
| 价格偏离度检查 | 事前 | 订单价格偏离市场中间价 ≤ 阈值 |
| 净敞口限额 | 事后 | 银行整体净敞口 ≤ 风险限额 |
| 止损预警 | 事后 | 持仓浮亏 ≥ 阈值 → 告警 |

### 10.2 实现方式

**本期**：纯 Java + 策略模式
```java
public interface RiskRule {
    RiskCheckResult check(RiskCheckContext context);
}

// 具体规则实现
public class CreditLimitRule implements RiskRule { ... }
public class SingleOrderLimitRule implements RiskRule { ... }
// ...

// 规则引擎接口（预留 Drools 接入点）
public interface RiskRuleEngine {
    RiskCheckResult evaluate(RiskCheckContext context);
}

// 默认实现：遍历规则链
public class DefaultRiskRuleEngine implements RiskRuleEngine {
    private final List<RiskRule> rules;
    // ...
}

// 未来 Drools 实现
// public class DroolsRiskRuleEngine implements RiskRuleEngine { ... }
```

**未来接入 Drools**：实现 `DroolsRiskRuleEngine` 替换默认实现，业务代码零改动。

---

## 十一、数据模型（核心表）

【参考来源：**QuickFIX/J**】— 订单、成交、持仓等核心业务表的字段设计参考了 FIX 协议的标准消息字段，如：
- `client_order_id` 对应 FIX 的 `ClOrdID`（客户订单号，用于幂等）
- `order_id` 对应 FIX 的 `OrderID`（交易所/做市商订单号）
- 订单状态枚举对应 FIX 的 `OrdStatus`
- `side`（买卖方向）、`type`（订单类型）等字段命名均遵循 FIX 惯例

这样设计的好处是：未来如果需要接入真实 FIX 协议，字段映射成本极低。

### 11.1 公共表

**outbox 表**
```sql
CREATE TABLE outbox (
  id          INTEGER PRIMARY KEY,
  event_id    VARCHAR(64) NOT NULL UNIQUE,
  topic       VARCHAR(64) NOT NULL,
  partition_key VARCHAR(64),
  payload     TEXT NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING/SENT/FAILED
  retry_count INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMP NOT NULL,
  sent_at     TIMESTAMP,
  shard_id    INT NOT NULL
);
CREATE INDEX idx_outbox_status ON outbox(status, id);
```

**event_store 表**
```sql
CREATE TABLE event_store (
  event_id        VARCHAR(64) PRIMARY KEY,
  topic           VARCHAR(64),
  partition_key   VARCHAR(64),
  event_seq       BIGINT,
  aggregate_type  VARCHAR(32),
  aggregate_id    VARCHAR(64),
  event_type      VARCHAR(32),
  payload         TEXT,
  occurred_at     TIMESTAMP,
  produced_by     VARCHAR(64),
  trace_id        VARCHAR(64),
  shard_id        INT NOT NULL
);
CREATE INDEX idx_es_key_seq ON event_store(partition_key, event_seq);
CREATE INDEX idx_es_aggregate ON event_store(aggregate_type, aggregate_id);
```

**processed_events 表**（每个消费服务独立）
```sql
CREATE TABLE processed_events (
  event_id     VARCHAR(64) PRIMARY KEY,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 11.2 refdata-service

**contract 表**
```sql
CREATE TABLE contract (
  id          INTEGER PRIMARY KEY,
  code        VARCHAR(32) NOT NULL UNIQUE,
  name        VARCHAR(64),
  exchange    VARCHAR(32),
  product     VARCHAR(32),
  multiplier  DECIMAL(18,4),
  tick_size   DECIMAL(18,8),
  min_qty     DECIMAL(18,4),
  listed_date DATE,
  expiry_date DATE,
  status      VARCHAR(16),
  created_at  TIMESTAMP,
  updated_at  TIMESTAMP
);
```

### 11.3 account-service

**customer 表**
```sql
CREATE TABLE customer (
  id           INTEGER PRIMARY KEY,
  customer_id  VARCHAR(32) NOT NULL UNIQUE,
  name         VARCHAR(128),
  level        VARCHAR(16),   -- VIP/NORMAL
  status       VARCHAR(16),
  credit_limit DECIMAL(20,2), -- 信用额度
  created_at   TIMESTAMP,
  updated_at   TIMESTAMP,
  shard_id     INT NOT NULL
);
```

### 11.4 oms-service

**orders 表**
```sql
CREATE TABLE orders (
  id               INTEGER PRIMARY KEY,
  order_id         VARCHAR(32) NOT NULL UNIQUE,
  client_order_id  VARCHAR(64),
  customer_id      VARCHAR(32) NOT NULL,
  symbol           VARCHAR(32) NOT NULL,
  side             VARCHAR(8) NOT NULL,   -- BUY/SELL
  type             VARCHAR(16) NOT NULL,  -- MARKET/LIMIT
  qty              DECIMAL(18,4) NOT NULL,
  filled_qty       DECIMAL(18,4) NOT NULL DEFAULT 0,
  price            DECIMAL(18,8),
  avg_price        DECIMAL(18,8),
  status           VARCHAR(24) NOT NULL,
  reject_reason    VARCHAR(256),
  version          INT NOT NULL DEFAULT 0,
  created_at       TIMESTAMP NOT NULL,
  updated_at       TIMESTAMP,
  shard_id         INT NOT NULL
);
CREATE INDEX idx_orders_customer ON orders(customer_id, created_at);
CREATE UNIQUE INDEX idx_orders_cl_ord ON orders(customer_id, client_order_id);
```

### 11.5 position-service

**position 表**
```sql
CREATE TABLE position (
  id           INTEGER PRIMARY KEY,
  customer_id  VARCHAR(32) NOT NULL,
  symbol       VARCHAR(32) NOT NULL,
  qty          DECIMAL(18,4) NOT NULL,  -- 正=多,负=空
  avg_cost     DECIMAL(18,8),
  realized_pnl DECIMAL(20,2),
  unrealized_pnl DECIMAL(20,2),
  version      INT NOT NULL DEFAULT 0,
  created_at   TIMESTAMP,
  updated_at   TIMESTAMP,
  shard_id     INT NOT NULL
);
CREATE UNIQUE INDEX idx_pos_cust_sym ON position(customer_id, symbol, shard_id);
```

**hedge_position 表**
```sql
CREATE TABLE hedge_position (
  id           INTEGER PRIMARY KEY,
  symbol       VARCHAR(32) NOT NULL UNIQUE,
  qty          DECIMAL(18,4) NOT NULL,
  avg_cost     DECIMAL(18,8),
  version      INT NOT NULL DEFAULT 0,
  updated_at   TIMESTAMP
);
```

---

## 十二、实施优先级

| 阶段 | 模块 | 说明 |
|---|---|---|
| P0 | common-core, common-persistence | 公共层，骨架 |
| P0 | eureka-server, config-server | 基础设施 |
| P0 | sim-exchange | 模拟交易所（没有行情源无法联调） |
| P0 | refdata-service | 基础数据 |
| P1 | market-data-service, pricing-service | 行情与报价（做市核心） |
| P1 | oms-service, risk-service | 订单与风控（交易核心） |
| P1 | frontend（报价面板） | 前端展示 |
| P2 | execution-service, position-service, account-service | 闭环 |
| P2 | outbox-relay-service | Outbox 消息投递，事件分发保障 |
| P3 | notify-service, sim-client, gateway | 完善与联调 |
| P3 | reconciliation-service | 定时跨服务对账（敞口/额度/对冲挂单） |
