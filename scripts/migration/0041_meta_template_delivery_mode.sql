-- =============================================================================
-- 0041_meta_template_delivery_mode.sql
--
-- 目的：模板声明交付模式（delivery_mode），实例化时继承，消灭「发布成功却无表」的使用姿势坑。
--
-- 问题起因（E2E 实证，2026-10 Phase C）
-- ----------------------
-- 模板实例化（MetaTemplateApplicationService.instantiate）此前要求调用方显式传
-- deliveryMode，而模板本身不声明交付模式：
--   · 调用方漏传/传 0（GENERATIVE）⇒ publishEntity 不触发 physicalStructureGateway.align
--     ⇒ 「发布成功但无物理表」，数据页无入口（实测踩坑，见 .workbuddy/memory/2026-10-12）。
-- 业界最佳实践（显式优于隐式）：创建向导必须让用户在「生成式 vs 运行时」间显式选择并
-- 预告后果；模板作为可复用蓝图，理应在模板侧声明、实例化侧继承，而不是每次实例化重新猜。
--
-- 设计
-- ----------------------
-- · meta_model_template.delivery_mode：与 meta_entity.delivery_mode 同口径
--   （0=GENERATIVE 生成式交付，不建运行时物理表；1=RUNTIME 运行时元数据面，发布即建表对齐）。
-- · 存量回填口径（证据驱动）：**有状态机动作定义的模板**（meta_model_template_action）
--   即面向运行时数据面的模板（报销/补货/巡检/访客等场景模板），回填 1；
--   无动作定义的纯主数据模板（如 tpl_customer_base）保持 0。
-- · 新模板默认 0（GENERATIVE 保守默认）：建错表的清理成本远高于不建表；需要运行时语义的
--   模板由种子/维护显式置 1。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0041_meta_template_delivery_mode.sql
-- 前置：0040_template_action_state_machine.sql 已落地（回填依赖其动作表判定）。
-- 幂等：ADD COLUMN 用 information_schema 条件判断；回填 UPDATE 可重复执行（结果收敛）。
-- expand-only：本脚本只加列 + 回填，不删不改旧列，与 ADR expand/contract 策略一致。
-- =============================================================================

USE bone;

-- ── 1. 加列（expand；information_schema 条件判断保证重复执行安全）────────────────
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_model_template' AND COLUMN_NAME = 'delivery_mode'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE meta_model_template ADD COLUMN delivery_mode TINYINT NOT NULL DEFAULT 0 COMMENT ''交付模式：0-生成式(GENERATIVE)不建运行时物理表 1-运行时(RUNTIME)发布即建表对齐（实例化默认继承）'' AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 2. 存量回填：有状态机动作定义的模板 ⇒ RUNTIME(1) ───────────────────────────
-- 与 0040 的口径一致：动作定义是运行时数据面的充要特征（无动作 = 纯主数据蓝图）。
UPDATE meta_model_template t
SET t.delivery_mode = 1, t.updated_at = NOW(3)
WHERE t.deleted = 0
  AND EXISTS (
    SELECT 1 FROM meta_model_template_action a
    WHERE a.template_id = t.id AND a.deleted = 0
  );

-- ── 3. 校验 ──────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_model_template WHERE deleted = 0) AS template_total,
  (SELECT COUNT(*) FROM meta_model_template WHERE deleted = 0 AND delivery_mode = 1) AS runtime_template_count,
  (SELECT COUNT(*) FROM meta_model_template WHERE deleted = 0 AND delivery_mode = 0) AS generative_template_count;
