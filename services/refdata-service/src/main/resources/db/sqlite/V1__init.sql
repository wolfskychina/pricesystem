-- refdata-service SQLite 初始化脚本
-- 注意：SQLite 数据库文件默认使用 UTF-8 编码，无需显式设置

-- 合约主数据表：记录期货合约静态信息（合约代码、乘数、tick 等）
CREATE TABLE IF NOT EXISTS contract (
  id          INTEGER PRIMARY KEY AUTOINCREMENT, -- 自增主键
  code        VARCHAR(32) NOT NULL UNIQUE,       -- 合约代码（如 AU2406）
  name        VARCHAR(64),                       -- 合约中文名（如 黄金2406）
  exchange    VARCHAR(32),                       -- 交易所代码（如 SHFE）
  product     VARCHAR(32),                       -- 品种（如 黄金/白银）
  multiplier  DECIMAL(18,4),                     -- 合约乘数
  tick_size   DECIMAL(18,8),                     -- 最小变动价位
  min_qty     DECIMAL(18,4),                     -- 最小下单数量
  listed_date DATE,                              -- 上市日期
  expiry_date DATE,                              -- 到期日
  status      VARCHAR(16),                       -- 状态（ACTIVE/EXPIRED/DELISTED）
  created_at  TIMESTAMP,                         -- 创建时间
  updated_at  TIMESTAMP                          -- 更新时间
);

CREATE INDEX IF NOT EXISTS idx_contract_exchange ON contract(exchange);
CREATE INDEX IF NOT EXISTS idx_contract_status ON contract(status);
