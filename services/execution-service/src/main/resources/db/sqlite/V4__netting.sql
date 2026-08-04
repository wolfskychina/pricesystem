-- 净额对冲（Net Exposure Netting）升级：hedge_batch_items 增加 netted 字段
-- netted=0：通过交易所对冲（正常对冲单）
-- netted=1：内部相消（净敞口为0，未提交交易所）
ALTER TABLE hedge_batch_items ADD COLUMN netted INTEGER DEFAULT 0;
