-- 【已废止】保险示例域历史脚本，非平台 DDL 真源。
-- 平台表结构见根目录 bone-init.sql 与 doc/architecture/数据库开发规范.md（方案 C：created_at / updated_at）。
-- ============================================================
-- 1. 基础数据表
-- ============================================================

/* 1.1 保险公司表 */
DROP TABLE IF EXISTS ic_insurer;
CREATE TABLE ic_insurer (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保险公司主键',
    insurer_code      VARCHAR(10)   NOT NULL UNIQUE COMMENT '保险公司编码',
    insurer_name      VARCHAR(100)  NOT NULL COMMENT '保险公司名称',
    contact_phone     VARCHAR(20)   COMMENT '联系电话',
    contact_address   VARCHAR(600)  COMMENT '联系地址',
    email             VARCHAR(50)   COMMENT '电子邮箱',
    license_number    VARCHAR(50)   COMMENT '许可证号',
    status            ENUM('active','inactive') NOT NULL DEFAULT 'active' COMMENT '状态',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_insurer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保险公司';


/* 1.2 人员表（统一管理投保人和被保险人） */
DROP TABLE IF EXISTS ic_insured_person;
CREATE TABLE ic_insured_person (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '人员主键',
    name              VARCHAR(50)   NOT NULL COMMENT '姓名',
    id_type           ENUM('id_card','passport','hkid','ssn') NOT NULL DEFAULT 'id_card' COMMENT '证件类型（id_card-身份证, passport-护照,  hkid-香港身份证, ssn-美国社保号）',
    id_card_no        VARCHAR(256)   COMMENT '证件号码,AES-256加密存储',
    id_card_hash      CHAR(64)      NOT NULL COMMENT '原始证件号SHA256哈希',
    nationality       VARCHAR(50)   NOT NULL DEFAULT 'Chinese' COMMENT '国籍',
    domicile          VARCHAR(50)   COMMENT '户籍所在地',
    mobile            VARCHAR(20)   COMMENT '手机号（国际格式，如+8613800138000）',
    email             VARCHAR(50)   COMMENT '邮箱',
    gender            ENUM('male','female','unknown') COMMENT '性别',
    birth_date        DATE          COMMENT '出生日期',
    occupation        VARCHAR(100)  COMMENT '职业信息',
    health_status     JSON          COMMENT '健康状况（如慢性病、过敏等）',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_id_card (id_card_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '人员';


-- ================================================
-- 1. 公司主数据：ic_company
-- ================================================
DROP TABLE IF EXISTS ic_company;
CREATE TABLE ic_company (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '企业主数据主键',
    code              VARCHAR(64)  NOT NULL UNIQUE COMMENT '公司编码',
    name              VARCHAR(150)  NOT NULL COMMENT '公司名称',
    parent_company_id         BIGINT        COMMENT '上级公司ID，关联本表',
    industry          VARCHAR(100)  COMMENT '所属行业',
    employee_count    INT UNSIGNED  COMMENT '员工人数',
    contact_person    VARCHAR(50)   COMMENT '联系人姓名',
    contact_phone     VARCHAR(20)   COMMENT '联系人电话',
    address           VARCHAR(500)  COMMENT '注册地址',
    email             VARCHAR(50)   COMMENT '企业邮箱',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_parent_company (parent_company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '公司主数据';


-- ================================================
-- 2. 保单持有人表：ic_policyholder
-- ================================================
DROP TABLE IF EXISTS ic_policyholder;
CREATE TABLE ic_policyholder (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保单持有人主键',
    name              VARCHAR(100)  NOT NULL COMMENT '个人、公司/组织名称',
    code              VARCHAR(50)   COMMENT '统一社会信用代码/证件号码（个人可为空）',
    holder_id         BIGINT COMMENT '根据 holder_type 关联不同表：individual=ic_insured_person.id, family=ic_family.id, company=ic_company.id',
    holder_type       ENUM('individual','family','company') NOT NULL DEFAULT 'company' COMMENT '持有人类型，individual-个人，family-家庭, company-公司',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_holder_type (holder_type),
    INDEX idx_holder_id (holder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保单持有人';


/* 1.4 家庭表 */
DROP TABLE IF EXISTS ic_family;
CREATE TABLE ic_family (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '家庭主键',
    family_name       VARCHAR(100)  NOT NULL COMMENT '家庭名称',
    head_person_id    BIGINT        NOT NULL COMMENT '家庭户主ID，关联 ic_insured_person',
    description       VARCHAR(200)  COMMENT '备注说明',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_head_person_id (head_person_id),
    UNIQUE INDEX uk_family_person (family_id, person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '家庭，用于维护家庭共保关系';


/* 1.5 家庭成员关联表 */
DROP TABLE IF EXISTS ic_family_member;
CREATE TABLE ic_family_member (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '家庭成员关联主键',
    family_id         BIGINT        NOT NULL COMMENT '家庭ID，关联 ic_family',
    person_id         BIGINT        NOT NULL COMMENT '人员ID，关联 ic_insured_person',
    relationship      VARCHAR(50)   COMMENT '与户主关系，如 spouse, child, parent, sibling',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_family_person (family_id, person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '家庭成员';


-- ============================================================
-- 2. 产品工厂
-- ============================================================

/* 2.1 保险产品配置表 */
DROP TABLE IF EXISTS ic_product_config;
CREATE TABLE ic_product_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产品主键',
    product_code      VARCHAR(20)   NOT NULL UNIQUE COMMENT '产品编码',
    product_name      VARCHAR(100)  NOT NULL COMMENT '产品名称',
    insurer_id        BIGINT        NOT NULL COMMENT '保险公司ID，关联 ic_insurer',
    product_type       ENUM('life','health','property','liability') NOT NULL COMMENT '产品类型',
    coverage_period    VARCHAR(50)   COMMENT '保障期间（如终身、10年）',
    premium_frequency  ENUM('annual','semi_annual','monthly') NOT NULL DEFAULT 'annual' COMMENT '缴费频率',
    coverage_schema   JSON          NOT NULL COMMENT '保障模板（主险及附加险结构）',
    pricing_model     JSON          NOT NULL COMMENT '定价模型（费率、计算公式等）',
    underwriting_flow JSON          COMMENT '核保流程配置',
    status            ENUM('draft','active','retired','pending') NOT NULL DEFAULT 'draft' COMMENT '产品状态',
    product_version   VARCHAR(10)   NOT NULL DEFAULT '1.0' COMMENT '产品版本',
    effective_date    DATE          NOT NULL COMMENT '产品生效日期',
    expire_date       DATE          COMMENT '产品失效日期',
    max_insured_age   INT UNSIGNED  COMMENT '最大投保年龄',
    min_insured_age   INT UNSIGNED  COMMENT '最小投保年龄',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_insurer_status (insurer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保险产品配置';


-- ============================================================
-- 3. 承保模块
-- ============================================================

/* 3.1 投保单 */
DROP TABLE IF EXISTS ic_proposal;
CREATE TABLE ic_proposal (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '提案主键',
    proposal_code         VARCHAR(32)   NOT NULL UNIQUE COMMENT '提案编号（INS+YYYYMMDD+8位序列）',
    product_config_id     BIGINT        NOT NULL COMMENT '产品配置ID',
    insured_type          ENUM('individual','family','group') NOT NULL COMMENT '投保类型：individual-个人，family-家庭,group-团体',
    policyholder_id       BIGINT        NOT NULL COMMENT '保单持有人ID',
    proposal_channel      ENUM('online','offline') NOT NULL DEFAULT 'online' COMMENT '投保渠道',
    proposal_type         ENUM('new','renewal','reinstatement') NOT NULL DEFAULT 'new' COMMENT '提案类型',
    proposal_status       ENUM('draft','underwriting','approved','rejected','withdrawn') NOT NULL DEFAULT 'draft' COMMENT '提案状态',
    health_questionnaire  JSON          COMMENT '健康告知（JSON格式）',
    risk_score            DECIMAL(5,2)  COMMENT '风险评分',
    underwriting_result   JSON          COMMENT '核保结论（含加费/除外）',
    effective_date        DATE          NOT NULL COMMENT '计划生效日期',
    tenant_id             BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code     VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    created_by             BIGINT        NOT NULL COMMENT '创建人',
    updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
    updated_by             BIGINT        NOT NULL COMMENT '修改人',
    deleted               TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_proposal_status (product_config_id, proposal_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '投保单';

-- ============================================================
-- 3. 承保模块（续）
-- ============================================================

/* 3.2 核保规则表 */
DROP TABLE IF EXISTS ic_underwriting_rule;
CREATE TABLE ic_underwriting_rule (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '核保规则主键',
    rule_code             VARCHAR(20)   NOT NULL UNIQUE COMMENT '规则编号（产品编码+规则类型+版本）',
    product_config_id     BIGINT        NOT NULL COMMENT '产品配置ID（业务关联）',
    rule_type             ENUM('auto','manual','hybrid') NOT NULL COMMENT '规则类型',
    underwriting_rules    JSON          COMMENT '核保规则（如拒保职业、疾病）',
    condition_expression  JSON          NOT NULL COMMENT '触发条件（JSON表达式）',
    action_expression     JSON          NOT NULL COMMENT '执行动作（JSON表达式）',
    priority              TINYINT UNSIGNED NOT NULL COMMENT '优先级（1-100）',
    version               VARCHAR(10)   NOT NULL COMMENT '规则版本',
    status                ENUM('active','inactive','deprecated') NOT NULL DEFAULT 'active' COMMENT '规则状态',
    effective_date        DATE          NOT NULL COMMENT '规则生效日期',
    expire_date           DATE          COMMENT '规则失效日期',
    tenant_id             BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code     VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by             BIGINT        NOT NULL COMMENT '创建人',
    updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by             BIGINT        NOT NULL COMMENT '修改人',
    deleted               TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_rule_product (product_config_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '核保规则';


/* 3.3 健康档案表 */
DROP TABLE IF EXISTS ic_health_profile;
CREATE TABLE ic_health_profile (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '健康档案主键',
    person_id         BIGINT        NOT NULL COMMENT '被保险人员ID（业务关联）',
    medical_history   JSON          COMMENT '既往病史（ICD-10编码）',
    exam_data         JSON          COMMENT '体检报告数据（结构化）',
    wearable_data     JSON          COMMENT '可穿戴设备数据（运动/睡眠）',
    genetic_risk      JSON          COMMENT '基因检测风险信息',
    last_updated_at  DATETIME(3)   NOT NULL COMMENT '最后更新时间',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_person (person_id),
    INDEX idx_medical_history ((CAST(medical_history->'$.diseases' AS CHAR(50)))),
    INDEX idx_wearable_activity ((CAST(wearable_data->'$.activity_level' AS CHAR(50))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '健康档案表';


-- ============================================================
-- 4. 保全模块
-- ============================================================

/* 4.1 保单 */
DROP TABLE IF EXISTS ic_policy;
CREATE TABLE ic_policy (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保单主键',
    policy_code         VARCHAR(32)   NOT NULL UNIQUE COMMENT '保单编号（POL+YYYYMMDD+8位序列）',
    proposal_id         BIGINT        NOT NULL COMMENT '投保单ID，关联 ic_proposal',
    product_config_id   BIGINT        NOT NULL COMMENT '产品配置ID（业务关联）',
    policyholder_id     BIGINT        COMMENT '保单持有人ID，关联 ic_policyholder（团体险时填写）',
    type                ENUM('individual','group') NOT NULL DEFAULT 'individual' COMMENT '保单类型：individual-个单，group-团单',
    status              ENUM('active','lapsed','terminated','on_hold','cancelled') NOT NULL DEFAULT 'active' COMMENT '保单状态',
    premium_amount      DECIMAL(12,2) NOT NULL COMMENT '年缴保费',
    coverage_detail     JSON          NOT NULL COMMENT '保障详情（支持复杂结构）',
    effective_date      DATE          NOT NULL COMMENT '保单生效日期',
    expiry_date         DATE          NOT NULL COMMENT '保单到期日期',
    renewal_count       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '续保次数',
    pre_policy_id       BIGINT        NULL COMMENT '关联保单',
    grace_period_days   INT DEFAULT 0 COMMENT '宽限期，单位天',
    version             INT DEFAULT 0 COMMENT '保单版本',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code   VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '保单创建时间',
    created_by           BIGINT        NOT NULL COMMENT '创建人',
    updated_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '保单更新时间',
    updated_by           BIGINT        NOT NULL COMMENT '修改人',
    deleted             TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_policy_product (product_config_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保单';

/* 4.2 保单被保险人关联表 */
DROP TABLE IF EXISTS ic_policy_insured;
CREATE TABLE ic_policy_insured (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保单被保险人关联主键',
    relate_id         BIGINT        NOT NULL COMMENT '关联单ID，关联 ic_proposal，ic_policy_endorsement，ic_policy_renewal',
    insured_type      ENUM('proposal','endorsement','renewal') NOT NULL DEFAULT 'proposal' COMMENT '关联单据类型：proposal-投保，endorsement-保全，renewal-续保',
    policy_id         BIGINT        NOT NULL COMMENT '保单ID（业务关联）',
    person_id         BIGINT        NOT NULL COMMENT '人员ID，关联 ic_insured_person',
    relationship      VARCHAR(50)   COMMENT '与主被保险人关系，如 spouse, child, self',
    is_main_insured   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否主被保险人（1-是，0-否）',
    coverage_detail  JSON          COMMENT '个人专属保障详情',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_policy_person (policy_id, person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保单被保险人';


/* 4.3 保全操作表 */
DROP TABLE IF EXISTS ic_policy_endorsement;
CREATE TABLE ic_policy_endorsement (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保全操作主键',
    endorsement_code  VARCHAR(20)   NOT NULL UNIQUE COMMENT '批单编号（END+YYYYMMDD+6位序列）',
    policy_id         BIGINT        NOT NULL COMMENT '保单ID（业务关联）',
    proposal_id       BIGINT        NOT NULL COMMENT '关联提案ID，关联 ic_proposal',
    endorsement_type  ENUM('beneficiary_change','coverage_adjust','renewal','premium_change','policy_suspend') NOT NULL COMMENT '保全操作类型',
    operation_source  ENUM('system','agent','customer') NOT NULL DEFAULT 'system' COMMENT '操作来源',
    change_reason     VARCHAR(500)  COMMENT '变更原因',
    operation_data    JSON NOT NULL COMMENT '变更内容（包含字段级差异）',
    approval_status   ENUM('pending','approved','rejected','cancelled') NOT NULL DEFAULT 'pending' COMMENT '审批状态',
    effective_date    DATE          NOT NULL COMMENT '操作生效日期',
    operator_id       BIGINT        NOT NULL COMMENT '操作人员ID',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保全操作';


/* 4.4 续保记录表 */
DROP TABLE IF EXISTS ic_policy_renewal;
CREATE TABLE ic_policy_renewal (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '续保记录主键',
    original_policy_id  BIGINT        NOT NULL COMMENT '原保单ID（业务关联）',
    renewed_policy_id   BIGINT        NOT NULL COMMENT '续保后保单ID（业务关联）',
    proposal_id         BIGINT        NOT NULL COMMENT '关联提案ID，关联 ic_proposal',
    renewal_type        ENUM('auto','manual','forced') NOT NULL COMMENT '续保类型',
    premium_change      DECIMAL(10,2) NOT NULL COMMENT '保费变化金额',
    coverage_change     JSON          COMMENT '保障内容变化记录',
    renewal_date        DATE          NOT NULL COMMENT '续保日期',
    reason_code         VARCHAR(20)   COMMENT '续保原因代码',
    underwriting_flag   BOOLEAN       NOT NULL DEFAULT false COMMENT '是否重新核保标识',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code   VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    created_by           BIGINT        NOT NULL COMMENT '创建人',
    updated_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
    updated_by           BIGINT        NOT NULL COMMENT '修改人',
    deleted             TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_renewal (original_policy_id, renewed_policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '续保记录';


-- ============================================================
-- 5. 性能优化与安全合规示例
-- ============================================================

/* 5.5 审计日志触发器示例 */
DROP TABLE IF EXISTS ic_audit_log;
CREATE TABLE ic_audit_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT        COMMENT '操作用户ID',
    operation_type    VARCHAR(50)   COMMENT '操作类型',
    target_table      VARCHAR(50)   COMMENT '目标表',
    target_id         BIGINT        COMMENT '目标ID',
    before_state      JSON          COMMENT '变更前数据',
    after_state       JSON          COMMENT '变更后数据',
    ip_address        VARCHAR(45)   COMMENT '操作IP',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by         BIGINT        NOT NULL COMMENT '创建人',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '审计日志表';