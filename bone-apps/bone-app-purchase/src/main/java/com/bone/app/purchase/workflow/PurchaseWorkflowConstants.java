package com.bone.app.purchase.workflow;

/**
 * 采购审批流接入常量（{@code bone-workflow-engine} 侧契约）。
 *
 * <p>定义 key 与 {@code wf_process_def.def_key} / {@code wf_binding.def_key} 种子对齐（迁移 0068）； 实例启动时按此
 * key 取 {@code findLatestPublished}，租户维度经 {@code TenantContext} 隔离。
 *
 * <p>采购审批流（合理默认，待业务确认）：提交 → 审批人 → 通过/驳回，单级审批；金额分级/会签等如需扩展， 在 0068 流程定义种子里加节点即可，本模块 Handler
 * 不变（差异全在流程定义配置）。
 */
final class PurchaseWorkflowConstants {

  /** 采购单流程定义 key（与 wf_process_def / wf_binding 种子一致）。 */
  static final String DEFINITION_KEY = "purchase_order";

  /** 动作码：本模块的逃逸 Handler 只认这几个码（平台动作定义侧声明 handler_bean 时对齐）。 */
  static final String ACTION_SUBMIT = "submit";

  static final String ACTION_APPROVE = "approve";

  static final String ACTION_REJECT = "reject";

  /** 撤回：发起人在审批人动手前自助纠错（作废待办、实例 WITHDRAWN、释放活动槽位）。 */
  static final String ACTION_WITHDRAW = "withdraw";

  private PurchaseWorkflowConstants() {}
}
