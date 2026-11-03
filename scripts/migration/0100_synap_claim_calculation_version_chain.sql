-- ============================================================
-- 0100_synap_claim_calculation_version_chain.sql
--
-- 一句话：给理算记录 t_adj_calculation 补齐契约 §9.4 的七版本链
--        （plan/catalog/model/prompt/graph 五列），并给理算明细
--        t_adj_calculation_line 补通用乐观锁列 version（§9.2）。
--
-- 背景（契约条款缺口，不是新需求）
-- ----------------------------------------------------------------
--   · 《工程详细设计（落地版）》§9.4 adj_calculation 明确要求「全版本链，
--     回放与复算必需」：rule_version / liability_version / plan_version /
--     catalog_version / model_version / prompt_version / graph_version。
--     模块内 V2 建表只落了 liability_version + rule_version 两列，
--     其余五列从未进入任何生效通道（scripts/migration / bone-init.sql 均无）。
--   · 没有版本链的后果：formula_snapshot 虽物化了责任参数，但「用的是哪一版
--     计划 / 医保目录 / AI 模型 / 提示词 / 决策图」无从库内举证——审计链 SEC-05
--     要求的可重建性缺了环境维度；与 claim_decision_execution（graph_version/
--     model_version/prompt_version/catalog_version 齐全，§9.4）不对称，
--     理算与决策两侧无法对齐到同一次执行。
--   · t_adj_calculation_line 缺 version 列，违反 §9.2「新增表含通用列
--     （…version…）」约定，与同表兄弟（t_adj_calculation、t_domain_event_outbox、
--     0098 修的 t_reconciliation_diff）不一致。
--
-- 为什么只加列、不改实体
-- ----------------------------------------------------------------
--   · 五个版本列目前没有 Java 写入方（Adjustment 聚合不映射它们）。
--     SDK 的 INSERT/UPDATE 列清单由实体元数据生成 ⇒ 表列是实体超集即安全，
--     缺列才炸（0085/0099 实测教训的反面）。写入侧接线（理算受理时锁定
--     plan_version 等）属于行为变更，须与消费方同批设计，本次不混入。
--   · line.version 同理：AdjustmentLine 实体未声明 @Version，加列不改变
--     任何现有读写行为；先补齐 §9.2 通用列基线，实体侧待有并发写场景再启用。
--
-- 默认值口径
-- ----------------------------------------------------------------
--   与契约 §9.4 一致 NOT NULL DEFAULT ''：空串 = 「该维度未参与本次理算」
--   （存量行天然如此），语义与 claim_decision_execution 的 model_version/
--   prompt_version/catalog_version DEFAULT '' 同款；不用 NULL，避免读侧判空。
--
-- 幂等 / 执行
-- ----------------------------------------------------------------
--   · 幂等：information_schema 探测 + PREPARE 动态 DDL（0085/0090/0094/
--     0095/0097/0098 同范式），重复执行影响 0 行，可安全重跑；
--   · 终态断言：六列全部就位且默认值 ''（version 为 0）才放行；
--   · bone-init.sql 同提交补列（G39 只比表级终态，列是盲区，须手动对齐）；
--   · 模块内 db/migration/V2 属参考态，不改（双源收敛见契约 §12.2.1，L3）。
--
-- 执行（生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0100_synap_claim_calculation_version_chain.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- ① t_adj_calculation 五个版本列（位置对齐契约 §9.4：rule/liability 之后）
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_calculation ADD COLUMN plan_version VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''计划版本(与 claim_case.plan_version 一致, §9.4)'' AFTER rule_version',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_calculation' AND COLUMN_NAME = 'plan_version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_calculation ADD COLUMN catalog_version VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''目录版本(医保目录锁定值, §8.2/§9.4)'' AFTER plan_version',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_calculation' AND COLUMN_NAME = 'catalog_version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_calculation ADD COLUMN model_version VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''模型版本(AI评估节点, 回放与复算必需, §9.4)'' AFTER catalog_version',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_calculation' AND COLUMN_NAME = 'model_version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_calculation ADD COLUMN prompt_version VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''提示词版本(AI评估节点, §9.4)'' AFTER model_version',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_calculation' AND COLUMN_NAME = 'prompt_version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_calculation ADD COLUMN graph_version VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''决策图版本(与 claim_decision_execution 一致, §9.4)'' AFTER prompt_version',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_calculation' AND COLUMN_NAME = 'graph_version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ② t_adj_calculation_line.version（§9.2 通用列，NOT NULL DEFAULT 0）
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_adj_calculation_line ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本(SDK @Version 维护; 实体未启用前恒为0)'' AFTER updated_at',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_adj_calculation_line' AND COLUMN_NAME = 'version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ③ 终态断言（六列全部就位才放行；版本链任一缺失 ⇒ 审计链不可重建）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0100;
DELIMITER $$
CREATE PROCEDURE sp_verify_0100()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_adj_calculation'
       AND COLUMN_NAME IN ('plan_version', 'catalog_version', 'model_version', 'prompt_version', 'graph_version');
    IF v_missing <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0100 校验失败：t_adj_calculation 版本链五列（plan/catalog/model/prompt/graph）未全部就位（§9.4 全版本链是回放与复算的硬前提）';
    END IF;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_adj_calculation_line'
       AND COLUMN_NAME = 'version'
       AND IS_NULLABLE = 'NO'
       AND COLUMN_DEFAULT = '0';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0100 校验失败：t_adj_calculation_line.version 未就位或非 NOT NULL DEFAULT 0（§9.2 通用列约定）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0100();
DROP PROCEDURE IF EXISTS sp_verify_0100;
