-- ============================================================
-- 0126_synap_claim_inspection_handoff.sql
--
-- 一句话：新建三张 append-only 事实表——
--   t_claim_inspection_action（抽检处置留痕）、
--   t_claim_user_agent（代理区间）、
--   t_claim_task_transfer（转交留痕），
--   把后督抽检从"只能登记"升级为"可处置、可追溯、可回退"，并把请假/转交/代理
--   与超时回池这些影响任务归属的事实纳入系统表达。
--
-- 为什么三张表都是 append-only
-- ----------------------------------------------------------------
-- 处置结论、转交、代理区间都是"事实"而非"状态"：事实只能追加，改写历史会让
-- 内控追溯失效（谁在何时把案件打回、转交给谁、代理谁）。因此不提供 UPDATE/DELETE
-- 入口，实体也不声明可变方法改这些字段。
--
-- 为什么代理唯一键用 state 列而不是 NOW() 直接判断"区间生效"
-- ----------------------------------------------------------------
-- MySQL 生成列必须是确定性的，禁止 NOW()/CURRENT_TIMESTAMP 这类非确定性函数，
-- 所以不能写 active_key = IF(valid_from<=NOW() AND valid_to>NOW(), ...)。
-- 正确做法：显式 state 列（ACTIVE/INACTIVE）由应用层在登记/失效时维护，
-- 生成列 active_key = IF(state='ACTIVE', 被代理人ID, NULL) 只承担"同一被代理人
-- 至多一条生效代理"的数据库强约束；"区间外不生效"由查询侧按 valid_from/valid_to
-- 双重判断（读不到即不生效），"区间重叠第二条被拒"由写入前的事前查询 +
-- 唯一键兜底共同保证（并发双写恰 1 成功，与 t_claim_task 同款口径）。
-- 生成列不进实体：bone-metadata-sdk 的 SELECT/INSERT 列由实体元数据生成，
-- 写进实体反而报未知列。
--
-- 为什么超时回池不新建调度框架
-- ----------------------------------------------------------------
-- 复用既有 ScheduleLockService 的分布式锁，扫描作业只是"带锁的幂等 UPDATE"，
-- 回池前置 = 任务仍处未领取态且归属未释放，配 @Version 乐观锁，
-- 重复扫描不产生第二条回池事实。due_at/version 由 0125 的 t_claim_task 已留位。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- "SQL 没报错"不等于"做到"：information_schema 强断言三张表关键列、业务索引
-- 与代理生效唯一键/生成列都真实存在。漏唯一键 = 并发代理互斥退化成应用层约定
-- （静默且不可见）；漏索引 = 回池扫描退化为全表扫描。临时过程用完即删不残留。
-- MESSAGE_TEXT 严格 ≤128 字符（MySQL 硬限制，超长报 ERROR 1648）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_claim_inspection_action` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `claim_id` bigint NOT NULL COMMENT '被抽检案件ID',
  `task_id` bigint DEFAULT NULL COMMENT '关联的重办任务ID(打回且开关开时生成;只登记时NULL)',
  `inspector_id` varchar(64) NOT NULL COMMENT '抽检人ID(取认证主体,不接受客户端声明)',
  `decision` varchar(32) NOT NULL COMMENT 'PASS/REJECT/BACK(通过/驳回/打回重办)',
  `back_reason` varchar(512) DEFAULT NULL COMMENT '打回原因(BACK时必填)',
  `reversal_verified` tinyint NOT NULL DEFAULT '0' COMMENT '已支付打回的冲正前置是否已核验(1=已冲正;0=未冲正或非已支付)',
  `feature_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '处置时INSPECTION_ACTION开关态(0=只登记不流转;留痕自证)',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '处置时间(事实发生时点)',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_claim` (`tenant_id`,`claim_id`,`created_at`),
  KEY `idx_tenant_inspector` (`tenant_id`,`inspector_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='抽检处置留痕(append-only事实:抽检人/结论/打回原因/关联任务,不可改写)';

CREATE TABLE IF NOT EXISTS `t_claim_user_agent` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `principal_id` varchar(64) NOT NULL COMMENT '被代理人ID(请假/缺席方)',
  `agent_id` varchar(64) NOT NULL COMMENT '代理人ID',
  `valid_from` datetime(3) NOT NULL COMMENT '生效起(含)',
  `valid_to` datetime(3) DEFAULT NULL COMMENT '生效止(不含);NULL=长期有效',
  `state` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE(应用层按区间维护;生成列唯一性口径)',
  `reason` varchar(200) DEFAULT NULL COMMENT '代理事由(可审计)',
  `created_by` bigint DEFAULT NULL COMMENT '登记人ID',
  `created_at` datetime(3) NOT NULL COMMENT '登记时间',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_active_agent` (`tenant_id`,`active_key`),
  KEY `idx_tenant_principal` (`tenant_id`,`principal_id`,`valid_from`,`valid_to`),
  `active_key` varchar(64) GENERATED ALWAYS AS (IF(`state` = 'ACTIVE', `principal_id`, NULL)) VIRTUAL COMMENT '生效代理唯一键载体(非ACTIVE为NULL不占唯一性;生成列不进实体)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='代理区间(append-only事实:同一被代理人同一时段至多一条生效代理)';

CREATE TABLE IF NOT EXISTS `t_claim_task_transfer` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `task_id` bigint NOT NULL COMMENT '被转交任务ID',
  `claim_id` bigint NOT NULL COMMENT '案件ID(便于追溯)',
  `from_assignee_id` varchar(64) DEFAULT NULL COMMENT '转出人ID(入池任务可为空)',
  `to_assignee_id` varchar(64) NOT NULL COMMENT '转入人ID',
  `reason` varchar(512) DEFAULT NULL COMMENT '转交原因',
  `transferred_by` varchar(64) NOT NULL COMMENT '操作人ID(取认证主体)',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '转交时间(事实发生时点,不可改写)',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_task` (`tenant_id`,`task_id`,`created_at`),
  KEY `idx_tenant_claim` (`tenant_id`,`claim_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='任务转交留痕(append-only事实:转出/转入/原因/时间,历史不可更新)';

DROP PROCEDURE IF EXISTS sp_verify_0126;
DELIMITER $$
CREATE PROCEDURE sp_verify_0126()
BEGIN
    DECLARE v_action_cols INT DEFAULT 0;
    DECLARE v_agent_cols INT DEFAULT 0;
    DECLARE v_transfer_cols INT DEFAULT 0;
    DECLARE v_uq_active INT DEFAULT 0;
    DECLARE v_gen INT DEFAULT 0;
    DECLARE v_idx INT DEFAULT 0;

    SELECT COUNT(*) INTO v_action_cols
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_claim_inspection_action'
       AND column_name IN ('id','tenant_id','claim_id','task_id','inspector_id',
                           'decision','back_reason','reversal_verified',
                           'feature_enabled','created_at','updated_at','updated_by','deleted');
    IF v_action_cols <> 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0126 assert failed: t_claim_inspection_action columns missing';
    END IF;

    SELECT COUNT(*) INTO v_agent_cols
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_claim_user_agent'
       AND column_name IN ('id','tenant_id','principal_id','agent_id','valid_from',
                           'valid_to','state','active_key','created_at','updated_at','updated_by','deleted');
    IF v_agent_cols <> 12 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0126 assert failed: t_claim_user_agent columns missing';
    END IF;

    SELECT COUNT(*) INTO v_transfer_cols
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_claim_task_transfer'
       AND column_name IN ('id','tenant_id','task_id','claim_id','from_assignee_id',
                           'to_assignee_id','reason','transferred_by','created_at','created_by','updated_at','updated_by','deleted');
    IF v_transfer_cols <> 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0126 assert failed: t_claim_task_transfer columns missing';
    END IF;

    SELECT COUNT(*) INTO v_uq_active
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 't_claim_user_agent'
       AND index_name = 'uk_tenant_active_agent' AND non_unique = 0;
    IF v_uq_active <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0126 assert failed: agent active unique key missing';
    END IF;

    SELECT COUNT(*) INTO v_gen
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_claim_user_agent'
       AND column_name = 'active_key' AND extra LIKE '%GENERATED%';
    IF v_gen <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0126 assert failed: active_key generated column missing';
    END IF;

    SELECT COUNT(DISTINCT index_name) INTO v_idx
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND ((table_name = 't_claim_inspection_action' AND index_name IN ('idx_tenant_claim','idx_tenant_inspector'))
         OR (table_name = 't_claim_task_transfer' AND index_name IN ('idx_tenant_task','idx_tenant_claim')));
    IF v_idx <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0126 assert failed: 4 business indexes missing';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0126();
DROP PROCEDURE IF EXISTS sp_verify_0126;
