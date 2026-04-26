-- 保单表 (ia_policy)
DROP TABLE IF EXISTS ia_policy;
CREATE TABLE ia_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    external_policy_no VARCHAR(50) COMMENT '保司保单号',
    policy_no VARCHAR(50) COMMENT '普康保单号',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '保单信息';

-- 保险计划表 (ia_plan)
DROP TABLE IF EXISTS ia_plan;
CREATE TABLE ia_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    policy_no VARCHAR(50) COMMENT '内部保单号',
    plan_name VARCHAR(100) COMMENT '计划名称',
    plan_code VARCHAR(50) COMMENT '计划代码',
    plan_limit INTEGER COMMENT '计划额度',
    version VARCHAR(16) COMMENT '计划版本',
    status VARCHAR(16) COMMENT '计划版本状态',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '保险计划信息';

-- 险种表 (ia_coverage)
DROP TABLE IF EXISTS ia_coverage;
CREATE TABLE ia_coverage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    policy_no VARCHAR(50) COMMENT '内部保单号',
    plan_id BIGINT COMMENT '计划ID',
    coverage_name VARCHAR(100) COMMENT '险种名称',
    coverage_code VARCHAR(50) COMMENT '险种代码',
    coverage_limit DECIMAL(10, 2) COMMENT '险种额度',
    version VARCHAR(16) COMMENT '计划版本',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '保险险种信息';


-- 责任表 (ia_liability)
DROP TABLE IF EXISTS ia_liability;
CREATE TABLE ia_liability (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    uuid BIGINT COMMENT 'UUID',
    policy_no VARCHAR(50) COMMENT '内部保单号',
    plan_id BIGINT COMMENT '计划ID',
    coverage_id BIGINT COMMENT '险种ID',
    liability_code VARCHAR(50) COMMENT '责任编码',
    liability_name VARCHAR(100) COMMENT '责任名称',
    liability_type VARCHAR(50) COMMENT '责任类型',
    version VARCHAR(16) COMMENT '计划版本',
    next_liability_id BIGINT COMMENT '后付责任ID',
    next_liability_type VARCHAR(16) COMMENT '后付责任类型',
    invoice_relate_able BOOLEAN COMMENT '能否被设置为发票关联责任',
    restrict_object JSON COMMENT '适用对象',
    restrict_scope JSON COMMENT '适用限定',
    restrict_out_insure JSON COMMENT '适用出险',
    waiting_period INTEGER COMMENT '等待期',
    pay_percent JSON COMMENT '赔付比例',
    liability_deduct JSON COMMENT '责任免赔',
    times_limit JSON COMMENT '次日限额',
    account_type VARCHAR(16) COMMENT '责任账户类型',
    quota_controller JSON COMMENT '控款方',
    insurance_quota_type VARCHAR(64) COMMENT '保额类型',
    liability_limit JSON COMMENT '责任额度',
    adjustment_rule JSON COMMENT '理算规则',
    formula VARCHAR(512) COMMENT '理算公式',
    remark VARCHAR(512) COMMENT '额外记录',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '保险责任信息';


-- 责任共保表 (ia_liability_sharing)
DROP TABLE IF EXISTS ia_liability_sharing;
CREATE TABLE ia_liability_sharing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    policy_no VARCHAR(50) COMMENT '内部保单号',
    plan_id BIGINT COMMENT '计划ID',
    share_id VARCHAR(50) COMMENT '责任共保ID',
    share_code VARCHAR(50) COMMENT '责任共保代码',
    share_limit INTEGER COMMENT '责任共保额度',
    version VARCHAR(16) COMMENT '计划版本',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '责任共保信息';


-- 责任共保关系表 (ia_liability_sharing_relation)
DROP TABLE IF EXISTS ia_liability_sharing_relation;
CREATE TABLE ia_liability_sharing_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    policy_no VARCHAR(50) COMMENT '内部保单号',
    plan_id BIGINT COMMENT '计划ID',
    liability_uuid BIGINT COMMENT '责任UUID',
    share_code VARCHAR(50) COMMENT '责任共保代码',
    version VARCHAR(16) COMMENT '计划版本',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '责任共保信息';


-- 理算明细表 (ia_adjustment_detail)
DROP TABLE IF EXISTS ia_adjustment_detail;
CREATE TABLE ia_adjustment_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    claim_id BIGINT COMMENT '关联赔案ID',
    invoice_id BIGINT COMMENT '发票ID',
    invoice_no VARCHAR(50) COMMENT '发票号',
    liability_uuid BIGINT COMMENT '责任UUID',
    version VARCHAR(16) COMMENT '计划版本',
    account_type VARCHAR(32) COMMENT '责任账户类型',
    deduct_type VARCHAR(50) COMMENT '免赔方式',
    deduct_amount DECIMAL(10, 2) COMMENT '免赔额',
    liability_quota DECIMAL(10, 2) COMMENT '责任额度',
    payout_amount DECIMAL(10, 2) COMMENT '赔付金额',
    formula VARCHAR(512) COMMENT '理算公式',
    invoice_result VARCHAR(128) COMMENT '发票结论',
    result_detail VARCHAR(128) COMMENT '结论明细',
    operator_id BIGINT COMMENT '操作人员ID',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '理算明细信息';


-- 理算裁定结果表 (ia_adjustment_result)
DROP TABLE IF EXISTS ia_adjustment_result;
CREATE TABLE ia_adjustment_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    claim_id BIGINT COMMENT '关联赔案ID',
    payout_amount DECIMAL(10, 2) COMMENT '赔付金额',
    public_amount DECIMAL(10, 2) COMMENT '公账赔付金额',
    individual_amount DECIMAL(10, 2) COMMENT '个账赔付金额',
    result VARCHAR(128) COMMENT '理算结论',
    result_detail VARCHAR(128) COMMENT '结论明细',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '理算裁定结果';


-- 计划版本操作记录表 (ia_plan_version_log)
DROP TABLE IF EXISTS ia_plan_version_log;
CREATE TABLE ia_plan_version_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    policy_no VARCHAR(50) COMMENT '内部保单号',
    plan_id BIGINT COMMENT '关联计划ID',
    update_target VARCHAR(64) COMMENT '更新对象',
    former_version VARCHAR(16) COMMENT '更新前版本',
    after_version VARCHAR(16) COMMENT '更新后版本',
    remark VARCHAR(512) COMMENT '更新说明',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '理算裁定结果';



-- 理算资金预警信息表 (ia_adjustment_fund_alert)
DROP TABLE IF EXISTS ia_adjustment_fund_alert;
CREATE TABLE ia_adjustment_fund_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    claim_id BIGINT COMMENT '关联赔案ID',
    claim_no VARCHAR(50) COMMENT '赔案号',
    invoice_id BIGINT COMMENT '发票ID',
    invoice_no VARCHAR(50) COMMENT '发票号',
    liability_uuid BIGINT COMMENT '责任UUID',
    version VARCHAR(16) COMMENT '计划版本',
    alert_message VARCHAR(1024) COMMENT '预警信息',
    operator_id BIGINT COMMENT '操作人员ID',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(50) COMMENT '业务身份ID',
    create_time TIMESTAMP COMMENT '创建时间',
    create_by BIGINT COMMENT '创建人ID',
    update_time TIMESTAMP COMMENT '修改时间',
    update_by BIGINT COMMENT '修改人ID',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志'
) COMMENT '理算资金预警信息';
