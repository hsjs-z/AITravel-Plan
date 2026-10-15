-- 支付事件需要按用户投递通知（FR-033），因此支付记录保留订单归属用户。
-- 允许为空：历史数据无此信息，且迁移不得要求 payment 服务读取订单表（宪法 I）。
ALTER TABLE payment ADD COLUMN user_id varchar(255) DEFAULT NULL;
