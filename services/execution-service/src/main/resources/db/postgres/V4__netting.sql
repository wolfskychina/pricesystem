-- 净额对冲（Net Exposure Netting）升级：hedge_batch_items 增加 netted 字段
-- 强制使用 UTF-8 客户端编码，确保中文字段注释/数据正确写入
SET client_encoding TO 'UTF8';

-- netted=0：通过交易所对冲（正常对冲单）
-- netted=1：内部相消（净敞口为0，未提交交易所）
ALTER TABLE hedge_batch_items ADD COLUMN IF NOT EXISTS netted INTEGER DEFAULT 0;

COMMENT ON COLUMN hedge_batch_items.netted IS '是否内部相消（0=交易所对冲，1=内部相消，净敞口为0未提交交易所）';
