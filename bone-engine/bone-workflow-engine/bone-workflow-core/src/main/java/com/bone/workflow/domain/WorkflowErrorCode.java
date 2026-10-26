package com.bone.workflow.domain;

/**
 * 审批流引擎错误分类。
 *
 * <p><b>为什么要分类而不是只抛字符串消息</b>：宿主需要把引擎失败翻译成平台稳定错误码（409 / 403 / 422 / 500）， 若只能靠解析异常
 * message，同一条语义在两处各写一套字符串，迟早漂移。故引擎给出<b>稳定分类</b>，宿主按分类映射。
 *
 * <p><b>分类口径</b>：按「宿主该怎么响应」划分，而非按「内部怎么实现」划分——每个枚举常量自带 {@link #httpStatus()}（单一真源）， 宿主 {@code
 * GlobalExceptionHandler} 直接按 {@code code.httpStatus()} 响应，<b>不得</b>靠解析 message
 * 翻译（否则同一条语义两处各写一套，迟早漂移）。
 *
 * <ul>
 *   <li>{@code NOT_FOUND} / {@code DEFINITION_INVALID} / {@code BRANCH_*} → 建模或数据问题（422）
 *   <li>{@code NOT_ASSIGNEE} / {@code NOT_INITIATOR} → 权限问题（403）
 *   <li>{@code CONFLICT} / {@code ALREADY_STARTED} / {@code ALREADY_DECIDED} / {@code
 *       INSTANCE_TERMINATED} → 并发与状态不允许（409）
 *   <li>{@code NO_ASSIGNEE} → 组织架构问题，挂起待人工介入（422 + 告警）
 * </ul>
 */
public enum WorkflowErrorCode {

  /** 流程定义不存在或未发布。 */
  DEFINITION_NOT_FOUND(422),

  /** 流程定义未通过发布期完整性校验。 */
  DEFINITION_INVALID(422),

  /** 流程实例不存在。 */
  INSTANCE_NOT_FOUND(422),

  /** 审批任务不存在。 */
  TASK_NOT_FOUND(422),

  /** 流程实例已终结，不允许再操作。 */
  INSTANCE_TERMINATED(409),

  /** 操作人不是该任务的办理人。 */
  NOT_ASSIGNEE(403),

  /** 操作人不是流程发起人。 */
  NOT_INITIATOR(403),

  /** 任务已被决策，不可改判。 */
  ALREADY_DECIDED(409),

  /** 并发冲突：任务/实例已被他人改动。 */
  CONFLICT(409),

  /** 该单据已有进行中的流程实例（重复发起）。 */
  ALREADY_STARTED(409),

  /** 审批节点解析不出审批人（实例被挂起）。 */
  NO_ASSIGNEE(422),

  /**
   * 自审批：解析出的审批人（或被指派/转办/加签的目标人）是<b>发起人本人</b>。
   *
   * <p>职责分离是内控红线（SOX 等）：「自己提单、自己审批」等于这道审批环节<b>事实上不存在</b>，
   * 而单据表面看是通过的。故一律拒绝，不静默跳过（跳过=少一道审批，同样破坏内控，且更隐蔽）。
   */
  SELF_APPROVAL(422),

  /** 驳回未填写意见：发起人无从得知为何被驳回、无法修改重提。 */
  REJECT_COMMENT_REQUIRED(422),

  /** 条件分支无任何命中且无默认边。 */
  BRANCH_NO_MATCH(422),

  /** 条件分支多条同时命中（配置歧义）。 */
  BRANCH_AMBIGUOUS(422),

  /** 条件分支配置非法。 */
  BRANCH_INVALID(422),

  /** 流程定义缺少必需节点或边。 */
  DEFINITION_MALFORMED(422);

  private final int httpStatus;

  WorkflowErrorCode(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  /** 该错误对应的 HTTP 状态码（宿主响应用，单一真源）。 */
  public int httpStatus() {
    return httpStatus;
  }
}
