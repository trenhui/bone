-- 责任配置乐观锁列：与所有兄弟聚合（t_claim_policy / t_claim_plan / t_claim_coverage ...）对齐，
-- 由 SDK @Version 维护。缺失会导致并发编辑/发布会静默覆盖、破坏"同时仅一个生效版本"不变式。
ALTER TABLE t_adj_liability
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(SDK @Version 维护)';
