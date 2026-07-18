-- refdata-service PostgreSQL 初始合约数据
-- 使用 ON CONFLICT DO NOTHING（与 SQLite 3.24+ 共用同一语法）

INSERT INTO contract (code, name, exchange, product, multiplier, tick_size, min_qty, listed_date, expiry_date, status, created_at, updated_at)
VALUES ('AU2406', '黄金2406', 'SHFE', '黄金', 1000, 0.02, 1, '2024-01-15', '2024-06-15', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO contract (code, name, exchange, product, multiplier, tick_size, min_qty, listed_date, expiry_date, status, created_at, updated_at)
VALUES ('AG2406', '白银2406', 'SHFE', '白银', 15, 1.0, 1, '2024-01-15', '2024-06-15', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO contract (code, name, exchange, product, multiplier, tick_size, min_qty, listed_date, expiry_date, status, created_at, updated_at)
VALUES ('CU2406', '铜2406', 'SHFE', '铜', 5, 10.0, 1, '2024-01-15', '2024-06-15', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO contract (code, name, exchange, product, multiplier, tick_size, min_qty, listed_date, expiry_date, status, created_at, updated_at)
VALUES ('RB2406', '螺纹钢2406', 'SHFE', '螺纹钢', 10, 1.0, 1, '2024-01-15', '2024-06-15', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;
