-- ============================================================
-- 0101_synap_claim_feature_toggle_config_payload.sql
--
-- 一句话：给 t_feature_toggle 补 config_json 承载列——让 §8.3/§11.4 声称的
--       「R-FAST/R-MAN 阈值经 feature_toggle 外置」从「文档里有」变成「库里有」。
--
-- 背景（契约条款 vs 实现的最后一处硬缺口）
-- ----------------------------------------------------------------
-- 契约 §11.4 原文：「R-FAST/R-MAN 阈值一期即从 feature_toggle.config_json 读取
-- （§8.3 配置外部化铁律的最小动作），不落编译期常量」；§8.3 表格里 R-FAST 的
-- 复合阈值（金额上限 / AI 置信度档位）同样指向 feature_toggle.config_json。
--
-- 而 0099 按**实体元数据**建的 t_feature_toggle 只有 4 个业务列
-- （feature_key / state / rollout_pct / remark），没有 config_json。于是：
--   · 文档说阈值可配、改阈值不发版；
--   · 实际阈值是 DecisionTriage / SettlementOrder 里的 public static final 常量，
--     改一个数就得重新构建发布。
-- 这不是「少一列」的整洁问题，而是**契约能力缺位**：运维在风险事件发生时无法
-- 快速收紧自动通过口径，只能发版——恰恰是配置外部化要消除的那件事。
--
-- 为什么只补 config_json，不补 scope_type / scope_value / effective_from / to
-- ----------------------------------------------------------------
-- §9.5 给过一版完整 DDL，含作用域（GLOBAL/TENANT/INSURER）、生效窗口与 append
-- 版本链。本次刻意**不做**这三项，理由是「无消费方即不建模」：
--   1) 作用域：当前唯一的消费结构是 TenantAbstractEntity 自带的 tenant_id，
--      「租户级」已是默认语义。INSURER 级开关没有业务提出，GLOBAL 行在多租户下
--      还得引入 tenant_id=0 哨兵（与 TenantScope 的 AUTO 过滤规则冲突）——
--      加进来就是「配了不生效」的第三个口径。
--   2) 生效窗口：effective_from/to 只有在查询侧按「当前时间取最新」时才生效，
--      而现在没有任何这种查询。加两列却没人读，等价于把「未实现」伪装成「已支持」。
--   3) append 版本链：业界主流（Unleash / LaunchDarkly / OpenFeature）都是
--      「单行当前态 + 变更审计」，需要历史时读审计表；append 链会让「同一 key
--      多行谁生效」成为查询侧必须回答的问题，而 SDK 的 Criteria 表达不了
--      「按 effective_from 取最新且未失效」这类时态条件。当前 @Version 乐观锁 +
--      created_at/updated_at/updated_by 审计列已覆盖并发与追溯诉求。
-- 以上取舍已回写契约 §9.5 与 §12.2，使「文档写的」与「库里有的」一致；
-- 后续若真出现按保司维度的开关诉求，再连作用域键一起设计，不预先埋空壳。
--
-- 语义（消费方见 TriageLimits / DecisionTriage）
-- ----------------------------------------------------------------
-- config_json 为 JSON 对象，键见 FeatureToggle 的 CONFIG_* 常量；缺键 = 用代码
-- 常量（零行为变更）。写入侧由领域方法校验，读取侧非法 JSON 回落常量并 WARN，
-- 即「配置坏了不改变资金口径」，而不是把服务拖挂。
--
-- 幂等
-- -------------------------------------------------------------
-- MySQL 的 ADD COLUMN 无 IF NOT EXISTS，故用 information_schema 判定 + 动态 SQL；
-- 末尾再断言列已存在且可空（TEXT 不允许字面量默认值，未配置即 NULL）。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、加列（幂等）
-- ─────────────────────────────────────────────────────────────
SET @has_config_json := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_feature_toggle'
       AND column_name = 'config_json'
);

SET @ddl_0101 := IF(
    @has_config_json = 0,
    'ALTER TABLE `t_feature_toggle` ADD COLUMN `config_json` TEXT NULL COMMENT ''开关附带参数(JSON：阈值/灰度细分；NULL=沿用代码常量)''',
    'DO 0'
);
PREPARE stmt_0101 FROM @ddl_0101;
EXECUTE stmt_0101;
DEALLOCATE PREPARE stmt_0101;

-- ─────────────────────────────────────────────────────────────
-- 二、终态断言（列必须在、必须可空）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_verify_0101$$
CREATE PROCEDURE sp_verify_0101()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_feature_toggle'
       AND column_name = 'config_json';
    IF v_missing = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：t_feature_toggle.config_json 未创建';
    END IF;

    -- 必须可空：未配置 = NULL = 沿用代码常量。若被建成 NOT NULL 且无默认值，
    -- 任何未带该列的 INSERT 都会 ERROR 1364——比缺列更难查。
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_feature_toggle'
       AND column_name = 'config_json'
       AND is_nullable = 'NO' AND column_default IS NULL;
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：config_json 为 NOT NULL 且无默认值（未配置场景写入即失败）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0101();
DROP PROCEDURE IF EXISTS sp_verify_0101;

-- ─────────────────────────────────────────────────────────────
-- 三、验证探针（手工执行，非脚本自动断言）
-- ─────────────────────────────────────────────────────────────
-- 读路径（列缺失时这里 ERROR 1054）：
--   SELECT feature_key, state, config_json FROM t_feature_toggle
--    WHERE tenant_id = 0 AND feature_key = 'AUTO_APPROVE' AND deleted = 0;
--
-- 写路径（含 JSON 值的往返）：
--   INSERT INTO t_feature_toggle
--     (id, feature_key, state, rollout_pct, remark, config_json,
--      tenant_id, created_at, updated_at, version, deleted)
--   VALUES (999999102,'TRIAGE_LIMITS','ENABLED',100,'PROBE',
--           '{"autoAmountLimit":"8000","aiManualThreshold":"0.75"}',
--           0,NOW(3),NOW(3),0,0);
--   SELECT config_json FROM t_feature_toggle WHERE id = 999999102;
--   DELETE FROM t_feature_toggle WHERE id = 999999102;
