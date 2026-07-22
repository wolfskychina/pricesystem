# Bank Market Maker Trading System — Final Design Document

## 1. System Overview

### 1.1 Background

The bank acts as a market maker in financial markets, tracking commodity quotes from major futures exchanges, providing two-sided quotes to clients, accepting and executing client orders, and simultaneously hedging positions in the futures market.

### 1.2 Design Objectives

- Loosely coupled, highly cohesive microservices architecture
- Hybrid event-driven and synchronous invocation with guaranteed consistency
- Swappable database backends (SQLite ↔ PostgreSQL)
- Horizontal scalability (logical sharding now, physical sharding ready)
- Testability (simulated exchange + simulated client)

### 1.3 Industry References

The following open-source projects and industry practices informed this design. Each reference is annotated with 【Reference】 in the relevant section.

| Reference Project | Description | What We Borrowed | Section |
|---|---|---|---|
| **QuickFIX/J** | Industry-standard Java implementation of the FIX protocol for order/market data communication between exchanges and broker-dealers | Order message model, market data format, order state machine concepts | 3.3, 3.1, Chapter 11 |
| **LMAX Disruptor** | High-performance lock-free ring buffer framework from LMAX Exchange, designed for ultra-low-latency event processing | Event-driven architecture, single-consumer-per-partition ordering, ring buffer decoupling | 4.3, Overall Architecture |
| **Event Sourcing** | DDD architectural pattern using an append-only event log as the sole source of truth | `event_store` table design, state replayability, audit traceability | 4.5 |
| **Transactional Outbox** | Classic microservices pattern for ensuring consistency between database updates and message publishing | Outbox table + Relay process to guarantee at-least-once event delivery | 4.2 |
| **Esper / Siddhi (CEP)** | Complex Event Processing engines for real-time stream pattern matching and aggregation | Real-time risk control and exposure monitoring concepts (not directly integrated this phase; risk engine is pure Java) | Chapter 10 |
| **Drools** | Open-source business rule engine that externalizes rules from code and supports hot updates | Rule externalization design philosophy; rule engine interface reserved | Chapter 10 |
| **OpenMAMA** | Open-source middleware-agnostic market data API that abstracts away underlying messaging differences | Market data normalization: heterogeneous exchange formats → unified `MarketData` model | 3.1 |
| **ShardingSphere** | Open-source database sharding middleware with multiple sharding strategies | Shard routing and horizontal scaling by `shard_id` | Chapter 5 |
| **Geometric Brownian Motion (GBM)** | Classical stochastic process in financial mathematics describing asset price dynamics (basis of Black-Scholes) | Simulated exchange market data generation for realistic price behavior | 3.10 |

> **Note:** QuickFIX/J, LMAX Disruptor, Esper, Drools, OpenMAMA, and ShardingSphere are widely adopted open-source projects or classical architectural patterns in the financial and internet industries. This design **does not directly embed these frameworks** (except for a reserved Drools interface). Instead, it borrows their design principles and implements lightweight alternatives to reduce learning and maintenance overhead.

---

## 2. Overall Architecture

### 2.1 Architecture Overview

```
                        ┌─────────────────────────────┐
                        │   Frontend: Quote Monitor    │
                        │  (Real-time quotes table,    │
                        │   adjustable refresh rate)   │
                        └──────────────┬──────────────┘
                                       │ REST/WebSocket
                                ┌──────▼──────┐
                                │ API Gateway │
                                │ (TraceId    │
                                │  injection, │
                                │  routing)   │
                                └──────┬──────┘
       ┌──────────────────────────────┼──────────────────────────────┐
       │                              │                              │
┌──────▼───────┐  ┌────────────┐  ┌──▼─────────┐  ┌────────────┐  ┌──▼──────────┐
│ Quote Service│  │ OMS Service│  │Pricing Svc │  │ Risk Svc   │  │Account Svc  │
│ (Quote Query)│  │(Order Mgmt)│  │(Market Mk) │  │(Risk Ctrl) │  │(Client Acct)│
└──────┬───────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └──────┬───────┘
       │                │               │               │              │
       └────────────────┴───────┬───────┴───────────────┘              │
                               │                                       │
                   ┌───────────▼────────────┐                          │
                   │   Kafka Message Bus    │                          │
                   └───────────┬────────────┘                          │
                               │                                       │
        ┌──────────────────────┼──────────────────────┐               │
        │                      │                      │               │
┌───────▼────────┐  ┌──────────▼─────────┐  ┌────────▼────────┐       │
│ MarketData Svc │  │ Execution Svc      │  │ Position Svc    │       │
│(Mkt Data Ingest│  │(Hedge Execution)   │  │(Position Mgmt)  │       │
└───────┬────────┘  └──────────┬─────────┘  └────────┬────────┘       │
        │                      │                      │               │
        │           ┌──────────▼────────────┐         │               │
        │           │  Simulated Exchange   │ ← Simulated futures     │
        └──────────►│  (Standalone process, │   exchange              │
                    │   stochastic process) │         │               │
                    └───────────────────────┘         │               │
                                                       │               │
        ┌──────────────────────────────────────────────┼───────────────┘
        │                                              │
┌───────▼────────┐                          ┌──────────▼─────────┐
│ Notify Svc     │                          │ Reconciliation Svc │
│(WebSocket Push)│                          │(Scheduled Recon)   │
└───────┬────────┘                          └───────────────────┘
        │
        │ Consumes trade-event / hedge-fill-event
        │
        ▼
┌────────────────────┐
│  Client WebSocket  │
│ (Per-customer sub) │
└────────────────────┘
```

Auxiliary services:
- **Eureka** (service registry & discovery), **Config Server** (configuration center), **RefData Service** (reference data)
- **Outbox Relay Service**: standalone process that polls each service's outbox table and delivers events to Kafka
- **Sim Client**: simulated client order placement for load testing and integration validation

### 2.2 Service Inventory

| Service | Responsibility | Priority |
|---|---|---|
| eureka-server | Service registration and discovery | P0 |
| config-server | Configuration center | P0 |
| gateway | API gateway (traceId injection, routing, CORS) | P3 |
| refdata-service | Reference data (contracts / trading calendars) | P0 |
| market-data-service | Market data ingestion and distribution | P1 |
| pricing-service | Market maker quote calculation | P1 |
| oms-service | Order management | P1 |
| risk-service | Risk control | P1 |
| execution-service | Hedge execution | P2 |
| position-service | Position management | P2 |
| account-service | Client account management | P2 |
| notify-service | Notification push (WebSocket + Kafka multi-topic consumption) | P3 |
| reconciliation-service | Scheduled cross-service reconciliation (exposure / credit / hedge orders) | P3 |
| outbox-relay-service | Outbox event delivery (poll outbox table → Kafka) | P2 |
| sim-exchange | Simulated futures exchange (async two-phase matching + GBM market data) | P0 |
| sim-client | Simulated client order placement (batch / single, for load testing and integration) | P3 |
| frontend | Quote monitoring dashboard | P2 |

---

## 3. Module Detailed Design

### 3.1 market-data-service (Market Data Ingestion Service)

【Reference: **OpenMAMA**】— Borrows the concept of "abstracting away underlying market data source differences and exposing a unified market data model." OpenMAMA is an open-source, middleware-agnostic market data API that supports ingesting market data from different exchanges and exposing it in a unified format. This system's market-data-service follows a similar approach: it ingests market data from the sim-exchange (with future support for real exchanges), normalizes it, and publishes it in a unified format.

- Connects to the simulated exchange via WebSocket and subscribes to market data
- Normalizes data into a unified `MarketData` model (abstracts away exchange-specific formats)
- Caches latest market data in Redis
- Publishes to the `market-data` Kafka topic
- REST: `GET /marketdata/{symbol}` — query latest price

### 3.2 pricing-service (Market Maker Pricing Service)

【Reference: **CME / Major Commodity Exchange Market Maker Practices**】— Quote frequency is benchmarked against domestic futures markets (CFFEX / SHFE / DCE / CZCE):

| Stage | Frequency | Implementation |
|-------|-----------|----------------|
| Exchange market data push | 500ms | sim-exchange GBM model generation |
| market-data normalization | Event-driven | WebSocket real-time subscription |
| pricing quote calculation | Event-driven + throttle (500ms) | Triggered by market data changes; same-symbol updates within 500ms are coalesced |
| customer-quote push | 500ms–1s | Published immediately after pricing completes |
| notify WebSocket push | 500ms–1s | Pushed by notify-service after subscription |
| REST quote query | On-demand | Redis cache, O(1) return |
| Quote TTL | 3 seconds | Auto-expiry to prevent slippage |

**Throttle Strategy**:
- pricing-service maintains an internal `(symbol, last_quote_time)` cache
- Multiple market data updates for the same symbol within 500ms trigger only one quote recalculation
- Prevents high-frequency push storms during volatile markets, protecting WebSocket clients
- Throttle window is configurable (`pricing.throttle.window-ms=500`)

**Quote TTL Design**:
- Each quote message carries an `expire_at` field (generation time + 3 seconds)
- notify-service includes this field when pushing to clients
- If the quote has expired at order submission time, the client is forced to re-request a quote (`POST /quotes/rfq`)
- Prevents clients from executing against stale quotes (e.g., 5 seconds old) in fast markets, which would cause slippage

- Subscribes to the `market-data` topic
- Calculates client bid/ask prices based on futures market data + spread
- Spread is configured per contract and client tier
- Publishes to the `customer-quote` topic
- REST: `GET /quotes/{symbol}`, `POST /quotes/rfq`

### 3.3 oms-service (Order Management Service)

【Reference: **QuickFIX/J**】— Borrows order management concepts from the FIX protocol. QuickFIX/J is the Java implementation of the industry-standard FIX (Financial Information eXchange) protocol, which defines standard order message fields, order state transitions (order lifecycle), and execution report formats. This system's OMS order state machine and data model reference FIX protocol design, such as the New → Filled → Cancelled state flow and the idempotency design using ClOrdID (client order ID).

- Receives client orders (market / limit)
- Order state machine: NEW → PENDING_RISK → ACCEPTED → PARTIALLY_FILLED → FILLED / REJECTED / CANCELLED
- Calls risk-service for pre-trade risk control (synchronous REST)
- Market maker acts as counterparty; matching = acceptance
- Publishes `trade-event` after execution (via Outbox)

### 3.4 risk-service (Risk Control Service)

【Reference: **Drools + Esper/Siddhi**】— Borrows ideas from two directions:
- **Drools**: Open-source business rule engine whose core value is "separation of rules from code, configurable by business users, hot-updatable rules." This system's risk module reserves a `RiskRuleEngine` interface for future Drools integration, enabling rule externalization.
- **Esper / Siddhi**: Complex Event Processing (CEP) engines that excel at detecting patterns and computing aggregations over real-time event streams (e.g., "cumulative trades exceeding limit within 5 minutes"). This phase implements risk control in pure Java without directly integrating a CEP engine, but the event stream processing design philosophy (consuming trade-event for post-trade risk control, real-time exposure monitoring) is inspired by CEP concepts.

- Pre-trade risk control: credit limit, single-order limit, position limit, price deviation
- In-trade risk control: real-time exposure monitoring
- Pure Java implementation with reserved Drools integration interface `RiskRuleEngine`
- REST: `POST /risk/pre-trade` (synchronous)

### 3.5 execution-service (Hedge Execution Service)

- Consumes `trade-event` (Kafka topic: trade-event)
- Computes hedge direction (opposite of client trade) and quantity (× hedge-ratio; default: full hedge)
- Submits orders to sim-exchange via ExchangeSessionClient (synchronous acceptance, returns NEW)
- Receives sim-exchange webhook callbacks (order status reports + fill notifications), updates hedge order status to FILLED
- Publishes `hedge-fill-event` (Kafka topic: hedge-fill-event)
- REST: `GET /execution/orders`, `GET /execution/orders/{id}/trades`
- Port: 8086

**Async Two-Phase Interaction with sim-exchange**:
```
1. TradeEventConsumer receives trade-event → ExecutionService.onTradeEvent
2. ExecutionService → ExchangeSessionClient.submitOrder (POST /exchange/orders)
   - sim-exchange synchronously returns order acceptance (NEW)
3. sim-exchange asynchronously matches and calls back via Webhook:
   - POST /execution/callback/order  → ExecutionService.onOrderNotification (status report)
   - POST /execution/callback/trade  → ExecutionService.onTradeNotification (fill notification)
4. ExecutionService publishes HedgeFillEvent to Kafka hedge-fill-event topic
```

**Database Tables**: hedge_orders (hedge orders), hedge_trades (hedge fill records), hedge_batch_items (batching child items)

**Hedge Batching**:
Multiple client fills for the same contract and direction within a short time window are aggregated into a single hedge order submitted to the exchange, reducing order count, transaction fees, and market impact.
- Batching granularity: grouped by `symbol + side` (same contract, same hedge direction)
- Dual trigger mechanism:
  - **Time window**: flush every `batching-window-ms` milliseconds (1000ms in dev, recommended 200ms in production)
  - **Quantity threshold**: flush immediately when accumulated quantity ≥ `batching-size-threshold` (default: 50 lots)
- Allocation rule (market orders): all child items share the same fill price; allocated proportionally by quantity, with the last item absorbing the residual
- Event publishing: after the batched order is filled, hedge-fill-events are published individually per original fill (position-service consumption model remains unchanged)
- Configurable toggle: `execution.batching-enabled`; when disabled, falls back to one-hedge-per-fill

**Hedge Batching Data Flow**:
```
trade-event → HedgeBatcher.enqueue
  ├─ batchingEnabled=false → ExecutionService.onTradeEventImmediate (immediate single hedge)
  └─ batchingEnabled=true  → Enqueue (hedge_batch_items status=PENDING)
       ├─ Time window trigger (@Scheduled fixedDelay)
       └─ Quantity threshold trigger (checked on enqueue)
            → ExecutionService.submitBatchedOrder (aggregated submission)
                 → hedge_orders.isBatched=1 + child items status=SUBMITTED
                 → ExchangeSessionClient.submitOrder (synchronous acceptance)
                 → sim-exchange async matching
                      → Webhook /execution/callback/trade
                           → ExecutionService.onTradeNotification
                                → Proportional allocation by child item quantity
                                → Publish hedge-fill-event per original fill
```

### 3.6 position-service (Position Management Service)

- Consumes `trade-event` to update client positions
- Consumes `hedge-fill-event` to update hedge positions
- Computes net exposure = client position − hedge position
- REST: `GET /positions/{customerId}`, `GET /positions/exposure`

### 3.7 Hedge Failure Handling

> **Design Principle**: Fail-fast with direct rejection (Hedge-First). Before a client order is executed, the system evaluates hedging capacity; if insufficient, the order is immediately rejected. This ensures simple client-side logic, high throughput, and statelessness. Execution only proceeds after the assessment passes, eliminating exposure risk at the source.

> When pre-hedge assessment passes but the subsequent actual hedge fails, a multi-layer defense system (retry → auto-unwind → DLQ) provides fallback handling to ensure risk remains controlled.

#### 3.7.1 Problem Analysis

**Business Scenario**: Client places order in OMS → risk check passes → OMS executes (status=FILLED) → publishes `trade-event` → execution-service consumes → computes hedge order → **exchange submission fails**

**Failure Cause Classification**:
- **Transient network errors**: TCP disconnect, DNS failure, HTTP 5xx (high frequency, auto-recoverable)
- **Exchange / counterparty system errors**: exchange downtime, risk rejection, rate limiting (medium frequency, retryable)
- **Business exceptions**: insufficient margin, contract suspension, risk intercept (low frequency, manual intervention required)
- **Unknown exceptions**: NPE, timeout, serialization errors (low frequency, DLQ fallback)

**Risk Assessment**: Client BUY 1 lot gold → market maker holds short 1 lot; adverse price movement causes loss; credit has been deducted but hedge not submitted.

#### 3.7.2 Multi-Layer Defense System

```
┌─────────────────────────────────────────────────────────────────────┐
│  Layer 1: Prevention — Pre-Hedge Assessment (Hedge-First)           │
│  → Evaluate hedge capacity before client order execution;           │
│    reject immediately if insufficient (fail-fast)                   │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 2: Local Retry — Exponential Backoff + Jitter                │
│  → 1s/2s/4s/8s/16s/32s, max 6 attempts, ~1 minute total             │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 3: State Machine Advancement — Scheduled Recovery Scan       │
│  → Scan RETRYING status every 10s, trigger retries;                 │
│    auto-unwind when failed exposure exceeds threshold                │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 4: Fallback Hedging — Multi-Channel (interface reserved)     │
│  → Auto-switch to backup channel when primary fails                 │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 5: Alert + Auto-Unwind — Exposure Threshold Breach           │
│  → Cumulative failed exposure exceeds threshold →                   │
│    trigger market-order reverse unwind + ops alert                  │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 6: Dead Letter Queue — Ultimate Fallback                     │
│  → Exhausted retries enter DLQ; compensation task + manual ops      │
└─────────────────────────────────────────────────────────────────────┘
```

#### 3.7.3 Hedge Order State Machine

```
              ┌──────────┐
              │  PENDING │ (Enqueued / awaiting submission)
              └─────┬────┘
                    │ submit to exchange
        ┌───────────┼─────────────┬──────────────┐
        ▼           ▼             ▼              ▼
   ┌─────────┐ ┌─────────┐ ┌──────────┐  ┌──────────────┐
   │SUBMITTED│ │RETRYING │ │  FAILED  │  │REJECTED      │
   │Accepted │ │Retrying │ │Exhausted │  │Biz Rejected  │
   └────┬────┘ └────┬────┘ └─────┬────┘  └──────┬───────┘
        │           │            │              │
        │           │ success    │              │
        ▼           ▼            ▼              ▼
   ┌──────────────────────────────────────────────────┐
   │              FILLED (filled, hedge successful)   │
   └──────────────────────────────────────────────────┘
                       │
                       │ partial fill
                       ▼
   ┌──────────────────────────────────────────────────┐
   │          PARTIAL_FILLED (partially filled)       │
   └──────────────────────────────────────────────────┘

   Any state → cumulative failed exposure exceeds threshold →
   trigger auto-unwind (EMERGENCY_HEDGED)
```

**State Definitions**:

| State            | Description                                             | Allowed Transitions                              | Terminal |
|------------------|---------------------------------------------------------|--------------------------------------------------|----------|
| PENDING          | Enqueued or created but not yet submitted               | → SUBMITTED / FAILED                             | No       |
| SUBMITTED        | Submitted to exchange, awaiting report                  | → RETRYING / FILLED / PARTIAL_FILLED / REJECTED  | No       |
| RETRYING         | Submission failed, awaiting next retry                  | → SUBMITTED / FAILED / EMERGENCY_HEDGED          | No       |
| FILLED           | Fully filled                                            | —                                                | Yes      |
| PARTIAL_FILLED   | Partially filled                                        | → FILLED / EMERGENCY_HEDGED                      | No       |
| REJECTED         | Exchange business rejection (insufficient margin, etc.) | → FAILED / EMERGENCY_HEDGED                      | No       |
| FAILED           | Retries exhausted or system error                       | → DLQ / EMERGENCY_HEDGED                         | Yes      |
| EMERGENCY_HEDGED | Auto-unwind executed                                    | —                                                | Yes      |

#### 3.7.4 Pre-Hedge Assessment (Hedge-First)

**Design Principle**: Fail-fast with direct rejection, ensuring simple client-side logic, high throughput, and statelessness.

> **Core Concept**: Evaluate hedge capacity **before** the client order is executed. If the assessment fails, the client order is immediately rejected (HTTP 409 Conflict). The client receives an explicit rejection and can immediately retry or abandon — no need to wait for asynchronous hedge results. This is the key design for ensuring system throughput and client-side simplicity.

**Assessment Dimensions**:

| Dimension | Assessment | Rejection Condition | Recovery |
|-----------|------------|---------------------|----------|
| **Channel Health** | Exchange reachability, response time | Consecutive failures > N or response time > threshold | Auto-recovery (scheduled probe) |
| **Market Maker Credit** | Available funds / margin sufficiency | Available credit < estimated order value | Fund replenishment |
| **Hedge Backlog** | Number of RETRYING hedge orders | Retry queue > max-retry-queue-size | System auto-resolves backlog |
| **Contract Status** | Contract suspension, trading hours | Contract suspended or outside trading hours | Wait for resumption |
| **Current Exposure** | Existing unhedged exposure quantity | Unhedged exposure > max-allowed-exposure-qty | Wait for hedge completion |

**Assessor Interface**:
```java
public interface HedgeCapacityChecker {
    /**
     * Evaluate hedge capacity; throw exception if insufficient.
     * @param symbol Contract code
     * @param qty Order quantity
     * @param side Client trade side (used to compute hedge direction)
     * @throws HedgeCapacityException Thrown when hedge capacity is insufficient (HTTP 409 Conflict)
     */
    void checkCapacity(String symbol, BigDecimal qty, String side);

    /**
     * Lightweight check (channel health and contract status only;
     * does not check credit or exposure).
     * Used for pre-quote quick assessment to avoid wasting risk resources.
     * @throws HedgeCapacityException Thrown when assessment fails
     */
    void quickCheck(String symbol);

    /**
     * Get current assessment status summary (for monitoring).
     * @return Capacity status summary
     */
    CapacityStatus getCapacityStatus();
}

public class CapacityStatus {
    private boolean healthy;            // Overall health status
    private int retryQueueSize;         // Current retry queue size
    private BigDecimal openExposureQty; // Current unhedged exposure quantity
    private boolean exchangeReachable;  // Exchange reachability
    private long lastHealthCheckTime;   // Last health check timestamp
}
```

**Configuration**:
```yaml
execution:
  hedge:
    capacity-check:
      enabled: true                    # Enable pre-hedge assessment
      max-retry-queue-size: 1000       # Max retry queue size; reject if exceeded
      max-allowed-exposure-qty: 100    # Max allowed unhedged exposure (lots)
      exchange-health-timeout-ms: 5000 # Exchange health check timeout
      health-check-interval-ms: 10000  # Health check interval (ms)
      consecutive-fail-threshold: 3    # Consecutive failure threshold (mark unavailable if exceeded)
```

**Integration Point**:

```
Client order flow (with pre-hedge assessment):
1. Client → Gateway → OMS: POST /orders
2. OMS: Idempotency check (clientOrderId)
3. OMS → Risk: POST /risk/pre-trade (synchronous pre-trade risk control)
4. OMS → Execution: POST /execution/hedge-capacity/check (synchronous pre-hedge assessment)
   ├─ Assessment passes → continue
   └─ Assessment fails → return HTTP 409 Conflict, order status=REJECTED
5. OMS: Execute trade (market maker as counterparty, order status=FILLED)
6. OMS: Write to outbox + event_store
7. Return to client: order filled
```

**HTTP Response Example (Rejection)**:
```json
{
  "code": 409,
  "message": "HEDGE_CAPACITY_INSUFFICIENT",
  "data": {
    "reason": "EXCHANGE_UNREACHABLE",
    "symbol": "AU2406",
    "retryQueueSize": 1200,
    "maxRetryQueueSize": 1000,
    "suggestedRetryAfter": 30000
  }
}
```

**Throughput Guarantee**:
- Pre-hedge assessment uses a **local cache + scheduled refresh** pattern, avoiding database access on every assessment
- Health check results cached for 10 seconds; contract status cached for 1 minute
- Assessment logic is simple and fast (O(1)), involving no complex computation or external calls
- Assessment failure triggers immediate rejection, avoiding downstream resource consumption (execution, message delivery, hedge submission, etc.)

#### 3.7.5 Retry Strategy

**Exponential Backoff + Jitter**:
- Initial interval: 1s
- Max interval: 32s
- Multiplier: 2.0
- Jitter: ±30%
- Max retry attempts: 6 (approximately 1 minute)
- Total timeout: 2 minutes

**Backoff Sequence** (1s ±30%):
```
1s → 2s → 4s → 8s → 16s → 32s ≈ 63s (≈80s with jitter)
```

**Configuration**:
```yaml
execution:
  hedge:
    retry:
      max-attempts: 6              # Max retry attempts
      initial-interval-ms: 1000     # Initial interval
      max-interval-ms: 32000       # Max interval
      multiplier: 2.0              # Multiplier
      jitter-ratio: 0.3            # Jitter ratio
      total-timeout-ms: 120000     # Total timeout
```

#### 3.7.6 Failed Exposure Tracking

**hedge_failure_exposure table**:
```sql
CREATE TABLE hedge_failure_exposure (
  id              BIGINT PRIMARY KEY,        -- Distributed ID (application-layer Snowflake generator)
  customer_id     VARCHAR(32) NOT NULL,
  symbol          VARCHAR(32) NOT NULL,
  side            VARCHAR(8) NOT NULL,       -- Hedge direction (same as client trade)
  pending_qty     DECIMAL(18,4) NOT NULL,    -- Quantity awaiting hedge
  exposure_amount DECIMAL(20,2) NOT NULL,    -- Exposure amount (qty × current price)
  status          VARCHAR(16) NOT NULL,      -- PENDING/HEDGED/EMERGENCY_CLOSED/EXPIRED
  retry_count     INT NOT NULL DEFAULT 0,
  last_retry_at   TIMESTAMP,
  original_trade_id VARCHAR(64) NOT NULL,    -- Associated client trade ID
  hedge_order_id  VARCHAR(64),               -- Associated hedge order
  created_at      TIMESTAMP NOT NULL,
  resolved_at     TIMESTAMP,
  shard_id        INT NOT NULL
);
CREATE INDEX idx_hfe_status ON hedge_failure_exposure(status, created_at);
CREATE INDEX idx_hfe_symbol ON hedge_failure_exposure(symbol, status);
```

**Status Definitions**:

| Status           | Description                                      |
|------------------|--------------------------------------------------|
| PENDING          | Awaiting hedge; exposure not yet resolved        |
| HEDGED           | Hedge successful; exposure resolved              |
| EMERGENCY_CLOSED | Auto-unwind executed; exposure resolved          |
| EXPIRED          | Unhedged beyond timeout (manual intervention timeout) |

#### 3.7.7 Auto-Unwind

**Trigger Conditions** (any one triggers):
1. Failed exposure quantity per contract > `auto-unwind-threshold-qty` (default: 100 lots)
2. Failed exposure amount > `auto-unwind-threshold-amount` (default: 1,000,000)
3. Failure duration > `auto-unwind-timeout` (default: 2 minutes)
4. Retry count > `max-attempts` and not yet unwound

**Execution Flow**:
1. Submit a market-order reverse unwind in the same direction as the hedge order to the exchange
2. Mark `EMERGENCY_HEDGED` upon successful unwind
3. Update `hedge_failure_exposure.status = EMERGENCY_CLOSED`
4. Alert operations (log + WebSocket push)
5. Write audit log

**Configuration**:
```yaml
execution:
  hedge:
    auto-unwind:
      enabled: true                       # Enable auto-unwind
      threshold-qty: 100                  # Quantity threshold (lots)
      threshold-amount: 1000000           # Amount threshold
      timeout-ms: 120000                  # Timeout threshold (ms)
```

#### 3.7.8 Dead Letter Queue (DLQ)

**hedge_dlq table**:
```sql
CREATE TABLE hedge_dlq (
  id              BIGINT PRIMARY KEY,        -- Distributed ID
  hedge_order_id  VARCHAR(64) NOT NULL,      -- Associated hedge order
  original_trade_id VARCHAR(64) NOT NULL,    -- Associated client trade
  customer_id     VARCHAR(32),
  symbol          VARCHAR(32),
  side            VARCHAR(8),
  qty             DECIMAL(18,4),
  reason          TEXT,                      -- Failure reason
  retry_count     INT NOT NULL DEFAULT 0,
  max_retry_count INT NOT NULL DEFAULT 3,    -- Max compensation retry count
  status          VARCHAR(16) NOT NULL,      -- PENDING/RECOVERED/FAILED
  created_at      TIMESTAMP NOT NULL,
  recovered_at    TIMESTAMP,
  shard_id        INT NOT NULL
);
CREATE INDEX idx_dlq_status ON hedge_dlq(status, created_at);
```

**Compensation Task**: Scans DLQ every 30 seconds and attempts hedge recovery; alerts for manual intervention when compensation retries are exhausted.

#### 3.7.9 State Machine Advancement Scheduled Task

**Schedule Frequency**: Every 10 seconds

**Execution Logic**:
1. Fetch hedge orders in RETRYING status; check if `next_retry_at` has arrived; trigger next retry
2. Fetch PENDING/RETRYING status; compute failed exposure
3. Exposure exceeds threshold → trigger auto-unwind
4. Cumulative retries > max-attempts → mark FAILED + enqueue to DLQ
5. Failure duration > auto-unwind-timeout but not unwound → emergency unwind

#### 3.7.10 Monitoring & Alerting

**REST Endpoints**:
```java
GET  /execution/hedge-orders/{id}            // Query hedge order status + retry history
GET  /execution/hedge-failures               // List current unresolved failed exposures
GET  /execution/hedge-failures/summary       // Failed exposure summary (by symbol)
POST /execution/hedge-orders/{id}/retry      // Manual retry
POST /execution/hedge-orders/{id}/cancel     // Manual cancel/unwind
GET  /execution/hedge-metrics               // Monitoring metrics
```

**Monitoring Metrics**:
| Metric | Type | Description |
|--------|------|-------------|
| `hedge_submit_total{result="success\|fail"}` | Counter | Hedge order submission count |
| `hedge_retry_count` | Histogram | Retry count distribution per hedge order |
| `hedge_open_exposure_qty` | Gauge | Current unhedged exposure quantity |
| `hedge_open_exposure_amount` | Gauge | Current unhedged exposure amount |
| `hedge_auto_unwind_total` | Counter | Auto-unwind trigger count |
| `hedge_dlq_size` | Gauge | Pending hedge orders in DLQ |
| `hedge_latency_ms` | Histogram | Latency from hedge submission to fill |

#### 3.7.11 Integration with Existing Modules

| Module | Modification |
|--------|-------------|
| `hedge_orders` table | Add status enum (RETRYING/FAILED/EMERGENCY_HEDGED), retry_count, next_retry_at, hedge_channel |
| `hedge_batch_items` table | Add hedge_status / retry_count |
| `ExecutionService` | Integrate state machine + retry + auto-unwind + DLQ |
| `HedgeBatcher` | Integrate pre-hedge assessment |
| `ReconciliationService` | Add hedge failure exposure reconciliation dimension |
| `notify-service` | Add hedge failure alert push |
| `OutboxService` | Reuse retry mechanism |

#### 3.7.12 Current Phase Scope

| Item | Status | Notes |
|------|--------|-------|
| Pre-hedge assessment (fail-fast) | ✅ Implemented | Direct rejection; ensures simple client logic |
| Local retry (exponential backoff) | ✅ Implemented | Max 6 attempts, ~1 minute |
| State machine advancement task | ✅ Implemented | Scans every 10s |
| Failed exposure tracking table | ✅ Implemented | hedge_failure_exposure |
| Auto-unwind | ✅ Implemented | Triggered on threshold breach |
| DLQ table + compensation task | ✅ Implemented | Ultimate fallback |
| Monitoring REST endpoints | ✅ Implemented | Query / manual retry / metrics |
| Multi-channel fallback hedging | ⏸️ Interface reserved | Backup channel interface reserved; not implemented this phase |
| Client-side cancel / reversal | ⏸️ Extension point | Not implemented |

#### 3.7.13 Risks & Trade-offs

| Dimension | Advantage | Risk |
|-----------|-----------|------|
| Fail-fast pre-hedge | Simple client logic, high throughput | Potential false rejection impacting fill rate |
| Auto-unwind | Immediate exposure elimination | Slippage loss; potential false trigger |
| State machine advancement | Traceable, recoverable | Additional scan task; minor resource overhead |
| DLQ fallback | No lost orders in extreme cases | Requires manual ops; longer recovery time |

---

### 3.8 account-service (Client Account Service)

- Client master data, credit limits, margin
- Consumes `trade-event` to deduct available credit
- REST: CRUD + `GET /accounts/{id}/credit`

### 3.9 refdata-service (Reference Data Service)

- Contract definitions (code, multiplier, tick size)
- Exchange information, trading calendars
- REST queries

### 3.10 notify-service (Notification Service)

- Subscribes to multiple Kafka topics (trade-event / hedge-fill-event)
- Pushes to connected clients via WebSocket; supports dual-filter subscription by customerId + type
- Session registry pattern: ConcurrentHashMap + CopyOnWriteArraySet for connection and subscription management
- REST: `GET /notify/health`, `GET /notify/stats`
- Port: 8087

### 3.11 reconciliation-service (Reconciliation Service)

- Scheduled cross-service reconciliation (@Scheduled cron, default every 5 minutes)
- Three-dimension reconciliation:
  - **Exposure reconciliation**: Iterate net exposure per symbol; flag inconsistency if |netExposure| > threshold
  - **Credit reconciliation**: Compare sum(usedCredit) vs sum(|customerPosition|); alert if discrepancy exceeds threshold
  - **Hedge order reconciliation**: Check count of hedge orders with status=NEW (indicating hedge delay)
- Downstream dependencies: position-service (exposure), account-service (credit), execution-service (hedge orders)
- Reconciliation results cached in memory for REST API queries
- REST: `GET /reconciliation/last` (latest result), `POST /reconciliation/trigger` (manual trigger), `GET /reconciliation/health`
- Port: 8089

### 3.12 sim-exchange (Simulated Futures Exchange)

【Reference: **Geometric Brownian Motion (GBM) Model**】— A classical stochastic process in financial mathematics that describes asset price dynamics over time. It is the foundational assumption of the Black-Scholes option pricing model. In real markets, asset prices (stocks, futures, etc.) approximately follow a log-normal distribution. The GBM model characterizes long-term trends and short-term volatility through two parameters — drift rate (μ) and volatility (σ) — producing market data that is more realistic than simple random walks.

【Reference: **CTP / CME Globex FIX Protocol**】— Real futures exchanges (domestic CTP, international CME Globex) employ an **asynchronous two-phase model** for order submission:
- **Domestic CTP**: `ReqOrderInsert` synchronously returns only "request received by front-end"; results are subsequently pushed via three async callbacks: `OnRspOrderInsert` (acceptance confirmation), `OnRtnOrder` (order status report), and `OnRtnTrade` (fill notification).
- **International CME Globex (FIX 4.4)**: After sending `NewOrderSingle (D)`, async `ExecutionReport (8)` messages are pushed, with Tag 150 (ExecType) distinguishing New / Partial Fill / Filled / Cancelled / Rejected states.
- **Communication Channel**: The market maker system (EMS) communicates with the exchange via **TCP persistent connection + SDK callbacks** (CTP) or **FIX session callbacks** (Globex), **not via message queues**. Kafka is used solely for internal event distribution among market maker microservices.

This simulation system accordingly implements: communication between sim-exchange and execution-service via **Webhook callbacks** to emulate the async push semantics of SDK/FIX, aligning with real exchange behavior.

- **Standalone process** Spring Boot application
- Reads initial prices from configuration
- Generates continuous stochastic market data using Geometric Brownian Motion (GBM), approximating real market behavior
- Broadcasts market data via WebSocket
- REST: `POST /exchange/orders` accepts hedge orders; **synchronously returns only order acceptance (status=NEW) or parameter validation rejection (REJECTED)**, does not return fills
- REST: `GET /exchange/orders/{orderId}` queries order status (asynchronous matching after acceptance; status transitions to ACCEPTED → FILLED/PARTIALLY_FILLED/REJECTED)
- REST: `POST /exchange/callbacks/register` registers Webhook callback URLs; after matching completes, asynchronously pushes order status changes and fill notifications (emulating CTP `OnRtnOrder` / `OnRtnTrade`)
- REST: `GET /exchange/marketdata/{symbol}` fetches latest price
- In-memory storage; data lost on restart (for simulation purposes this phase)

**Async Two-Phase Matching Flow**:
```
0. EMS startup → sim-exchange: POST /exchange/callbacks/register
   - Registers EMS Webhook URLs for order status reports and trade fill notifications
   - sim-exchange stores the callback endpoints for later async pushes

1. EMS → sim-exchange: POST /exchange/orders (submit order)
2. sim-exchange: Parameter validation → pass: insert into order table (status=NEW) → synchronously return order object
3. sim-exchange: Async matching thread matches after N ms delay
   - Update order status to ACCEPTED → FILLED/PARTIALLY_FILLED/REJECTED
4. sim-exchange → EMS: POST {ems_webhook}/execution/callback/order (order status report, emulating OnRtnOrder)
5. sim-exchange → EMS: POST {ems_webhook}/execution/callback/trade (fill notification, emulating OnRtnTrade)
```

**Sequence Diagram**:
```
        EMS                                sim-exchange
        │                                       │
        │  1. POST /exchange/callbacks/register │
        │ ────────────────────────────────────► │
        │      {orderCallbackUrl, tradeCallbackUrl}
        │                                       │
        │◄──────────────────────────────────────│
        │           200 OK (registered)         │
        │                                       │
        │              [ Startup complete ]     │
        │                                       │
        │  2. POST /exchange/orders             │
        │ ────────────────────────────────────► │
        │                                       │
        │     3. Synchronously validates order  │
        │     inserts order (status=NEW)        │
        │                                       │
        │◄──────────────────────────────────────│
        │       200 OK {orderId, status=NEW}    │
        │                                       │
        │              [ Async matching ]       │
        │                                       │
        │◄──────────────────────────────────────│
        │  4. POST /execution/callback/order    │
        │     {orderId, status=FILLED}          │
        │                                       │
        │  200 OK ───────────────────────────►  │
        │                                       │
        │◄──────────────────────────────────────│
        │  5. POST /execution/callback/trade    │
        │     {orderId, fillQty, fillPrice}     │
        │                                       │
        │  200 OK ───────────────────────────►  │
        │                                       │
```

**Semantic Alignment with Real Exchanges**:
| Real Exchange | Simulation Implementation |
|---|---|
| CTP `ReqOrderInsert` / FIX `NewOrderSingle` | `POST /exchange/orders` (synchronous acceptance) |
| CTP `OnRtnOrder` order status report | Webhook `POST /execution/callback/order` |
| CTP `OnRtnTrade` fill notification | Webhook `POST /execution/callback/trade` |
| TCP persistent connection + SDK callback | HTTP Webhook callback |
| Internal Kafka distribution (market maker) | Kafka trade-event / hedge-fill-event (unchanged) |

**Market Data Model**: Geometric Brownian Motion
```
S_{t+Δt} = S_t * exp((μ - 0.5σ²)Δt + σ√Δt * Z)
Z ~ N(0,1) standard normal distribution
μ: drift rate (daily return, configurable)
σ: volatility (annualized volatility, configurable)
Δt: time step (configurable, e.g., 1 second = 1/86400 day)
```

**Configuration Example**:
```yaml
sim-exchange:
  symbols:
    - code: "AU2406"
      name: "Gold 2406"
      initialPrice: 520.50
      volatility: 0.15    # Annualized volatility 15%
      drift: 0.05         # Annualized drift rate 5%
      tickSize: 0.02
      multiplier: 1000
    - code: "AG2406"
      name: "Silver 2406"
      initialPrice: 6280.0
      volatility: 0.25
      drift: 0.03
      tickSize: 1.0
      multiplier: 15
  intervalMs: 1000  # Market data push interval
```

### 3.13 Frontend: Quote Monitoring Dashboard

- **Minimal implementation**: Single-page HTML + JavaScript (or lightweight Vue)
- Features: Real-time display of exchange quotes + market maker quote table
- Adjustable refresh rate (1s / 5s / 10s / 30s)
- Data source: REST polling or WebSocket push
- Fields: Contract code, exchange bid, exchange ask, bank bid, bank ask, change, change percentage

---

## 4. Consistency Strategy (Key Chapter)

### 4.1 Tiered Consistency Strategy

| Business Scenario                      | Consistency Requirement              | Mechanism                                      |
|----------------------------------------|--------------------------------------|------------------------------------------------|
| Client order → risk check → acceptance | Strong consistency                   | Synchronous REST + local transaction           |
| Fill → position / credit / hedge       | Eventual consistency + auditable     | Outbox + Kafka + idempotent consumption        |
| Market data distribution               | Tolerant of minor loss / reordering  | Plain Kafka                                    |
| Quote push                             | Eventual consistency                 | At-Most-Once                                   |

### 4.2 Transactional Outbox Pattern

【Reference: **Transactional Outbox Pattern**】— A classical design pattern in microservices architecture for ensuring consistency between database updates and message publishing. It is a standard solution in Enterprise Integration Patterns (EIP) and microservices design patterns. The core idea: treat "messages to be sent" as part of business data, writing them to the database in the same local transaction as the business data. A separate Relay process then asynchronously reads and sends them to the message queue. This leverages the database's local transaction to guarantee "business changes and event records either both succeed or both fail," completely eliminating event loss.

**Core Concept**: Business state changes and event records are written within the same local transaction, guaranteeing all-or-nothing semantics.

```
OMS processes fill (single local transaction):
  1. UPDATE orders SET status='FILLED' WHERE id=? AND version=?
  2. INSERT INTO positions ...
  3. INSERT INTO outbox(event_id, topic, key, payload, status, created_at)
     VALUES('evt-001','trade-event','C001',{...},'PENDING',now)
  4. INSERT INTO event_store(...)   -- Write to event store in same transaction
  -- Transaction commit

Outbox Relay (scheduled polling task):
  5. SELECT * FROM outbox WHERE status='PENDING' ORDER BY id LIMIT 100
     FOR UPDATE SKIP LOCKED
  6. Send to Kafka (with retry)
  7. UPDATE outbox SET status='SENT', sent_at=now WHERE id=?
```

### 4.3 Kafka Ordering Guarantee

【Reference: **LMAX Disruptor**】— An open-source high-performance lock-free ring buffer framework from LMAX Exchange. Its core design philosophy is "single-thread consumption + ring buffer + sequential processing," achieving ultra-low-latency event processing with minimal complexity. This system borrows this concept: events for the same customer are routed to the same Kafka partition via partition key (analogous to a slot sequence in a ring buffer), with a single consumer per partition (analogous to single-thread consumption), thereby guaranteeing strict ordering of events for the same customer in a distributed environment while achieving overall parallelism through multiple partitions.

**Partitioning Strategy**:
- `trade-event` topic: 32 partitions, `partitionKey = customerId`
- Events for the same customer are routed to the same partition → naturally ordered
- Single consumer per partition → processing order = enqueue order

**Event Sequence Numbers**:
```
TradeEvent {
  eventId: "evt-xxxx"
  customerId: "C001"
  eventSeq: 7           ← Nth event for this customer, monotonically increasing
  occurredAt: timestamp
  payload: {...}
}
```
Consumer detects sequence gap → alert + compensating replay from event_store.

**Cross-Customer Ordering Note**:
The market maker executes as counterparty to clients (principal-to-principal), not as an exchange matching engine. Client A's and Client B's trades are independent bilateral transactions; the market maker's obligation is "continuous quoting + execution at quoted prices," not "processing in request arrival order." Therefore:
- Same-customer events require strict ordering (first buy first deducted from position, first fill first deducted from credit) — guaranteed by customerId partitioning ✅
- Cross-customer states are independent and can be processed in parallel — multi-partition parallel consumption suffices
- Market maker's global exposure is an "eventually consistent" aggregated read model; millisecond-level latency is acceptable; global FIFO is unnecessary

### 4.4 Idempotent Consumption

Each consumer maintains a `processed_events` table:
```sql
CREATE TABLE processed_events (
  event_id VARCHAR(64) PRIMARY KEY,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Consumption logic:
```
BEGIN;
  -- Execute business operation
  INSERT INTO positions ... ;
  -- Write to idempotency table
  INSERT INTO processed_events(event_id) VALUES(?);
COMMIT;
```
On duplicate consumption, INSERT primary key conflict → entire transaction rolls back → side effects rolled back → idempotent.

### 4.5 Event Sourcing

【Reference: **Event Sourcing**】— A core architectural pattern in Domain-Driven Design (DDD), popularized by Martin Fowler and others. The core idea: instead of directly persisting current state, record all state-changing events in an append-only log; current state can be reconstructed by replaying all events from the beginning. Advantages include complete audit traceability, state reconstruction at any point in time, and event replay for testing and reconciliation. This system's `event_store` table implements event sourcing while retaining current-state tables (orders, positions, etc.) for query performance — a simplified version of "Event Sourcing + CQRS."

All business events are written to the `event_store` table (append-only):
```sql
CREATE TABLE event_store (
  event_id        VARCHAR(64) PRIMARY KEY,
  topic           VARCHAR(64),
  partition_key   VARCHAR(64),   -- customerId
  event_seq       BIGINT,        -- Event sequence number for this customer
  aggregate_type  VARCHAR(32),   -- Order / Position / Account
  aggregate_id    VARCHAR(64),   -- orderId / customerId
  event_type      VARCHAR(32),   -- OrderCreated / TradeFilled / ...
  payload         TEXT,          -- JSON
  occurred_at     TIMESTAMP,
  produced_by     VARCHAR(64),   -- Service instance ID
  trace_id        VARCHAR(64)    -- Trace ID
);
CREATE INDEX idx_key_seq ON event_store(partition_key, event_seq);
CREATE INDEX idx_aggregate ON event_store(aggregate_type, aggregate_id);
```

**Use Cases**:
- Audit traceability: Query complete event chain by customer / order
- State reconstruction: Replay events to rebuild state at any point in time
- Reconciliation fallback: Compare replayed computed values vs actual state tables

### 4.6 Reconciliation & Self-Healing

Scheduled reconciliation tasks (hourly / daily):
1. Replay from event_store to compute theoretical positions and credit
2. Compare with actual positions / accounts tables
3. Compare with sim-exchange hedge fill records
4. Discrepancy → alert + generate compensating event

### 4.7 Distributed Tracing

- Generate `traceId` for each external request
- Propagate via HTTP headers in REST calls
- Propagate via message headers in Kafka
- Log traceId via MDC
- Full-chain audit reconstruction for any single order

### 4.8 Kafka Key Configuration

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

## 5. Sharding Strategy

【Reference: **ShardingSphere**】— An open-source database sharding middleware that provides comprehensive sharding strategies (hash, range, composite), read-write splitting, and distributed transactions. This system borrows its "shard routing + logical-to-physical shard mapping" design philosophy: the `ShardRouter` interface computes the shard, and `DataSourceProvider` routes to the physical data source. Unlike ShardingSphere, this phase implements a lightweight shard routing layer in-house for simplicity and control. Migration to ShardingSphere is possible in the future if more advanced sharding capabilities are needed.

### 5.1 Current Phase: Logical Sharding (Single Database)

- All business tables include a `shard_id INT NOT NULL` column
- All query/update SQL must include `WHERE shard_id = ?`
- `ShardRouter` interface: `int shardOf(String key)`
- Default implementation: `SingleShardRouter`, always returns 0
- `DynamicDataSource`: single data source

### 5.2 Future: Physical Sharding Expansion Path

```
Phase 1: Single-database logical sharding (current phase)
  ↓
Phase 2: Physical sharding (same database type, multiple data sources)
  - Replace ShardRouter → HashShardRouter (64 shards)
  - Replace DataSourceProvider → MultiDataSourceProvider
  - Data migration: split by shard_id to physical databases
  ↓
Phase 3: Cross-database type switching (SQLite → PostgreSQL)
  - Replace datasource configuration + driver
  - MyBatis databaseId handles dialect differences
  - Sharding logic remains unchanged
```

### 5.3 Shard Extension Interfaces

```java
// Defined in common-core
public interface ShardRouter {
    int shardOf(String key);
    int totalShards();
}

// Defined in common-persistence
public interface ShardDataSourceProvider {
    DataSource getDataSource(int shardId);
    int totalShards();
}
```

---

## 6. Database Switching Strategy

### 6.1 Implementation Approach

1. **Spring Profile-Based DataSource Switching**
   - `application-sqlite.yml`: SQLite Driver + jdbc:sqlite:trading.db
   - `application-postgres.yml`: PostgreSQL Driver + jdbc:postgresql://...

2. **MyBatis databaseId for Dialect Handling**
   - Configure `DatabaseIdProvider`
   - Distinguish in Mapper XML using `databaseId="sqlite"` / `databaseId="postgresql"`

3. **SQL Compatibility Constraints**
   - **Primary Key Generation**: Application-layer Snowflake ID generator (`IdGenerator`); all table primary keys are `BIGINT PRIMARY KEY`, no database auto-increment
     - 64-bit long ID: timestamp(41) + datacenter(5) + worker(5) + sequence(12)
     - ~4.096M QPS per instance, inherently distributed, monotonically increasing for favorable index behavior
     - Clock rollback detection: throws exception on detection; upper layer retries or degrades
   - Timestamp fields: uniformly generated in Java to avoid database function differences
   - String types: uniformly use `VARCHAR` / `TEXT`

4. **Schema Migration**
   - Flyway, with scripts organized under `db/sqlite/` and `db/postgres/`

5. **Dependency Isolation**
   - Both JDBC drivers included in pom.xml
   - Active connection determined by Profile

### 6.2 Default Databases

- Local development / simulation testing: SQLite
- Production / load testing: PostgreSQL

---

## 7. Technology Stack

| Layer | Choice |
|---|---|
| Microservices Framework | Spring Boot 3.x + Spring Cloud 2023.x |
| Service Registry | Eureka |
| Configuration Center | Spring Cloud Config |
| API Gateway | Spring Cloud Gateway |
| Persistence Layer | MyBatis-Spring-Boot-Starter 3.x |
| Database | SQLite (default) / PostgreSQL (switchable) |
| Cache | Redis (market data / sessions) |
| Message Bus | Kafka |
| Real-Time Push | Spring WebSocket |
| Risk Control | Pure Java (Drools interface reserved) |
| Frontend | Minimal HTML/JS or lightweight Vue |
| Build Tool | Maven multi-module |

---

## 8. Project Structure

```
trading-system/
├── pom.xml                              (parent)
├── docs/
│   ├── design.md                        (Chinese design document)
│   └── design-en.md                     (English design document)
├── common/
│   ├── common-core/                     (DTO / enums / exceptions / ShardRouter / Result)
│   └── common-persistence/              (MyBatis base classes / DataSource / Outbox / EventStore)
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
│   ├── outbox-relay-service/            (Outbox event delivery)
│   ├── notify-service/                  (WebSocket push + Kafka consumption)
│   ├── reconciliation-service/          (Scheduled cross-service reconciliation)
│   └── gateway/                         (API gateway)
├── sim/
│   ├── sim-exchange/                    (Simulated futures exchange, standalone process)
│   └── sim-client/                      (Simulated client, standalone process)
├── tests/
│   └── integration-tests/               (End-to-end integration tests)
├── frontend/                            (Quote monitoring dashboard)
└── db/
    ├── sqlite/                          (SQLite Flyway migration scripts)
    └── postgres/                        (PostgreSQL Flyway migration scripts)
```

---

## 9. Key Business Flows

### 9.1 Client Order Execution — Main Flow

```
1. Client → Gateway: POST /api/orders {symbol, side, qty, type, clientOrderId}
2. Gateway → OMS: Route to oms-service
3. OMS: Idempotency check (clientOrderId unique index)
4. OMS → Risk: POST /risk/pre-trade (synchronous pre-trade risk control)
   4.1 Risk → Account: Query credit limit
   4.2 Risk → RefData: Query contract parameters
   4.3 Risk → Position: Query current positions
   4.4 Risk returns pass / reject
5. OMS: Risk check passes → Market maker fills against client at quoted price
   (market maker as counterparty, order status=FILLED)
6. OMS local transaction:
   - Update orders table status to FILLED
   - Write to trades table (client fill record)
   - Write to outbox table
   - Write to event_store table
7. OMS returns to client: Order filled (FILLED, with fill price and quantity)
8. Outbox Relay: Poll outbox → send to Kafka trade-event
9. Concurrent consumers (idempotent processing):
   - Position: Update client positions
   - Account: Deduct available credit
   - Risk: Post-trade exposure check
   - Execution: Generate hedge order
10. Execution → Sim-Exchange: POST /exchange/orders (submit hedge order)
    - sim-exchange synchronously returns order acceptance (NEW), no fill
    - sim-exchange asynchronously matches and pushes fill via Webhook callback
11. Execution receives Webhook fill notification → Kafka: publish hedge-fill-event
12. Position: Update hedge position, compute net exposure
13. Notify → Client: WebSocket push fill report
```

### 9.2 Market Data to Client Quote Flow

```
1. Sim-Exchange: GBM model generates market data → WebSocket broadcast
2. MarketData: Receive market data → normalize → cache in Redis
3. MarketData → Kafka: publish market-data
4. Pricing: Consume market data → compute spread → generate client two-sided quote
5. Pricing → Kafka: publish customer-quote
6. Notify: Consume customer-quote → WebSocket push to clients
7. Frontend: Poll REST or WebSocket to display quotes
```

---

## 10. Risk Control Module Design

【Reference: **Drools + Esper/Siddhi**】— Risk control is a core module in financial systems. The industry typically employs a "rule engine + CEP engine" combination:
- **Drools** handles configurable rules (limits, thresholds) that business users can adjust
- **Esper/Siddhi** handles real-time event stream pattern matching (e.g., "3 anomalous trades within 5 minutes")

This phase implements risk control in pure Java, but decouples rule logic from business logic through the `RiskRuleEngine` interface and Strategy pattern, reserving extension points for future Drools or CEP engine integration.

### 10.1 Risk Rule Catalog

| Rule | Type | Description |
|---|---|---|
| Client Credit Limit Check | Pre-trade | Order value ≤ available credit limit |
| Single Order Limit | Pre-trade | Single order quantity ≤ contract per-order limit |
| Daily Cumulative Limit | Pre-trade | Cumulative daily fills ≤ client daily limit |
| Position Limit | Pre-trade | Current position + new ≤ client position cap |
| Price Deviation Check | Pre-trade | Order price deviation from market mid ≤ threshold |
| Net Exposure Limit | Post-trade | Bank-wide net exposure ≤ risk limit |
| Stop-Loss Alert | Post-trade | Unrealized loss ≥ threshold → alert |

### 10.2 Implementation

**Current Phase**: Pure Java + Strategy pattern
```java
public interface RiskRule {
    RiskCheckResult check(RiskCheckContext context);
}

// Concrete rule implementations
public class CreditLimitRule implements RiskRule { ... }
public class SingleOrderLimitRule implements RiskRule { ... }
// ...

// Rule engine interface (Drools integration point reserved)
public interface RiskRuleEngine {
    RiskCheckResult evaluate(RiskCheckContext context);
}

// Default implementation: iterate rule chain
public class DefaultRiskRuleEngine implements RiskRuleEngine {
    private final List<RiskRule> rules;
    // ...
}

// Future Drools implementation
// public class DroolsRiskRuleEngine implements RiskRuleEngine { ... }
```

**Future Drools Integration**: Implement `DroolsRiskRuleEngine` to replace the default implementation with zero business code changes.

---

## 11. Data Model (Core Tables)

【Reference: **QuickFIX/J**】— The field design for core business tables (orders, fills, positions) references standard FIX protocol message fields:
- `client_order_id` corresponds to FIX `ClOrdID` (client order ID, for idempotency)
- `order_id` corresponds to FIX `OrderID` (exchange / market maker order ID)
- Order status enums correspond to FIX `OrdStatus`
- `side` (buy/sell direction), `type` (order type), and other field naming follows FIX conventions

This design ensures minimal field mapping cost for future FIX protocol integration.

### 11.1 Common Tables

**outbox table**
```sql
CREATE TABLE outbox (
  id          BIGINT PRIMARY KEY,  -- Distributed ID (application-layer Snowflake generator)
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

**event_store table**
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

**processed_events table** (per consuming service)
```sql
CREATE TABLE processed_events (
  event_id     VARCHAR(64) PRIMARY KEY,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 11.2 refdata-service

**contract table**
```sql
CREATE TABLE contract (
  id          BIGINT PRIMARY KEY,  -- Distributed ID (application-layer Snowflake generator)
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

**customer table**
```sql
CREATE TABLE customer (
  id           BIGINT PRIMARY KEY,  -- Distributed ID (application-layer Snowflake generator)
  customer_id  VARCHAR(32) NOT NULL UNIQUE,
  name         VARCHAR(128),
  level        VARCHAR(16),   -- VIP/NORMAL
  status       VARCHAR(16),
  credit_limit DECIMAL(20,2), -- Credit limit
  created_at   TIMESTAMP,
  updated_at   TIMESTAMP,
  shard_id     INT NOT NULL
);
```

### 11.4 oms-service

**orders table**
```sql
CREATE TABLE orders (
  id               BIGINT PRIMARY KEY,  -- Distributed ID (application-layer Snowflake generator)
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

**position table**
```sql
CREATE TABLE position (
  id           BIGINT PRIMARY KEY,  -- Distributed ID (application-layer Snowflake generator)
  customer_id  VARCHAR(32) NOT NULL,
  symbol       VARCHAR(32) NOT NULL,
  qty          DECIMAL(18,4) NOT NULL,  -- Positive=long, negative=short
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

**hedge_position table**
```sql
CREATE TABLE hedge_position (
  id           BIGINT PRIMARY KEY,  -- Distributed ID (application-layer Snowflake generator)
  symbol       VARCHAR(32) NOT NULL UNIQUE,
  qty          DECIMAL(18,4) NOT NULL,
  avg_cost     DECIMAL(18,8),
  version      INT NOT NULL DEFAULT 0,
  updated_at   TIMESTAMP
);
```

---

## 12. Implementation Priorities

| Phase | Module | Description |
|---|---|---|
| P0 | common-core, common-persistence | Common layer, skeleton |
| P0 | eureka-server, config-server | Infrastructure |
| P0 | sim-exchange | Simulated exchange (required for integration testing) |
| P0 | refdata-service | Reference data |
| P1 | market-data-service, pricing-service | Market data and pricing (market making core) |
| P1 | oms-service, risk-service | Order management and risk control (trading core) |
| P1 | frontend (quote dashboard) | Frontend display |
| P2 | execution-service, position-service, account-service | Business loop closure |
| P2 | outbox-relay-service | Outbox event delivery, event distribution guarantee |
| P3 | notify-service, sim-client, gateway | Enhancement and integration |
| P3 | reconciliation-service | Scheduled cross-service reconciliation (exposure / credit / hedge orders) |

---

## 13. Design Change History

> This section records significant design document changes for traceability of design evolution and decision context.

### 2026-07-20 — Primary Key Generation Strategy: Database Auto-Increment → Application-Layer Snowflake ID Generator

**Rationale:**
- Database auto-increment primary keys (`AUTOINCREMENT` / `BIGSERIAL`) have bottlenecks in distributed environments:
  - Single point of dependency: all write operations concentrate on the database's auto-increment counter, becoming a hotspot under high concurrency
  - Cross-shard conflicts: in sharded scenarios, each shard's auto-increment sequence is independent, cannot guarantee global uniqueness
  - Non-contiguous sequences: batch inserts, transaction rollbacks, etc. cause non-contiguous IDs, unfavorable for ID-based sorting and pagination
  - High database switching cost: SQLite and PostgreSQL have significantly different auto-increment syntax (`AUTOINCREMENT` vs `SERIAL/IDENTITY`)

**Changes:**
1. **New `common-core.idgen` module**: Implemented Snowflake-style distributed ID generator (`IdGenerator`)
   - 64-bit long ID: timestamp(41) + datacenterId(5) + workerId(5) + sequence(12)
   - ~4.096M QPS per instance, pure in-memory computation, no database dependency
   - Clock rollback detection: throws exception on detection; upper layer retries or degrades
   - Spring auto-configuration: `IdGeneratorConfig` injects datacenterId / workerId via `@Value`
2. **Modified all DDL scripts**: 14 SQLite + 14 PostgreSQL Flyway migration scripts
   - `INTEGER PRIMARY KEY AUTOINCREMENT` → `BIGINT PRIMARY KEY`
   - `BIGSERIAL PRIMARY KEY` → `BIGINT PRIMARY KEY`
   - Comments updated: `auto-increment primary key` → `distributed ID primary key (application-layer Snowflake generator)`
3. **Modified all Mappers**: Added `id` field to `@Insert` statements in 9 Mappers
   - Removed `@Options(useGeneratedKeys = true, keyProperty = "id")`
4. **Modified all Services**: Injected `IdGenerator` into 7 business Services + `OutboxServiceImpl`
   - Call `entity.setId(idGenerator.nextLongId())` before `INSERT`
   - Affected: AccountService, ContractService, OrderService, ExecutionService, HedgeBatcher, PositionService, OutboxServiceImpl

**Impact:**
- Project-wide: 14 SQL scripts, 9 Mappers, 8 Services
- No impact on existing data (only DDL scripts modified; no production data yet)
- Performance improvement: ID generation from database round-trip (~1-5ms) to pure in-memory computation (~1μs)
- Sharding-friendly: long ID can be directly used for shard routing via modulo (`id % totalShards`)

**Decision Record:**
- Compared four solutions: Snowflake, Leaf-Segment, Leaf-Snowflake, UID Generator
- Selected Snowflake because:
  - No external dependencies (Leaf requires DB/ZK); lightest weight with current architecture
  - Sufficient performance (4096 IDs/ms > business peak)
  - Monotonically increasing IDs, favorable for B+Tree indexing
  - 64-bit long directly usable for shard routing

### 2026-07-20 — Architecture Overview Update: Added P3 Phase Modules

**Rationale:**
- P3 phase developed 4 modules (gateway, sim-client, notify-service, reconciliation-service)
- The architecture overview diagram, service inventory, module detailed design, project structure, and implementation priorities in the design document did not reflect reconciliation-service and outbox-relay-service

**Changes:**
- Updated 2.1 Architecture Overview: Added Notify Service and Reconciliation Service nodes
- Updated 2.2 Service Inventory: Added reconciliation-service, outbox-relay-service
- Added 3.10 notify-service / 3.11 reconciliation-service detailed design
- Updated Chapter 8 Project Structure: Added outbox-relay-service, reconciliation-service, tests/integration-tests
- Updated Chapter 12 Implementation Priorities: Added outbox-relay-service (P2), reconciliation-service (P3)

### 2026-07-20 — Hedge Failure Handling: Hedge-First Direct Rejection Strategy

**Rationale:**
- Hedge failure after client order execution creates one-sided exposure risk for the market maker; a comprehensive response plan is needed
- Design principle: fail-fast with direct rejection, ensuring simple client-side logic, high throughput, and statelessness
- Adopt pre-hedge assessment (Hedge-First) mode: evaluate hedge capacity before execution; reject if insufficient

**Changes:**

1. **New design section**: 3.7 Hedge Failure Handling
   - Problem analysis: failure cause classification, risk assessment
   - Multi-layer defense system: prevention (pre-hedge assessment) → local retry → state machine advancement → auto-unwind → DLQ
   - Hedge order state machine: added RETRYING/FAILED/EMERGENCY_HEDGED states
   - Pre-hedge assessment (Hedge-First): 5-dimension assessment, fail-fast rejection, throughput guarantee
   - Retry strategy: exponential backoff + jitter, max 6 attempts (~1 minute)
   - Failed exposure tracking: hedge_failure_exposure table design
   - Auto-unwind: threshold-triggered market-order reverse unwind
   - DLQ: dead letter queue + compensation task
   - Monitoring REST endpoints: query failed exposure / manual retry / monitoring metrics

2. **New database tables**:
   - `hedge_failure_exposure`: failed exposure tracking table
   - `hedge_dlq`: dead letter queue table

3. **Modified database tables**:
   - `hedge_orders`: added retry_count, next_retry_at, hedge_channel fields
   - `hedge_batch_items`: added retry_count field

4. **New interfaces and classes**:
   - `HedgeCapacityChecker`: pre-hedge assessor interface
   - `HedgeRecoveryScheduler`: state machine advancement scheduled task
   - Monitoring REST endpoints: query failed exposure, manual retry, manual cancel, monitoring metrics

**Design Principles:**
- **Direct rejection**: Pre-hedge assessment failure immediately rejects client order (HTTP 409); client does not need to wait for async results
- **Client simplicity**: After receiving explicit rejection, client can immediately retry or abandon; no complex async callback handling
- **High throughput**: Assessment uses local cache + scheduled refresh, O(1) complexity, does not block order flow
- **Controlled risk**: Multi-layer defense fallback ensures risk remains controlled

### 2026-07-20 — pricing-service Quote Frequency and TTL Design

**Rationale:**
- Market maker quote frequency needs to be determined by asset class, avoiding blind use of high-frequency (HFT) or low-frequency (RFQ) modes
- Lack of quote TTL (Quote Time-To-Live) causes clients to execute against stale quotes, resulting in slippage
- Lack of throttling causes WebSocket push storms during volatile markets, affecting client stability

**Benchmark Reference:**
- Domestic futures market (CFFEX / major commodity exchanges) market maker practices
- Market data push 500ms, quote push 500ms–1s, quote TTL 3 seconds

**Changes:**
1. **Updated 3.2 pricing-service detailed design**:
   - Added quote frequency table: covering 7 stages with frequency and implementation
   - Added throttle strategy: coalesce same-symbol market data updates within 500ms window
   - Added quote TTL design: each quote carries `expire_at` field, expires after 3 seconds
   - Added RFQ flow: client forced to re-request quote after expiry

2. **Key design points**:
   - Market data → quote: event-driven (triggered on each market data change)
   - Quote → push: throttled at 500ms (prevents push storms during volatile markets)
   - Quote → client: carries `expire_at`; client responsible for validation
   - Order → quote: server validates quote freshness; rejects with prompt to re-request if expired

**Impact:**
- pricing-service: added throttle cache (`ConcurrentHashMap<symbol, last_quote_time>`)
- customer-quote message body: added `expire_at`, `throttle_window_ms` fields
- notify-service: includes `expire_at` when pushing to WebSocket clients
- oms-service: validates quote freshness at order submission
- Configuration: `pricing.throttle.window-ms=500`, `pricing.quote.ttl-seconds=3`

### 2026-07-20 — Hedge Failure Handling Implementation Plan

**Rationale:**
- Section 3.7 completed design only; needed to further define implementation scope and impact

**Changes:**
- execution-service: core modifications — added pre-hedge assessment, retry, auto-unwind, DLQ capabilities
- oms-service: integrated pre-hedge assessment call
- reconciliation-service: added hedge failure exposure reconciliation dimension
- Database: 2 new tables, 2 modified tables
