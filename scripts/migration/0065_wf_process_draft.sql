-- ============================================================
-- 0065_wf_process_draft.sql
-- 工作流设计器 M0：草稿表 + 版本唯一键
--
-- 为什么：
--   0056 落地后 wf_process_def 只有读路径（WfProcessDefSdkRepository 注释「v1 无发布写路径」），
--   流程定义只能靠迁移/种子数据落库——业务方改一个审批人就要发一次版。设计器（前端建模界面）
--   的前置不是画布，而是这条写路径；本脚本补齐写路径所需的两项持久化改动。
--
--   ① 草稿独立成表 wf_process_draft
--      草稿可变，而 wf_process_def 是「整图版本化的不可变快照」——在途实例按启动时钉住的
--      def_version 装载定义，版本行一旦可改，历史单据的审批依据就随之漂移。
--      更硬的约束来自读取侧：findByVersionRow 的谓词只判 deleted=0、不判 status。草稿若与已发布
--      版本共用 (def_key, def_version)，同一对值会读出两行 → 在途实例推进时抛
--      IncorrectResultSizeDataAccessException，错误现场在「审批」，根因在「发布」。
--
--   ② wf_process_def 补唯一键 uk_wf_def_ver
--      发布时版本号 = 该 key 现有最大版本 + 1。两个运营同时点发布会撞号；靠唯一键把它变成一次
--      明确的失败，而不是两条脏数据（后者要到实例推进时才现形）。0056 只建了
--      idx_wf_def_key(tenant_id, def_key, status)，不含 def_version，兜不住这种情况。
--
-- 前置检查（非幂等风险点，执行前必须为空结果）：
--   若存量环境已有重复版本（历史上有过补丁式修订），加唯一键会失败。先跑：
--     SELECT tenant_id, def_key, def_version, COUNT(*) c FROM wf_process_def
--      WHERE deleted = 0 GROUP BY tenant_id, def_key, def_version HAVING c > 1;
--   有结果则先人工合并（保留 enabled=1 且 updated_at 最新的一行，其余置 deleted=1）再执行本脚本。
--
-- 幂等性：CREATE TABLE IF NOT EXISTS + information_schema 探测分支，可重复执行。
-- ============================================================

-- ---------- 阶段 1：草稿表 ----------
CREATE TABLE IF NOT EXISTS wf_process_draft (
    id              VARCHAR(64)  NOT NULL COMMENT '草稿主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    def_key         VARCHAR(128) NOT NULL COMMENT '流程定义KEY（一 key 一稿）',
    def_name        VARCHAR(255) NOT NULL COMMENT '流程名称',
    definition_json JSON         NOT NULL COMMENT '流程定义JSON原文（含引擎不读的 ui 段：画布坐标）',
    revision        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（前端读到的值作下一次覆盖的谓词）',
    created_by      VARCHAR(64)  NOT NULL COMMENT '创建人',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_by      VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    updated_at      DATETIME(3)  DEFAULT NULL COMMENT '更新时间',
    deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记（实际丢弃走物理删除，见仓储注释）',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_draft_key (tenant_id, def_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流程定义草稿（设计态，可变）';

-- ---------- 阶段 2：版本唯一键（幂等：information_schema.statistics 探测）----------
SET @idx_wf_def_ver = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'wf_process_def' AND index_name = 'uk_wf_def_ver'
);
SET @sql_wf_def_ver = IF(@idx_wf_def_ver = 0,
  'ALTER TABLE wf_process_def ADD UNIQUE KEY uk_wf_def_ver (tenant_id, def_key, def_version)',
  'SELECT 1');
PREPARE st_wf_def_ver FROM @sql_wf_def_ver;
EXECUTE st_wf_def_ver;
DEALLOCATE PREPARE st_wf_def_ver;

-- ---------- 验证 ----------
-- SHOW CREATE TABLE wf_process_draft;
-- SHOW CREATE TABLE wf_process_def;   -- 应含 uk_wf_def_ver
