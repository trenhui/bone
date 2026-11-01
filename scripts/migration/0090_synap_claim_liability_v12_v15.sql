-- ============================================================
-- 0090_synap_claim_liability_v12_v15.sql
--
-- 一句话：把 synap-claim 的 Flyway 孤儿脚本 V12 / V13 / V14 / V15 落到**本仓唯一生效的迁移通道**。
--
-- 背景（与 0079 同源的第二批，不是新问题，是同一盲区的再次命中）
-- ----------------------------------------------------------------
-- 0079 已说明：synap-claim 自带 V1..V15 是一条**从未被执行的通道**（无 flyway-core 依赖、
-- 无 spring.flyway.* 配置、manifest 无条目），只保留了语义原文，不产生结构。
-- 0079 落地了 V10/V11 的 6 列；本脚本落地 V12/V13/V14/V15 的 10 列：
--
--   V12  t_adj_liability  + invoice_deduct / adjust_deduct
--   V13  t_adj_liability  + daily_limit / daily_limit_basis
--        t_quota_transaction + biz_date
--   V14  t_adj_liability  + limit_mode / limit_hospital_codes / limit_diagnosis_codes
--   V15  t_adj_liability  + catalog_policy / catalog_code
--
-- 后果（不修会怎样，均为运行期实证）
-- ----------------------------------------------------------------
--   · t_quota_transaction.biz_date 缺失 ⇒ 建结算单 500：
--       SQLSyntaxErrorException: Unknown column 'm.biz_date' in 'field list'
--     即 A7 资金链（软占→审核→复核→支付）整体不可用，是 2026-10-31 联调实测得到的 500。
--   · t_adj_liability 九列缺失 ⇒ 日限额（Q-05）、发票级/理算级免赔（GAP②⑥）、
--     适用限定（V1 §2 要素 6）、目录准入（缺口 ①）四项能力在**任何环境**都读不到配置：
--     实体有 @Column、库里没有列，SELECT 直接 Unknown column；即便侥幸跳过，
--     "配了不生效"比"没配"更危险——运营以为限额开着，系统按不启用跑（超赔）。
--
-- 为什么全部 NOT NULL DEFAULT（V12/V13/V14/V15 原稿口径，不得改为可空）
-- ----------------------------------------------------------------
--   责任配置是版本化实体，已生效版本不可改。新增列必须让历史行与新行都有确定值
--   （0 / 空串 / 'NONE' = 不启用），否则 SELECT 读到 NULL、装载期校验判不出"未配"，
--   与 liability_deduct / liability_limit 同口径。历史行取默认值即"沿用不启用"，
--   不回填、不猜测——猜测一个日限额或目录版本等于替运营做了决定。
--
-- 幂等
-- ----------------------------------------------------------------
--   逐列 information_schema 探测 + PREPARE 动态 DDL（与 0066/0079 同范式），可重复执行。
--   末尾断言 10 列齐全，缺任何一列即 SIGNAL 失败——**不允许"脚本跑过了"被误当"列建好了"**。
--
-- 与 bone-init.sql 的关系（同提交必须一起改）
-- ----------------------------------------------------------------
--   G39 只比表级终态，不看列。本批 10 列在 0079 之前、之后都未在 init 中出现，
--   ⇒「只灌 bone-init.sql 的新环境」灌完必炸。故本脚本与 init 的两张表定义同提交补齐。
-- ============================================================

-- ---------- ① V12 t_adj_liability.invoice_deduct ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN invoice_deduct DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''发票级免赔(GAP②)：比例前、年度免赔前，按费用池基数占比分摊扣减；0=不启用''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'invoice_deduct'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ② V12 t_adj_liability.adjust_deduct ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN adjust_deduct DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''理算级免赔(GAP⑥)：比例后、控额前，对理算金额二次绝对额扣减；0=不启用''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'adjust_deduct'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ③ V13 t_adj_liability.daily_limit ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN daily_limit DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''日赔付限额(Q-05)：同一业务日累计上限；0=不启用''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'daily_limit'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ④ V13 t_adj_liability.daily_limit_basis ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN daily_limit_basis VARCHAR(24) NOT NULL DEFAULT ''NONE'' COMMENT ''日窗口口径(Q-05)：NONE/SAME_DAY_INVOICE/SAME_DAY_CLAIM''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'daily_limit_basis'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑤ V13 t_quota_transaction.biz_date ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_quota_transaction ADD COLUMN biz_date DATE DEFAULT NULL COMMENT ''业务日(发票日期/赔案受理日)：日限额按此累计；NULL=不参与日累计''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_quota_transaction' AND COLUMN_NAME = 'biz_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑥ V14 t_adj_liability.limit_mode ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN limit_mode VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''适用限定方式(V1 §2 要素6)：NONE=不启用 / WHITELIST=只赔名单内 / BLACKLIST=名单内不赔''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'limit_mode'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑦ V14 t_adj_liability.limit_hospital_codes ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN limit_hospital_codes VARCHAR(1024) NOT NULL DEFAULT '''' COMMENT ''适用限定-医院编码名单(逗号/顿号/分号/空白分隔，空=该维度不限定)：按编码比对，不按名称''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'limit_hospital_codes'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑧ V14 t_adj_liability.limit_diagnosis_codes ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN limit_diagnosis_codes VARCHAR(512) NOT NULL DEFAULT '''' COMMENT ''适用限定-病种编码名单(同上分隔规则，空=该维度不限定)''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'limit_diagnosis_codes'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑨ V15 t_adj_liability.catalog_policy ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN catalog_policy VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''目录准入策略(缺口①/适用限定药品·诊疗)：NONE=不查目录(与历史一致) / MANDATORY=每条明细必须查到结论，查不到即退补件''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'catalog_policy'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑩ V15 t_adj_liability.catalog_code ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_liability ADD COLUMN catalog_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''目录编码(如 NATIONAL_2024)：按哪一版目录判定；策略为 NONE 时必须为空（配了不会被查询，装载期拒绝）''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_liability' AND COLUMN_NAME = 'catalog_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 终态断言：10 列必须齐全 ----------
DROP PROCEDURE IF EXISTS `tmp_assert_0085`;
DELIMITER $$
CREATE PROCEDURE `tmp_assert_0085`()
BEGIN
    DECLARE v_missing INT;

    SELECT COUNT(*) INTO v_missing
      FROM (
        SELECT 't_adj_liability' AS t, 'invoice_deduct' AS c          UNION ALL
        SELECT 't_adj_liability', 'adjust_deduct'                     UNION ALL
        SELECT 't_adj_liability', 'daily_limit'                       UNION ALL
        SELECT 't_adj_liability', 'daily_limit_basis'                 UNION ALL
        SELECT 't_quota_transaction', 'biz_date'                      UNION ALL
        SELECT 't_adj_liability', 'limit_mode'                        UNION ALL
        SELECT 't_adj_liability', 'limit_hospital_codes'              UNION ALL
        SELECT 't_adj_liability', 'limit_diagnosis_codes'             UNION ALL
        SELECT 't_adj_liability', 'catalog_policy'                    UNION ALL
        SELECT 't_adj_liability', 'catalog_code'
      ) AS expected
     WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = expected.t
           AND col.column_name = expected.c
     );

    IF v_missing <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0085 校验失败：synap-claim V12/V13/V14/V15 列未补齐（A7 建结算单将 500）';
    END IF;
END$$
DELIMITER ;
CALL `tmp_assert_0085`();
DROP PROCEDURE IF EXISTS `tmp_assert_0085`;
