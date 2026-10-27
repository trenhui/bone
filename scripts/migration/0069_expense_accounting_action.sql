-- =============================================================================
-- 0069 · 报销接入会计引擎（业财一体，doc/design/modules/12 工作流引擎设计 §3/§4）
-- -----------------------------------------------------------------------------
-- 复用横向能力 voucherGenerateHandler（handler_bean=voucherGenerateHandler，由
-- bone-accounting-engine 经 @AutoConfiguration 自动装配，依赖见 bone-app-expense/pom.xml）。
-- 本脚本仅在报销终态动作后追加「生成凭证」动作；凭证公式 / 借贷口径差异全在配置数据
-- （0067 的 fin_voucher_template / fin_account_mapping），不写一行会计业务逻辑。
--
-- 触发链路：confirmPayout（APPROVED→PAID，逃逸动作，handler=expenseReimbursementConfirmPayoutHandler）
--           成功 → 业务终态 PAID
--           → 调用 generateVoucher 动作（PAID→PAID，非逃逸、只生成凭证不流转 status）
--           → voucherGenerateHandler 读 fin_voucher_template 生成 fin_voucher + 分录（fin_voucher_entry）。
--
-- 设计要点（单一写者 / 解耦）：handler_bean 为单值，故会计不"并联"在 confirmPayout 上，
-- 而是独立 generateVoucher 动作，业务终态与会计凭证解耦，符合官方接入建议。
--
-- 幂等：仅当 (tenant_id, entity_code, action_code) 不存在时插入，可重复执行。
--
-- L3：本脚本为 DDL/数据变更，需架构师审批后执行（仅草稿，不自动落库）。
-- =============================================================================

INSERT INTO meta_entity_action_def
  (id, tenant_id, gmt_create, gmt_modified, version,
   entity_code, action_code, label, from_status, to_status, status_field,
   escape, requires_idempotency, sort_order, handler_bean)
SELECT
  COALESCE((SELECT MAX(id) FROM meta_entity_action_def), 0) + 1,
  0, NOW(), NOW(), 0,
  'expense_reimbursement', 'generateVoucher', '生成凭证',
  'PAID', 'PAID', 'status',
  0, 0, 5, 'voucherGenerateHandler'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM meta_entity_action_def
  WHERE tenant_id = 0
    AND entity_code = 'expense_reimbursement'
    AND action_code = 'generateVoucher'
);
