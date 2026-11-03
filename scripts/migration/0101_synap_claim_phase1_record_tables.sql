-- ============================================================
-- 0101_synap_claim_phase1_record_tables.sql
--
-- 一句话：补建 §9.5 一期表清单里「既无实体也无表」的四张记录/字典表
--       —— t_claim_stakeholder（干系人）/ t_claim_rule_hit（规则命中）
--       / t_claim_review_record（复核留痕）/ t_adj_fee_type（费用类型字典）。
--
-- 背景（缺陷实证，非推断）
-- ----------------------------------------------------------------
-- 2026-11-03 的「实体 → DDL」机械比对只拦到「有实体无表」的两张（已由 0099 修）；
-- 但 §9.5 表清单与活库对账还暴露出另一类缺口：**四张表连实体都没有**，
-- 即「无代码无表」——既不是 0099 那种「代码就绪只差落地」，也不是单纯少张表。
-- 它们是一期建表的契约条款（落地版 §9.5 表清单逐行登记「未落地（无实体/无表）」），
-- 缺失会让一期口径的「干系人假名化」「规则命中留痕」「大额双人复核留痕」「费用类型子限额」
-- 四件事在代码侧完全没有承载，只能退化成代码常量或根本不做。
--
-- 为什么这一批一起做（不是各做各的）
-- ----------------------------------------------------------------
-- 四张表同属「一期建表」契约条款，且都与既有聚合共享 SDK 建表约定：
-- 主键恒 id BIGINT；审计列（tenant_id/created_at/updated_at/created_by/updated_by/deleted）
-- 由 TenantAbstractEntity 框架注入——**即使 append-only 表也必须带这些列**，否则 SDK 的
-- INSERT 列清单（UpsertBuilder 取实体元数据）会因缺列报 ERROR 1054（0089 同款教训：
-- 「append-only」由「代码不写 UPDATE + 不授 UPDATE 权限」保证，不是由缺列保证）。
-- 因此 t_claim_rule_hit / t_claim_review_record 虽是 append-only，仍带 updated_at/updated_by/deleted，
-- 但不带 @Version（无 UPDATE 路径，乐观锁无意义）；t_claim_stakeholder / t_adj_fee_type 是可变表，带 @Version。
--
-- 列清单以实体元数据为准（0099 同口径）：SDK 的 INSERT/UPDATE 列由实体元数据生成，
-- 表列必须是实体超集，否则写入即 ERROR 1054；契约 §9.5 对这四张的 DDL 描述与实体列有出入
-- （append-only 两表缺框架审计列、可变两表口径一致），本次按实体列建表（先关掉「无代码无表」硬缺陷），
-- 契约与实体的逐列收敛属另一件事，不在这里顺手改，否则造出第三种口径。
--
-- 幂等
-- -------------------------------------------------------------
-- CREATE TABLE IF NOT EXISTS + information_schema 终态断言；
-- 已存在同名异形表会静默通过建表，但被「逐列 + 索引 + 关键约束」断言拦下。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、建表：t_claim_stakeholder（§9.5 A1 干系人，可变）
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_claim_stakeholder` (
  `id`            BIGINT        NOT NULL COMMENT '干系人ID(分布式ID)',
  `claim_id`      BIGINT        NOT NULL COMMENT '案件ID',
  `role`          VARCHAR(16)   NOT NULL COMMENT 'REPORTER/INSURED/APPLICANT/PAYEE',
  `person_id_hash` CHAR(64)     NOT NULL COMMENT '假名化人员ID(PIPL 29 擦除锚点)',
  `name_masked`   VARCHAR(64)   NOT NULL COMMENT '姓名(遮蔽)',
  `relation`      VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '与被保人关系',
  `phone_masked`  VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '手机号(前后4位)',
  `collect_type`  VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '领款人类型',
  `bank_ref`      VARCHAR(256)  NOT NULL DEFAULT '' COMMENT '领款账户引用(加密)',
  `tenant_id`     BIGINT        NOT NULL COMMENT '租户',
  `created_by`    BIGINT        DEFAULT NULL COMMENT '创建人ID',
  `created_at`    DATETIME(3)   NOT NULL COMMENT '创建时间',
  `updated_by`    BIGINT        DEFAULT NULL COMMENT '修改人ID',
  `updated_at`    DATETIME(3)   NOT NULL COMMENT '更新时间',
  `version`       BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `deleted`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (`id`),
  KEY `idx_claim` (`claim_id`),
  KEY `idx_person` (`tenant_id`, `person_id_hash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '赔案干系人(报案人/被保人/申请人/领款人)';

-- ─────────────────────────────────────────────────────────────
-- 二、建表：t_claim_rule_hit（§9.5 A3 规则命中，append-only）
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_claim_rule_hit` (
  `id`            BIGINT        NOT NULL COMMENT '命中ID(分布式ID)',
  `claim_id`      BIGINT        NOT NULL COMMENT '案件ID',
  `execution_id`  BIGINT        NOT NULL COMMENT '决策执行ID',
  `rule_code`     VARCHAR(64)   NOT NULL COMMENT '规则编码',
  `rule_version`  VARCHAR(32)   NOT NULL COMMENT '规则版本',
  `rule_category` VARCHAR(16)   NOT NULL COMMENT 'R-AREA/R-DIR/R-DED/R-RAT/R-CAP/R-DEC/R-FAST/R-MAN/R-FRAUD',
  `priority`      INT           NOT NULL DEFAULT 5 COMMENT '裁决优先级 P0-P5',
  `input_snapshot` JSON         DEFAULT NULL COMMENT '命中时输入快照',
  `conclusion`    VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '规则结论',
  `hit_at`        DATETIME(3)   NOT NULL COMMENT '命中时间',
  `tenant_id`     BIGINT        NOT NULL COMMENT '租户(安全隔离+按租户对账)',
  `created_by`    BIGINT        DEFAULT NULL COMMENT '创建人ID',
  `created_at`    DATETIME(3)   NOT NULL COMMENT '创建时间',
  `updated_by`    BIGINT        DEFAULT NULL COMMENT '修改人ID',
  `updated_at`    DATETIME(3)   NOT NULL COMMENT '更新时间(随创建写一次，业务不再改)',
  `deleted`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删标记(业务不删，列由框架注入)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_claim` (`tenant_id`, `claim_id`),
  KEY `idx_rule` (`rule_code`, `rule_version`),
  KEY `idx_execution` (`execution_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '规则命中记录(append-only)';

-- ─────────────────────────────────────────────────────────────
-- 三、建表：t_claim_review_record（§9.5 A1 复核留痕，append-only）
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_claim_review_record` (
  `id`            BIGINT        NOT NULL COMMENT '复核记录ID(分布式ID)',
  `claim_id`      BIGINT        NOT NULL COMMENT '案件ID',
  `settlement_no` VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '结算单号(资金侧复核时填)',
  `role`          VARCHAR(24)   NOT NULL COMMENT 'REVIEW 核赔 / SECOND_REVIEW 双人复核 / APPROVE 调账审批',
  `reviewer_id`   VARCHAR(64)   NOT NULL COMMENT '复核人',
  `operator_id`   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '发起人(校验 reviewer_id != operator_id, §11.10)',
  `amount`        DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '本次复核金额',
  `decision`      VARCHAR(24)   NOT NULL COMMENT 'APPROVED/REJECTED/REQUEST_MORE_EVIDENCE',
  `comment`       VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '复核意见(拒赔五要素)',
  `created_at`    DATETIME(3)   NOT NULL COMMENT '复核时间',
  `tenant_id`     BIGINT        NOT NULL COMMENT '租户(安全隔离+按租户对账)',
  `created_by`    BIGINT        DEFAULT NULL COMMENT '创建人ID',
  `updated_by`    BIGINT        DEFAULT NULL COMMENT '修改人ID',
  `updated_at`    DATETIME(3)   NOT NULL COMMENT '更新时间(随创建写一次，业务不再改)',
  `deleted`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删标记(业务不删，列由框架注入)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_claim_role_reviewer` (`claim_id`, `role`, `reviewer_id`),
  KEY `idx_claim` (`claim_id`),
  KEY `idx_reviewer` (`tenant_id`, `reviewer_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '复核/审核留痕(append-only)';

-- ─────────────────────────────────────────────────────────────
-- 四、建表：t_adj_fee_type（§9.5 A4 费用类型字典，可变）
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_adj_fee_type` (
  `id`              BIGINT        NOT NULL COMMENT '费用类型ID(分布式ID)',
  `fee_type_code`   VARCHAR(32)   NOT NULL COMMENT '费用类型编码',
  `fee_type_name`   VARCHAR(64)   NOT NULL COMMENT '费用类型名称',
  `source`          VARCHAR(16)   NOT NULL COMMENT 'INPUT 录入项 / CALCULATE 计算项',
  `input_code`      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT 'source=INPUT 时的录入编码',
  `calculate_formula` VARCHAR(256) NOT NULL DEFAULT '' COMMENT 'source=CALCULATE 时的计算式',
  `parent_code`     VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '父费用类型编码',
  `is_default`      TINYINT       NOT NULL DEFAULT 0 COMMENT '是否默认适用全部责任',
  `sort_no`         INT           NOT NULL DEFAULT 0 COMMENT '排序',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RETIRED',
  `tenant_id`       BIGINT        NOT NULL COMMENT '租户',
  `created_by`      BIGINT        DEFAULT NULL COMMENT '创建人ID',
  `created_at`      DATETIME(3)   NOT NULL COMMENT '创建时间',
  `updated_by`      BIGINT        DEFAULT NULL COMMENT '修改人ID',
  `updated_at`      DATETIME(3)   NOT NULL COMMENT '更新时间',
  `version`         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `deleted`         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_type_code` (`tenant_id`, `fee_type_code`),
  KEY `idx_parent` (`tenant_id`, `parent_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '费用类型字典(录入项/计算项)';

-- ─────────────────────────────────────────────────────────────
-- 五、断言（跑过了 ≠ 做到了：逐表 / 逐列 / 逐索引探测）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0101$$
CREATE PROCEDURE sp_verify_0101()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    -- ① 四张表都必须存在
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('t_claim_stakeholder', 't_claim_rule_hit',
                          't_claim_review_record', 't_adj_fee_type');
    IF v_missing < 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：四张一期记录/字典表未全部建成';
    END IF;

    -- ② t_claim_stakeholder 列必须齐全（实体声明 16 列）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'id' AS c UNION ALL SELECT 'claim_id' UNION ALL SELECT 'role'
        UNION ALL SELECT 'person_id_hash' UNION ALL SELECT 'name_masked'
        UNION ALL SELECT 'relation' UNION ALL SELECT 'phone_masked'
        UNION ALL SELECT 'collect_type' UNION ALL SELECT 'bank_ref'
        UNION ALL SELECT 'tenant_id' UNION ALL SELECT 'created_by'
        UNION ALL SELECT 'created_at' UNION ALL SELECT 'updated_by'
        UNION ALL SELECT 'updated_at' UNION ALL SELECT 'version' UNION ALL SELECT 'deleted'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 't_claim_stakeholder'
           AND col.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：t_claim_stakeholder 缺列（写路径会 Unknown column）';
    END IF;

    -- ③ t_claim_rule_hit 列必须齐全（实体声明 16 列）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'id' AS c UNION ALL SELECT 'claim_id' UNION ALL SELECT 'execution_id'
        UNION ALL SELECT 'rule_code' UNION ALL SELECT 'rule_version'
        UNION ALL SELECT 'rule_category' UNION ALL SELECT 'priority'
        UNION ALL SELECT 'input_snapshot' UNION ALL SELECT 'conclusion'
        UNION ALL SELECT 'hit_at' UNION ALL SELECT 'tenant_id'
        UNION ALL SELECT 'created_by' UNION ALL SELECT 'created_at'
        UNION ALL SELECT 'updated_by' UNION ALL SELECT 'updated_at' UNION ALL SELECT 'deleted'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 't_claim_rule_hit'
           AND col.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：t_claim_rule_hit 缺列（写路径会 Unknown column）';
    END IF;

    -- ④ t_claim_review_record 列必须齐全（实体声明 15 列）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'id' AS c UNION ALL SELECT 'claim_id' UNION ALL SELECT 'settlement_no'
        UNION ALL SELECT 'role' UNION ALL SELECT 'reviewer_id' UNION ALL SELECT 'operator_id'
        UNION ALL SELECT 'amount' UNION ALL SELECT 'decision' UNION ALL SELECT 'comment'
        UNION ALL SELECT 'created_at' UNION ALL SELECT 'tenant_id'
        UNION ALL SELECT 'created_by' UNION ALL SELECT 'updated_by'
        UNION ALL SELECT 'updated_at' UNION ALL SELECT 'deleted'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 't_claim_review_record'
           AND col.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：t_claim_review_record 缺列（写路径会 Unknown column）';
    END IF;

    -- ⑤ t_adj_fee_type 列必须齐全（实体声明 17 列）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'id' AS c UNION ALL SELECT 'fee_type_code' UNION ALL SELECT 'fee_type_name'
        UNION ALL SELECT 'source' UNION ALL SELECT 'input_code' UNION ALL SELECT 'calculate_formula'
        UNION ALL SELECT 'parent_code' UNION ALL SELECT 'is_default' UNION ALL SELECT 'sort_no'
        UNION ALL SELECT 'status' UNION ALL SELECT 'tenant_id' UNION ALL SELECT 'created_by'
        UNION ALL SELECT 'created_at' UNION ALL SELECT 'updated_by'
        UNION ALL SELECT 'updated_at' UNION ALL SELECT 'version' UNION ALL SELECT 'deleted'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 't_adj_fee_type'
           AND col.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：t_adj_fee_type 缺列（写路径会 Unknown column）';
    END IF;

    -- ⑥ 唯一键必须存在（缺它 ⇒ 同租户同编码可重复插入 / 同案件同角色同复核人可重复留痕）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'uk_fee_type_code' AS k, 't_adj_fee_type' AS t
        UNION ALL SELECT 'uk_claim_role_reviewer', 't_claim_review_record'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.statistics s
         WHERE s.table_schema = DATABASE()
           AND s.table_name = expected.t
           AND s.index_name = expected.k
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：唯一键缺失（同键可重复插入）';
    END IF;

    -- ⑦ 可变两表的 version 必须 NOT NULL DEFAULT 0（@Version 为 null 时 SDK 直接拒绝更新）
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name IN ('t_claim_stakeholder', 't_adj_fee_type')
       AND column_name = 'version'
       AND (is_nullable = 'YES' OR column_default IS NULL);
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0101 校验失败：version 可空或无默认值（乐观锁首次写入即失败）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0101();
DROP PROCEDURE IF EXISTS sp_verify_0101;

-- ─────────────────────────────────────────────────────────────
-- 六、验证（手工执行，非脚本自动断言）
-- ─────────────────────────────────────────────────────────────
-- 写路径探针（列缺失时这里会 ERROR 1054）：
--   INSERT INTO t_claim_rule_hit
--     (id, claim_id, execution_id, rule_code, rule_version, rule_category,
--      priority, conclusion, hit_at, tenant_id, created_at, updated_at, deleted)
--   VALUES (999999301, 999999001, 999999002, 'R-FAST', 'v1', 'R-FAST', 5,
--      'HIT', NOW(3), 0, NOW(3), NOW(3), 0);
--   DELETE FROM t_claim_rule_hit WHERE id = 999999301;
