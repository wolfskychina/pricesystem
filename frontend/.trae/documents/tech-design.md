# 技术架构文档: 银行做市商报价监控面板

## 1. 技术栈

采用**零依赖、零构建**的最简方案：

| 技术 | 说明 |
|---|---|
| HTML5 | 页面结构 |
| CSS3 | 样式（原生，不使用预处理器） |
| 原生 JavaScript (ES5+) | 业务逻辑，无框架 |
| Fetch API | HTTP 请求 |
| setTimeout 轮询 | 数据定时刷新 |

**零依赖**：无需 npm install、无需构建工具、打开即用。

## 2. 项目结构

```
frontend/
├── index.html           # 页面结构
├── styles.css           # 样式
├── app.js               # 业务逻辑
└── .trae/
    └── documents/
        ├── prd.md       # 产品需求文档
        └── tech-design.md  # 本文件
```

## 3. 核心模块设计

### 3.1 API 层 (app.js)

封装三个后端服务的 API 调用：
- `fetchMarketData()` → GET `/api/market-data/marketdata`
- `fetchQuote(symbol)` → GET `/api/pricing/quotes/{symbol}`
- `fetchOrders()` → GET `/api/oms/orders`
- `createOrder(data)` → POST `/api/oms/orders`
- `cancelOrder(orderId)` → DELETE `/api/oms/orders/{orderId}`

HTTP 工具函数：`httpGet` / `httpPost` / `httpDelete`，统一错误处理。

### 3.2 轮询机制

- 行情数据 + 报价数据：同一轮询循环，每次刷新间隔刷新
- 订单数据：独立轮询循环
- 支持切换刷新频率（1s / 5s / 10s / 30s）
- 支持暂停/恢复
- 使用 `setTimeout` 递归调用（而非 setInterval），避免请求积压

### 3.3 行情报价表

- 展示字段：合约代码、交易所买价、交易所卖价、最新价、银行买价、银行卖价、中间价、价差(bps)、涨跌、更新时间
- 涨跌颜色：涨=绿，跌=红，平=灰
- 银行报价列高亮（蓝色粗体 + 浅蓝背景）
- 实时更新合约下拉选项

### 3.4 下单面板

- 客户ID、合约选择、买卖方向、订单类型（市价/限价）、数量、价格
- 方向按钮：买入=红，卖出=绿
- 限价单才显示价格输入框
- 提交后显示成功/失败消息

### 3.5 订单列表

- 字段：订单ID、合约、方向、类型、数量、成交、价格、状态、时间、操作
- 状态标签颜色：
  - NEW / ACCEPTED：蓝色
  - PENDING_RISK：黄色
  - FILLED：绿色
  - REJECTED：红色
  - CANCELLED：灰色
- NEW/PENDING_RISK/ACCEPTED 状态显示撤单按钮

## 4. 数据流

```
页面加载
  │
  ├── startPolling()
  │     ├── tickMarket()
  │     │     ├── GET /marketdata        → 行情数据
  │     │     └── GET /quotes/{symbol}   → 报价数据（每个合约）
  │     │     └── renderQuoteTable()     → 渲染报价表
  │     └── tickOrders()
  │           └── GET /orders            → 订单数据
  │           └── renderOrderList()      → 渲染订单列表
  │
  ├── 用户操作
  │     ├── 切换刷新频率 → 重启轮询
  │     ├── 暂停/恢复 → 停止/启动轮询
  │     ├── 手动刷新 → 立即执行一次
  │     ├── 提交下单 → POST /orders → 刷新订单列表
  │     └── 撤单 → DELETE /orders/{id} → 刷新订单列表
```

## 5. API 代理

前端通过路径前缀区分后端服务（建议由 Nginx 或 Vite 代理转发）：

| 路径前缀 | 转发到 | 说明 |
|---|---|---|
| `/api/market-data` | http://localhost:8082 | 行情服务 |
| `/api/pricing` | http://localhost:8083 | 报价服务 |
| `/api/oms` | http://localhost:8084 | 订单服务 |

## 6. 运行方式

**最简方式**：直接用浏览器打开 `index.html`（需配合后端服务 + 代理）。

**本地开发**（推荐使用 Python 内置 http server 或 npx serve）：

```bash
# 方式1: Python
cd frontend && python3 -m http.server 5173

# 方式2: npx
cd frontend && npx serve -l 5173 .
```

然后访问 http://localhost:5173
