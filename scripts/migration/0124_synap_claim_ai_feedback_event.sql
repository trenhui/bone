-- ============================================================
-- 0124_synap_claim_ai_feedback_event.sql
--
-- 一句话：新建 t_claim_ai_feedback_event，把「AI 建议曝光 → 人工裁决」从浏览器
--         本地事件升级为服务端可审计的 append-only 事实，闭合工作台 L1 回流。
--
-- 为什么是追加新表而不是复用 hitl_item
-- ----------------------------------------------------------------
-- bone-ai 已有通用 HITL（hitl_item / hitl_disposition，含 ReasonType 四值），
-- 但网关白名单只转发 /api/v1/claims/**，/api/ai/v1/** 不通；理赔前端调不到它。
-- 在理赔域自建一条细记录有两点不可替代：
--   ① 曝光是采纳率的分母，通用 HITL 只有「待终审项」，没有曝光事实；
--   ② 裁决必须绑定 traceId + AI 参与四态，才能证明「只对真实模型参与做裁决」。
-- 本表只记人对 AI 建议的判断，不改案件状态、不写金额、不触发决策执行。
--
-- 为什么 event_id 唯一
-- ----------------------------------------------------------------
-- 客户端为每个事件生成 UUID 作幂等键：网络超时后的重试必须返回首次结果，
-- 而不是产生第二条曝光（会美化采纳率）或第二条裁决（会污染评测集）。
-- 同键不同载荷判 409，不静默覆盖。租户内唯一，跨租户互不干扰。
--
-- 为什么所有 PII 都不进表
-- ----------------------------------------------------------------
-- 检索键可为姓名/手机号，但回流事件只存 claimId / traceId / 原因码 / 备注，
-- 不存姓名、证件、金额、决策结论值。备注限 200 字符且由前端明示「仅展示用」。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- 「SQL 没报错」不等于「做到」：information_schema 强断言表与 12 个关键列存在，
-- 且唯一键已建立（漏唯一键 = 幂等退化成重复计数，静默且不可见）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_claim_ai_feedback_event` (
  `id` bigint NOT NULL COMMENT '主键(分布式ID)',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `event_id` varchar(64) NOT NULL COMMENT '客户端事件UUID(幂等键,租户内唯一)',
  `claim_id` bigint NOT NULL COMMENT '案件ID',
  `event_type` varchar(16) NOT NULL COMMENT 'IMPRESSION/DISPOSITION',
  `trace_id` varchar(128) NOT NULL DEFAULT '' COMMENT '决策轨迹ID',
  `involvement` varchar(32) NOT NULL COMMENT 'AI参与四态，DISPOSITION仅允许AI_ASSISTED',
  `disposition` varchar(16) DEFAULT NULL COMMENT 'ADOPT/REJECT/MODIFY',
  `reason_type` varchar(32) DEFAULT NULL COMMENT 'ReasonType四值，REJECT/MODIFY必填',
  `comment_text` varchar(200) DEFAULT NULL COMMENT '人工备注(不进结构化口径)',
  `modified_fields_json` varchar(512) DEFAULT NULL COMMENT '被改字段名白名单JSON(不含值)',
  `scene` varchar(32) NOT NULL DEFAULT 'case' COMMENT 'case/review，禁blind_label',
  `actor_id` varchar(64) NOT NULL DEFAULT '' COMMENT '操作人(取认证主体,不接受客户端声明)',
  `version` bigint NOT NULL DEFAULT '0' COMMENT 'SDK版本列',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime(3) NOT NULL COMMENT '服务端接收时间(唯一时间真源)',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `updated_at` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_event` (`tenant_id`,`event_id`),
  KEY `idx_tenant_claim` (`tenant_id`,`claim_id`,`created_at`),
  KEY `idx_tenant_type` (`tenant_id`,`event_type`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='AI建议曝光与人工裁决回流(append-only,不碰金额与决策)';

-- 终态断言。IF 只能在过程上下文使用：临时过程 + CALL + DROP，不留过程体。
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0124$$
CREATE PROCEDURE sp_verify_0124()
BEGIN
    DECLARE v_cols INT DEFAULT 0;
    DECLARE v_uq INT DEFAULT 0;

    SELECT COUNT(*) INTO v_cols
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_ai_feedback_event'
       AND column_name IN ('id','tenant_id','event_id','claim_id','event_type',
                           'trace_id','involvement','disposition','reason_type',
                           'scene','actor_id','created_at');
    IF v_cols <> 12 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0124 assert failed: feedback table/columns missing';
    END IF;

    SELECT COUNT(*) INTO v_uq
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_ai_feedback_event'
       AND index_name = 'uk_tenant_event'
       AND non_unique = 0;
    IF v_uq <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0124 assert failed: idempotency unique key missing';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0124();
DROP PROCEDURE IF EXISTS sp_verify_0124;
