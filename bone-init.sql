-- ============================================================
-- Bone 数据库初始化脚本
-- 数据库: bone
-- 用户名: root
-- 密码: 请在环境变量 BONE_DB_PASSWORD 中配置
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS bone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE bone;

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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_head_person_id (head_person_id)
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    create_by             BIGINT        NOT NULL COMMENT '创建人',
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
    update_by             BIGINT        NOT NULL COMMENT '修改人',
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
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by             BIGINT        NOT NULL COMMENT '创建人',
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by             BIGINT        NOT NULL COMMENT '修改人',
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
    last_update_time  DATETIME(3)   NOT NULL COMMENT '最后更新时间',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '保单创建时间',
    create_by           BIGINT        NOT NULL COMMENT '创建人',
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '保单更新时间',
    update_by           BIGINT        NOT NULL COMMENT '修改人',
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
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
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    create_by           BIGINT        NOT NULL COMMENT '创建人',
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
    update_by           BIGINT        NOT NULL COMMENT '修改人',
    deleted             TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_renewal (original_policy_id, renewed_policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '续保记录';

-- ============================================================
-- 5. IAM模块表
-- ============================================================

/* 5.1 用户表 */
DROP TABLE IF EXISTS iam_user;
CREATE TABLE iam_user (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键',
    username          VARCHAR(50)   NOT NULL UNIQUE COMMENT '用户名',
    password          VARCHAR(255)  NOT NULL COMMENT '密码（加密存储）',
    name              VARCHAR(100)  NOT NULL COMMENT '姓名',
    email             VARCHAR(100)  NOT NULL UNIQUE COMMENT '邮箱',
    phone             VARCHAR(20)   COMMENT '手机号',
    status            ENUM('active','inactive','locked') NOT NULL DEFAULT 'active' COMMENT '状态',
    last_login_time   DATETIME      COMMENT '最后登录时间',
    last_login_ip     VARCHAR(45)   COMMENT '最后登录IP',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '用户';

/* 5.2 角色表 */
DROP TABLE IF EXISTS iam_role;
CREATE TABLE iam_role (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色主键',
    code              VARCHAR(50)   NOT NULL UNIQUE COMMENT '角色编码',
    name              VARCHAR(100)  NOT NULL COMMENT '角色名称',
    description       VARCHAR(255)  COMMENT '角色描述',
    status            ENUM('active','inactive') NOT NULL DEFAULT 'active' COMMENT '状态',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_code (code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '角色';

/* 5.3 权限表 */
DROP TABLE IF EXISTS iam_permission;
CREATE TABLE iam_permission (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限主键',
    code              VARCHAR(50)   NOT NULL UNIQUE COMMENT '权限编码',
    name              VARCHAR(100)  NOT NULL COMMENT '权限名称',
    description       VARCHAR(255)  COMMENT '权限描述',
    resource_type     VARCHAR(50)   NOT NULL COMMENT '资源类型',
    resource_path     VARCHAR(255)  NOT NULL COMMENT '资源路径',
    action            VARCHAR(20)   NOT NULL COMMENT '操作类型',
    status            ENUM('active','inactive') NOT NULL DEFAULT 'active' COMMENT '状态',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_code (code),
    INDEX idx_resource (resource_type, resource_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '权限';

/* 5.4 用户角色关联表 */
DROP TABLE IF EXISTS iam_user_role;
CREATE TABLE iam_user_role (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户角色关联主键',
    user_id           BIGINT        NOT NULL COMMENT '用户ID，关联 iam_user',
    role_id           BIGINT        NOT NULL COMMENT '角色ID，关联 iam_role',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '用户角色关联';

/* 5.5 角色权限关联表 */
DROP TABLE IF EXISTS iam_role_permission;
CREATE TABLE iam_role_permission (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色权限关联主键',
    role_id           BIGINT        NOT NULL COMMENT '角色ID，关联 iam_role',
    permission_id     BIGINT        NOT NULL COMMENT '权限ID，关联 iam_permission',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_role_permission (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '角色权限关联';

/* 5.6 审计日志表 */
DROP TABLE IF EXISTS iam_audit_log;
CREATE TABLE iam_audit_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计日志主键',
    user_id           BIGINT        COMMENT '操作用户ID，关联 iam_user',
    username          VARCHAR(50)   COMMENT '操作用户名',
    operation_type    VARCHAR(50)   NOT NULL COMMENT '操作类型',
    operation_name    VARCHAR(100)  NOT NULL COMMENT '操作名称',
    target_type       VARCHAR(50)   COMMENT '目标类型',
    target_id         BIGINT        COMMENT '目标ID',
    target_name       VARCHAR(100)  COMMENT '目标名称',
    operation_result  ENUM('success','failed') NOT NULL DEFAULT 'success' COMMENT '操作结果',
    error_message     VARCHAR(500)  COMMENT '错误信息',
    ip_address        VARCHAR(45)   COMMENT '操作IP地址',
    user_agent        VARCHAR(255)  COMMENT '用户代理',
    request_url       VARCHAR(255)  COMMENT '请求URL',
    request_method    VARCHAR(10)   COMMENT '请求方法',
    request_params    JSON          COMMENT '请求参数',
    response_data     JSON          COMMENT '响应数据',
    execution_time    INT           COMMENT '执行时间（毫秒）',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_operation (operation_type, operation_result),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '审计日志';

-- ============================================================
-- 6. 系统模块表
-- ============================================================

/* 6.1 系统配置表 */
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置主键',
    config_key        VARCHAR(100)  NOT NULL UNIQUE COMMENT '配置键',
    config_value      TEXT          NOT NULL COMMENT '配置值',
    config_type       VARCHAR(50)   NOT NULL COMMENT '配置类型',
    description       VARCHAR(255)  COMMENT '配置描述',
    status            ENUM('active','inactive') NOT NULL DEFAULT 'active' COMMENT '状态',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_config_key (config_key),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '系统配置';

/* 6.2 系统日志表 */
DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    log_level         ENUM('debug','info','warn','error','fatal') NOT NULL DEFAULT 'info' COMMENT '日志级别',
    log_type          VARCHAR(50)   NOT NULL COMMENT '日志类型',
    log_message       TEXT          NOT NULL COMMENT '日志消息',
    log_data          JSON          COMMENT '日志数据',
    source            VARCHAR(100)  COMMENT '日志来源',
    trace_id          VARCHAR(100)  COMMENT '跟踪ID',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_log_level (log_level),
    INDEX idx_log_type (log_type),
    INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '系统日志';

/* 6.3 系统监控表 */
DROP TABLE IF EXISTS sys_monitor;
CREATE TABLE sys_monitor (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '监控主键',
    monitor_type      VARCHAR(50)   NOT NULL COMMENT '监控类型',
    monitor_name      VARCHAR(100)  NOT NULL COMMENT '监控名称',
    monitor_value     DECIMAL(20,2) NOT NULL COMMENT '监控值',
    monitor_unit      VARCHAR(20)   COMMENT '监控单位',
    threshold         DECIMAL(20,2) COMMENT '阈值',
    status            ENUM('normal','warning','error') NOT NULL DEFAULT 'normal' COMMENT '状态',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_monitor_type (monitor_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '系统监控';

-- ============================================================
-- 7. 集成模块表
-- ============================================================

/* 7.1 连接器表 */
DROP TABLE IF EXISTS int_connector;
CREATE TABLE int_connector (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '连接器主键',
    code              VARCHAR(50)   NOT NULL UNIQUE COMMENT '连接器编码',
    name              VARCHAR(100)  NOT NULL COMMENT '连接器名称',
    type              VARCHAR(50)   NOT NULL COMMENT '连接器类型',
    config            JSON          NOT NULL COMMENT '连接器配置',
    status            ENUM('active','inactive') NOT NULL DEFAULT 'active' COMMENT '状态',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_code (code),
    INDEX idx_type (type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '连接器';

/* 7.2 流程表 */
DROP TABLE IF EXISTS int_flow;
CREATE TABLE int_flow (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流程主键',
    code              VARCHAR(50)   NOT NULL UNIQUE COMMENT '流程编码',
    name              VARCHAR(100)  NOT NULL COMMENT '流程名称',
    description       VARCHAR(255)  COMMENT '流程描述',
    flow_definition   JSON          NOT NULL COMMENT '流程定义',
    status            ENUM('draft','active','inactive') NOT NULL DEFAULT 'draft' COMMENT '状态',
    version           VARCHAR(10)   NOT NULL DEFAULT '1.0' COMMENT '流程版本',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_code (code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '流程';

/* 7.3 流程执行记录表 */
DROP TABLE IF EXISTS int_flow_execution;
CREATE TABLE int_flow_execution (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '执行记录主键',
    flow_id           BIGINT        NOT NULL COMMENT '流程ID，关联 int_flow',
    execution_id      VARCHAR(100)  NOT NULL UNIQUE COMMENT '执行ID',
    status            ENUM('pending','running','completed','failed','cancelled') NOT NULL DEFAULT 'pending' COMMENT '执行状态',
    input_data        JSON          COMMENT '输入数据',
    output_data       JSON          COMMENT '输出数据',
    error_message     VARCHAR(500)  COMMENT '错误信息',
    start_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
    end_time          DATETIME(3)   COMMENT '结束时间',
    duration          INT           COMMENT '执行时长（毫秒）',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    INDEX idx_flow_id (flow_id),
    INDEX idx_execution_id (execution_id),
    INDEX idx_status (status),
    INDEX idx_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '流程执行记录';

-- ============================================================
-- 8. 初始化数据
-- ============================================================

-- 初始化租户数据
INSERT INTO ic_company (code, name, parent_company_id, industry, employee_count, contact_person, contact_phone, address, email, tenant_id, create_by, update_by) VALUES
('TENANT_001', '默认租户', NULL, 'IT', 100, '管理员', '13800138000', '北京市朝阳区', 'admin@example.com', 1, 1, 1);

-- 初始化用户数据
INSERT INTO iam_user (username, password, name, email, phone, status, tenant_id, create_by, update_by) VALUES
('admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '管理员', 'admin@example.com', '13800138000', 'active', 1, 1, 1);

-- 初始化角色数据
INSERT INTO iam_role (code, name, description, status, tenant_id, create_by, update_by) VALUES
('ADMIN', '管理员', '系统管理员角色', 'active', 1, 1, 1),
('USER', '普通用户', '普通用户角色', 'active', 1, 1, 1);

-- 初始化权限数据
INSERT INTO iam_permission (code, name, description, resource_type, resource_path, action, status, tenant_id, create_by, update_by) VALUES
('PERM_USER_MANAGE', '用户管理', '用户管理权限', 'USER', '/api/users', 'ALL', 'active', 1, 1, 1),
('PERM_ROLE_MANAGE', '角色管理', '角色管理权限', 'ROLE', '/api/roles', 'ALL', 'active', 1, 1, 1),
('PERM_PERMISSION_MANAGE', '权限管理', '权限管理权限', 'PERMISSION', '/api/permissions', 'ALL', 'active', 1, 1, 1),
('PERM_AUDIT_LOG', '审计日志', '审计日志权限', 'AUDIT', '/api/audit-logs', 'READ', 'active', 1, 1, 1);

-- 初始化用户角色关联
INSERT INTO iam_user_role (user_id, role_id, tenant_id, create_by, update_by) VALUES
(1, 1, 1, 1, 1);

-- 初始化角色权限关联
INSERT INTO iam_role_permission (role_id, permission_id, tenant_id, create_by, update_by) VALUES
(1, 1, 1, 1, 1),
(1, 2, 1, 1, 1),
(1, 3, 1, 1, 1),
(1, 4, 1, 1, 1),
(2, 4, 1, 1, 1);

-- 初始化系统配置
INSERT INTO sys_config (config_key, config_value, config_type, description, status, tenant_id, create_by, update_by) VALUES
('system.title', 'Bone Platform', 'system', '系统标题', 'active', 1, 1, 1),
('system.version', '1.0.0', 'system', '系统版本', 'active', 1, 1, 1),
('system.timezone', 'Asia/Shanghai', 'system', '系统时区', 'active', 1, 1, 1),
('system.language', 'zh-CN', 'system', '系统语言', 'active', 1, 1, 1);

-- 初始化保险公司数据
INSERT INTO ic_insurer (insurer_code, insurer_name, contact_phone, contact_address, email, license_number, status, create_by, update_by, tenant_id, biz_identity_code) VALUES
('IC001', '中国平安保险', '95511', '深圳市福田区', 'service@pingan.com', '1234567890', 'active', 1, 1, 1, 'DEFAULT'),
('IC002', '中国人寿保险', '95519', '北京市西城区', 'service@chinalife.com', '0987654321', 'active', 1, 1, 1, 'DEFAULT');

-- 初始化产品配置数据
INSERT INTO ic_product_config (product_code, product_name, insurer_id, product_type, coverage_period, premium_frequency, coverage_schema, pricing_model, status, product_version, effective_date, tenant_id, biz_identity_code, create_by, update_by) VALUES
('P001', '平安福重疾险', 1, 'health', '终身', 'annual', '{"mainCoverage": "重大疾病保障", "additionalCoverage": ["轻症保障", "身故保障"]}', '{"basePremium": 5000, "ageFactor": 0.02, "genderFactor": 1.0}', 'active', '1.0', '2024-01-01', 1, 'DEFAULT', 1, 1);

COMMIT;