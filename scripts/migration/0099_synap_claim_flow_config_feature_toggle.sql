-- ============================================================
-- 0099_synap_claim_flow_config_feature_toggle.sql
--
-- 一句话：给两个「实体 + 仓储已提交、物理表却从未建」的配置聚合补建表
--       —— t_claim_flow_config（流程/钩子配置）与 t_feature_toggle（租户级特性开关）。
--
-- 背景（缺陷实证，非推断）
-- ----------------------------------------------------------------
-- 2026-11-03 对 31 个 @Table 实体做「实体 → DDL」机械比对，结论：
--   29 个实体在 bone-init.sql 有同名 CREATE TABLE，唯独这两个全仓零 DDL
--   （bone-init.sql / scripts/migration / db/migration 全无命中，活库亦无此表）。
-- 而两个聚合的 Java 侧一应俱全：实体（带 @Version）、仓储接口（带 TenantScope）、
-- 建表意图写在类注释里——ClaimFlowConfig 原文写的是「本批补齐表 + 实体 + 仓储，
-- 闭合方案 §9.5 表清单」。也就是说：**表是计划里的一环，只是没落地。**
--
-- 为什么这不只是「少一张表」（三个后果，按严重度）
-- ----------------------------------------------------------------
-- 1) 首个消费方接入即 1146 Table doesn't exist。仓储接口是 SDK Criteria 形态，
--    编译期、单测期都不会碰真表（测试用内存替身），所以这个洞**不会在 CI 里红**，
--    只会在功能上线那一刻炸——与「建得进读不出」同类，属于延迟暴露型缺陷。
-- 2) 实体声明了 @Version，表却缺 version 列 ⇒ 即便有人手工建表补上业务列，
--    SDK 的 UPDATE 也会因乐观锁列缺失而失败（0098 已踩过一次同款）。
-- 3) §9.5「一期建表」是契约条款，不是可选项：flow 配置是「新保司接入 ≤7 天」
--    的承载，feature_toggle 是 R-FAST/R-MAN 阈值外置的落点。没有表，两条都只能是
--    代码里的常量——「配了不生效」的另一种形态。
--
-- 设计取舍：列清单以**实体元数据**为准，不以契约 DDL 为准
-- ----------------------------------------------------------------
-- SDK 的 INSERT/UPDATE 列清单由实体元数据生成（UpsertBuilder.buildMySql →
-- ctx.getTable().getColumns()），不由 DB 实际列生成。因此表的列必须是实体的超集，
-- 否则写入即 ERROR 1054；反过来多出实体没声明的列不会影响写入。
-- 契约 §9.5 给出过一版更完整的 feature_toggle（toggle_key/scope_type/scope_value/
-- config_json/effective_from/to），与当前实体（feature_key/state/rollout_pct/remark）
-- 存在**语义差异**（作用域维度与生效窗口尚未建模）。本次按实体列建表——先把「有代码
-- 没表」这个硬缺陷关掉；契约与实体的收敛属另一件事（涉及作用域键与版本链，需与
-- 该特性的作者同批做），不在这里顺手改，否则会造出「表按契约、代码按实体」的第三种口径。
-- 该差异已登记到工程落地版 §12.2 实现状态表，避免读者误以为 §9.5 那版已落地。
--
-- 幂等
-- -------------------------------------------------------------
-- CREATE TABLE IF NOT EXISTS + information_schema 终态断言。
-- 已存在同名异形表会静默通过建表，但被「逐列 + 唯一键」断言拦下（缺即 SIGNAL 失败）。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、建表：t_claim_flow_config（§6.7 流程配置化）
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_claim_flow_config` (
  `id`             BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  `flow_key`       VARCHAR(64)   NOT NULL COMMENT '流程键(如 ADJUSTMENT_ORDER；生命周期引擎按此读取)',
  `config_version` INT           NOT NULL COMMENT '配置发版号(禁止热改线上运行配置)',
  `config_json`    TEXT          DEFAULT NULL COMMENT '流程/钩子配置 JSON(节点编排/跳转/条件)',
  `active_flag`    INT           NOT NULL DEFAULT 1 COMMENT '生效标记(1=当前生效版本, 0=历史版本)',
  `tenant_id`      BIGINT        NOT NULL COMMENT '租户',
  `created_by`     BIGINT        DEFAULT NULL COMMENT '创建人ID',
  `created_at`     DATETIME(3)   NOT NULL COMMENT '创建时间',
  `updated_by`     BIGINT        DEFAULT NULL COMMENT '修改人ID',
  `updated_at`     DATETIME(3)   NOT NULL COMMENT '更新时间',
  `version`        BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `deleted`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_flow_version` (`tenant_id`, `flow_key`, `config_version`),
  KEY `idx_flow` (`tenant_id`, `flow_key`, `active_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户级流程/钩子配置(版本化, 改配置不发版)';

-- ─────────────────────────────────────────────────────────────
-- 二、建表：t_feature_toggle（§11.4 三态开关 + 灰度比例）
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_feature_toggle` (
  `id`          BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  `feature_key` VARCHAR(64)  NOT NULL COMMENT '特性键(如 AUTO_APPROVE)',
  `state`       VARCHAR(16)  NOT NULL COMMENT 'ENABLED/DISABLED(三态: 无记录=继承全局开关)',
  `rollout_pct` INT          NOT NULL DEFAULT 0 COMMENT '灰度比例 0-100(按租户哈希命中)',
  `remark`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注/变更原因',
  `tenant_id`   BIGINT       NOT NULL COMMENT '租户',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `created_at`  DATETIME(3)  NOT NULL COMMENT '创建时间',
  `updated_by`  BIGINT       DEFAULT NULL COMMENT '修改人ID',
  `updated_at`  DATETIME(3)  NOT NULL COMMENT '更新时间',
  `version`     BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_feature` (`tenant_id`, `feature_key`),
  KEY `idx_key` (`feature_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户级特性开关与灰度比例(三态)';

-- ─────────────────────────────────────────────────────────────
-- 三、断言（跑过了 ≠ 做到了：逐表 / 逐列 / 逐索引探测）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0099$$
CREATE PROCEDURE sp_verify_0099()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    -- ① 两张表都必须存在
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('t_claim_flow_config', 't_feature_toggle');
    IF v_missing < 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0099 校验失败：t_claim_flow_config / t_feature_toggle 未全部建成';
    END IF;

    -- ② t_claim_flow_config 列必须齐全（实体声明 12 列；少一列 ⇒ 写入 ERROR 1054）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'id' AS c UNION ALL SELECT 'flow_key' UNION ALL SELECT 'config_version'
        UNION ALL SELECT 'config_json' UNION ALL SELECT 'active_flag' UNION ALL SELECT 'tenant_id'
        UNION ALL SELECT 'created_by' UNION ALL SELECT 'created_at' UNION ALL SELECT 'updated_by'
        UNION ALL SELECT 'updated_at' UNION ALL SELECT 'version' UNION ALL SELECT 'deleted'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 't_claim_flow_config'
           AND col.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0099 校验失败：t_claim_flow_config 缺列（写路径会 Unknown column）';
    END IF;

    -- ③ t_feature_toggle 列必须齐全（实体声明 12 列）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'id' AS c UNION ALL SELECT 'feature_key' UNION ALL SELECT 'state'
        UNION ALL SELECT 'rollout_pct' UNION ALL SELECT 'remark' UNION ALL SELECT 'tenant_id'
        UNION ALL SELECT 'created_by' UNION ALL SELECT 'created_at' UNION ALL SELECT 'updated_by'
        UNION ALL SELECT 'updated_at' UNION ALL SELECT 'version' UNION ALL SELECT 'deleted'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 't_feature_toggle'
           AND col.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0099 校验失败：t_feature_toggle 缺列（写路径会 Unknown column）';
    END IF;

    -- ④ 唯一键必须存在（缺它 ⇒ 同租户同键可插入多行，"三态"退化成"多态"）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'uk_tenant_flow_version' AS k, 't_claim_flow_config' AS t
        UNION ALL SELECT 'uk_tenant_feature', 't_feature_toggle'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.statistics s
         WHERE s.table_schema = DATABASE()
           AND s.table_name = expected.t
           AND s.index_name = expected.k
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0099 校验失败：唯一键缺失（同租户同键将可重复插入）';
    END IF;

    -- ⑤ version 列必须 NOT NULL DEFAULT 0（@Version 为 null 时 SDK 直接拒绝更新）
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name IN ('t_claim_flow_config', 't_feature_toggle')
       AND column_name = 'version'
       AND (is_nullable = 'YES' OR column_default IS NULL);
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0099 校验失败：version 可空或无默认值（乐观锁首次写入即失败）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0099();
DROP PROCEDURE IF EXISTS sp_verify_0099;

-- ─────────────────────────────────────────────────────────────
-- 四、验证（手工执行，非脚本自动断言）
-- ─────────────────────────────────────────────────────────────
-- 写路径探针（列缺失时这里会 ERROR 1054）：
--   INSERT INTO t_feature_toggle
--     (id, feature_key, state, rollout_pct, remark, tenant_id, created_at, updated_at, version, deleted)
--   VALUES (999999101,'AUTO_APPROVE','DISABLED',0,'PROBE',0,NOW(3),NOW(3),0,0);
--   DELETE FROM t_feature_toggle WHERE id = 999999101;
--
-- 读路径探针（按租户 + 特性键定位唯一开关）：
--   SELECT state, rollout_pct FROM t_feature_toggle
--    WHERE tenant_id = 0 AND feature_key = 'AUTO_APPROVE' AND deleted = 0;
