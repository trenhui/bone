package com.bone.app.expense.workflow;

/**
 * 报销审批流接入常量（{@code bone-workflow-engine} 侧契约）。
 *
 * <p>定义 key 与 {@code wf_process_def.def_key} / {@code wf_binding.def_key} 种子对齐（迁移 0056）； 实例启动时按此
 * key 取 {@code findLatestPublished}，租户维度经 {@code TenantContext} 隔离。
 */
final class ExpenseWorkflowConstants {

  /** 报销单流程定义 key（与 wf_process_def / wf_binding 种子一致）。 */
  static final String DEFINITION_KEY = "expense_reimbursement";

  /** 动作码：本模块的逃逸 Handler 只认这几个码（平台动作定义侧声明 handler_bean 时对齐）。 */
  static final String ACTION_SUBMIT = "submit";

  static final String ACTION_APPROVE = "approve";

  static final String ACTION_REJECT = "reject";

  static final String ACTION_WITHDRAW = "withdraw";

  private ExpenseWorkflowConstants() {}
}
