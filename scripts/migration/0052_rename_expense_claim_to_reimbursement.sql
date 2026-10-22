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
--
-- 自校验（脚本第 8 步 SELECT）通过判据：
--   · new_tables = 2（物理表 t_expense_reimbursement / _item 均已存在）
--   · renamed_entities_active ≥ 1（expense_reimbursement_item 通常 active；header 是否 active 取决于业务软删状态）
--   · renamed_actions ≥ 1、renamed_perms ≥ 6（权限码为基名形态，runtime:expense_reimbursement%
--     覆盖「基名 3 条 + 子实体 3 条」共 6 条；注意模式不带末尾冒号，否则会漏掉子实体权限）
--   · leftover_old_tables / leftover_old_perms / leftover_old_code 必须全部 = 0
--     （非 0 说明仍有旧名残留，须先排查再放过，不可带旧码上线）
--
-- 回滚预案：本脚本为「改名」型一次性迁移，且应用代码已同步改名；生产环境**不可反向执行本脚本**
--   （反向执行会使库名与已上线代码失配，导致运行期找不到实体/表）。唯一安全的回滚方式是：
--   执行前对目标库做全量备份 / 快照，异常时整库恢复至迁移前状态。
--   若仅在「代码尚未上线」的预发环境需回退元数据改名，可按第 2~7 步反向 REPLACE 手工构造，
--   但必须同步回退应用代码，否则不可用。
--   ⚠ 注意：本脚本含 DDL（RENAME TABLE），MySQL 下 DDL 会隐式提交，脚本整体非原子；
--      中途失败会留下部分改动，故执行前备份是硬性前置条件。
--
-- 验证记录：已在临时库（镜像真实旧码种子：主/子实体、action_def×4、iam 基名+子实体×6、
--   ui_schema/field/template 内嵌旧码、待 RENAME 物理表）端到端跑通，自校验 7 项全部达标
--   （new_tables=2 / renamed_perms=6 / 三项 leftover=0）；并在 dev 库实际执行验证，
--   配套后端集成测试（ServerContextLoads / OrderPaymentMetadataE2E / MetadataManagementController）12 例全绿。
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

-- ── 8. 校验（通过判据见脚本头部「自校验」段：new_* 达目标值、leftover_old_* 全为 0） ──
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
