-- ============================================================
-- 0097_synap_claim_consent_trace.sql
--
-- 一句话：给赔案主表 t_claim_case 增加两列 PIPL 同意留痕字段
--        （consent_record_id / consent_withdrawn_flag）。
--
-- 背景（为什么这两列是合规前置，而不是"以后再说"）
-- ----------------------------------------------------------------
-- 医疗健康数据属**敏感个人信息**，PIPL 第 29 条要求**单独同意**（不得以保单条款概括同意替代），
-- 第 15 条要求撤回同意后**停止基于该同意的处理**。契约三处已把这两列写死：
--   · 工程落地版 §9.5 t_claim_case DDL：`consent_record_id VARCHAR(64) DEFAULT NULL`
--     / `consent_withdrawn_flag TINYINT NOT NULL DEFAULT 0`；
--   · 07-delivery/05 数据字典补录：「同意留痕与假名化职责，随 claim 主表」；
--   · 08-go-live/06 DSAR 册 G10：DSAR 端点 + 撤回同意属 **P0 合规前置**（首单正式客户前）。
--
-- 本轮之前，全仓 `grep -ri consent` **零命中**：即"声称已做 PIPL 合规、实际一条留痕都没有"。
-- 这正是最危险的一类缺口——它不是运行期报错，而是监管报批（金发〔2026〕8 号）时才发现
-- 「撤回同意的证据链不存在」，且历史数据无法回溯补齐。
--
-- 列口径（与契约逐字对齐，勿自行变更）
-- ----------------------------------------------------------------
--   · consent_record_id   VARCHAR(64) NULL —— 指向**外部同意流水**的记录号。本系统不建同意台账
--     （同意在投保/授权环节采集，属上游），此处只保存可回查的引用；NULL = 未登记（存量案件）。
--   · consent_withdrawn_flag TINYINT NOT NULL DEFAULT 0 —— 1 = 已撤回。默认 0 保证存量行行为不变。
--     为什么用 TINYINT 而不是 BOOLEAN/枚举：与 t_claim_case.hang_up_status / deleted 同口径，
--     且避免"枚举带 getCode() 落 varchar 列 ⇒ 建得进读不出"那类历史坑。
--
-- 幂等 / 执行
-- ----------------------------------------------------------------
--   · 幂等：information_schema 探测 + PREPARE 动态 DDL（0085/0090/0094/0095 同范式），
--     重复执行影响 0 行，可安全重跑；
--   · 终态断言：两列都必须就位，且 consent_withdrawn_flag 必须 NOT NULL DEFAULT 0——
--     缺列会让写入期直接 ERROR 1054（实体元数据生成 INSERT 列），
--     而默认值错会让"存量案件被静默判成已撤回"，两种都要 fail-fast；
--   · bone-init.sql 同提交在 t_claim_case 补这两列（G39 门禁只比表级终态，列是盲区，须手动对齐）；
--   · 模块内 db/migration/V*.sql 属**参考态**（无 Flyway 依赖、无 runner、CI 不引用），
--     本脚本未在其下同步副本，避免与并行会话的编号推进冲突；双源收敛见契约 §12.2.1（L3）。
--
-- 执行（生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0097_synap_claim_consent_trace.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- ① t_claim_case.consent_record_id（单独同意记录号，可空）
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_case ADD COLUMN consent_record_id VARCHAR(64) DEFAULT NULL COMMENT ''PIPL 单独同意记录号(外部同意流水引用; NULL=未登记)'' AFTER insured_id_hash',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_case' AND COLUMN_NAME = 'consent_record_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ② t_claim_case.consent_withdrawn_flag（撤回标记，NOT NULL DEFAULT 0）
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_case ADD COLUMN consent_withdrawn_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''同意撤回标记(1=已撤回, 新处理链拒绝使用该主体数据)'' AFTER consent_record_id',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_case' AND COLUMN_NAME = 'consent_withdrawn_flag'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ③ 终态断言（缺列 ⇒ 写路径 1054；默认值错 ⇒ 存量案件被误判已撤回）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0097;
DELIMITER $$
CREATE PROCEDURE sp_verify_0097()
BEGIN
    DECLARE v_col INT DEFAULT 0;
    DECLARE v_nullable VARCHAR(8) DEFAULT NULL;
    DECLARE v_default VARCHAR(64) DEFAULT NULL;

    SELECT COUNT(*) INTO v_col
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_case'
       AND COLUMN_NAME IN ('consent_record_id', 'consent_withdrawn_flag');
    IF v_col <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0097 校验失败：t_claim_case 的 consent_record_id / consent_withdrawn_flag 未就位';
    END IF;

    SELECT IS_NULLABLE, COLUMN_DEFAULT INTO v_nullable, v_default
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_case'
       AND COLUMN_NAME = 'consent_withdrawn_flag';
    IF v_nullable <> 'NO' OR v_default IS NULL OR v_default <> '0' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0097 校验失败：consent_withdrawn_flag 必须 NOT NULL DEFAULT 0（否则存量案件会被误判为已撤回）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0097();
DROP PROCEDURE IF EXISTS sp_verify_0097;
