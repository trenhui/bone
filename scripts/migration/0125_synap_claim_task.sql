-- ============================================================
-- 0125_synap_claim_task.sql
--
-- 一句话：新建 t_claim_task（一案多任务的归属事实）与 t_claim_assign_rule
--         （可审计的分配规则），把"归属"从 t_claim_case.first_reviewer_id 一类单列
--         升级为独立可领取、可审计、可回退的行级事实。
--
-- 为什么必须新表而不是给 t_claim_case 加列
-- ----------------------------------------------------------------
-- ① 一案多任务：初审 / 补件 / 复审 / 抽检各自是独立状态与独立领取对象，
--    单列只能表达"一个案件有一个负责人"，加列即把第二个任务覆盖掉第一个。
-- ② 行级并发领取：领取是 UPDATE 单行 + 乐观锁，加列方案没有独立主键，
--    两个人同时领取同一案件时无法区分"谁领到了哪个任务"。
-- ③ 可审计：归属是事实（D4 写入期分配、查询期只读）。事实需要独立行的
--    created_at / claimed_at / assign_rule_id，加列只能留最后一次。
--
-- 为什么用生成列 active_key 而不是应用层先查后写
-- ----------------------------------------------------------------
-- 判据是"同案同类型至多一条生效任务"。MySQL 没有 partial unique index，
-- 标准解法是生成列 + 唯一键：
--     active_key = IF(生效, CONCAT(claim_id,':',task_type), NULL)
--     UNIQUE(tenant_id, active_key)
-- 关键在 NULL：MySQL 唯一索引不把 NULL 视为重复 ⇒ 终态行（DONE/CANCELLED）
-- 可以有任意多条，不受约束；只有生效行占名额。应用层先查后写在并发下存在
-- 双写窗口，"并发双领取恰 1 成功"无法由数据库强保证。
--
-- 为什么生成列不进实体
-- ----------------------------------------------------------------
-- bone-metadata-sdk 的 SELECT/INSERT 列清单由实体元数据生成（不是 SELECT *），
-- 生成列既不会被 SELECT 出来也不会被 INSERT 进去，实体里写它反而会报未知列。
--
-- 为什么规则表优先用 priority 而不是"规则引擎"
-- ----------------------------------------------------------------
-- 匹配维度只有 4 个（险种 / 金额段 / 渠道 / 任务类型）且必须可解释、可审计。
-- 求解顺序固定：priority 升序 + id 升序（同优先级按主键定序，保证同一输入
-- 永远得到同一结果——否则同一任务在不同时刻归属不同，批 2 的归因与批 4 的
-- 超时回池都会读到漂移结果）。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- "SQL 没报错"不等于"做到"：information_schema 强断言两张表的关键列、
-- 三条业务索引与生效唯一键都真实存在。漏唯一键 = 并发互斥退化成应用层
-- 约定（静默且不可见）；漏索引 = 待办列表退化为全表扫描（EXPLAIN 判据失效）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_claim_task` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `task_no` varchar(64) NOT NULL COMMENT '任务号(租户内唯一,外部引用键)',
  `claim_id` bigint NOT NULL COMMENT '案件ID(一案多任务,非唯一)',
  `task_type` varchar(32) NOT NULL COMMENT 'INITIAL_REVIEW/FIX_SUPPLEMENT/SECOND_REVIEW/INSPECTION',
  `status` varchar(32) NOT NULL COMMENT 'PENDING_CLAIM/ASSIGNED/PROCESSING/DONE/CANCELLED',
  `assignee_type` varchar(16) DEFAULT NULL COMMENT 'USER/DEPT/ROLE;NULL=入池未指派',
  `assignee_id` varchar(64) DEFAULT NULL COMMENT '归属主体ID(USER=用户ID DEPT=部门ID ROLE=角色码)',
  `assign_dept_id` varchar(64) DEFAULT NULL COMMENT '归属部门(部门池口径)',
  `assign_rule_id` bigint DEFAULT NULL COMMENT '命中的分配规则ID(可审计:为什么是这个归属)',
  `assign_reason` varchar(32) DEFAULT NULL COMMENT 'RULE_MATCH/NO_RULE/ALL_INELIGIBLE/BACKFILL',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级(数值越小越先处理)',
  `claim_type` varchar(32) NOT NULL DEFAULT '' COMMENT '险种快照(规则匹配维度)',
  `source_channel` varchar(32) NOT NULL DEFAULT '' COMMENT '渠道快照(规则匹配维度)',
  `claim_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '申请金额快照(金额段匹配与审计)',
  `assigned_at` datetime(3) DEFAULT NULL COMMENT '指派时间(入池任务为NULL)',
  `claimed_at` datetime(3) DEFAULT NULL COMMENT '被领取时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '进入终态(DONE/CANCELLED)时间',
  `due_at` datetime(3) DEFAULT NULL COMMENT '处理时限(批4超时回池用,本批只留位)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT 'SDK版本列(乐观锁,并发自领互斥)',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  `active_key` varchar(64) GENERATED ALWAYS AS (IF(`status` IN ('PENDING_CLAIM','ASSIGNED','PROCESSING') AND `deleted` = 0, CONCAT(`claim_id`, ':', `task_type`), NULL)) VIRTUAL COMMENT '生效任务唯一键载体(终态为NULL,不占唯一性;生成列不进实体)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_task_no` (`tenant_id`,`task_no`),
  UNIQUE KEY `uk_tenant_active` (`tenant_id`,`active_key`),
  KEY `idx_tenant_assignee_status` (`tenant_id`,`assignee_id`,`status`,`priority`,`created_at`),
  KEY `idx_tenant_dept_status` (`tenant_id`,`assign_dept_id`,`status`,`created_at`),
  KEY `idx_tenant_claim_type` (`tenant_id`,`claim_id`,`task_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='案件任务(一案多任务:独立领取/独立状态/归属事实可审计)';

CREATE TABLE IF NOT EXISTS `t_claim_assign_rule` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `rule_name` varchar(64) NOT NULL COMMENT '规则名(运营可读)',
  `task_type` varchar(32) NOT NULL COMMENT '适用任务类型;* 表示全部类型',
  `claim_type` varchar(32) NOT NULL DEFAULT '' COMMENT '险种;空=不限制',
  `source_channel` varchar(32) NOT NULL DEFAULT '' COMMENT '渠道;空=不限制',
  `amount_min` decimal(12,2) DEFAULT NULL COMMENT '金额下限(含);NULL=不设下限',
  `amount_max` decimal(12,2) DEFAULT NULL COMMENT '金额上限(不含);NULL=不设上限',
  `assignee_type` varchar(16) NOT NULL COMMENT '目标类型 USER/DEPT/ROLE',
  `assignee_id` varchar(64) NOT NULL COMMENT '目标ID;空串=规则未配置完整(不可命中)',
  `assign_dept_id` varchar(64) DEFAULT NULL COMMENT '目标部门(部门池归属口径)',
  `priority` int NOT NULL DEFAULT '100' COMMENT '求解优先级(升序优先;同值按id升序定序)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT 'SDK版本列',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_rule_match` (`tenant_id`,`task_type`,`claim_type`,`source_channel`,`assignee_type`,`assignee_id`),
  KEY `idx_tenant_match` (`tenant_id`,`enabled`,`task_type`,`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='案件分配规则(固定求解顺序:priority升序+id升序)';

-- 终态断言。IF 只能在过程上下文使用：临时过程 + CALL + DROP，不留过程体。
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0125$$
CREATE PROCEDURE sp_verify_0125()
BEGIN
    DECLARE v_task_cols INT DEFAULT 0;
    DECLARE v_rule_cols INT DEFAULT 0;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_uq_active INT DEFAULT 0;
    DECLARE v_gen INT DEFAULT 0;

    SELECT COUNT(*) INTO v_task_cols
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_task'
       AND column_name IN ('id','task_no','claim_id','task_type','status',
                           'assignee_type','assignee_id','assign_dept_id','assign_rule_id',
                           'assign_reason','priority','claim_type','source_channel','claim_amount',
                           'assigned_at','claimed_at','finished_at','tenant_id','version','deleted');
    IF v_task_cols <> 20 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0125 assert failed: t_claim_task columns missing';
    END IF;

    SELECT COUNT(*) INTO v_rule_cols
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_assign_rule'
       AND column_name IN ('id','rule_name','task_type','claim_type','source_channel',
                           'amount_min','amount_max','assignee_type','assignee_id',
                           'priority','enabled','tenant_id','version','deleted');
    IF v_rule_cols <> 14 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0125 assert failed: t_claim_assign_rule columns missing';
    END IF;

    SELECT COUNT(DISTINCT index_name) INTO v_idx
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_task'
       AND index_name IN ('idx_tenant_assignee_status',
                          'idx_tenant_dept_status',
                          'idx_tenant_claim_type');
    IF v_idx <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0125 assert failed: 3 task indexes missing';
    END IF;

    SELECT COUNT(*) INTO v_uq_active
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_task'
       AND index_name = 'uk_tenant_active'
       AND non_unique = 0;
    IF v_uq_active <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0125 assert failed: active unique key missing';
    END IF;

    SELECT COUNT(*) INTO v_gen
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_task'
       AND column_name = 'active_key'
       AND extra LIKE '%GENERATED%';
    IF v_gen <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0125 assert failed: active_key generated column missing';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0125();
DROP PROCEDURE IF EXISTS sp_verify_0125;
