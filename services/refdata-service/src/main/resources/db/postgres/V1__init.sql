-- refdata-service PostgreSQL 初始化脚本
-- 适配差异：INTEGER PRIMARY KEY AUTOINCREMENT -> BIGSERIAL PRIMARY KEY
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
