-- 保单分类字段：团体(GROUP)/个人(INDIVIDUAL)
-- 业界惯例（参考 pk-tpa / 主流 TPA）：团险与个险共用"保单→计划→险种→责任"配置链路，
-- 差异在业务语义与入口交互（团单多被保人共享额度池、个单单人单账户），故用分类字段而非两套聚合。
-- 历史行为空，按 GROUP 兜底（含历史数据回填；实体层 null 亦兜底 GROUP）。
ALTER TABLE t_claim_policy
    ADD COLUMN policy_type VARCHAR(16) DEFAULT NULL COMMENT '保单类型:GROUP团体/INDIVIDUAL个人(空视为团体)'
    AFTER product_type;

UPDATE t_claim_policy SET policy_type = 'GROUP' WHERE policy_type IS NULL;
