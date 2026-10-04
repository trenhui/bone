-- 0018 — mdm_steward 补 updated_at（HC-008 缺口，gap-candidate → 待批准执行）
--
-- 背景：HC-008 要求新增表含 tenant_id / created_at / updated_at / deleted 四列。
--   2026-10-02 基线审计发现 mdm_steward（2026-09-25 新增）确有缺口：表带 version（乐观锁）
--   与 deleted（软删），StewardApplicationService#unassign 走 stewardRepository.deleteById
--   软删，但无 updated_at —— created_at 只说明"何时授的"，撤销后无从追溯"何时撤的"。
--   治理审计要能回答"某数据责任人在某段时间是否持有该角色"，缺这一列答案不成立。
--
-- 为何不是纯仪式列：StewardAssignment 是活跃读写实体（实库已有 1 行指派），
--   且 mdm_steward 参与 scripts/verify_tenant_isolation_e2e.py 的租户隔离验证清单。
--
-- 重要：补列**不等于**问题解决。bone-metadata-sdk 不自动填充 updated_at
--   （grep SDK 源码确认：updated_at 仅出现在 ColumnAllocation 等元数据分配表与
--   平台自带的历史表 DDL 中，通用 insert/update 不写该列），必须由实体显式声明
--   `private LocalDateTime updatedAt;` 并在领域行为里赋值 —— 范式见 MasterDataEntity#touch。
--   故本脚本的批准前置条件是：实体侧改动与本脚本同批落地，否则补了列恒为 NULL。
--
-- 处置口径：
--   1. expand 式补列（只 ADD COLUMN，不删不改），符合 ADR-生产数据库增量迁移策略；
--   2. 幂等：MySQL 8.0 的 ADD COLUMN 不支持 IF NOT EXISTS，故用 information_schema
--      判定 + 动态 SQL（见阶段 1）；
--   3. 存量回填 updated_at = created_at —— 若留 DEFAULT CURRENT_TIMESTAMP，
--      ADD 动作会把历史行的"更新时间"错标为迁移执行时刻，凭空造出
--      "这些指派刚被改过"的假象，污染治理审计；
--   4. 不改 bone-init.sql：生产基线在 expand/contract 的 contract 阶段统一同步，
--      避免开发库 DROP DATABASE 重建时与本脚本重复执行产生分歧。
--
-- 范围：本脚本**只处理 mdm_steward**。mdm_steward_scope 虽同样缺 updated_at，
--   但它是 R7 已登记的声明门禁盲区（无 @Table 实体映射、无仓储、实库 0 行），
--   补列无实际读写通道承接，且该表是否随 G3 下线尚未裁决 —— 属另一议题，不并入本脚本。
--
-- L3 批准与执行记录：本脚本已于 2026-10-03 经用户批准在 bone 开发库执行，验收全绿。
--   执行前备份：bak_20261003_steward（1 行，指派 758734411498782720 / APPROVER）。
--   验收结论：① 列已就位（tenant_id/created_at/updated_at/deleted/version 齐备）；
--   ② 存量 1 行回填 updated_at = created_at（未凭空标为迁移时刻）；
--   ③ 幂等复跑成功且未覆盖回填（复跑后 updated_at 仍 = created_at）；
--   ④ 校验行 updated_at_null = 0；⑤ 行数未丢、APPROVER 指派完好。
--   执行后闭环已完成：bone-init.sql 已补列、StewardAssignment 已加 updatedAt + revoke()、
--   基线 mdm_steward 条目已移除。
--   注意：生产库执行前需架构师单独批准（生产迁移属 L4，禁止 AI 执行）。
--
-- 批准后的完整闭环（四步，已全部完成）：
--   (a) 执行本脚本；                                        ✅ 2026-10-03
--   (b) bone-init.sql 的 mdm_steward 建表语句补 updated_at 列； ✅
--   (c) StewardAssignment 实体补 updatedAt 字段 + 在 unassign 路径（及 assign）赋值； ✅
--   (d) 基线 mdm_steward 一条 missing 收敛为空并移出基线。      ✅
--   (b)(c) 与 (a) 同批落地，未出现"列存在但恒 NULL"的中间态。

-- ------------------------------------------------------------------
-- 阶段 1：补列（幂等 —— 仅在列不存在时执行）
-- ------------------------------------------------------------------
SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE mdm_steward ADD COLUMN updated_at DATETIME(3) NULL DEFAULT NULL COMMENT ''更新时间（0018 补齐 HC-008）'' AFTER created_at',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mdm_steward'
    AND COLUMN_NAME = 'updated_at'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ------------------------------------------------------------------
-- 阶段 2：存量回填（避免历史行的"更新时间"被错标为迁移执行时刻）
-- ------------------------------------------------------------------
UPDATE mdm_steward
SET updated_at = created_at
WHERE updated_at IS NULL;

-- ------------------------------------------------------------------
-- 阶段 3：校验（updated_at 为空的行数必须为 0）
-- ------------------------------------------------------------------
SELECT 'mdm_steward' AS tbl, COUNT(*) AS rows_total,
       SUM(updated_at IS NULL) AS updated_at_null
FROM mdm_steward;
