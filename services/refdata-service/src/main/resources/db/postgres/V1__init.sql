-- refdata-service PostgreSQL 初始化脚本
-- 适配差异：INTEGER PRIMARY KEY AUTOINCREMENT -> BIGSERIAL PRIMARY KEY
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

-- 合约主数据表：记录期货合约静态信息（合约代码、乘数、tick 等）
CREATE TABLE IF NOT EXISTS contract (
  id          BIGSERIAL PRIMARY KEY,
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

CREATE INDEX IF NOT EXISTS idx_contract_exchange ON contract(exchange);
CREATE INDEX IF NOT EXISTS idx_contract_status ON contract(status);

COMMENT ON TABLE contract IS '合约主数据表：记录期货合约静态信息（合约代码、乘数、tick 等）';
COMMENT ON COLUMN contract.id IS '自增主键';
COMMENT ON COLUMN contract.code IS '合约代码（如 AU2406）';
COMMENT ON COLUMN contract.name IS '合约中文名（如 黄金2406）';
COMMENT ON COLUMN contract.exchange IS '交易所代码（如 SHFE）';
COMMENT ON COLUMN contract.product IS '品种（如 黄金/白银）';
COMMENT ON COLUMN contract.multiplier IS '合约乘数';
COMMENT ON COLUMN contract.tick_size IS '最小变动价位';
COMMENT ON COLUMN contract.min_qty IS '最小下单数量';
COMMENT ON COLUMN contract.listed_date IS '上市日期';
COMMENT ON COLUMN contract.expiry_date IS '到期日';
COMMENT ON COLUMN contract.status IS '状态（ACTIVE/EXPIRED/DELISTED）';
COMMENT ON COLUMN contract.created_at IS '创建时间';
COMMENT ON COLUMN contract.updated_at IS '更新时间';
