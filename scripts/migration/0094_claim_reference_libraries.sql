-- ============================================================
-- 0094_claim_reference_libraries.sql
--
-- 一句话：补齐理赔配置的基础目录库（医院/疾病/药品/费用类别四张租户级主数据表）
--        + 责任级费用类别规则表 + 保单作业配置（理赔申请书/理赔通知单生成开关），
--        并为「基础目录库」播种侧栏菜单（/claim#/library，order 15）。
--
-- 背景（为什么补，对标参考站 policyRuleConfig 的差距项）
-- ----------------------------------------------------------------
-- 此前 synap-claim 的责任配置里 适用限定-医院/病种、目录编码 全是裸文本框：
-- 用户手工输入逗号分隔编码串，没有可用目录可查 ⇒ 编码写错理算期静默失配；
-- 责任只能配"一刀切"的赔付比例/限额，无法按费用类别（床位费/手术费…）差异化；
-- 保单级没有"理赔申请书/理赔通知单要不要生成"的作业开关，作业链路只能全量出单。
-- 参考站（pk-tpa-saas policyRuleConfig）三项能力齐备，本脚本补齐数据底座：
--   ① 四张基础目录库表（医院/疾病/药品/费用类别，租户级主数据）；
--   ② t_adj_liability_fee_rule：责任 × 费用类别 的差异化规则（比例/限额/社保内）；
--   ③ t_claim_policy 补 doc_claim_apply / doc_claim_notice 两个作业开关（NEED/NOT_NEED）；
--   ④ 「基础目录库」菜单（/claim#/library），routes.tsx 同步注册。
--
-- 口径
-- ----------------------------------------------------------------
--   · 目录表是租户级主数据（随租户维护），责任表单仍存编码串（limit_hospital_codes 等
--     分隔串不变）⇒ 理算引擎零改动，只换录入方式；费用类别规则挂在 liability_uuid
--     （配置态，非版本态），随责任发布快照进 formula_snapshot 由理算消费。
--   · 种子用显式小 ID（94xxxxxx），避开分布式 ID 区间；费用类别标准 10 类对租户
--     0/1001 各播一份（模板租户 + 默认租户），医院/疾病/药品各 4 条示例便于选型验收。
--   · 菜单口径与 0092 一致：叶子 id = 目录 id + 24（23 已被责任配置占用）、
--     order_no = 15（13 之后顺延，12/14 已随 0093 撤下）、permission = claim:policy:read、
--     icon = DatabaseOutlined（bone-shell MENU_ICON_MAP 白名单内）。
--   · 幂等：建表 IF NOT EXISTS + 列断言；ALTER 走 information_schema 探测 + PREPARE
--     动态 DDL（0085/0090 同范式）；种子 NOT EXISTS 防重；菜单 NOT EXISTS 防重。
--   · bone-init.sql 同提交补齐表/列/种子（G39 门禁只比表级终态，列是盲区）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0094_claim_reference_libraries.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- ① 基础目录库四张主数据表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS t_ref_hospital (
  id              BIGINT       NOT NULL COMMENT '主键',
  hospital_code   VARCHAR(32)  NOT NULL COMMENT '医院编码(责任适用限定名单存此编码)',
  hospital_name   VARCHAR(128) NOT NULL COMMENT '医院名称',
  hospital_level  VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '医院等级(三甲/三乙/二甲等)',
  social_flag     TINYINT      NOT NULL DEFAULT 1 COMMENT '医保定点(1是/0否)',
  province        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '省',
  city            VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '市',
  tenant_id       BIGINT       NOT NULL COMMENT '租户',
  created_by      BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by      BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)  NOT NULL COMMENT '修改时间',
  version         BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_hospital_code (tenant_id, hospital_code),
  KEY idx_tenant_name (tenant_id, hospital_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基础目录-医院';

CREATE TABLE IF NOT EXISTS t_ref_disease (
  id              BIGINT       NOT NULL COMMENT '主键',
  disease_code    VARCHAR(32)  NOT NULL COMMENT '疾病编码(ICD-10, 责任适用限定名单存此编码)',
  disease_name    VARCHAR(128) NOT NULL COMMENT '疾病名称',
  icd_category    VARCHAR(16)  NOT NULL DEFAULT '' COMMENT 'ICD类目(如 循环系统I)',
  tenant_id       BIGINT       NOT NULL COMMENT '租户',
  created_by      BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by      BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)  NOT NULL COMMENT '修改时间',
  version         BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_disease_code (tenant_id, disease_code),
  KEY idx_tenant_name (tenant_id, disease_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基础目录-疾病';

CREATE TABLE IF NOT EXISTS t_ref_drug (
  id              BIGINT       NOT NULL COMMENT '主键',
  drug_code       VARCHAR(32)  NOT NULL COMMENT '药品编码',
  drug_name       VARCHAR(128) NOT NULL COMMENT '药品名称',
  social_flag     TINYINT      NOT NULL DEFAULT 1 COMMENT '社保内(1是/0否)',
  medical_type    VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '医保分类(甲类/乙类/丙类)',
  tenant_id       BIGINT       NOT NULL COMMENT '租户',
  created_by      BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by      BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)  NOT NULL COMMENT '修改时间',
  version         BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_drug_code (tenant_id, drug_code),
  KEY idx_tenant_name (tenant_id, drug_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基础目录-药品';

CREATE TABLE IF NOT EXISTS t_ref_fee_category (
  id              BIGINT      NOT NULL COMMENT '主键',
  fee_code        VARCHAR(32) NOT NULL COMMENT '费用类别编码(费用明细归集口径)',
  fee_name        VARCHAR(64) NOT NULL COMMENT '费用类别名称',
  sort_no         INT         NOT NULL DEFAULT 0 COMMENT '排序号',
  tenant_id       BIGINT      NOT NULL COMMENT '租户',
  created_by      BIGINT      DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_by      BIGINT      DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3) NOT NULL COMMENT '修改时间',
  version         BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT     NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_fee_code (tenant_id, fee_code),
  KEY idx_tenant_sort (tenant_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基础目录-费用类别';

-- 责任 × 费用类别 差异化规则（配置态，挂 liability_uuid；发布时随公式快照进理算）
CREATE TABLE IF NOT EXISTS t_adj_liability_fee_rule (
  id              BIGINT        NOT NULL COMMENT '主键',
  liability_uuid  VARCHAR(64)   NOT NULL COMMENT '责任UUID(配置态, 各版本共享)',
  fee_code        VARCHAR(32)   NOT NULL COMMENT '费用类别编码(须存在于 t_ref_fee_category)',
  fee_name        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '费用类别名称(冗余, 免联表展示)',
  pay_percent     DECIMAL(6,2)  NOT NULL DEFAULT 100 COMMENT '赔付比例(百分数, 0-100)',
  limit_amount    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '该类别单次限额(0=不限)',
  social_only     TINYINT       NOT NULL DEFAULT 1 COMMENT '仅赔社保内(1是/0否)',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by      BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by      BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '修改时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_uuid_fee (tenant_id, liability_uuid, fee_code),
  KEY idx_tenant_uuid (tenant_id, liability_uuid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '责任费用类别规则(按类别差异化比例/限额)';

-- ─────────────────────────────────────────────────────────────
-- ② t_claim_policy 作业配置两列（幂等 ALTER，0085/0090 范式）
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_policy ADD COLUMN doc_claim_apply VARCHAR(16) NOT NULL DEFAULT ''NEED'' COMMENT ''理赔申请书作业开关: NEED需生成/NOT_NEED无需生成'' AFTER config_status',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_policy' AND COLUMN_NAME = 'doc_claim_apply'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_policy ADD COLUMN doc_claim_notice VARCHAR(16) NOT NULL DEFAULT ''NEED'' COMMENT ''理赔通知单作业开关: NEED需生成/NOT_NEED无需生成'' AFTER doc_claim_apply',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_policy' AND COLUMN_NAME = 'doc_claim_notice'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ③ 种子数据（显式小 ID 94xxxxxx，避开分布式 ID 区间；NOT EXISTS 幂等）
-- ─────────────────────────────────────────────────────────────
-- 费用类别标准 10 类（模板租户 0 + 默认租户 1001）
INSERT INTO t_ref_fee_category (id, tenant_id, fee_code, fee_name, sort_no, created_by, created_at, updated_by, updated_at)
SELECT 94000000 + d.tenant_id * 10 + d.sort_no, d.tenant_id, d.fee_code, d.fee_name, d.sort_no, NULL, NOW(3), NULL, NOW(3)
  FROM (
        SELECT 0 AS tenant_id, 'BED_FEE' AS fee_code, '床位费' AS fee_name, 1 AS sort_no UNION ALL
        SELECT 0, 'EXAM_FEE', '检查费', 2 UNION ALL
        SELECT 0, 'TREAT_FEE', '治疗费', 3 UNION ALL
        SELECT 0, 'SURGERY_FEE', '手术费', 4 UNION ALL
        SELECT 0, 'DRUG_FEE', '药品费', 5 UNION ALL
        SELECT 0, 'MATERIAL_FEE', '材料费', 6 UNION ALL
        SELECT 0, 'NURSING_FEE', '护理费', 7 UNION ALL
        SELECT 0, 'CONSULT_FEE', '诊查费', 8 UNION ALL
        SELECT 0, 'LAB_FEE', '化验费', 9 UNION ALL
        SELECT 0, 'OTHER', '其他', 10 UNION ALL
        SELECT 1001, 'BED_FEE', '床位费', 1 UNION ALL
        SELECT 1001, 'EXAM_FEE', '检查费', 2 UNION ALL
        SELECT 1001, 'TREAT_FEE', '治疗费', 3 UNION ALL
        SELECT 1001, 'SURGERY_FEE', '手术费', 4 UNION ALL
        SELECT 1001, 'DRUG_FEE', '药品费', 5 UNION ALL
        SELECT 1001, 'MATERIAL_FEE', '材料费', 6 UNION ALL
        SELECT 1001, 'NURSING_FEE', '护理费', 7 UNION ALL
        SELECT 1001, 'CONSULT_FEE', '诊查费', 8 UNION ALL
        SELECT 1001, 'LAB_FEE', '化验费', 9 UNION ALL
        SELECT 1001, 'OTHER', '其他', 10
       ) d
 WHERE NOT EXISTS (
    SELECT 1 FROM t_ref_fee_category t
     WHERE t.tenant_id = d.tenant_id AND t.fee_code = d.fee_code
 );

-- 医院示例（每租户 4 条）
INSERT INTO t_ref_hospital (id, tenant_id, hospital_code, hospital_name, hospital_level, social_flag, province, city, created_by, created_at, updated_by, updated_at)
SELECT 94000100 + d.tenant_id + d.seq, d.tenant_id, d.hospital_code, d.hospital_name, d.hospital_level, d.social_flag, d.province, d.city, NULL, NOW(3), NULL, NOW(3)
  FROM (
        SELECT 0    AS tenant_id, 0 AS seq, 'HOSP110105001' AS hospital_code, '北京协和医院' AS hospital_name, '三甲' AS hospital_level, 1 AS social_flag, '北京' AS province, '东城区' AS city UNION ALL
        SELECT 0,    1, 'HOSP310104001', '上海交通大学医学院附属瑞金医院', '三甲', 1, '上海', '黄浦区' UNION ALL
        SELECT 0,    2, 'HOSP440103001', '中山大学附属第一医院', '三甲', 1, '广东', '广州市' UNION ALL
        SELECT 0,    3, 'HOSP330106001', '浙江大学医学院附属第一医院', '三甲', 1, '浙江', '杭州市' UNION ALL
        SELECT 1001, 0, 'HOSP110105001', '北京协和医院', '三甲', 1, '北京', '东城区' UNION ALL
        SELECT 1001, 1, 'HOSP310104001', '上海交通大学医学院附属瑞金医院', '三甲', 1, '上海', '黄浦区' UNION ALL
        SELECT 1001, 2, 'HOSP440103001', '中山大学附属第一医院', '三甲', 1, '广东', '广州市' UNION ALL
        SELECT 1001, 3, 'HOSP330106001', '浙江大学医学院附属第一医院', '三甲', 1, '浙江', '杭州市'
       ) d
 WHERE NOT EXISTS (
    SELECT 1 FROM t_ref_hospital t
     WHERE t.tenant_id = d.tenant_id AND t.hospital_code = d.hospital_code
 );

-- 疾病示例（每租户 4 条，ICD-10）
INSERT INTO t_ref_disease (id, tenant_id, disease_code, disease_name, icd_category, created_by, created_at, updated_by, updated_at)
SELECT 94000200 + d.tenant_id + d.seq, d.tenant_id, d.disease_code, d.disease_name, d.icd_category, NULL, NOW(3), NULL, NOW(3)
  FROM (
        SELECT 0    AS tenant_id, 0 AS seq, 'I10.x00' AS disease_code, '原发性高血压' AS disease_name, '循环系统I' AS icd_category UNION ALL
        SELECT 0,    1, 'E11.x00', '2型糖尿病', '内分泌系统E' UNION ALL
        SELECT 0,    2, 'J06.900', '急性上呼吸道感染', '呼吸系统J' UNION ALL
        SELECT 0,    3, 'K35.900', '急性阑尾炎', '消化系统K' UNION ALL
        SELECT 1001, 0, 'I10.x00', '原发性高血压', '循环系统I' UNION ALL
        SELECT 1001, 1, 'E11.x00', '2型糖尿病', '内分泌系统E' UNION ALL
        SELECT 1001, 2, 'J06.900', '急性上呼吸道感染', '呼吸系统J' UNION ALL
        SELECT 1001, 3, 'K35.900', '急性阑尾炎', '消化系统K'
       ) d
 WHERE NOT EXISTS (
    SELECT 1 FROM t_ref_disease t
     WHERE t.tenant_id = d.tenant_id AND t.disease_code = d.disease_code
 );

-- 药品示例（每租户 4 条，甲/乙/丙类 + 社保外）
INSERT INTO t_ref_drug (id, tenant_id, drug_code, drug_name, social_flag, medical_type, created_by, created_at, updated_by, updated_at)
SELECT 94000300 + d.tenant_id + d.seq, d.tenant_id, d.drug_code, d.drug_name, d.social_flag, d.medical_type, NULL, NOW(3), NULL, NOW(3)
  FROM (
        SELECT 0    AS tenant_id, 0 AS seq, 'DRUGJA001' AS drug_code, '阿莫西林胶囊' AS drug_name, 1 AS social_flag, '甲类' AS medical_type UNION ALL
        SELECT 0,    1, 'DRUGYB001', '阿托伐他汀钙片', 1, '乙类' UNION ALL
        SELECT 0,    2, 'DRUGJA002', '头孢呋辛酯片', 1, '甲类' UNION ALL
        SELECT 0,    3, 'DRUGBL001', '某进口肿瘤靶向药', 0, '丙类' UNION ALL
        SELECT 1001, 0, 'DRUGJA001', '阿莫西林胶囊', 1, '甲类' UNION ALL
        SELECT 1001, 1, 'DRUGYB001', '阿托伐他汀钙片', 1, '乙类' UNION ALL
        SELECT 1001, 2, 'DRUGJA002', '头孢呋辛酯片', 1, '甲类' UNION ALL
        SELECT 1001, 3, 'DRUGBL001', '某进口肿瘤靶向药', 0, '丙类'
       ) d
 WHERE NOT EXISTS (
    SELECT 1 FROM t_ref_drug t
     WHERE t.tenant_id = d.tenant_id AND t.drug_code = d.drug_code
 );

-- ─────────────────────────────────────────────────────────────
-- ④ 菜单：「基础目录库」（/claim#/library，order 15）
-- ─────────────────────────────────────────────────────────────
DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0094;
CREATE TEMPORARY TABLE tmp_claim_dir_0094 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_claim_dir_0094 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

-- 叶子 id = 目录 id + 24；幂等：同租户同 path 已存在则跳过
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 24, d.tenant_id, '基础目录库', d.dir_id, '/claim#/library',
       'DatabaseOutlined', 15, 'claim:policy:read', 1, NULL, NULL
  FROM tmp_claim_dir_0094 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/library' AND m.deleted = 0
 );

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0094;

-- ─────────────────────────────────────────────────────────────
-- 验收
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0094;
DELIMITER $$
CREATE PROCEDURE sp_verify_0094()
BEGIN
    DECLARE v_missing INT DEFAULT 0;
    DECLARE v_cols    INT DEFAULT 0;
    DECLARE v_seed    INT DEFAULT 0;
    DECLARE v_leaf    INT DEFAULT 0;
    DECLARE v_order   INT DEFAULT 0;
    DECLARE v_drift   INT DEFAULT 0;
    DECLARE v_perm    INT DEFAULT 0;
    DECLARE v_icon    INT DEFAULT 0;

    -- ① 五张新表存在且关键列齐全（同名异形/缺列即失败）
    SELECT COUNT(*) INTO v_missing
      FROM (
        SELECT 't_ref_hospital' AS t, 'hospital_code' AS c UNION ALL
        SELECT 't_ref_hospital', 'hospital_level'  UNION ALL
        SELECT 't_ref_hospital', 'social_flag'     UNION ALL
        SELECT 't_ref_disease', 'disease_code'     UNION ALL
        SELECT 't_ref_disease', 'icd_category'     UNION ALL
        SELECT 't_ref_drug', 'drug_code'           UNION ALL
        SELECT 't_ref_drug', 'medical_type'        UNION ALL
        SELECT 't_ref_fee_category', 'fee_code'    UNION ALL
        SELECT 't_ref_fee_category', 'sort_no'     UNION ALL
        SELECT 't_adj_liability_fee_rule', 'liability_uuid' UNION ALL
        SELECT 't_adj_liability_fee_rule', 'fee_code'       UNION ALL
        SELECT 't_adj_liability_fee_rule', 'pay_percent'    UNION ALL
        SELECT 't_adj_liability_fee_rule', 'limit_amount'
      ) AS expected
     WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = expected.t
           AND col.column_name = expected.c
     );
    IF v_missing <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：基础目录库五表存在缺列（同名异形或建表失败）';
    END IF;

    -- ② t_claim_policy 作业配置两列必须就位
    SELECT COUNT(*) INTO v_cols
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_policy'
       AND COLUMN_NAME IN ('doc_claim_apply', 'doc_claim_notice');
    IF v_cols <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：t_claim_policy 作业配置列未补齐';
    END IF;

    -- ③ 费用类别种子：租户 0/1001 各 10 条
    SELECT COUNT(*) INTO v_seed
      FROM t_ref_fee_category
     WHERE tenant_id IN (0, 1001) AND deleted = 0;
    IF v_seed <> 20 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：费用类别种子不足（0/1001 租户各应 10 条）';
    END IF;

    -- ④ 平台/默认租户各恰好 13 条叶子：1..11 作业链路 + 13 额度管理 + 15 基础目录库
    SELECT COUNT(*) INTO v_leaf FROM iam_menu m
      JOIN (SELECT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%';
    IF v_leaf <> 2 * 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：平台/默认租户的理赔叶子菜单不是 13 条';
    END IF;

    -- ⑤ 顺序覆盖 {1..11, 13, 15} 共 13 个值且无重复
    SELECT COUNT(DISTINCT order_no) INTO v_order FROM iam_menu m
      JOIN (SELECT DISTINCT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d
        ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%';
    IF v_order <> 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：理赔菜单 order_no 未覆盖 1..11/13/15';
    END IF;

    -- ⑥ 菜单名必须与前端 routes.tsx 逐字一致（探针按名比对）
    SELECT COUNT(*) INTO v_drift FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/library'
       AND m.tenant_id IN (0, 1001)
       AND m.name <> '基础目录库';
    IF v_drift > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：基础目录库菜单名称与 routes.tsx 不一致（存在漂移行）';
    END IF;

    -- ⑦ permission 必须是读码（否则只持读权限的角色看不见入口）
    SELECT COUNT(*) INTO v_perm FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/library'
       AND m.tenant_id IN (0, 1001)
       AND m.permission <> 'claim:policy:read';
    IF v_perm > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：基础目录库菜单 permission 不是 claim:policy:read';
    END IF;

    -- ⑧ icon 必须在 bone-shell MENU_ICON_MAP 白名单（否则静默降级成默认图标）
    SELECT COUNT(*) INTO v_icon FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/library'
       AND m.tenant_id IN (0, 1001)
       AND (m.icon IS NULL OR m.icon = '' OR m.icon <> 'DatabaseOutlined');
    IF v_icon > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0094 校验失败：基础目录库菜单 icon 缺失或漂移（会静默降级）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0094();
DROP PROCEDURE IF EXISTS sp_verify_0094;
