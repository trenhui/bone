-- =============================================================================
-- 0032_runtime_action_spi.sql
--
-- 目的（低代码 Runtime 数据面 §4.7 / cases 费用报销全链路）：落地「动作 SPI」所需
-- 的元数据表，使已发布的 RUNTIME 实体可以声明「状态机动作」（如报销的提交/审批/打款），
-- 经统一 HTTP 端点驱动状态流转，而非让调用方用 PUT 直写 status 绕过状态机（G14）。
--
-- 设计
-- ----------------------
-- · meta_entity_action_def：动作定义（平台/租户级共享，tenant_id 复用既有多租户隔离）。
--   - from_status / to_status：状态机边（from_status 为 NULL 表示「任意状态均可触发」）。
--   - status_field：被流转的状态字段 code（默认 'status'，支持多状态字段实体）。
--   - is_escape / handler_bean：逃逸动作（有副作用，如调外部支付）的 Spring bean 名。
--   - params_schema：动作入参 JSON Schema（前端据此渲染表单，后端据此校验必填）。
--   - requires_idempotency：资金类动作需调用方带 Idempotency-Key，防重放二次记账。
-- · meta_runtime_action_execution：动作执行日志（幂等去重 + RC-009 记录级变更审计）。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0032_runtime_action_spi.sql
-- 前置：t_expense_claim 已存在（0031 模板实例化产物）；本脚本只增表 + 种子，不删不改旧数据。
-- =============================================================================

USE bone;

-- ── 1. 动作定义表 ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS meta_entity_action_def (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         BIGINT       NOT NULL DEFAULT 0,
  entity_code       VARCHAR(128) NOT NULL,
  action_code       VARCHAR(128) NOT NULL,
  label             VARCHAR(128) NOT NULL,
  from_status       VARCHAR(64)  DEFAULT NULL,
  to_status         VARCHAR(64)  DEFAULT NULL,
  status_field      VARCHAR(64)  NOT NULL DEFAULT 'status',
  is_escape         TINYINT      NOT NULL DEFAULT 0,
  handler_bean      VARCHAR(255) DEFAULT NULL,
  params_schema     VARCHAR(2048) DEFAULT NULL,
  permission_code   VARCHAR(128) DEFAULT NULL,
  requires_idempotency TINYINT   NOT NULL DEFAULT 0,
  sort_order        INT          NOT NULL DEFAULT 0,
  created_at        DATETIME(3)  DEFAULT NULL,
  updated_at        DATETIME(3)  DEFAULT NULL,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  version           BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_action_tenant_entity_code (tenant_id, entity_code, action_code),
  KEY idx_action_entity_from (tenant_id, entity_code, from_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RUNTIME 实体状态机动作定义';

-- ── 2. 动作执行日志（幂等 + 审计） ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS meta_runtime_action_execution (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL DEFAULT 0,
  entity_code     VARCHAR(128) NOT NULL,
  record_id       VARCHAR(64)  NOT NULL,
  action_code     VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(255) DEFAULT NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=成功, 2=失败',
  result_json     VARCHAR(2048) DEFAULT NULL,
  operator_id     VARCHAR(64)  DEFAULT NULL,
  created_at      DATETIME(3)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_exec_tenant_entity_record_action_idem (tenant_id, entity_code, record_id, action_code, idempotency_key),
  KEY idx_exec_tenant_entity_record (tenant_id, entity_code, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RUNTIME 动作执行日志（幂等去重 + 记录级变更审计）';

-- ── 3. 报销场景种子（expense_claim，tenant_id=0 平台/演示租户） ──────────────
-- 状态机：DRAFT → SUBMITTED → APPROVED / REJECTED →（APPROVED）→ PAID
INSERT INTO meta_entity_action_def
  (tenant_id, entity_code, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, permission_code, requires_idempotency, sort_order,
   created_at, updated_at, deleted, version)
VALUES
  (0, 'expense_claim', 'submit',        '提交',     'DRAFT',     'SUBMITTED', 'status', 0, NULL, NULL, 'metadata:runtime:write', 0, 10, NOW(3), NOW(3), 0, 0),
  (0, 'expense_claim', 'approve',       '审批通过', 'SUBMITTED', 'APPROVED',  'status', 0, NULL, NULL, 'metadata:runtime:write', 0, 20, NOW(3), NOW(3), 0, 0),
  (0, 'expense_claim', 'reject',        '驳回',     'SUBMITTED', 'REJECTED',  'status', 0, NULL, NULL, 'metadata:runtime:write', 0, 21, NOW(3), NOW(3), 0, 0),
  (0, 'expense_claim', 'confirmPayout', '确认打款', 'APPROVED',  'PAID',      'status', 1, 'expenseClaimConfirmPayoutHandler',
   '[{"name":"channel","type":"enum","required":true,"values":["PAYROLL","BANK_TRANSFER"],"label":"打款渠道"},{"name":"note","type":"string","required":false,"label":"备注"}]',
   'metadata:runtime:write', 1, 30, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
  label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
  status_field = VALUES(status_field), is_escape = VALUES(is_escape), handler_bean = VALUES(handler_bean),
  params_schema = VALUES(params_schema), permission_code = VALUES(permission_code),
  requires_idempotency = VALUES(requires_idempotency), sort_order = VALUES(sort_order),
  updated_at = NOW(3), version = version + 1;

-- ── 4. 校验 ────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_entity_action_def WHERE deleted = 0 AND entity_code = 'expense_claim') AS action_def_count,
  (SELECT COUNT(*) FROM meta_entity_action_def WHERE deleted = 0 AND entity_code = 'expense_claim' AND is_escape = 1) AS escape_action_count;
