-- ============================================================
-- 0122_synap_claim_blind_label_task.sql
--
-- 一句话：新建 t_claim_blind_label_task——独立盲标任务表，
--         把「人工结论与 AI 建议分离存储」从纪律变成结构（G4-1 数据飞轮）。
--
-- 背景（为什么必须建新表，而不是复用 t_claim_inspection）
-- ----------------------------------------------------------------
-- 后督抽检员在流程内看得到 AI 建议（这正是抽检的工作方式），因此其结论
-- 不能当金标准：人工复核受 AI 建议影响（Sculley《Hidden Technical Debt》
-- feedback loops），偏差会被当成 ground truth 放大。独立盲标要求标注人在
-- 不看任何 AI 痕迹的前提下重判同一案件——这需要与抽检单分离的存储：
--   · 提交前表里没有任何 AI 数据（ai_suggestion_json 在解盲时才从决策轨迹
--     读入），绕过应用层直查库也看不到——不是「不让看」而是「无物可看」；
--   · 抽检与盲标共用一张表会让「看没看过 AI 建议」这个关键语义丢失。
--
-- 四道防反馈环闸（应用侧同批落码）
-- ----------------------------------------------------------------
--   1. 载荷禁区：label_payload_json 键名过 AI 痕迹黑名单扫描（应用层，
--      BlindLabelTask.FORBIDDEN_PAYLOAD_KEYS），命中 422；
--   2. 物理分离：ai_suggestion_json 解盲时才写入；
--   3. 一次提交：submit 仅 PENDING 态一次，结论不可改写；
--   4. 标注人去污染：assignee_id 与该案抽检质检人同人即 409
--      （应用层校验，表结构不承载）。
--
-- 形态（为什么 agreement 只有两个值）
-- ----------------------------------------------------------------
-- PAIRED 只表示「成对样本已产出」；人工结论与 AI 建议的语义一致率由评测
-- 流水线离线判定（判定规则可升级重算），不冻结在解盲事务里。
-- AI 参与口径复用单一真源 confidence_source == MODEL（0121 引入的溯源列）：
-- 证明不了是模型参与（STUB/FALLBACK/UNKNOWN）就不算，「桩环境盲标出来的
-- AI 一致率」是假证据。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- information_schema 强断言表存在且关键列齐全（脚本「没报错」不等于「做到」）。
-- 幂等：CREATE TABLE IF NOT EXISTS，不碰存量数据。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_claim_blind_label_task` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `task_no` varchar(64) NOT NULL COMMENT '盲标任务号(租户内唯一)',
  `claim_id` bigint NOT NULL COMMENT '案件ID',
  `assignee_id` varchar(64) NOT NULL COMMENT '标注人ID(与本案抽检质检人互斥,防既有印象污染)',
  `status` varchar(16) NOT NULL COMMENT 'PENDING/LABELED/UNBLINDED(终态不可变)',
  `label_payload_json` text NOT NULL COMMENT '标注载荷JSON(材料引用;键名过AI痕迹禁区扫描)',
  `payload_hash` char(64) NOT NULL COMMENT '载荷指纹(sha256;派发即冻结)',
  `label_result_json` varchar(2000) DEFAULT NULL COMMENT '人工盲标结论JSON(提交时一次写入,不可改)',
  `submitted_at` datetime(3) DEFAULT NULL COMMENT '盲标结论提交时间',
  `ai_suggestion_json` varchar(2000) DEFAULT NULL COMMENT 'AI建议快照(解盲时才从决策轨迹读入;提交前库内无AI数据=物理分离)',
  `agreement` varchar(16) DEFAULT NULL COMMENT '配对结果(PAIRED成对产出/AI_ABSENT AI未真实参与;一致率由评测侧离线判定)',
  `unblinded_at` datetime(3) DEFAULT NULL COMMENT '解盲时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本(SDK @Version 维护)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_task_no` (`tenant_id`,`task_no`),
  KEY `idx_tenant_claim` (`tenant_id`,`claim_id`,`status`),
  KEY `idx_tenant_assignee` (`tenant_id`,`assignee_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='独立盲标任务(G4-1防反馈环:派发脱敏+一次提交+解盲才配对)';

-- 终态断言：表与关键列必须存在。IF 只能在过程上下文使用：临时过程 + CALL + DROP，不留过程体。
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0122$$
CREATE PROCEDURE sp_verify_0122()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_blind_label_task'
       AND column_name IN ('id', 'task_no', 'claim_id', 'assignee_id', 'status',
                           'label_payload_json', 'payload_hash', 'ai_suggestion_json',
                           'agreement', 'tenant_id', 'version');
    IF v_missing <> 11 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0122 terminal assert failed: blind label table/columns missing';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0122();
DROP PROCEDURE IF EXISTS sp_verify_0122;
