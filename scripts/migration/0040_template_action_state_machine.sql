-- =============================================================================
-- 0040_template_action_state_machine.sql
--
-- 目的：让「从模板实例化」产出**可用的状态机**，而不是只有对象与字段。
--
-- 为什么要这张表（问题起因，实证非推断）
-- ----------------------
-- 模板实例化（MetaTemplateApplicationService.instantiate）只复制 entity + fields，
-- 从不写 meta_entity_action_def。而 CatalogRuntimeEntityProvider 的 state_field
-- **只能**从 actionDefRepository.listByEntity() 推导 ⇒ 模板实例的 stateField == null，
-- 连锁失效：
--   ① JdbcRuntimeActionDefRepository.injectEntryState() 直接 return
--      → 必填的 status 无人注入 → POST 400「字段「status」为必填」
--      → **浏览器实测：UI 新建记录必失败**（流程条推荐路径端到端不通）
--   ② assertStateFieldNotWritable() 不生效
--      → 创建时可直写终态：实测 status=PAID / APPROVED 均 201 成功
--      → 状态机可被绕过（跳过提交/审批/打款直接产出「已打款」单据）
-- 平台此前没有任何动作定义的写入 API/UI，用户无法自救 ⇒ 必须在实例化这条路径上产出。
--
-- 设计
-- ----------------------
-- · meta_model_template_action：模板级状态机动作定义（meta_entity_action_def 的模板投影）。
--   - template_id 归属某一模板（tenant_id = 0，平台资产）；实例化时按**新实体 code** 复制展开。
--   - 字段语义与 0032 的 meta_entity_action_def 一一对应，避免两套方言。
--   - 不复制 permission_code：实例化时按新实体重写为 runtime:{entityCode}:{actionCode}
--     （0032 存的是旧粗码 metadata:runtime:write，若照抄会让新实体的动作授权永远命中不了新实体命名空间）。
-- · 状态取值全部来自**模板自身已声明的 status 枚举**（0029/0031 种子），不臆造业务状态。
--
-- 授权语义（Runtime 数据面设计 §4.6，本迁移刻意保持 fail-closed）
-- ----------------------
-- 非逃逸动作（状态流转）由 runtime:*:write 通配覆盖；**逃逸动作（有资金/外部副作用，
-- 如确认打款）必须命中动作级细码** runtime:{entityCode}:confirmPayout，通配 write 不覆盖。
-- 故模板实例化的最后一步「确认打款」在未显式授权前会 403，并给出「该授予什么码」——
-- 这是有意为之的收口，不是缺陷；上线由管理员在 IAM 完成授权后即可用。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0040_template_action_state_machine.sql
-- 前置：0029_scenario_templates.sql / 0031_scenario_template_expense.sql 已落地场景模板。
-- 幂等：建表 IF NOT EXISTS + 种子 ON DUPLICATE KEY UPDATE；本脚本只增表 + 种子，不删不改旧数据。
-- =============================================================================

USE bone;

-- ── 1. 模板级动作定义表 ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS meta_model_template_action (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id             BIGINT       NOT NULL DEFAULT 0,
  template_id           BIGINT       NOT NULL,
  action_code           VARCHAR(128) NOT NULL,
  label                 VARCHAR(128) NOT NULL,
  from_status           VARCHAR(64)  NOT NULL,
  to_status             VARCHAR(64)  NOT NULL,
  status_field          VARCHAR(64)  NOT NULL DEFAULT 'status',
  is_escape             TINYINT      NOT NULL DEFAULT 0,
  handler_bean          VARCHAR(255) DEFAULT NULL,
  params_schema         VARCHAR(2048) DEFAULT NULL,
  requires_idempotency  TINYINT      NOT NULL DEFAULT 0,
  sort_order            INT          NOT NULL DEFAULT 0,
  created_at            DATETIME(3)  DEFAULT NULL,
  updated_at            DATETIME(3)  DEFAULT NULL,
  deleted               TINYINT      NOT NULL DEFAULT 0,
  version               BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tpl_action_code (tenant_id, template_id, action_code),
  KEY idx_tpl_action_from (tenant_id, template_id, from_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型模板状态机动作定义（实例化时展开为 meta_entity_action_def）';

-- ── 2. 费用报销（tpl_expense_reimbursement，id=9150） ──────────────────────────────
-- 状态机：DRAFT → SUBMITTED → APPROVED / REJECTED →（APPROVED）→ PAID
-- 与 0032 给 expense_reimbursement 的参考运转同构；确认打款为逃逸动作（handler + 幂等）。
INSERT INTO meta_model_template_action
  (tenant_id, template_id, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, requires_idempotency, sort_order,
   created_at, updated_at, deleted, version)
VALUES
  (0, 9150, 'submit',        '提交',     'DRAFT',     'SUBMITTED', 'status', 0, NULL, NULL, 0, 10, NOW(3), NOW(3), 0, 0),
  (0, 9150, 'approve',       '审批通过', 'SUBMITTED', 'APPROVED',  'status', 0, NULL, NULL, 0, 20, NOW(3), NOW(3), 0, 0),
  (0, 9150, 'reject',        '驳回',     'SUBMITTED', 'REJECTED',  'status', 0, NULL, NULL, 0, 21, NOW(3), NOW(3), 0, 0),
  (0, 9150, 'confirmPayout', '确认打款', 'APPROVED',  'PAID',      'status', 1,
   'expenseReimbursementConfirmPayoutHandler',
   '[{"name":"channel","type":"enum","required":true,"values":["PAYROLL","BANK_TRANSFER"],"label":"打款渠道"},{"name":"note","type":"string","required":false,"label":"备注"}]',
   1, 30, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
  label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
  status_field = VALUES(status_field), is_escape = VALUES(is_escape), handler_bean = VALUES(handler_bean),
  params_schema = VALUES(params_schema), requires_idempotency = VALUES(requires_idempotency),
  sort_order = VALUES(sort_order), updated_at = NOW(3), version = version + 1;

-- ── 3. 补货管理（tpl_replenishment，id=9130） ──────────────────────────────
-- 状态机：PENDING 待补货 → ORDERED 已下单 → SHIPPED 已发货 → RECEIVED 已收货
--        逃逸：PENDING/ORDERED → CANCELLED 已取消
INSERT INTO meta_model_template_action
  (tenant_id, template_id, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, requires_idempotency, sort_order,
   created_at, updated_at, deleted, version)
VALUES
  (0, 9130, 'placeOrder',    '下单',     'PENDING', 'ORDERED',   'status', 0, NULL, NULL, 0, 10, NOW(3), NOW(3), 0, 0),
  (0, 9130, 'ship',          '发货',     'ORDERED', 'SHIPPED',   'status', 0, NULL, NULL, 0, 20, NOW(3), NOW(3), 0, 0),
  (0, 9130, 'receive',       '收货',     'SHIPPED', 'RECEIVED',  'status', 0, NULL, NULL, 0, 30, NOW(3), NOW(3), 0, 0),
  (0, 9130, 'cancel',        '取消',     'PENDING', 'CANCELLED', 'status', 0, NULL, NULL, 0, 40, NOW(3), NOW(3), 0, 0),
  (0, 9130, 'cancelOrdered', '取消订单', 'ORDERED', 'CANCELLED', 'status', 0, NULL, NULL, 0, 41, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
  label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
  status_field = VALUES(status_field), is_escape = VALUES(is_escape), handler_bean = VALUES(handler_bean),
  params_schema = VALUES(params_schema), requires_idempotency = VALUES(requires_idempotency),
  sort_order = VALUES(sort_order), updated_at = NOW(3), version = version + 1;

-- ── 4. 访客通行（tpl_visitor_pass，id=9140） ───────────────────────────────
-- 状态机：PENDING 待审批 → APPROVED 已通过 → CHECKED_IN 已入场 → CHECKED_OUT 已离场
--        逃逸：PENDING → REJECTED 已拒绝
INSERT INTO meta_model_template_action
  (tenant_id, template_id, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, requires_idempotency, sort_order,
   created_at, updated_at, deleted, version)
VALUES
  (0, 9140, 'approve',  '审批通过', 'PENDING',    'APPROVED',    'status', 0, NULL, NULL, 0, 10, NOW(3), NOW(3), 0, 0),
  (0, 9140, 'checkIn',  '入场登记', 'APPROVED',   'CHECKED_IN',  'status', 0, NULL, NULL, 0, 20, NOW(3), NOW(3), 0, 0),
  (0, 9140, 'checkOut', '离场登记', 'CHECKED_IN', 'CHECKED_OUT', 'status', 0, NULL, NULL, 0, 30, NOW(3), NOW(3), 0, 0),
  (0, 9140, 'reject',   '拒绝',     'PENDING',    'REJECTED',    'status', 0, NULL, NULL, 0, 40, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
  label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
  status_field = VALUES(status_field), is_escape = VALUES(is_escape), handler_bean = VALUES(handler_bean),
  params_schema = VALUES(params_schema), requires_idempotency = VALUES(requires_idempotency),
  sort_order = VALUES(sort_order), updated_at = NOW(3), version = version + 1;

-- ── 5. 设备巡检（tpl_device_inspection，id=9120） ──────────────────────────
-- 状态机：OPEN 待处理 → CLOSED 已闭环（result 正常/异常为巡检结论，不由用例推进）
INSERT INTO meta_model_template_action
  (tenant_id, template_id, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, requires_idempotency, sort_order,
   created_at, updated_at, deleted, version)
VALUES
  (0, 9120, 'close',  '确认闭环', 'OPEN',  'CLOSED',   'status', 0, NULL, NULL, 0, 10, NOW(3), NOW(3), 0, 0),
  (0, 9120, 'reopen', '重新打开', 'CLOSED','OPEN',     'status', 0, NULL, NULL, 0, 20, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
  label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
  status_field = VALUES(status_field), is_escape = VALUES(is_escape), handler_bean = VALUES(handler_bean),
  params_schema = VALUES(params_schema), requires_idempotency = VALUES(requires_idempotency),
  sort_order = VALUES(sort_order), updated_at = NOW(3), version = version + 1;

-- 注：tpl_customer_base（9101，客户主数据）**没有 status 字段**（纯主数据、无状态机），
--     因此不产生动作定义 —— 它也不受本次死锁影响（无必填受保护状态列）。

-- ── 6. 校验 ────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_model_template_action WHERE deleted = 0) AS template_action_count,
  (SELECT COUNT(DISTINCT template_id) FROM meta_model_template_action WHERE deleted = 0) AS template_count,
  (SELECT COUNT(*) FROM meta_model_template_action WHERE deleted = 0 AND is_escape = 1) AS escape_action_count,
  (SELECT COUNT(*) FROM meta_model_template WHERE deleted = 0 AND status = 1) AS published_template_count;
