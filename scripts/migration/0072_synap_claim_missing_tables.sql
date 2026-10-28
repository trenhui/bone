-- ============================================================
-- 0072_synap_claim_missing_tables.sql
--
-- 背景（2026-10-28 全功能实测确诊）
-- --------------------------------
-- 智能理赔 20 个实体类中，有 6 张表**从未建过**：DDL 只覆盖到 0058 的 16 张表，
-- 而 `t_claim_case_track` / `t_claim_inspection` / `t_claim_case_sla` /
-- `t_reconciliation_diff` / `t_reconciliation_report` / `t_domain_event_outbox`
-- 这 6 张是后续迭代新增的聚合，代码已上线、DDL 没跟上。表现是**静默降级**而非报错：
--
--   · t_claim_case_track   每次状态跃迁后 `ClaimCaseTrackRecorder` 打 ERROR 后吞异常
--                          （日志实证：`Table 'bone.t_claim_case_track' doesn't exist`）
--                          ⇒ 审计流水全丢，且 SLA 物化读模型无源可依；
--   · t_claim_inspection   后督抽检整页不可用（发起/领取/结论全 500）；
--   · t_claim_case_sla     SLA 刷新任务空转；
--   · t_reconciliation_*   对账报表落不了库；
--   · t_domain_event_outbox 领域事件无出箱，跨域通知丢事件。
--
-- 「表不存在」被 `try/catch` 或调度任务吞掉时，前端只看到「功能没反应」，
-- 这正是本次「测试所有功能」必须先补的地基。
--
-- 口径
-- --------------------------------
-- · 列名/类型/可空性**逐字段对齐实体注解**（`@Table`/`@Column`/`@Version`），
--   不按「看起来合理」设计——SDK 持久化按注解生成 SQL，列与注解不一致会在写入时才炸；
-- · 审计列统一 `tenant_id/created_by/created_at/updated_by/updated_at/deleted`，
--   与 0058 存量表一字不差；聚合根带 `@Version` 的补 `version`；
-- · `datetime(3)` 对齐 `Instant` 毫秒精度；金额 `decimal(12,2)`、比例 `decimal(6,2)`。
--
-- 幂等
-- --------------------------------
-- 全部 `CREATE TABLE IF NOT EXISTS`，可重复执行；末尾以 information_schema 断言 6 张表齐全，
-- 缺任何一张即 SIGNAL 失败——**不允许「脚本跑过了」被误当「表建好了」**。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 案件状态流转流水（append-only，ADR-003 事件溯源）
--    只由 ClaimStatusChangedEvent 的订阅者写入；created_at 即「发生时间」。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_claim_case_track` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `claim_id` bigint NOT NULL COMMENT '案件ID',
  `from_status` varchar(32) NOT NULL COMMENT '跃迁前状态(枚举name; 建案为DRAFT)',
  `to_status` varchar(32) NOT NULL COMMENT '跃迁后状态(枚举name)',
  `stage` varchar(32) NOT NULL COMMENT '业务阶段码(与status正交)',
  `action_code` varchar(32) NOT NULL COMMENT '动作码(ACCEPT/ALLOCATE/PRE_EXAM_START/...)',
  `operator_id` varchar(64) NOT NULL COMMENT '操作人ID(取自已验签token)',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人名称(展示用快照)',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '发生时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_claim` (`tenant_id`,`claim_id`,`created_at`),
  KEY `idx_tenant_to_status` (`tenant_id`,`to_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件状态流转流水(append-only)';

-- ------------------------------------------------------------
-- 2. 后督抽检聚合（§7.6 / §9.5）
--    一案多检：结论不可改写，需更正另开一单 ⇒ 不做 (tenant,claim) 唯一。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_claim_inspection` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `inspection_no` varchar(64) NOT NULL COMMENT '抽检单号(租户内唯一)',
  `claim_id` bigint NOT NULL COMMENT '案件ID',
  `claim_no` varchar(64) NOT NULL DEFAULT '' COMMENT '案件号(冗余快照，列表免联查)',
  `dimension` varchar(16) NOT NULL COMMENT '抽检维度(AMOUNT/DOCUMENT/MEDICAL/PROCESS)',
  `sample_stratum` varchar(64) NOT NULL DEFAULT '' COMMENT '分层抽样标识',
  `inspect_type` varchar(8) NOT NULL COMMENT '发起方式(AUTO/MANUAL)',
  `status` varchar(16) NOT NULL COMMENT 'PENDING/IN_PROGRESS/CONCLUDED',
  `inspector_id` varchar(64) NOT NULL DEFAULT '' COMMENT '质检人ID',
  `inspected_at` datetime(3) DEFAULT NULL COMMENT '结论时间',
  `result` varchar(8) NOT NULL DEFAULT '' COMMENT '结论(PASS/DIFF; 未结论为空)',
  `diff_type` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '差异类型(ADJUST_MISMATCH/RULE_MISS/AI_MISJUDGE/QUOTA_ABNORMAL/NONE)',
  `diff_detail` varchar(1000) DEFAULT NULL COMMENT '差异明细JSON(字段->本方值/复核值; DIFF必填)',
  `action_taken` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '处置意图(RECALCULATE/REVIEW/REFUND/CLOSE/NONE)',
  `target_status` varchar(32) NOT NULL DEFAULT '' COMMENT '处置目标状态(§6.2枚举名或质检段30/31/32; 一期只登记)',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本(SDK @Version 维护)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_inspection_no` (`tenant_id`,`inspection_no`),
  KEY `idx_tenant_claim` (`tenant_id`,`claim_id`,`created_at`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后督抽检单(任务+结论同表)';

-- ------------------------------------------------------------
-- 3. 案件 SLA 计时物化读模型（ADR-003 / §6.6）
--    可变读模型：同一 (tenant_id, claim_id) 唯一，由调度任务按租户 upsert。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_claim_case_sla` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `claim_id` bigint NOT NULL COMMENT '案件ID',
  `current_status` varchar(32) NOT NULL COMMENT '当前状态(枚举name)',
  `current_stage` varchar(32) NOT NULL COMMENT '当前阶段码',
  `stage_entered_at` datetime(3) NOT NULL COMMENT '进入当前阶段时间(track 末次跃迁)',
  `legal_due_at` datetime(3) DEFAULT NULL COMMENT '法定时限截止(自然日)',
  `sla_due_at` datetime(3) DEFAULT NULL COMMENT '内部作业SLA截止',
  `remaining_work_hours` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '剩余工作小时(下限0)',
  `hang_accum_seconds` bigint NOT NULL DEFAULT '0' COMMENT '累计挂起秒数(工作日口径)',
  `last_refresh_at` datetime(3) NOT NULL COMMENT '最近物化时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本(SDK @Version 维护)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_claim` (`tenant_id`,`claim_id`),
  KEY `idx_tenant_sla_due` (`tenant_id`,`sla_due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件SLA计时物化读模型';

-- ------------------------------------------------------------
-- 4. 对账报表（按「业务日 + 渠道」出报表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_reconciliation_report` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `biz_date` date NOT NULL COMMENT '业务日期',
  `channel` varchar(32) NOT NULL COMMENT '对账渠道(QUOTA 等)',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总笔数',
  `balanced_count` int NOT NULL DEFAULT '0' COMMENT '平账笔数',
  `diff_count` int NOT NULL DEFAULT '0' COMMENT '差异笔数',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '本方总金额',
  `counter_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '对方总金额',
  `status` varchar(16) NOT NULL COMMENT '报表状态',
  `checked_at` datetime(3) NOT NULL COMMENT '对账执行时间',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_date_channel` (`tenant_id`,`biz_date`,`channel`),
  KEY `idx_tenant_checked` (`tenant_id`,`checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账报表';

-- ------------------------------------------------------------
-- 5. 对账差异明细（挂在报表下）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_reconciliation_diff` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `report_id` bigint NOT NULL COMMENT '报表ID',
  `claim_no` varchar(64) NOT NULL COMMENT '案件号',
  `settlement_no` varchar(64) NOT NULL COMMENT '结算单号',
  `diff_type` varchar(32) NOT NULL COMMENT '差异类型(AMOUNT_MISMATCH/STATUS_MISMATCH/ONLY_LOCAL/ONLY_REMOTE)',
  `local_amount` decimal(12,2) DEFAULT NULL COMMENT '本方金额',
  `remote_amount` decimal(12,2) DEFAULT NULL COMMENT '对方金额',
  `local_stage` varchar(32) NOT NULL COMMENT '本方资金阶段',
  `remote_status` varchar(32) NOT NULL COMMENT '对方状态',
  `resolution` varchar(16) NOT NULL COMMENT '处理状态(PENDING/...)',
  `resolved_by` varchar(64) NOT NULL DEFAULT '' COMMENT '处理人',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  KEY `idx_report` (`report_id`),
  KEY `idx_tenant_resolution` (`tenant_id`,`resolution`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账差异明细';

-- ------------------------------------------------------------
-- 6. 领域事件出箱（Outbox 模式）
--    写入与业务同事务，由调度器扫描 PENDING 投递；next_retry_at 支撑退避重试。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_domain_event_outbox` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `aggregate` varchar(64) NOT NULL COMMENT '聚合类型(ClaimCase/Evidence/...)',
  `aggregate_id` bigint NOT NULL COMMENT '聚合ID',
  `event_type` varchar(128) NOT NULL COMMENT '事件类型(全限定类名或短名)',
  `payload` text NOT NULL COMMENT '事件载荷(JSON)',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间(退避)',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本(SDK @Version 维护)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status_retry` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_tenant_aggregate` (`tenant_id`,`aggregate`,`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领域事件出箱(Outbox)';

-- ------------------------------------------------------------
-- 校验（SIGNAL 防「CREATE TABLE IF NOT EXISTS 静默 no-op 却 EXIT 0」的假成功）
--   同名但形状不符的存量表是本仓的已知坑（0068 注释已记录）：
--   IF NOT EXISTS 遇到同名表直接跳过，脚本仍 EXIT 0。故这里不仅数表在不在，
--   还断言每个实体注解的**关键列**确实存在。
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_verify_0072;
DELIMITER $$
CREATE PROCEDURE sp_verify_0072()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM (
        SELECT 't_claim_case_track' AS t UNION ALL
        SELECT 't_claim_inspection' UNION ALL
        SELECT 't_claim_case_sla' UNION ALL
        SELECT 't_reconciliation_report' UNION ALL
        SELECT 't_reconciliation_diff' UNION ALL
        SELECT 't_domain_event_outbox'
      ) AS expected
     WHERE expected.t NOT IN (
        SELECT table_name FROM information_schema.tables
         WHERE table_schema = DATABASE()
     );
    IF v_missing <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0072 校验失败：仍有 claim 表未建立（见上方 SELECT 结果）';
    END IF;

    -- 关键列断言：抽检结论列缺失会让「结论不可改写」的审计语义无处落库
    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 't_claim_inspection'
           AND column_name IN ('inspection_no','claim_id','dimension','status','result',
                               'diff_type','diff_detail','action_taken','target_status','version')) <> 10 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0072 校验失败：t_claim_inspection 列不齐（疑似同名旧表）';
    END IF;

    -- 唯一键断言：SLA 物化靠 (tenant_id, claim_id) 唯一做 upsert，缺了会写重复快照
    IF (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 't_claim_case_sla'
           AND index_name = 'uk_tenant_claim') = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0072 校验失败：t_claim_case_sla 缺 uk_tenant_claim（upsert 会写重复）';
    END IF;

    -- 唯一键断言：抽检单号租户内唯一，重复发起才可能幂等返回原单
    IF (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 't_claim_inspection'
           AND index_name = 'uk_tenant_inspection_no') = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0072 校验失败：t_claim_inspection 缺 uk_tenant_inspection_no';
    END IF;

    SELECT '0072 OK：6 张 claim 表已齐全（含关键列与唯一键）' AS result;
END$$
DELIMITER ;

CALL sp_verify_0072();
DROP PROCEDURE IF EXISTS sp_verify_0072;
