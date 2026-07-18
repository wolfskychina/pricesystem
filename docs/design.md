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

### 1.3 行业参考
- QuickFIX/J（FIX 协议，本期用 REST/WebSocket 替代）
- LMAX Disruptor（低延迟队列思想）
- Esper/Siddhi（CEP，本期暂不引入）
- Drools（规则引擎，本期预留接口）
- OpenMAMA（行情分发思想）

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
                                └──────┬──────┘
       ┌──────────────────────────────┼──────────────────────────────┐
       │                              │                              │
┌──────▼───────┐  ┌────────────┐  ┌──▼─────────┐  ┌────────────┐  ┌──▼──────────┐
│ Quote Service│  │ OMS Service│  │Pricing Svc │  │ Risk Svc   │  │Account Svc  │
│  (报价查询)  │  │ (订单管理) │  │ (做市报价) │  │ (风控)     │  │ (客户账户)  │
└──────┬───────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────────────┘
       │                │               │               │
       └────────────────┴───────┬───────┴───────────────┘
                               │
                   ┌───────────▼────────────┐
                   │   Kafka 消息总线        │
                   └───────────┬────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼────────┐  ┌──────────▼─────────┐  ┌────────▼────────┐
│ MarketData Svc │  │ Execution Svc      │  │ Position Svc    │
│ (行情接入)     │  │ (对冲执行)         │  │ (持仓管理)      │
└───────┬────────┘  └──────────┬─────────┘  └─────────────────┘
        │                      │
        │           ┌──────────▼────────────┐
        │           │  Simulated Exchange   │ ← 模拟期货交易所
        └──────────►│  (独立进程,随机波动)   │
                    └───────────────────────┘
```

辅助服务：Eureka（注册发现）、Config Server（配置中心）、RefData Service（基础数据）、Notify Service（推送）。

### 2.2 服务清单

| 服务 | 职责 | 优先级 |
|---|---|---|
| eureka-server | 服务注册与发现 | P0 |
| config-server | 配置中心 | P0 |
| gateway | API 网关 | P3 |
| refdata-service | 基础数据（合约/交易日历） | P0 |
| market-data-service | 行情接入与分发 | P1 |
| pricing-service | 做市报价计算 | P1 |
| oms-service | 订单管理 | P1 |
| risk-service | 风控 | P1 |
| execution-service | 对冲执行 | P2 |
| position-service | 持仓管理 | P2 |
| account-service | 客户账户 | P2 |
| notify-service | 消息推送（WebSocket） | P3 |
| sim-exchange | 模拟期货交易所 | P0 |
| sim-client | 模拟客户 | P3 |
| frontend | 报价监控面板 | P2 |

---

## 三、模块详细设计

### 3.1 market-data-service（行情接入服务）
- 连接模拟交易所 WebSocket，订阅行情
- 归一化为统一 `MarketData` 模型
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
- 接收客户订单（市价/限价）
- 订单状态机：NEW → PENDING_RISK → ACCEPTED → PARTIALLY_FILLED → FILLED / REJECTED / CANCELLED
- 调用 risk-service 做事前风控（同步 REST）
- 做市商即对手方，撮合即接受
- 成交后发布 `trade-event` 事件（经 Outbox）

### 3.4 risk-service（风控服务）
- 事前风控：信用额度、单笔限额、持仓限额、价格偏离度
- 事中风控：实时敞口监控
- 纯 Java 实现，预留 Drools 接入接口 `RiskRuleEngine`
- REST: `POST /risk/pre-trade` 同步调用

### 3.5 execution-service（对冲执行服务）
- 消费 `trade-event`
- 计算对冲方向和数量
- 调用模拟交易所 REST 下单
- 接收期货成交通知，回写持仓
- 发布 `hedge-fill-event`

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
- 订阅 Kafka 多主题
- WebSocket 推送给连接的客户
- 支持按客户订阅主题

### 3.10 sim-exchange（模拟期货交易所）
- **独立进程** Spring Boot 应用
- 从配置文件读取初始报价
- 按几何布朗运动（GBM）生成连续随机波动行情，接近真实市场
- WebSocket 广播行情
- REST: `POST /exchange/orders` 接受对冲单，立即撮合成交
- REST: `GET /exchange/marketdata/{symbol}` 拉取最新价
- 内存存储，重启丢失（本期模拟用）

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

### 3.11 前端：报价监控面板
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
│   ├── common-core/                     (DTO/枚举/异常/ShardRouter)
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
│   ├── notify-service/
│   └── gateway/
├── sim/
│   ├── sim-exchange/                    (模拟期货交易所，独立进程)
│   └── sim-client/                      (模拟客户，独立进程)
├── frontend/                            (报价监控面板)
└── db/
    ├── sqlite/
    │   └── V1__init.sql
    └── postgres/
        └── V1__init.sql
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
5. OMS: 风控通过 → 撮合成交（做市商即对手方）
6. OMS 本地事务:
   - 更新 orders 表状态
   - 写入 outbox 表
   - 写入 event_store 表
7. OMS 返回客户: 订单已受理
8. Outbox Relay: 轮询 outbox → 发送到 Kafka trade-event
9. 并发消费者（幂等处理）:
   - Position: 更新客户头寸
   - Account: 扣减可用额度
   - Risk: 事后敞口检查
   - Execution: 生成对冲单
10. Execution → Sim-Exchange: POST /exchange/orders (对冲下单)
11. Execution → Kafka: 发布 hedge-fill-event
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
| P3 | notify-service, sim-client, gateway | 完善与联调 |
| P3 | 对账任务 | 一致性兜底 |
