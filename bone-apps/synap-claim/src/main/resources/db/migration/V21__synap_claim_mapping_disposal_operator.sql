-- V21: 未命中处置操作人留痕
--
-- 背景（多保司配置面 task 2.3）：spec 要求处置动作留痕「操作人、时间、动作、reason_code」。
-- V20 只留了时间/动作/理由，缺操作人——「谁把这条队列项标成 IGNORED」查不到，
-- 误处置无法追责也无法回放。加一列，OPEN 时为空串（与 ignore_reason 同口径）。
ALTER TABLE t_claim_mapping_miss
  ADD COLUMN resolved_by VARCHAR(64) NOT NULL DEFAULT '' COMMENT '处置操作人(RESOLVED/IGNORED时留痕)' AFTER resolved_mapping_id;
