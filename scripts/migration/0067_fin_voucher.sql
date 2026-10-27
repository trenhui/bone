-- 0067_fin_voucher.sql
-- 会计引擎（bone-accounting-engine / voucherGenerateHandler）配置前提：物理表 + 凭证模板/科目映射种子。
--
-- 依赖：bone-accounting-engine 已在 bone-app-expense / bone-app-purchase 引用（自动装配 voucherGenerateHandler）。
-- 接入姿态（doc/design/modules/12 §3/§4）：业务模块仅在动作定义声明 handler_bean=voucherGenerateHandler，
--           业务终态（confirmPayout / pay）后由 generateVoucher 动作触发；差异全在下方配置数据。
--
-- 实体元数据建模（meta_entity / meta_field）按「与报销同机制」的建模通道完成（场景模板 0051 同款，或
--   e2e 动态建模）——本脚本只负责物理表与配置数据种子，避免双真源。VoucherGenerateHandler 经
--   runtimeEntityCatalog.findPublishedRuntime(fin_*) 取物理表名，故联调/上线前须确保 fin_* 实体已发布。
--
-- ⚠ L3 待架构师审批执行；物理表 SDK 标准列（tenant_id/审计列/deleted）与字段类型需与 bone-metadata-sdk
--   列约定最终复核；以下科目编码（5601/1002/2202/2221/6603…）为示例口径，待财务确认后替换。

-- ============================ 物理表 ============================

CREATE TABLE IF NOT EXISTS fin_voucher (
    id            BIGINT       NOT NULL,
    tenant_id     BIGINT       NOT NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    voucher_type  VARCHAR(20)  NOT NULL,
    source_entity_code VARCHAR(64) NOT NULL,
    source_record_id    BIGINT NOT NULL,
    source_action       VARCHAR(32) NULL,
    source_trigger_id   VARCHAR(64) NULL,
    template_code       VARCHAR(64) NULL,
    template_version    VARCHAR(32) NULL,
    business_date       DATE    NULL,
    posting_date        DATE    NULL,
    period              VARCHAR(7) NULL,
    ledger_code         VARCHAR(32) NOT NULL DEFAULT 'MAIN',
    coa_code            VARCHAR(32) NOT NULL DEFAULT '*',
    base_currency       VARCHAR(8)  NULL,
    currency            VARCHAR(8)  NULL,
    total_debit         DECIMAL(20,6) NOT NULL DEFAULT 0,
    total_credit        DECIMAL(20,6) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NULL,
    voucher_no          VARCHAR(64) NULL,
    remark              VARCHAR(255) NULL,
    PRIMARY KEY (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fin_voucher_entry (
    id            BIGINT       NOT NULL,
    tenant_id     BIGINT       NOT NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    voucher_id    BIGINT       NOT NULL,
    line_no       INT          NOT NULL,
    direction     VARCHAR(8)   NOT NULL,
    account_code  VARCHAR(64)  NOT NULL,
    amount        DECIMAL(20,6) NOT NULL DEFAULT 0,
    currency      VARCHAR(8)   NULL,
    exchange_rate DECIMAL(20,6) NOT NULL DEFAULT 1,
    base_amount   DECIMAL(20,6) NOT NULL DEFAULT 0,
    summary       VARCHAR(255) NULL,
    settle_no     VARCHAR(64)  NULL,
    settle_mode   VARCHAR(32)  NULL,
    PRIMARY KEY (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fin_voucher_entry_dim (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    entry_id        BIGINT       NOT NULL,
    dimension_code  VARCHAR(32)  NOT NULL,
    dimension_value VARCHAR(128) NULL,
    PRIMARY KEY (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fin_voucher_template (
    id                BIGINT       NOT NULL,
    tenant_id         BIGINT       NOT NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME     NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME     NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    template_code     VARCHAR(64)  NOT NULL,
    template_version  VARCHAR(32)  NOT NULL DEFAULT '1.0',
    source_entity_code VARCHAR(64) NOT NULL,
    source_action     VARCHAR(32)  NOT NULL DEFAULT '*',
    line_rules        LONGTEXT     NOT NULL,
    rounding_diff_account VARCHAR(64) NULL,
    ledger_rule       VARCHAR(64)  NOT NULL DEFAULT 'MAIN',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fin_account_mapping (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    mapping_key     VARCHAR(128) NOT NULL,
    coa_code        VARCHAR(32)  NOT NULL DEFAULT '*',
    ledger_code     VARCHAR(32)  NOT NULL DEFAULT '*',
    biz_field_value VARCHAR(128) NOT NULL DEFAULT '*',
    account_code    VARCHAR(64)  NOT NULL,
    priority        INT          NOT NULL DEFAULT 0,
    effective_from  DATE         NULL,
    effective_to    DATE         NULL,
    PRIMARY KEY (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================ 凭证模板种子 ============================
-- 口径对齐 VoucherGenerateHandlerTest（业财一体方案 §11）：
--   报销：CHILD:items 按 expense_category.code 映射 DEBIT×N + 专票进项税(condition) + HEADER CREDIT(expense_reimbursement.channel)
--   采购：CHILD:items 按 purchase_category.code 映射 DEBIT(应付/成本) + HEADER CREDIT(purchase_order.channel 银行/应付)

INSERT INTO fin_voucher_template
    (id, tenant_id, template_code, template_version, source_entity_code, source_action, line_rules, rounding_diff_account, ledger_rule, status)
VALUES
    (67001, 0, 'EXP_REIMBURSE', '1.0', 'expense_reimbursement', 'confirmPayout',
     '[{"direction":"DEBIT","scope":"CHILD:items","amount_formula":"items.amount","account_source":"expense_category.code","summary_template":"{applicant} 报销","dim_defaults":[{"dimension_code":"DEPT","value_field":"department"}]},{"direction":"DEBIT","scope":"CHILD:items","condition":{"child":"items","field":"invoice_type","op":"eq","value":"SPECIAL"},"amount_formula":"items.amount * 0.13","account_source":"expense_reimbursement_item.invoice_type","summary_template":"进项税"},{"direction":"CREDIT","scope":"HEADER","amount_formula":"header.amount","account_source":"expense_reimbursement.channel","summary_template":"报销打款"}]',
     '6603', 'MAIN', 'ACTIVE'),
    (67002, 0, 'PUR_ORDER_PAY', '1.0', 'purchase_order', 'pay',
     '[{"direction":"DEBIT","scope":"HEADER","amount_formula":"header.amount","account_source":"purchase_category.code","summary_template":"采购付款"},{"direction":"CREDIT","scope":"HEADER","amount_formula":"header.amount","account_source":"purchase_order.channel","summary_template":"银行/应付"}]',
     '6603', 'MAIN', 'ACTIVE');

-- ============================ 科目映射种子（示例口径，待财务确认） ============================
-- mapping_key = 来源实体.字段（与模板 account_source 对齐）；biz_field_value='*' 为兜底。
INSERT INTO fin_account_mapping
    (id, tenant_id, mapping_key, coa_code, ledger_code, biz_field_value, account_code, priority)
VALUES
    -- 报销：费用类别 → 费用科目
    (67011, 0, 'expense_category.code', '*', '*', 'TRAVEL',   '560101', 0),
    (67012, 0, 'expense_category.code', '*', '*', 'MEAL',     '560102', 0),
    (67013, 0, 'expense_category.code', '*', '*', 'OFFICE',   '560103', 0),
    (67014, 0, 'expense_category.code', '*', '*', 'COMM',     '560104', 0),
    (67015, 0, 'expense_category.code', '*', '*', 'OTHER',    '560199', 0),
    -- 报销：打款渠道 → 资产/银行存款科目
    (67016, 0, 'expense_reimbursement.channel', '*', '*', 'BANK',    '100201', 0),
    (67017, 0, 'expense_reimbursement.channel', '*', '*', 'CASH',    '100101', 0),
    -- 报销：专票进项税
    (67018, 0, 'expense_reimbursement_item.invoice_type', '*', '*', 'SPECIAL', '222101', 0),
    -- 采购：采购类别 → 存货/成本科目
    (67021, 0, 'purchase_category.code', '*', '*', '*', '140501', 0),
    -- 采购：付款渠道 → 银行/应付
    (67022, 0, 'purchase_order.channel', '*', '*', 'BANK', '100201', 0),
    (67023, 0, 'purchase_order.channel', '*', '*', 'ACCEPTANCE', '220201', 0),
    (67024, 0, 'purchase_order.channel', '*', '*', 'WECHAT', '100201', 0);
