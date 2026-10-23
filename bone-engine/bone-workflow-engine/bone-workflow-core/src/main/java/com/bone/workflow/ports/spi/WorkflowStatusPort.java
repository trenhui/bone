package com.bone.workflow.ports.spi;

/**
 * 业务单据状态推进端口（端口）。
 *
 * <p><b>关键纪律：引擎不直接写业务单据的 {@code status} 字段</b>。状态字段是平台状态机（动作定义 + 守卫）的管辖
 * 范围，直写会绕开状态机守卫、造成「流程说批了、单据状态没动」或反之的双真源。故引擎只表达<b>意图</b> （「该单据审批通过了」），由宿主把意图翻译成平台动作调用。
 *
 * <p>宿主实现应当走平台的动作端点（等价于调用 {@code RuntimeActionService.invokeAction}），让状态流转同样留下 审计痕迹。
 */
public interface WorkflowStatusPort {

  /**
   * 通知宿主：流程已通过，请把业务单据推进到「审批通过」状态。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   * @param instanceId 流程实例 id
   * @param operatorId 操作人
   */
  void markApproved(String entityCode, String recordId, String instanceId, String operatorId);

  /**
   * 通知宿主：流程已驳回，请把业务单据推进到「审批驳回」状态。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   * @param instanceId 流程实例 id
   * @param operatorId 操作人
   * @param reason 驳回原因
   */
  void markRejected(
      String entityCode, String recordId, String instanceId, String operatorId, String reason);

  /**
   * 通知宿主：流程已撤回，请把业务单据恢复到可编辑状态。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   * @param instanceId 流程实例 id
   * @param operatorId 操作人
   */
  void markWithdrawn(String entityCode, String recordId, String instanceId, String operatorId);

  /**
   * 通知宿主：流程已挂起（审批人解析不出，等待人工介入）。
   *
   * <p>宿主据此把单据标为「卡单」并告警——挂起不通知运维，就会变成无人知晓的死单。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   * @param instanceId 流程实例 id
   * @param reason 挂起原因
   */
  void markSuspended(String entityCode, String recordId, String instanceId, String reason);

  /**
   * 通知宿主：流程被管理员强制终止（实例进入 {@code TERMINATED} 终态）。
   *
   * <p>与撤回区分：撤回是发起人收回应办、单据可重新编辑；终止是管理员对卡死 / 错提单据的不可逆处置，单据不再可重提。
   * 宿主据此把业务单据推进到「已终止」状态（若其状态机无对应状态，至少做归档 / 锁定处理）。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   * @param instanceId 流程实例 id
   * @param operatorId 操作人（管理员）
   * @param reason 终止原因（审计用）
   */
  void markTerminated(
      String entityCode, String recordId, String instanceId, String operatorId, String reason);

  /**
   * 通知宿主：挂起的流程已恢复并续跑（审批人经重新解析已就位）。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   * @param instanceId 流程实例 id
   */
  void markResumed(String entityCode, String recordId, String instanceId);
}
