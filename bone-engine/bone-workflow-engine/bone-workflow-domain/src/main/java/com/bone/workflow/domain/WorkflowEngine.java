package com.bone.workflow.domain;

import java.util.Map;

/**
 * 审批流引擎用例入口（领域侧对外契约）。
 *
 * <p>实现位于 {@code bone-workflow-runtime}；本接口定义在 domain 是为了「用例签名属于领域语义」，让宿主（如 bone-metadata-server
 * 的动作 Handler）只依赖领域契约，不依赖编排细节。
 *
 * <p><b>异常约定</b>：引擎内部以 {@link WorkflowEngineException}（携带稳定 {@link WorkflowErrorCode}）表达失败，
 * <b>不</b>定义平台业务异常类型——引擎不依赖平台，不该把平台的错误码体系带进来。宿主装配层按 {@code code()} 把异常翻译成平台的稳定错误码（422 / 403 / 409
 * 等），保持对外契约稳定。<b>不要</b>靠解析异常 message 翻译， 否则同一条语义会在两处各写一套字符串、迟早漂移。
 */
public interface WorkflowEngine {

  /**
   * 启动流程。
   *
   * @param entityCode 业务实体编码（如 {@code expense_reimbursement}）
   * @param recordId 业务单据 id
   * @param definitionKey 流程定义标识
   * @param operatorId 发起人
   * @return 新建的流程实例
   */
  WorkflowInstance start(
      String entityCode, String recordId, String definitionKey, String operatorId);

  /**
   * 完成一项审批任务。
   *
   * @param taskId 任务 id
   * @param decision 审批结论
   * @param comment 审批意见（可空）
   * @param operatorId 操作人（须为该任务办理人，否则拒绝）
   * @return 推进后的流程实例
   */
  WorkflowInstance completeTask(
      String taskId, WorkflowTask.Decision decision, String comment, String operatorId);

  /**
   * 撤回流程（仅发起人、且流程未终结时可撤回）。
   *
   * @param instanceId 流程实例 id
   * @param operatorId 操作人
   * @return 撤回后的流程实例
   */
  WorkflowInstance withdraw(String instanceId, String operatorId);

  /**
   * 恢复挂起的流程（仅发起人、且仅当实例处于 {@code SUSPENDED}）。
   *
   * <p>挂起通常因「审批人解析不出」（组织架构断链）。修复组织架构后，调用本方法重新解析审批人并续跑； 若仍解析不出则保持挂起并明确报错，避免「恢复成功」的假象。
   *
   * @param instanceId 流程实例 id
   * @param operatorId 操作人（发起人）
   * @return 恢复后的流程实例；若实例已处于 RUNNING（从未挂起/已恢复）则幂等返回
   */
  WorkflowInstance resume(String instanceId, String operatorId);

  /**
   * 读取某业务单据当前的流程实例（无流程时返回空）。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   */
  java.util.Optional<WorkflowInstance> findInstance(String entityCode, String recordId);

  /**
   * 读取某流程实例的待办任务。
   *
   * @param instanceId 流程实例 id
   */
  java.util.List<WorkflowTask> listPendingTasks(String instanceId);

  /**
   * 条件求值的上下文数据来源：把业务单据数据取给引擎（由宿主提供）。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   */
  Map<String, Object> loadRecordData(String entityCode, String recordId);
}
