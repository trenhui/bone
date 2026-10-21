-- =============================================================================
-- 0052_mdm_entity_workflow.sql
--
-- 目的：落地 UC-MD-T7 实体级评审发布流（3a 设计 §UC-MD-T7，此前标记 [Target]
-- 「当前只有裸 publish」）。
--
-- 为什么要这张迁移（问题起因，实证非推断）
-- ----------------------
-- ① MasterDataEntity 仅有 DRAFT/PUBLISHED/DISABLED 三态，publish() 无任何门禁：
--    建模人改完定义即可一键发布，L2/L3 治理等级虽推导出 workflow_enabled=true，
--    但该开关只被记录级发布消费（RecordApplicationService#publish 的工作流门禁），
--    实体自身「先审后发」完全缺失——治理等级对实体定义是空转的。
-- ② 设计文档 §1.3 角色表：审批人 Approver「❌ 无（审批流未落库）」。实体发布缺少
--    提交人/批准人/驳回人/原因/时间三类审计字段，SoD（审批人不得自审）无从校验。
--
-- 设计（expand/contract，兼容生产增量）
-- ----------------------
-- · mdm_entity 新增七列均为 nullable + DEFAULT NULL（Expand 阶段）：存量行全部为
--   DRAFT/PUBLISHED/DISABLED，新列取 NULL 不破坏任何既有查询；SDK 显式全列 INSERT
--   自此携带七列（H2 夹具 schema-test.sql 同步补齐，否则 BadSqlGrammar——与
--   mdm_entity/mdm_record 此前 deleted 列漂移同类）。
-- · status 列类型不变（VARCHAR(32)）：新状态 PENDING_REVIEW/APPROVED 均为合法字符串，
--   无枚举表/CHECK 约束需要迁移（与记录级六态同构，字符串语义）。
-- · 权限码 masterdata:entities:approve 见本目录后补的 IAM 登记段（与
--   masterdata:records:approve 同构：提交用 write，审批用 approve）。
--
-- 回滚（Contract 反向）
-- ----------------------
-- ALTER TABLE mdm_entity
--     DROP COLUMN submitted_by, DROP COLUMN submitted_at, DROP COLUMN approved_by,
--     DROP COLUMN approved_at, DROP COLUMN rejected_by, DROP COLUMN reject_reason,
--     DROP COLUMN reject_at;
-- （七列无索引/无外键，DROP 安全；回滚后 L2/L3 实体的 publish 门禁会重新失效，
--  故回滚必须同步回退代码，不可只回滚 DDL。）
-- =============================================================================

ALTER TABLE mdm_entity
    ADD COLUMN submitted_by  BIGINT       DEFAULT NULL COMMENT '评审提交人ID（SoD 校验：审批人不得为提交人）',
    ADD COLUMN submitted_at  DATETIME(3)  DEFAULT NULL COMMENT '提交评审时间',
    ADD COLUMN approved_by   BIGINT       DEFAULT NULL COMMENT '评审批准人ID（审计追溯）',
    ADD COLUMN approved_at   DATETIME(3)  DEFAULT NULL COMMENT '审批通过时间',
    ADD COLUMN rejected_by   BIGINT       DEFAULT NULL COMMENT '驳回人ID（审计追溯）',
    ADD COLUMN reject_reason VARCHAR(512) DEFAULT NULL COMMENT '最近一次评审驳回原因（必填落库，重新提交后保留至下次批准/驳回）',
    ADD COLUMN reject_at     DATETIME(3)  DEFAULT NULL COMMENT '驳回时间';
