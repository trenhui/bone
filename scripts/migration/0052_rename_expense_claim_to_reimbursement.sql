-- =============================================================================
-- 0052_rename_expense_claim_to_reimbursement.sql
--
-- 目的：将报销实体 expense_claim 改名为 expense_reimbursement（与 claim_case 理赔实体并存，
--       名字更贴合「费用报销/ reimbursement」语义，避免与理赔 claim 混淆）。
--
-- 适用范围：
--   · 0037 / 0047 / 0051 已执行（dev/联调库）的环境——种子源码已被同名更新（见各迁移文件），
--     但已落库的数据与元数据仍用旧码，本脚本负责「存量数据迁移」。
--   · 全新库：0037 直接建 expense_reimbursement，本脚本各 UPDATE/RENAME 因无旧记录而空转（幂等）。
--
-- 改动层（与需求「实体码 + 物理表 + 类名」一致）：
--   1. 物理表改名（RENAME TABLE，保留数据，无外键约束可安全重命名）
--   2. meta_entity.code / table_name
--   3. meta_entity_action_def.entity_code + handler_bean
--   4. meta_entity_ui_schema.schema_json / published_schema_json 内嵌 entityCode / fieldLockRules 指向
--   5. meta_field.validation_rules 内嵌 childEntityCode 指向
--   6. meta_model_template.ui_schema_json 内嵌 entityCode
--   7. iam_permission.code / resource_path（细粒度读写码）
--
-- 幂等：RENAME 前置存在性判断；其余 UPDATE 按旧码定位，无旧行即空转。可重复执行。
-- 执行（L3，由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0052_rename_expense_claim_to_reimbursement.sql
-- =============================================================================

USE bone;

-- ── 0. 依赖护栏：前置迁移未执行则直接报错 ──────────────────────────────
SELECT id FROM meta_entity WHERE code IN ('expense_claim', 'expense_claim_item') LIMIT 0;

-- ── 1. 物理表改名（保留数据；无外键约束，RENAME 安全） ───────────────────
SET @rename_sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema = DATABASE() AND table_name = 't_expense_claim') > 0,
    'RENAME TABLE t_expense_claim TO t_expense_reimbursement, '
      't_expense_claim_item TO t_expense_reimbursement_item',
    'SELECT 1'));
PREPARE _rename FROM @rename_sql;
EXECUTE _rename;
DEALLOCATE PREPARE _rename;

-- ── 2. 实体码 + 物理表名（保留中文 name/display_name 不变，仅改 code 与物理表） ──
UPDATE meta_entity
   SET code = 'expense_reimbursement', table_name = 't_expense_reimbursement'
 WHERE code = 'expense_claim';

UPDATE meta_entity
   SET code = 'expense_reimbursement_item', table_name = 't_expense_reimbursement_item'
 WHERE code = 'expense_claim_item';

-- ── 3. 动作定义：实体码 + handler_bean（确认打款处理器 bean 名同步） ──────
UPDATE meta_entity_action_def
   SET entity_code = 'expense_reimbursement'
 WHERE entity_code = 'expense_claim';

UPDATE meta_entity_action_def
   SET handler_bean = REPLACE(handler_bean,
        'expenseClaimConfirmPayoutHandler', 'expenseReimbursementConfirmPayoutHandler')
 WHERE handler_bean LIKE '%expenseClaimConfirmPayoutHandler%';

-- ── 4. UI schema JSON 内嵌 entityCode / fieldLockRules 指向 ────────────
UPDATE meta_entity_ui_schema
   SET schema_json = REPLACE(REPLACE(schema_json,
        'expense_claim_item', 'expense_reimbursement_item'),
        'expense_claim', 'expense_reimbursement')
 WHERE schema_json LIKE '%expense_claim%';

UPDATE meta_entity_ui_schema
   SET published_schema_json = REPLACE(REPLACE(published_schema_json,
        'expense_claim_item', 'expense_reimbursement_item'),
        'expense_claim', 'expense_reimbursement')
 WHERE published_schema_json LIKE '%expense_claim%';

-- ── 5. 字段 validation_rules 内嵌 childEntityCode 指向 ─────────────────
UPDATE meta_field
   SET validation_rules = REPLACE(REPLACE(validation_rules,
        'expense_claim_item', 'expense_reimbursement_item'),
        'expense_claim', 'expense_reimbursement')
 WHERE validation_rules LIKE '%expense_claim%';

-- ── 6. 模板 JSON 内嵌 entityCode ──────────────────────────────────────
UPDATE meta_model_template
   SET ui_schema_json = REPLACE(REPLACE(ui_schema_json,
        'expense_claim_item', 'expense_reimbursement_item'),
        'expense_claim', 'expense_reimbursement')
 WHERE ui_schema_json LIKE '%expense_claim%';

-- ── 7. 权限码 + 资源路径（细粒度读写码） ───────────────────────────────
-- 注意：实际权限码是基名形态 runtime:expense_claim:*（非 _item），故须做基名 REPLACE；
--       "expense_claim_item" 含 "expense_claim" 子串，基名替换可同时覆盖两种形态。
UPDATE iam_permission
   SET code = REPLACE(REPLACE(code, 'expense_claim_item', 'expense_reimbursement_item'),
                      'expense_claim', 'expense_reimbursement'),
       resource_path = REPLACE(REPLACE(resource_path, 'expense_claim_item', 'expense_reimbursement_item'),
                               'expense_claim', 'expense_reimbursement')
 WHERE code LIKE '%expense_claim%' OR resource_path LIKE '%expense_claim%';

-- ── 8. 校验（new_* 应为目标值；leftover_old_* 应为 0） ───────────────────
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
     AND entity_code = 'expense_reimbursement') AS renamed_actions,
  (SELECT COUNT(*) FROM iam_permission
     AND code LIKE 'runtime:expense_reimbursement:%') AS renamed_perms,
  (SELECT COUNT(*) FROM iam_permission
     WHERE code LIKE '%expense_claim%' OR resource_path LIKE '%expense_claim%') AS leftover_old_perms,
  (SELECT COUNT(*) FROM meta_entity
     WHERE code IN ('expense_claim','expense_claim_item')) AS leftover_old_code;
