-- ============================================================
-- 【1. 基础主数据】
-- ============================================================

/* 【1.1】系统数据字典表 */
DROP TABLE IF EXISTS sys_data_dict;
CREATE TABLE sys_data_dict (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '数据字典主键',
    dict_type         VARCHAR(50)   NOT NULL COMMENT '字典类型',
    dict_code         VARCHAR(50)   NOT NULL COMMENT '字典编码',
    dict_value        VARCHAR(512)  NOT NULL COMMENT '字典值',
    description       VARCHAR(255)  COMMENT '描述',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    parent_id         BIGINT        COMMENT '父ID',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_dict (tenant_id, dict_type, dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '系统数据字典';


/* 【1.2】保险公司表 */
DROP TABLE IF EXISTS ic_insurer;
CREATE TABLE ic_insurer (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保险公司主键',
    insurer_code      VARCHAR(10)   NOT NULL UNIQUE COMMENT '保险公司编码',
    insurer_name      VARCHAR(100)  NOT NULL COMMENT '保险公司名称',
    contact_phone     VARCHAR(20)   COMMENT '联系电话',
    contact_address   VARCHAR(600)  COMMENT '联系地址',
    email             VARCHAR(50)   COMMENT '电子邮箱',
    license_number    VARCHAR(50)   COMMENT '许可证号',
    status            ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_insurer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保险公司';


/* 【1.3】人员表 */
DROP TABLE IF EXISTS ic_insured_person;
CREATE TABLE ic_insured_person (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '人员主键',
    name                VARCHAR(50)   NOT NULL COMMENT '姓名',
    id_type             ENUM('ID_CARD','PASSPORT','HKID','SSN') NOT NULL DEFAULT 'ID_CARD' COMMENT '证件类型',
    id_card_no          VARCHAR(256)  COMMENT '证件号码（AES-256加密存储，密钥版本V1.2）',
    id_card_hash        CHAR(64)      NOT NULL COMMENT '原始证件号SHA256哈希',
    nationality         VARCHAR(50)   NOT NULL DEFAULT 'CHINESE' COMMENT '国籍',
    domicile            VARCHAR(50)   COMMENT '户籍所在地',
    mobile              VARCHAR(20)   COMMENT '手机号（国际格式，如+8613800138000）',
    email               VARCHAR(50)   COMMENT '邮箱',
    gender              ENUM('MALE','FEMALE','UNKNOWN') COMMENT '性别',
    birth_date          DATE          COMMENT '出生日期',
    occupation          VARCHAR(100)  COMMENT '职业信息',
    health_status       JSON          COMMENT '健康状况（如慢性病、过敏等）',
    source              VARCHAR(50)   COMMENT '数据来源（如系统录入、第三方导入）',
    id_card_key_version VARCHAR(10)   COMMENT '证件密钥版本',
    family_id           BIGINT        COMMENT '所属家庭ID',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code   VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by           BIGINT        NOT NULL COMMENT '创建人',
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by           BIGINT        NOT NULL COMMENT '修改人',
    deleted             TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_id_card (id_card_no),
    UNIQUE INDEX uk_id_card_hash (id_card_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '人员';


/* 【1.4】公司主数据 */
DROP TABLE IF EXISTS ic_company;
CREATE TABLE ic_company (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '企业主数据主键',
    code              VARCHAR(64)   NOT NULL UNIQUE COMMENT '公司编码',
    name              VARCHAR(150)  NOT NULL COMMENT '公司名称',
    parent_company_id BIGINT        COMMENT '上级公司ID，关联本表',
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
COMMENT '公司';


/* 【1.5】保单持有人 */
DROP TABLE IF EXISTS ic_policyholder;
CREATE TABLE ic_policyholder (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保单持有人主键',
    name              VARCHAR(100)  NOT NULL COMMENT '个人、公司/组织名称',
    code              VARCHAR(50)   COMMENT '统一社会信用代码/证件号码（个人可为空）',
    holder_id         BIGINT        COMMENT '关联的持有人标识（如个人ID、家庭ID、公司ID）',
    holder_type       ENUM('INDIVIDUAL','FAMILY','COMPANY') NOT NULL DEFAULT 'COMPANY' COMMENT '持有人类型',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_holder_type (holder_type),
    INDEX idx_holder_id (holder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保单持有人';



-- ============================================================
-- 【2. 产品工厂】
-- ============================================================

/* 【2.1】保险产品配置 */
DROP TABLE IF EXISTS ic_product_config;
CREATE TABLE ic_product_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产品主键',
    product_code      VARCHAR(20)   NOT NULL UNIQUE COMMENT '产品编码',
    product_name      VARCHAR(100)  NOT NULL COMMENT '产品名称',
    insurer_id        BIGINT        NOT NULL COMMENT '保险公司ID，关联 ic_insurer',
    product_type       ENUM('LIFE','HEALTH','PROPERTY','LIABILITY') NOT NULL COMMENT '产品类型',
    coverage_period    VARCHAR(50)   COMMENT '保障期间（如终身、10年）',
    premium_frequency  ENUM('ANNUAL','SEMI_ANNUAL','MONTHLY') NOT NULL DEFAULT 'ANNUAL' COMMENT '缴费频率',
    coverage_schema   JSON          NOT NULL COMMENT '保障模板（主险及附加险结构）',
    pricing_model     JSON          NOT NULL COMMENT '定价模型（费率、计算公式等）',
    underwriting_flow JSON          COMMENT '核保流程配置',
    status            ENUM('DRAFT','ACTIVE','RETIRED','PENDING') NOT NULL DEFAULT 'DRAFT' COMMENT '产品状态',
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


/* 【2.2】产品条款关联表 */
DROP TABLE IF EXISTS ic_product_clause;
CREATE TABLE ic_product_clause (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产品条款关联主键',
    product_config_id BIGINT        NOT NULL COMMENT '产品配置ID，关联 ic_product_config',
    clause_text       TEXT          NOT NULL COMMENT '条款内容',
    effective_date    DATE          NOT NULL COMMENT '条款生效日期',
    expire_date       DATE          COMMENT '条款失效日期',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_product_clause (product_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '产品条款关联';


-- ============================================================
-- 【3. 承保模块】
-- ============================================================

/* 【3.1】投保单 */
DROP TABLE IF EXISTS ic_proposal;
CREATE TABLE ic_proposal (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '提案主键',
    proposal_code         VARCHAR(32)   NOT NULL UNIQUE COMMENT '提案编号（INS+YYYYMMDD+8位序列）',
    product_config_id     BIGINT        NOT NULL COMMENT '产品配置ID',
    insured_type          ENUM('INDIVIDUAL','FAMILY','GROUP') NOT NULL COMMENT '投保类型：INDIVIDUAL-个人，FAMILY-家庭，GROUP-团体',
    policyholder_id       BIGINT        NOT NULL COMMENT '保单持有人ID，关联 ic_policyholder',
    proposal_channel      ENUM('ONLINE','OFFLINE') NOT NULL DEFAULT 'ONLINE' COMMENT '投保渠道',
    proposal_type         ENUM('NEW','RENEWAL','REINSTATEMENT') NOT NULL DEFAULT 'NEW' COMMENT '提案类型',
    proposal_status       ENUM('DRAFT','UNDERWRITING','APPROVED','REJECTED','WITHDRAWN') NOT NULL DEFAULT 'DRAFT' COMMENT '提案状态',
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


/* 【3.2】核保规则表 */
DROP TABLE IF EXISTS ic_underwriting_rule;
CREATE TABLE ic_underwriting_rule (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '核保规则主键',
    rule_code             VARCHAR(20)   NOT NULL UNIQUE COMMENT '规则编号（产品编码+规则类型+版本）',
    product_config_id     BIGINT        NOT NULL COMMENT '产品配置ID（业务关联）',
    rule_type             ENUM('AUTO','MANUAL','HYBRID') NOT NULL COMMENT '规则类型',
    underwriting_rules    JSON          COMMENT '核保规则（如拒保职业、疾病）',
    condition_expression  JSON          NOT NULL COMMENT '触发条件（JSON表达式）',
    action_expression     JSON          NOT NULL COMMENT '执行动作（JSON表达式）',
    priority              TINYINT UNSIGNED NOT NULL COMMENT '优先级（1-100）',
    version               VARCHAR(10)   NOT NULL COMMENT '规则版本',
    status                ENUM('ACTIVE','INACTIVE','DEPRECATED') NOT NULL DEFAULT 'ACTIVE' COMMENT '规则状态',
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


/* 【3.3】健康档案表 */
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
    UNIQUE KEY uniq_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '健康档案';


-- ============================================================
-- 【4. 保全模块】
-- ============================================================

/* 【4.1】保单 */
DROP TABLE IF EXISTS ic_policy;
CREATE TABLE ic_policy (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保单主键',
    policy_code         VARCHAR(32)   NOT NULL UNIQUE COMMENT '保单编号（POL+YYYYMMDD+8位序列）',
    proposal_id         BIGINT        NOT NULL COMMENT '投保单ID，关联 ic_proposal',
    product_config_id   BIGINT        NOT NULL COMMENT '产品配置ID（业务关联）',
    policyholder_id     BIGINT        NOT NULL COMMENT '保单持有人ID，关联 ic_policyholder',
    type                ENUM('INDIVIDUAL','GROUP') NOT NULL DEFAULT 'INDIVIDUAL' COMMENT '保单类型：INDIVIDUAL-个单，GROUP-团单',
    status              ENUM('ACTIVE','LAPSED','TERMINATED','ON_HOLD','CANCELLED') NOT NULL DEFAULT 'ACTIVE' COMMENT '保单状态',
    premium_amount      DECIMAL(12,2) NOT NULL COMMENT '年缴保费',
    quota_control_type  ENUM('STATIC','DYNAMIC') DEFAULT 'STATIC' COMMENT '额度控制类型',
    quota_reset_cycle   ENUM('NONE','YEARLY','HALF_YEAR') DEFAULT 'NONE' COMMENT '额度重置周期',
    total_quota         DECIMAL(18,2) COMMENT '保单总额度',
    used_quota          DECIMAL(18,2) COMMENT '已用额度',
    coverage_detail     JSON          NOT NULL COMMENT '保障详情（支持复杂结构）',
    effective_date      DATE          NOT NULL COMMENT '保单生效日期',
    expiry_date         DATE          NOT NULL COMMENT '保单到期日期',
    renewal_count       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '续保次数',
    pre_policy_id       BIGINT        DEFAULT NULL COMMENT '关联原保单ID',
    grace_period_days   INT DEFAULT 0 COMMENT '宽限期（天）',
    version             INT DEFAULT 0 COMMENT '保单版本',
    renewal_policy_id   BIGINT        DEFAULT NULL COMMENT '续保时关联原保单ID',
    remark              VARCHAR(255)  COMMENT '备注信息，记录特殊说明',
    source              VARCHAR(50)   COMMENT '数据来源（如系统录入、第三方导入）',
    last_sync_time      DATETIME(3)   COMMENT '上次与其他系统同步时间',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code   VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '保单创建时间',
    create_by           BIGINT        NOT NULL COMMENT '创建人',
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '保单更新时间',
    update_by           BIGINT        NOT NULL COMMENT '修改人',
    deleted             TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_policy_product (product_config_id, status),
    INDEX idx_policy_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保单';


/* 【4.2】保单被保险人关联表 */
DROP TABLE IF EXISTS ic_policy_insured;
CREATE TABLE ic_policy_insured (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保单被保险人关联主键',
    relate_id               BIGINT        NOT NULL COMMENT '关联单ID（关联投保单、保全单、续保单）',
    insured_type            ENUM('PROPOSAL','ENDORSEMENT','RENEWAL') NOT NULL DEFAULT 'PROPOSAL' COMMENT '关联单据类型',
    policy_id               BIGINT        NOT NULL COMMENT '保单ID',
    person_id               BIGINT        NOT NULL COMMENT '人员ID，关联 ic_insured_person',
    relationship            VARCHAR(50)   COMMENT '与主被保险人关系',
    is_main_insured         TINYINT       NOT NULL DEFAULT 0 COMMENT '是否主被保险人',
    coverage_detail         JSON          COMMENT '个人专属保障详情',
    individual_quota        DECIMAL(18,2) COMMENT '个人额度',
    used_individual_quota   DECIMAL(18,2) COMMENT '已用个人额度',
    tenant_id               BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code       VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by               BIGINT        NOT NULL COMMENT '创建人',
    update_time             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by               BIGINT        NOT NULL COMMENT '修改人',
    deleted                 TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_policy_person (policy_id, person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '保单被保险人关联';


/* 【4.3】保全操作表 */
DROP TABLE IF EXISTS ic_policy_endorsement;
CREATE TABLE ic_policy_endorsement (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '保全操作主键',
    endorsement_code  VARCHAR(20)   NOT NULL UNIQUE COMMENT '批单编号',
    policy_id         BIGINT        NOT NULL COMMENT '保单ID',
    proposal_id       BIGINT        NOT NULL COMMENT '关联投保单ID',
    endorsement_type  ENUM('BENEFICIARY_CHANGE','COVERAGE_ADJUST','RENEWAL','PREMIUM_CHANGE','POLICY_SUSPEND') NOT NULL COMMENT '保全操作类型',
    operation_source  ENUM('SYSTEM','AGENT','CUSTOMER') NOT NULL DEFAULT 'SYSTEM' COMMENT '操作来源',
    change_reason     VARCHAR(500)  COMMENT '变更原因',
    operation_data    JSON          NOT NULL COMMENT '变更内容',
    approval_status   ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '审批状态',
    effective_date    DATETIME(3)   NOT NULL COMMENT '操作生效日期',
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


/* 【4.4】续保记录表 */
DROP TABLE IF EXISTS ic_policy_renewal;
CREATE TABLE ic_policy_renewal (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '续保记录主键',
    original_policy_id  BIGINT        NOT NULL COMMENT '原保单ID',
    renewed_policy_id   BIGINT        NOT NULL COMMENT '续保后保单ID',
    proposal_id         BIGINT        NOT NULL COMMENT '关联投保单ID',
    renewal_type        ENUM('AUTO','MANUAL','FORCED') NOT NULL COMMENT '续保类型',
    premium_change      DECIMAL(10,2) NOT NULL COMMENT '保费变化金额',
    coverage_change     JSON          COMMENT '保障内容变化记录',
    renewal_date        DATE          NOT NULL COMMENT '续保日期',
    reason_code         VARCHAR(20)   COMMENT '续保原因代码',
    underwriting_flag   BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '是否重新核保',
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


/* 【4.5】保单额度变更记录表 */
DROP TABLE IF EXISTS ic_policy_quota_change;
CREATE TABLE ic_policy_quota_change (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    change_no         VARCHAR(32)   NOT NULL COMMENT '额度变更单号',
    policy_id         BIGINT        NOT NULL COMMENT '关联保单ID',
    person_id         BIGINT        COMMENT '人员ID,如保单团体公账无需指定个人',
    quota_type        ENUM('GROUP','INDIVIDUAL') NOT NULL COMMENT '额度类型：GROUP-团体，INDIVIDUAL-个人',
    previous_value    DECIMAL(18,2) NOT NULL COMMENT '变更前额度值',
    current_value     DECIMAL(18,2) NOT NULL COMMENT '变更后额度值',
    effective_date    DATETIME(3)   NOT NULL COMMENT '变更生效日期',
    approval_status   ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING' COMMENT '审批状态',
    risk_check_id     BIGINT        COMMENT '风控检查ID',
    change_type       ENUM('ALLOCATE','USE','ADJUST','RECOVER') COMMENT '变更类型',
    operator_role     ENUM('SYSTEM','AGENT','CUSTOMER') COMMENT '操作角色',
    change_reason     TEXT          COMMENT '变更原因',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uk_change_no (change_no),
    INDEX idx_policy_id (policy_id),
    INDEX idx_quota_type (quota_type),
    INDEX idx_effective_date (effective_date)
) ENGINE=InnoDB
COMMENT '保单额度变更记录';


-- ============================================================
-- 【6. 审计日志表】
-- ============================================================
DROP TABLE IF EXISTS ic_audit_log;
CREATE TABLE ic_audit_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计日志主键',
    user_id             BIGINT        COMMENT '操作用户ID',
    operation_type      VARCHAR(50)   COMMENT '操作类型',
    target_table        VARCHAR(50)   COMMENT '目标表',
    target_id           BIGINT        COMMENT '目标ID',
    before_state        JSON          COMMENT '变更前数据',
    after_state         JSON          COMMENT '变更后数据',
    ip_address          VARCHAR(45)   COMMENT '操作IP',
    device_fingerprint  VARCHAR(100)  COMMENT '操作设备指纹',
    geo_location        VARCHAR(100)  COMMENT '操作地理位置',
    session_id          VARCHAR(50)   COMMENT '会话ID',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code   VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    create_by           BIGINT        NOT NULL COMMENT '创建人',
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    update_by           BIGINT        NOT NULL COMMENT '修改人',
    deleted             TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '审计日志';


-- ============================================================
-- 【7. 交易模块】
-- ============================================================

/* 【7.1】商户 */
DROP TABLE IF EXISTS tr_merchant;
CREATE TABLE tr_merchant (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商户唯一ID',
    merchant_code    VARCHAR(50)   NOT NULL UNIQUE COMMENT '商户编码（规则：MCH_地区编码_序号）',
    name             VARCHAR(100)  NOT NULL COMMENT '商户全称',
    merchant_type    ENUM('INSURANCE','TPA','HOSPITAL','PHARMACY','HEALTH_SERVICE') NOT NULL COMMENT '商户类型',
    parent_id        BIGINT        COMMENT '上级机构ID',
    service_capability JSON       NOT NULL COMMENT '服务能力',
    settlement_config  JSON       NOT NULL COMMENT '结算配置',
    tier             ENUM('HEADQUARTER','BRANCH','SITE') NOT NULL COMMENT '机构层级',
    is_active        TINYINT       DEFAULT 1 COMMENT '是否启用',
    region_rules     JSON          COMMENT '区域规则配置',
    chain_config     JSON          COMMENT '连锁配置',
    is_headquarter   TINYINT GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(chain_config, '$.isheadquarter'))) VIRTUAL COMMENT '是否总部（0否1是）',
    service_type     ENUM('ONLINE','OFFLINE','OTO') NOT NULL COMMENT '门店类型',
    address          VARCHAR(255)  COMMENT '门店地址',
    contact_person   VARCHAR(100)  COMMENT '联系人',
    contact_phone    VARCHAR(50)   COMMENT '联系电话',
    latitude         DECIMAL(9,6)  COMMENT '纬度',
    longitude        DECIMAL(9,6)  COMMENT '经度',
    province         VARCHAR(100)  COMMENT '省份',
    city             VARCHAR(100)  COMMENT '城市',
    district         VARCHAR(100)  COMMENT '区县',
    create_time      DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by        BIGINT        NOT NULL COMMENT '创建人',
    update_time      DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by        BIGINT        NOT NULL COMMENT '修改人',
    deleted          TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_merchant_name (name),
    INDEX idx_merchant_code (merchant_code),
    INDEX idx_geo_location (province, city, district) COMMENT '地理区域索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '商户';


/* 【7.2】产品 */
DROP TABLE IF EXISTS tr_product;
CREATE TABLE tr_product (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '产品唯一ID',
    product_code   VARCHAR(50)   NOT NULL UNIQUE COMMENT '商品编码（规则：PROD_分类_序号）',
    product_name   VARCHAR(100)  NOT NULL COMMENT '商品名称',
    merchant_id    BIGINT        NOT NULL COMMENT '关联门店ID（关联商户表）',
    product_type   ENUM('DRUG','SERVICE','CHECKUP') NOT NULL COMMENT '商品类型',
    category_id    BIGINT        NOT NULL COMMENT '品类',
    pricing_model  JSON          NOT NULL COMMENT '定价策略',
    validity_period INT          COMMENT '有效期（天）',
    insurance_price DECIMAL(18,2) COMMENT '保险结算价',
    cash_price     DECIMAL(18,2)  COMMENT '现金支付价',
    min_purchase   INT DEFAULT 1 COMMENT '最小购买数量',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '产品';


/* 【7.3】品类 */
DROP TABLE IF EXISTS tr_category;
CREATE TABLE tr_category (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '品类唯一ID',
    category_code  VARCHAR(50)   NOT NULL UNIQUE COMMENT '品类编码',
    category_name  VARCHAR(100)  NOT NULL COMMENT '品类名称',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '品类';


/* 【7.4】商品 */
DROP TABLE IF EXISTS tr_goods;
CREATE TABLE tr_goods (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品唯一ID',
    product_id     BIGINT        NOT NULL COMMENT '产品ID',
    quantity       INT           NOT NULL COMMENT '购买数量',
    unit_price     DECIMAL(18,2) NOT NULL COMMENT '单价',
    total_price    DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_price) VIRTUAL COMMENT '小计金额',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '商品';


/* 【7.5】交易订单 */
DROP TABLE IF EXISTS tr_order;
CREATE TABLE tr_order (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单唯一ID',
    order_no               VARCHAR(50)   NOT NULL UNIQUE COMMENT '订单编号',
    order_type             ENUM('DIRECT_PAY','CLAIM','REFUND') NOT NULL COMMENT '订单类型',
    qr_code                VARCHAR(50)   COMMENT '二维码',
    merchant_id            BIGINT        NOT NULL COMMENT '商户ID',
    merchant_name          VARCHAR(100)  NOT NULL COMMENT '商户名称',
    total_amount           DECIMAL(18,2) NOT NULL COMMENT '订单总金额',
    currency               VARCHAR(3)    NOT NULL DEFAULT 'CNY' COMMENT '交易币种',
    payment_policy         JSON          NOT NULL COMMENT '支付策略',
    claim_evidence         JSON          COMMENT '理赔凭证',
    payment_status         ENUM('PARTIAL_PAID','FULLY_PAID','FAILED') DEFAULT 'PARTIAL_PAID' COMMENT '支付状态',
    settlement_status      ENUM('PENDING','COMPLETED','FAILED') DEFAULT 'PENDING' COMMENT '结算状态',
    payment_account        VARCHAR(50)   COMMENT '默认支付账号',
    original_order_no      VARCHAR(50)   COMMENT '原订单号（退款时使用）',
    policy_id              BIGINT        NOT NULL COMMENT '关联保单ID',
    user_id                BIGINT        NOT NULL COMMENT '用户ID',
    user_mobile            VARCHAR(20)   NOT NULL COMMENT '用户手机',
    version                INT DEFAULT 0 COMMENT '乐观锁版本号',
    payment_channel        ENUM('ALIPAY','WECHAT','BANK_TRANSFER','OTHER') NOT NULL COMMENT '支付渠道',
    third_party_trade_no   VARCHAR(50)   COMMENT '第三方支付流水号',
    bank_serial_no         VARCHAR(50)   COMMENT '银行流水号',
    clearing_date          DATE          COMMENT '清算日期',
    tenant_id              BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code      VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time            DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by              BIGINT        NOT NULL COMMENT '创建人',
    update_time            DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by              BIGINT        NOT NULL COMMENT '修改人',
    deleted                TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_settlement_status (settlement_status, create_time) COMMENT '结算状态+时间联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '交易订单';


/* 【7.6】订单明细 */
DROP TABLE IF EXISTS tr_order_item;
CREATE TABLE tr_order_item (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细唯一ID',
    order_id       BIGINT        NOT NULL COMMENT '订单ID（外键）',
    product_id     BIGINT        NOT NULL COMMENT '产品ID（外键）',
    goods_id       BIGINT        NOT NULL COMMENT '商品ID（外键）',
    quantity       INT           NOT NULL COMMENT '购买数量',
    unit_price     DECIMAL(18,2) NOT NULL COMMENT '单价',
    total_price    DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_price) VIRTUAL COMMENT '小计金额',
    insurance_covered DECIMAL(18,2) COMMENT '保险覆盖金额',
    cash_payment   DECIMAL(18,2) COMMENT '现金支付金额',
    refund_amount  DECIMAL(18,2) DEFAULT 0.00 COMMENT '退款金额',
    expire_time    DATETIME(6)   NOT NULL COMMENT '支付过期时间',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_order_goods(order_id, goods_id) COMMENT '订单-商品联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '订单明细';


/* 【7.7】消费规则 */
DROP TABLE IF EXISTS tr_consume_rule;
CREATE TABLE tr_consume_rule (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则唯一ID',
    rule_code      VARCHAR(50)   NOT NULL UNIQUE COMMENT '规则编码',
    rule_type      ENUM('WHITELIST','BLACKLIST') NOT NULL COMMENT '规则类型',
    rule_condition JSON          NOT NULL COMMENT '条件逻辑',
    rule_action    JSON          NOT NULL COMMENT '执行动作',
    priority       TINYINT       NOT NULL COMMENT '执行优先级',
    effective_time DATETIME      NOT NULL COMMENT '生效时间',
    expiration_time DATE         COMMENT '失效时间',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_rule_scope (tenant_id, biz_identity_code) COMMENT '租户+业务身份联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '消费规则';


/* 【7.8】规则执行日志 */
DROP TABLE IF EXISTS tr_rule_execution_log;
CREATE TABLE tr_rule_execution_log (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id       BIGINT        NOT NULL COMMENT '订单ID（外键）',
    rule_code      VARCHAR(50)   NOT NULL COMMENT '规则编码',
    decision       ENUM('ACCEPT','REJECT') NOT NULL COMMENT '决策结果',
    evidence       JSON          NOT NULL COMMENT '执行证据链',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_decision_audit (decision, create_time) COMMENT '决策结果+时间联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '规则执行日志';


-- ============================================================
-- 【8. 支付模块】
-- ============================================================
/* 【8.1】主资金账户 */
DROP TABLE IF EXISTS py_fund_account;
CREATE TABLE py_fund_account (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_no     VARCHAR(50)   NOT NULL UNIQUE COMMENT '账户编号',
    account_type   ENUM('MASTER','VIRTUAL','ESCROW') NOT NULL COMMENT '账户类型',
    currency       VARCHAR(3)    NOT NULL DEFAULT 'CNY' COMMENT '交易币种',
    init_balance   DECIMAL(18,2) NOT NULL COMMENT '初始额度',
    balance        DECIMAL(18,2) NOT NULL COMMENT '可用余额',
    frozen_balance DECIMAL(18,2) NOT NULL COMMENT '冻结金额',
    quota_policy   JSON          NOT NULL COMMENT '额度策略',
    risk_policy    JSON          NOT NULL COMMENT '风控策略',
    version        INT DEFAULT 0 COMMENT '乐观锁版本号',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '主资金账户';


/* 【8.2】虚拟账户 */
DROP TABLE IF EXISTS py_virtual_account;
CREATE TABLE py_virtual_account (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    va_no          VARCHAR(50)   NOT NULL UNIQUE COMMENT '虚拟账户编号',
    account_no     VARCHAR(50)   NOT NULL COMMENT '关联主账户编号',
    account_type   ENUM('DIRECT_PAY','TPA','HEALTH_SERVICE') NOT NULL COMMENT '账户类型',
    insurance_type ENUM('MEDICAL','DENTAL','VISION') NOT NULL COMMENT '保险类型',
    family_group_id BIGINT       COMMENT '所属家庭组',
    total_quota    DECIMAL(18,2) NOT NULL COMMENT '总额度',
    used_quota     DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已使用额度',
    daily_quota    DECIMAL(18,2) NOT NULL COMMENT '日限额',
    monthly_quota  DECIMAL(18,2) NOT NULL COMMENT '月限额',
    usage_rules    JSON          NOT NULL COMMENT '使用规则',
    last_reset_time DATETIME(3)  NOT NULL COMMENT '额度重置时间',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_va_quota (insurance_type, used_quota) COMMENT '险种+额度索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '虚拟账户';


/* 【8.3】资金流水 */
DROP TABLE IF EXISTS py_fund_ledger;
CREATE TABLE py_fund_ledger (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    ledger_no         VARCHAR(50)   NOT NULL UNIQUE COMMENT '流水编号',
    operation_type    ENUM('PAYMENT','REFUND','ADJUSTMENT','FEE','TRANSFER') NOT NULL COMMENT '操作类型',
    amount            DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    currency          VARCHAR(3)    NOT NULL DEFAULT 'CNY' COMMENT '交易币种',
    account_no        VARCHAR(50)   NOT NULL COMMENT '资金账户',
    related_biz_no    VARCHAR(50)   NOT NULL COMMENT '关联业务单号',
    original_ledger_no VARCHAR(50)  COMMENT '原始流水号',
    pre_balance       DECIMAL(18,2) NOT NULL COMMENT '交易前余额',
    post_balance      DECIMAL(18,2) NOT NULL COMMENT '交易后余额',
    transaction_fee   DECIMAL(18,2) DEFAULT 0.00 COMMENT '交易手续费',
    version           INT DEFAULT 0 COMMENT '乐观锁版本号',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time       DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_ledger_flow (operation_type, create_time) COMMENT '操作类型+时间索引',
    INDEX idx_original_ledger (original_ledger_no) COMMENT '冲正流水索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '资金流水';


/* 【8.4】支付异常 */
DROP TABLE IF EXISTS py_payment_exception;
CREATE TABLE py_payment_exception (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id       BIGINT        NOT NULL COMMENT '订单ID',
    error_code     VARCHAR(20)   NOT NULL COMMENT '错误类型编码',
    error_detail   JSON          NOT NULL COMMENT '错误详情',
    retry_policy   JSON          NOT NULL COMMENT '重试策略',
    retry_count    INT DEFAULT 0 COMMENT '已重试次数',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_error_code (error_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '支付异常';


/* 【8.5】健康福利卡 */
DROP TABLE IF EXISTS py_benefit_fund_card;
CREATE TABLE py_benefit_fund_card (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '福利卡唯一ID',
    card_no        VARCHAR(50)   NOT NULL UNIQUE COMMENT '卡号',
    card_type      ENUM('DIRECT_PAY_CARD','HEALTH_SERVICE_CARD') NOT NULL COMMENT '卡类型',
    person_id      BIGINT   NOT NULL COMMENT '持卡人唯一标识',
    quota          DECIMAL(18,2) NOT NULL COMMENT '卡额度',
    restriction_rule JSON         NOT NULL COMMENT '限制规则',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by      BIGINT        NOT NULL COMMENT '创建人',
    update_time    DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by      BIGINT        NOT NULL COMMENT '修改人',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_holder_incentives (person_id) COMMENT '持卡人索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '健康福利卡';


/* 【8.6】家庭共享模块 */
DROP TABLE IF EXISTS py_family_group;
CREATE TABLE py_family_group (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '唯一ID',
    group_no          VARCHAR(50)   NOT NULL UNIQUE COMMENT '家庭共享组编号',
    master_account_id BIGINT        NOT NULL COMMENT '主账户ID',
    sharing_mode      ENUM('FIXED','DYNAMIC') NOT NULL COMMENT '共享模式',
    reset_cycle       ENUM('MONTHLY','QUARTERLY','YEARLY') NOT NULL COMMENT '额度重置周期',
    next_reset_time   DATETIME(3)      NOT NULL COMMENT '下次重置时间',
    insurer_id        BIGINT        NOT NULL COMMENT '保险公司ID',
    employer_id       BIGINT        COMMENT '投保单位ID',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time       DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_insurer (insurer_id, employer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '家庭共享组';

DROP TABLE IF EXISTS py_family_config;
CREATE TABLE py_family_config (
    id                BIGINT PRIMARY KEY auto_increment COMMENT '唯一ID',
    config_level      ENUM('INSURER','EMPLOYER','POLICY') NOT NULL COMMENT '配置层级',
    related_id        BIGINT        NOT NULL COMMENT '关联ID',
    is_enabled        TINYINT       DEFAULT 0 COMMENT '是否启用',
    rules             JSON          NOT NULL COMMENT '配置规则',
    allow_override    TINYINT       DEFAULT 0 COMMENT '是否允许下级覆盖',
    version           INT DEFAULT 0 COMMENT '版本号',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time       DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_config (config_level, related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '家庭配置表';

DROP TABLE IF EXISTS py_family_member;
CREATE TABLE py_family_member (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '唯一ID',
    family_group_id   BIGINT        COMMENT '所属家庭组ID',
    master_account_id BIGINT        NOT NULL COMMENT '主账户ID',
    person_id         BIGINT        NOT NULL COMMENT '人员ID，关联 ic_insured_person',
    relation_type     VARCHAR(20)   NOT NULL COMMENT '关系类型',
    quota_ratio       DECIMAL(5,2)  NOT NULL COMMENT '额度分摊比例',
    start_date        DATE          NOT NULL COMMENT '生效日期',
    end_date          DATE          COMMENT '失效日期',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32)   NOT NULL COMMENT '业务身份',
    create_time       DATETIME(3)   NOT NULL COMMENT '创建时间',
    create_by         BIGINT        NOT NULL COMMENT '创建人',
    update_time       DATETIME(3)   NOT NULL COMMENT '修改时间',
    update_by         BIGINT        NOT NULL COMMENT '修改人',
    deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT '家庭成员';

-- ============================================================
-- 结束
-- ============================================================