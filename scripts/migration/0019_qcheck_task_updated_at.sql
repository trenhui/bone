-- 0019 — mdm_qcheck_task 补 updated_at（HC-008 缺口，gap-candidate → 待批准执行）
--
-- 背景：HC-008 要求新增表含 tenant_id / created_at / updated_at / deleted 四列。
--   mdm_qcheck_task（主数据质量检查任务）确有缺口：表有 status（RUNNING → COMPLETED）
--   与 completed_at，但无 updated_at —— 任务从"开始"到"完成"之间的状态变更时刻
--   无法与业务事件时间线对齐，质量治理排查时只能说"完成了"、说不出"什么时候完成的"
--   （completed_at 记的是 endedAt，属业务时间；updated_at 记的是行被写坏的时刻）。
--
-- 为何不是纯仪式列：QualityCheck 是活跃读写实体（实库 9 行存量），
--   QualityApplicationService#performCheck 走明确的 save(RUNNING) → complete() → update(COMPLETED)
--   两步写，是本仓唯一一处"同一聚合先插后改"的显式生命周期路径。
--
-- 重要：补列**不等于**问题解决。bone-metadata-sdk 不自动填充 updated_at
--   （grep SDK 源码确认：updated_at 仅出现在 ColumnAllocation 等元数据分配表与
--   平台自带的历史表 DDL 中，通用 insert/update/deleteById 均不写该列），
--   必须由实体显式声明 `private LocalDateTime updatedAt;` 并在领域行为里赋值。
--   故本脚本的批准前置条件是：实体侧改动与本脚本同批落地，否则补了列恒为 NULL。
--
-- 处置口径（与 0018 一致）：
--   1. expand 式补列（只 ADD COLUMN，不删不改），符合 ADR-生产数据库增量迁移策略；
--   2. 幂等：MySQL 8.0 的 ADD COLUMN 不支持 IF NOT EXISTS，故用 information_schema
--      判定 + 动态 SQL（见阶段 1）；
--   3. 存量回填 updated_at = created_at —— 若留 DEFAULT CURRENT_TIMESTAMP，
--      ADD 动作会把历史行的"更新时间"错标为迁移执行时刻，凭空造出
--      "这 9 个任务刚被改过"的假象，污染质量治理审计；
--   4. 不改 bone-init.sql 于本脚本内（基线同步属代码侧改动，见下方闭环 (b)）。
--
-- deleted 说明：本表**不需要**补 deleted。质检任务无删除语义（无 delete 调用、
--   领域无revoke 行为），completed_at 已是终态标识。基线中该表 missing 保留
--   ["updated_at","deleted"]，其中 deleted 部分属 by-design。
--
-- L3 批准与执行记录：本脚本已于 2026-10-03 经用户批准在 bone 开发库执行，验收全绿。
--   执行前备份：bak_20261003_qtask（9 行质检任务）。
--   验收结论：① 列已就位（tenant_id/created_at/updated_at齐备）；
--   ② 存量 9 行回填 updated_at = created_at（未凭空标为迁移时刻）；
--   ③ 幂等复跑成功且未覆盖回填；④ 校验行 updated_at_null = 0；⑤ 行数未丢。
--   执行后闭环已完成：bone-init.sql 已补列、QualityCheck 已加 updatedAt（create/complete 赋值）、
--   基线 mdm_qcheck_task 的 updated_at 缺口闭合（条目因 deleted 属 by-design 保留，非缺口）。
--   注意：生产库执行前需架构师单独批准（生产迁移属 L4，禁止 AI 执行）。
--
-- 批准后的完整闭环（四步，已全部完成）：
--   (a) 执行本脚本；                                        ✅ 2026-10-03
--   (b) bone-init.sql 的 mdm_qcheck_task 建表语句补 updated_at 列；✅
--   (c) QualityCheck 实体补 updatedAt 字段 + 在 create()/complete() 赋值；       ✅
--   (d) 基线 mdm_qcheck_task 的 missing 去掉 updated_at。                        ✅

-- ------------------------------------------------------------------
-- 阶段 1：补列（幂等 —— 仅在列不存在时执行）
-- ------------------------------------------------------------------
SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE mdm_qcheck_task ADD COLUMN updated_at DATETIME(3) NULL DEFAULT NULL COMMENT ''更新时间（0019 补齐 HC-008）'' AFTER created_at',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mdm_qcheck_task'
    AND COLUMN_NAME = 'updated_at'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ------------------------------------------------------------------
-- 阶段 2：存量回填（避免历史行的"更新时间"被错标为迁移执行时刻）
-- ------------------------------------------------------------------
UPDATE mdm_qcheck_task
SET updated_at = created_at
WHERE updated_at IS NULL;

-- ------------------------------------------------------------------
-- 阶段 3：校验（updated_at 为空的行数必须为 0）
-- ------------------------------------------------------------------
SELECT 'mdm_qcheck_task' AS tbl, COUNT(*) AS rows_total,
       SUM(updated_at IS NULL) AS updated_at_null
FROM mdm_qcheck_task;
