-- ============================================================
-- 交易模块
-- ============================================================

-- 清理历史数据
DROP TABLE IF EXISTS tr_merchant;
DROP TABLE IF EXISTS tr_product;
DROP TABLE IF EXISTS tr_category;
DROP TABLE IF EXISTS tr_item;
DROP TABLE IF EXISTS tr_order;
DROP TABLE IF EXISTS tr_order_item;
DROP TABLE IF EXISTS tr_consume_rule;
DROP TABLE IF EXISTS tr_rule_execution_log;

-- 1. 商户
CREATE TABLE tr_merchant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商户唯一ID',
    merchant_code VARCHAR(50) NOT NULL UNIQUE COMMENT '商户编码（规则：MCH_地区编码_序号）',
    name VARCHAR(100) NOT NULL COMMENT '商户全称',
    merchant_type ENUM('INSURANCE','TPA','HOSPITAL','PHARMACY','HEALTH_SERVICE') NOT NULL COMMENT '商户类型',
    parent_id BIGINT COMMENT '上级机构ID',
    service_capability JSON NOT NULL COMMENT '服务能力',
    settlement_config JSON NOT NULL COMMENT '结算配置',
    tier ENUM('HEADQUARTER','BRANCH','SITE') NOT NULL COMMENT '机构层级',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    region_rules JSON COMMENT '区域规则配置',
    chain_config JSON COMMENT '连锁配置',
    is_headquarter TINYINT GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(chain_config, '$.isHeadquarter'))) VIRTUAL COMMENT '是否总部（0否1是）',
    service_type ENUM('ONLINE', 'OFFLINE', 'OTO') NOT NULL COMMENT '门店类型',
    address VARCHAR(255) COMMENT '门店地址',
    contact_person VARCHAR(100) COMMENT '联系人',
    contact_phone VARCHAR(50) COMMENT '联系电话',
    latitude DECIMAL(9,6) COMMENT '纬度',
    longitude DECIMAL(9,6) COMMENT '经度',
    province VARCHAR(100) COMMENT '省份',
    city VARCHAR(100) COMMENT '城市',
    district VARCHAR(100) COMMENT '区县',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_merchant_name (name),
    INDEX idx_merchant_code (merchant_code),
    INDEX idx_geo_location (province, city, district) COMMENT '地理区域索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '商户';

-- 2. 产品
CREATE TABLE tr_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品唯一ID',
    product_code VARCHAR(50) NOT NULL UNIQUE COMMENT '商品编码（规则：PROD_分类_序号）',
    product_name VARCHAR(100) NOT NULL COMMENT '商品名称',
    merchant_id BIGINT NOT NULL COMMENT '关联门店ID（关联商户表）',
    product_type ENUM('DRUG', 'SERVICE', 'CHECKUP') NOT NULL COMMENT '商品类型',
    category_id BIGINT NOT NULL COMMENT '品类',
    pricing_model JSON NOT NULL COMMENT '定价策略',
    validity_period INT COMMENT '有效期（天）',
    insurance_price DECIMAL(18,2) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(pricing_model, '$.insurance.basePrice'))) VIRTUAL COMMENT '保险结算价',
    cash_price DECIMAL(18,2) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(pricing_model, '$.cashPrice'))) VIRTUAL COMMENT '现金支付价',
    min_purchase INT DEFAULT 1 COMMENT '最小购买数量',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '产品';

-- 3. 品类
CREATE TABLE tr_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '品类唯一ID',
    category_code VARCHAR(50) NOT NULL UNIQUE COMMENT '品类编码',
    category_name VARCHAR(100) NOT NULL COMMENT '品类名称',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '品类';

-- 4. 商品（订单商品）
CREATE TABLE tr_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单商品唯一ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    quantity INT NOT NULL COMMENT '购买数量',
    unit_price DECIMAL(18,2) NOT NULL COMMENT '单价',
    total_price DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_price) VIRTUAL COMMENT '小计金额',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '订单商品';

-- 5. 交易订单
CREATE TABLE tr_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单唯一ID',
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单编号',
    order_type ENUM('DIRECT_PAY', 'CLAIM', 'REFUND') NOT NULL COMMENT '订单类型',
    qr_code VARCHAR(50) COMMENT '二维码',
    merchant_id BIGINT NOT NULL COMMENT '商户ID',
    merchant_name VARCHAR(100) NOT NULL COMMENT '商户名称',
    total_amount DECIMAL(18,2) NOT NULL COMMENT '订单总金额',
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '交易币种',
    payment_policy JSON NOT NULL COMMENT '支付策略',
    claim_evidence JSON COMMENT '理赔凭证',
    payment_status ENUM('PARTIAL_PAID','FULLY_PAID','FAILED') DEFAULT 'PARTIAL_PAID' COMMENT '支付状态',
    settlement_status ENUM('PENDING','COMPLETED','FAILED') DEFAULT 'PENDING' COMMENT '结算状态',
    payment_account VARCHAR(50) COMMENT '默认支付账号',
    original_order_no VARCHAR(50) COMMENT '原订单号（退款时使用）',
    policy_id BIGINT NOT NULL COMMENT '关联保单ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    user_mobile VARCHAR(20) NOT NULL COMMENT '用户手机',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_settlement_status (settlement_status, create_time) COMMENT '结算状态+时间联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '交易订单';

-- 6. 订单明细
CREATE TABLE tr_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细唯一ID',
    order_id BIGINT NOT NULL COMMENT '订单ID（外键）',
    product_id BIGINT NOT NULL COMMENT '产品ID（外键）',
    item_id BIGINT NOT NULL COMMENT '商品ID（外键）',
    quantity INT NOT NULL COMMENT '购买数量',
    unit_price DECIMAL(18,2) NOT NULL COMMENT '单价',
    total_price DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_price) VIRTUAL COMMENT '小计金额',
    insurance_covered DECIMAL(18,2) COMMENT '保险覆盖金额',
    cash_payment DECIMAL(18,2) COMMENT '现金支付金额',
    refund_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '退款金额',
    expire_time DATETIME(6) NOT NULL COMMENT '支付过期时间',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_order_product (order_id, product_id) COMMENT '订单-商品联合索引',
    CONSTRAINT chk_refund CHECK (refund_amount <= total_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '订单明细';

-- 7. 消费规则
CREATE TABLE tr_consume_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则唯一ID',
    rule_code VARCHAR(50) NOT NULL UNIQUE COMMENT '规则编码',
    rule_type ENUM('WHITELIST', 'BLACKLIST') NOT NULL COMMENT '规则类型',
    rule_condition JSON NOT NULL COMMENT '条件逻辑',
    rule_action JSON NOT NULL COMMENT '执行动作',
    priority TINYINT NOT NULL COMMENT '执行优先级',
    effective_time DATETIME NOT NULL COMMENT '生效时间',
    expiration_time DATETIME COMMENT '失效时间',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_rule_scope (tenant_id, biz_identity_code) COMMENT '租户+业务身份联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '消费规则';

-- 8. 规则执行日志
CREATE TABLE tr_rule_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志唯一ID',
    order_id BIGINT NOT NULL COMMENT '订单ID（外键）',
    rule_code VARCHAR(50) NOT NULL COMMENT '规则编码',
    decision ENUM('ACCEPT', 'REJECT') NOT NULL COMMENT '决策结果',
    evidence JSON NOT NULL COMMENT '执行证据链',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_decision_audit (decision, create_time) COMMENT '决策结果+时间联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '消费规则执行日志';

-- ============================================================
-- 支付模块
-- ============================================================
DROP TABLE IF EXISTS py_fund_account;
DROP TABLE IF EXISTS py_virtual_account;
DROP TABLE IF EXISTS py_fund_ledger;
DROP TABLE IF EXISTS py_payment_exception;
DROP TABLE IF EXISTS py_benefit_fund_card;
DROP TABLE IF EXISTS py_family_group;
DROP TABLE IF EXISTS py_family_config;
DROP TABLE IF EXISTS py_family_member;

-- 1. 主资金账户
CREATE TABLE py_fund_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资金账户唯一ID',
    account_no VARCHAR(50) NOT NULL UNIQUE COMMENT '账户编号',
    account_type ENUM('MASTER','VIRTUAL','ESCROW') NOT NULL COMMENT '账户类型',
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '交易币种',
    init_balance DECIMAL(18,2) NOT NULL COMMENT '初始额度',
    balance DECIMAL(18,2) NOT NULL COMMENT '可用余额',
    frozen_balance DECIMAL(18,2) NOT NULL COMMENT '冻结金额',
    quota_policy JSON NOT NULL COMMENT '额度策略',
    risk_policy JSON NOT NULL COMMENT '风控策略',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '主资金账户';

-- 2. 虚拟账户
CREATE TABLE py_virtual_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '虚拟账户唯一ID',
    va_no VARCHAR(50) NOT NULL UNIQUE COMMENT '虚拟账户编号',
    account_no VARCHAR(50) NOT NULL COMMENT '关联主账户编号',
    account_type ENUM('DIRECT_PAY', 'TPA', 'HEALTH_SERVICE') NOT NULL COMMENT '账户类型',
    insurance_type ENUM('MEDICAL', 'DENTAL', 'VISION') NOT NULL COMMENT '保险类型',
    family_group_id BIGINT COMMENT '所属家庭组',
    total_quota DECIMAL(18,2) NOT NULL COMMENT '总额度',
    used_quota DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已使用额度',
    daily_quota DECIMAL(18,2) NOT NULL COMMENT '日限额',
    monthly_quota DECIMAL(18,2) NOT NULL COMMENT '月限额',
    usage_rules JSON NOT NULL COMMENT '使用规则',
    last_reset_time DATETIME NOT NULL COMMENT '额度重置时间',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_va_quota (insurance_type, used_quota) COMMENT '险种+额度索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '虚拟账户';

-- 3. 资金流水
CREATE TABLE py_fund_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资金流水唯一ID',
    ledger_no VARCHAR(50) NOT NULL UNIQUE COMMENT '流水编号',
    operation_type ENUM('PAYMENT', 'REFUND', 'ADJUSTMENT', 'FEE', 'TRANSFER') NOT NULL COMMENT '操作类型',
    amount DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '交易币种',
    account_no VARCHAR(50) NOT NULL COMMENT '资金账户',
    related_biz_no VARCHAR(50) NOT NULL COMMENT '关联业务单号',
    original_ledger_no VARCHAR(50) COMMENT '原始流水号',
    pre_balance DECIMAL(18,2) NOT NULL COMMENT '交易前余额',
    post_balance DECIMAL(18,2) NOT NULL COMMENT '交易后余额',
    transaction_fee DECIMAL(18,2) DEFAULT 0.00 COMMENT '交易手续费',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_ledger_flow (operation_type, create_time) COMMENT '操作类型+时间索引',
    INDEX idx_original_ledger (original_ledger_no) COMMENT '冲正流水索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '资金流水';

-- 4. 支付异常
CREATE TABLE py_payment_exception (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    error_code VARCHAR(20) NOT NULL COMMENT '错误类型编码',
    error_detail JSON NOT NULL COMMENT '错误详情',
    retry_policy JSON NOT NULL COMMENT '重试策略',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    version INT DEFAULT 0,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_error_code(error_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '支付异常';

-- 5. 健康福利卡
CREATE TABLE py_benefit_fund_card (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '福利卡唯一ID',
    card_no VARCHAR(50) NOT NULL UNIQUE COMMENT '卡号',
    card_type ENUM('DIRECT_PAY_CARD', 'HEALTH_SERVICE_CARD') NOT NULL COMMENT '卡类型',
    holder_id VARCHAR(50) NOT NULL COMMENT '持卡人唯一标识',
    quota DECIMAL(18,2) NOT NULL COMMENT '卡额度',
    restriction_rule JSON NOT NULL COMMENT '限制规则',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_holder_incentives (holder_id) COMMENT '持卡人索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '健康福利卡';

-- 6. 家庭共享额度模型
-- 家庭共享主表
CREATE TABLE py_family_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_no VARCHAR(50) NOT NULL UNIQUE COMMENT '家庭共享组编号',
    master_account_id BIGINT NOT NULL COMMENT '主账户ID',
    sharing_mode ENUM('FIXED','DYNAMIC') NOT NULL,
    reset_cycle ENUM('MONTHLY','QUARTERLY','YEARLY') NOT NULL,
    next_reset_time DATETIME NOT NULL,
    version INT DEFAULT 0,
    insurer_id BIGINT NOT NULL COMMENT '保司ID',
    employer_id BIGINT COMMENT '投保单位ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_insurer (insurer_id, employer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '家庭共享组';

-- 多级配置表
CREATE TABLE py_family_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_level ENUM('INSURER','EMPLOYER','POLICY') NOT NULL,
    related_id BIGINT NOT NULL COMMENT '关联ID',
    is_enabled TINYINT DEFAULT 0,
    rules JSON NOT NULL COMMENT '配置规则',
    allow_override TINYINT DEFAULT 0 COMMENT '是否允许下级覆盖',
    version INT DEFAULT 0,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY uniq_config (config_level, related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '多级配置表';

-- 家庭成员关系表
CREATE TABLE py_family_member (
    id BIGINT PRIMARY KEY,
    family_group_id BIGINT COMMENT '所属家庭组',
    master_account_id BIGINT NOT NULL,
    relation_type VARCHAR(20) NOT NULL,
    quota_ratio DECIMAL(5,2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '家庭成员';

-- ============================================================
-- 风控与监控体系
-- ============================================================
DROP TABLE IF EXISTS py_family_quota_alert_rules;
DROP TABLE IF EXISTS py_family_audit_log;

-- 实时预警规则
CREATE TABLE py_family_quota_alert_rules (
    id BIGINT PRIMARY KEY,
    insurer_id BIGINT NOT NULL,
    threshold DECIMAL(5,2) COMMENT '额度使用百分比阈值',
    notify_channels JSON COMMENT '通知渠道',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 家庭共享操作审计日志
CREATE TABLE py_family_audit_log (
    log_id BIGINT PRIMARY KEY,
    action_type ENUM('CONFIG_UPDATE','QUOTA_ADJUST','MEMBER_CHANGE'),
    target_level ENUM('INSURER','EMPLOYER','POLICY'),
    old_value JSON,
    new_value JSON,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    biz_identity_code VARCHAR(32) NOT NULL COMMENT '业务身份',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    create_by BIGINT NOT NULL COMMENT '创建人',
    update_time DATETIME(3) NOT NULL COMMENT '修改时间',
    update_by BIGINT NOT NULL COMMENT '修改人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT = '家庭共享操作审计';
