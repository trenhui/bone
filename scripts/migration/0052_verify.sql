-- =============================================================================
-- 0052 改名迁移 · 事后校验（独立脚本）
--
-- 用途：在生产库执行完 0052_rename_expense_claim_to_reimbursement.sql 主脚本后，
--       单独运行本脚本，确认改名结果符合预期。可重复执行，纯 SELECT 无副作用。
--
-- 通过判据（详见同目录 0052_rename_expense_claim_to_reimbursement_RUNBOOK.md 第四节）：
--   · new_tables = 2（物理表 t_expense_reimbursement / _item 均已存在）
--   · renamed_entities_active ≥ 1（item 通常 active；header 视业务软删状态）
--   · renamed_actions ≥ 1、renamed_perms ≥ 6（runtime:expense_reimbursement%
--     覆盖「基名 3 条 + 子实体 3 条」共 6 条；模式不带末尾冒号才覆盖子实体权限）
--   · leftover_old_tables / leftover_old_perms / leftover_old_code 必须全部 = 0
--     （非 0 说明仍有旧名残留，须先排查再放过，不可带旧码上线）
-- =============================================================================

USE bone;

SELECT
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('t_expense_reimbursement','t_expense_reimbursement_item')) AS new_tables,
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('t_expense_claim','t_expense_claim_item')) AS leftover_old_tables,
  (SELECT COUNT(*) FROM meta_entity WHERE deleted = 0
     AND code IN ('expense_reimbursement','expense_reimbursement_item')) AS renamed_entities_active,
  (SELECT COUNT(*) FROM meta_entity_action_def
     WHERE deleted = 0 AND entity_code = 'expense_reimbursement') AS renamed_actions,
  (SELECT COUNT(*) FROM iam_permission
     WHERE deleted = 0 AND code LIKE 'runtime:expense_reimbursement%') AS renamed_perms,
  (SELECT COUNT(*) FROM iam_permission
     WHERE code LIKE '%expense_claim%' OR resource_path LIKE '%expense_claim%') AS leftover_old_perms,
  (SELECT COUNT(*) FROM meta_entity
     WHERE code IN ('expense_claim','expense_claim_item')) AS leftover_old_code;
