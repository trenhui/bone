-- V23: 共享额度（共保代码）支撑。
-- t_adj_liability 加 shared_code / prior_liability_uuid；t_adj_calculation 加 shared_code。
--
-- 与生效通道 scripts/migration/0118_synap_claim_shared_limit.sql DDL 逐字一致；bone-init.sql 同步加列。
-- 责任限额账不新增列：SHARED_POOL 维度直接复用 liability_uuid 列承载共保代码（避免改唯一索引，写读两侧同键）。
--
-- 语义：
--   shared_code         非空=该责任走共享池（SHARED_POOL 维度，按共保代码分行）；空=独立责任。
--   prior_liability_uuid 先赔后赔前序责任 UUID（V1 金额推导公式未定义，仍由引擎显式拒绝）。
--   t_adj_calculation.shared_code 与 liability_uuid 同源，结算时据此选共享池账户。

ALTER TABLE t_adj_liability
  ADD COLUMN shared_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '共享保额共保代码(空=独立责任)' AFTER status,
  ADD COLUMN prior_liability_uuid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '先赔后赔前序责任UUID(空=无)' AFTER shared_code;

ALTER TABLE t_adj_calculation
  ADD COLUMN shared_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '本案所属共享池代码(与 liability_uuid 同源, 结算选账户用)' AFTER liability_version;
