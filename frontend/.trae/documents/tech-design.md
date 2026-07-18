# 技术架构文档: 银行做市商报价监控面板

## 1. 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| React | 18.x | UI 框架 |
| TypeScript | 5.x | 类型安全 |
| Vite | 6.x | 构建工具 |
| Tailwind CSS | 4.x | 样式框架 |

## 2. 项目结构

```
frontend/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.ts
├── postcss.config.js
├── src/
│   ├── main.tsx                # 入口
│   ├── App.tsx                 # 根组件
│   ├── index.css               # Tailwind 入口
│   ├── api/
│   │   ├── client.ts           # HTTP 客户端封装
│   │   ├── market-data.ts      # 行情 API
│   │   ├── pricing.ts          # 报价 API
│   │   └── orders.ts           # 订单 API
│   ├── types/
│   │   └── index.ts            # 类型定义
│   ├── hooks/
│   │   └── usePolling.ts       # 轮询 Hook
│   ├── components/
│   │   ├── Header.tsx          # 顶部导航
│   │   ├── QuoteTable.tsx      # 报价表格
│   │   ├── OrderPanel.tsx      # 下单面板
│   │   ├── OrderList.tsx       # 订单列表
│   │   └── RefreshControl.tsx  # 刷新控制
│   └── pages/
│       └── Dashboard.tsx       # 主页面
```

## 3. 核心组件设计

### 3.1 API Client

- 基于 fetch 封装，支持 JSON 序列化/反序列化
- 统一错误处理
- 后端服务地址通过环境变量配置（VITE_API_BASE_URL）

### 3.2 usePolling Hook

- 接收 API 调用函数和刷新间隔
- 返回数据、加载状态、错误信息
- 支持暂停/恢复
- 组件卸载时自动清理定时器

### 3.3 QuoteTable 组件

- 接收行情数据和报价数据
- 合并展示（交易所价格 + 银行报价）
- 涨跌颜色：正数绿色，负数红色
- 价格格式化：整数部分千分位，小数2位

### 3.4 OrderPanel 组件

- 表单：合约选择、方向、类型、数量、价格
- 提交调用 POST /orders
- 成功后刷新订单列表

### 3.5 OrderList 组件

- 表格展示订单列表
- 状态标签：NEW(蓝)、PENDING_RISK(黄)、ACCEPTED(蓝)、FILLED(绿)、REJECTED(红)、CANCELLED(灰)
- 未成交订单显示撤单按钮

## 4. 数据流

```
Dashboard
  ├── usePolling → GET /marketdata → 行情数据
  ├── usePolling → GET /quotes/{symbol} → 报价数据
  │
  ├── QuoteTable (行情 + 报价)
  │
  └── Tab 切换
      ├── OrderPanel → POST /orders
      └── OrderList → GET /orders, DELETE /orders/{id}
```

## 5. 环境变量

- `VITE_MARKET_DATA_URL`: market-data-service 地址 (默认 http://localhost:8082)
- `VITE_PRICING_URL`: pricing-service 地址 (默认 http://localhost:8083)
- `VITE_OMS_URL`: oms-service 地址 (默认 http://localhost:8084)

## 6. 构建与运行

- 开发：`pnpm dev` → http://localhost:5173
- 构建：`pnpm build` → dist/
- 预览：`pnpm preview`
