-- ============================================================
-- 0004_notification_ntf_message_tenant.sql
-- R9（多租户数据隔离方案设计.md）：ntf_message 加 tenant_id 列，站内信按租户隔离。
-- 依据：ntf_message 含 user_id（用户 PII），用户归属于租户，故站内信必须随租户隔离，
--       否则任一租户可读写其他租户的站内信（跨租户 PII 泄露）。
-- 策略：ADR-生产数据库增量迁移策略（expand/contract）。本脚本只负责结构扩展 + 存量回填，
--       不 DROP 任何表。删除列（contract）不在本轮，待全量切换后再评估。
-- 前置：表结构以 bone-init.sql 为准（本脚本只补 tenant_id 列）。
-- 执行前：必须备份（见阶段 0）。
-- 状态：L3 DDL —— 需架构师审批后执行，本文件为草案，未在生产/开发库执行。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_ntf_message_0004 AS SELECT * FROM ntf_message;

-- ---------- 阶段 1：Expand（加列，DEFAULT 0 保证存量行不破坏 NOT NULL） ----------
ALTER TABLE ntf_message
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（R9 隔离维度）';
ALTER TABLE ntf_message
    ADD KEY idx_ntf_tenant_user (tenant_id, user_id);

-- ---------- 阶段 2：回填存量（user_id → 所属租户） ----------
-- 通过 iam_account 反查 user_id 对应的 tenant_id；匹配不到（user_id 为空或账户已删）的
-- 行保持 tenant_id = 0（平台孤儿行），由业务侧后续清理或归属到平台租户。
UPDATE ntf_message nm
SET nm.tenant_id = (
        SELECT a.tenant_id
        FROM iam_account a
        WHERE a.id = nm.user_id
        LIMIT 1
    )
WHERE nm.user_id IS NOT NULL
  AND nm.tenant_id = 0;

-- ---------- 阶段 3：校验（执行后人工确认，非阻断） ----------
-- SELECT tenant_id, COUNT(*) FROM ntf_message GROUP BY tenant_id;
-- 期望：0 值的行应仅剩「user_id 为空 / 账户已删」的孤儿数据。

-- ---------- 回滚 ----------
-- 仅在未进入 contract 阶段、且确需回退时执行：
-- ALTER TABLE ntf_message DROP KEY idx_ntf_tenant_user;
-- ALTER TABLE ntf_message DROP COLUMN tenant_id;
-- （恢复数据：INSERT INTO ntf_message SELECT * FROM bak_ntf_message_0004 需先处理主键冲突）
