package com.bone.workflow.domain;

import java.util.Map;

/**
 * 审批流引擎用例入口（领域侧对外契约）。
 *
 * <p>实现位于 {@code bone-workflow-core} 的 {@code runtime} 包；本接口定义在 domain 是为了「用例签名属于领域语义」，让宿主（如
 * bone-metadata-server 的动作 Handler）只依赖领域契约，不依赖编排细节。
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
   * 完成一项审批任务；驳回时可选择「退回指定节点」而非整单驳回。
   *
   * <p><b>驳回退回指定节点（企业报销/采购审批最高频诉求，钉钉/飞书/宜搭标配）</b>：审批人驳回时， 除「整单驳回」外，可指定 {@code backToNodeCode}
   * 把流程运行期跳回某个上游节点（通常是上一审批节点或发起节点）， 由该节点重新派单/重新求值后继续走——发起人据驳回意见修改后无需另起新单。
   *
   * <p><b>为什么是运行期跳转而非定义里的「驳回边」</b>：审批节点仍须满足「恰 1 条无条件出边」（WF-014）， 否则发布期就会拦下；退回是运行期行为，不改变定义模型。
   *
   * <p><b>守卫（fail-closed）</b>：
   *
   * <ul>
   *   <li>{@code backToNodeCode == null}（或 {@code decision == APPROVE} 时忽略该参数）⇒ 维持原语义，整单驳回；
   *   <li>目标节点不存在 / 为结束节点 / 等于当前节点 ⇒ 拒绝并抛 {@code REJECT_TARGET_NODE_INVALID}，绝不静默整单驳回冒充「退回」；
   *   <li>同实例累计驳回次数达上限（防 A 驳 B、B 驳 A 无限往返）⇒ 强制整单驳回终止。
   * </ul>
   *
   * @param taskId 任务 id
   * @param decision 审批结论
   * @param comment 审批意见（驳回必填，否则 {@code REJECT_COMMENT_REQUIRED}）
   * @param operatorId 操作人（须为该任务办理人，否则拒绝）
   * @param backToNodeCode 驳回时退回的目标节点编码（可空；仅 {@code REJECT} 生效，{@code APPROVE} 时忽略）
   * @return 推进后的流程实例
   */
  WorkflowInstance completeTask(
      String taskId,
      WorkflowTask.Decision decision,
      String comment,
      String operatorId,
      String backToNodeCode);

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
   * 改派待办任务（管理员干预）：把某位办理人的待办转给他人。
   *
   * <p><b>为什么需要</b>：审批人请假、离职、或指派规则解析出一个已经不该办事的人，是审批流最常见的卡点。
   * 没有改派能力，这些单据只能干等到超时，或者靠管理员直接改库。改派是「挂起」之外的第二条人工介入通道—— 且比挂起更轻：不需要修组织架构，改完立刻续跑。
   *
   * <p><b>约束</b>：任务必须仍为 {@code PENDING} 且实例未终结；否则拒绝（改派一个已办结的任务毫无意义）。
   *
   * @param taskId 任务 id
   * @param newAssigneeId 新办理人账户 id
   * @param operatorId 操作人（管理员）
   * @param reason 改派原因（审计用）
   * @return 改派后的任务
   */
  WorkflowTask reassignTask(String taskId, String newAssigneeId, String operatorId, String reason);

  /**
   * 催办某待办任务（无状态变更）。
   *
   * <p>只发通知 + 留痕，不改变任务与流程状态——催办是「提醒」，不是「决策」。
   *
   * @param taskId 任务 id
   * @param operatorId 操作人（催办人）
   * @param comment 催办留言
   * @return 被催办的任务
   */
  WorkflowTask urgeTask(String taskId, String operatorId, String comment);

  /**
   * 转办待办任务（审批人本人把待办转交他人）。
   *
   * <p><b>与 {@code reassignTask} 区分</b>：改派是管理员干预（不限操作人身份）；转办是审批人<b>本人</b>把属于自己的待办交出去， 原任务置 {@code
   * TRANSFERRED} 并新建一条指向新办理人的 {@code PENDING} 任务——审计上保留「谁转给了谁」的完整链路， 而非把原任务静默改人。常用于审批人请假 / 离职前交接。
   *
   * <p><b>约束</b>：操作人须为该任务当前办理人；任务须为 {@code PENDING} 且实例未终结；否则拒绝（转办他人已决策的任务是无意义的）。
   *
   * @param taskId 任务 id
   * @param newAssigneeId 新办理人账户 id
   * @param operatorId 操作人（须为当前办理人本人）
   * @param comment 转办留言（审计用）
   * @return 新建的转办后任务
   */
  WorkflowTask transferTask(String taskId, String newAssigneeId, String operatorId, String comment);

  /**
   * 加签（审批人本人邀请他人共同审批同一节点）。
   *
   * <p><b>语义</b>：在原节点新增一条指向 {@code newAssigneeId} 的 {@code PENDING} 任务，节点完成与否仍由该节点的审批模式 （{@code
   * SINGLE/ALL/ANY}）统一判定——会签（ALL）节点上，被加签人须一并通过；单签 / 或签节点上，加签只是多一个可决策人。 这与「管理员强制指派 {@code
   * assignTask}」区分：加签是审批人主动邀请协同，指派是管理员兜底注入。
   *
   * <p><b>约束</b>：操作人须为该任务当前办理人；任务须为 {@code PENDING} 且实例未终结；同一节点下 {@code newAssigneeId}
   * 已有待办则拒绝（避免重复加签）。
   *
   * @param taskId 任务 id（当前办理人本人的待办）
   * @param newAssigneeId 被加签人账户 id
   * @param operatorId 操作人（须为当前办理人本人）
   * @param comment 加签留言（审计用）
   * @return 新建的加签任务
   */
  WorkflowTask addSign(String taskId, String newAssigneeId, String operatorId, String comment);

  /**
   * 读取某业务单据当前的流程实例（无流程时返回空）。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   */
  java.util.Optional<WorkflowInstance> findInstance(String entityCode, String recordId);

  /**
   * 按实例 id 读实例（待办列表按任务反查单据时需要：任务只持有 {@code instanceId}）。
   *
   * @param instanceId 流程实例 id
   */
  java.util.Optional<WorkflowInstance> findInstanceById(String instanceId);

  /**
   * 读取某流程实例的待办任务。
   *
   * @param instanceId 流程实例 id
   */
  /**
   * 读取某办理人的任务（跨实例：<b>企业审批最常用的「我的待办 / 我的已办」</b>）。
   *
   * <p><b>为什么必须引擎提供</b>：仓储层已有 {@code listTasksByAssignee}，宿主若直接绕过引擎读表，就等于把「谁是办理人」这道 唯一的授权边界放到宿主自己拼
   * SQL 的地方——多租户谓词漏一条即跨租户串数据（{@code @Sql} 通道不经租户过滤器）。 经引擎收口后， 只有引擎的 tenant 谓词那一处需要审查。
   *
   * @param assigneeId 办理人账户 id
   * @param status 任务状态：{@code PENDING} = 待办；{@code APPROVED}/{@code REJECTED} = 已办
   */
  java.util.List<WorkflowTask> listTasksByAssignee(String assigneeId, WorkflowTask.Status status);

  java.util.List<WorkflowTask> listPendingTasks(String instanceId);

  /**
   * 读取某流程实例的<b>完整审批轨迹</b>（全部任务，含已决策 / 已取消，按创建序）。
   *
   * <p>审批单据详情页据此展示「谁在何时批了什么、走了哪步、当前卡在哪个节点」。与 {@link #listPendingTasks}（只取 PENDING）不同，此处还原全量时间线；节点推进
   * / 挂起 / 终止等实例级事件目前由调用方结合 {@code phase} 与任务时间线拼装。
   *
   * @param instanceId 流程实例 id
   */
  java.util.List<WorkflowTask> listInstanceHistory(String instanceId);

  /**
   * 管理员强制指派（打破「审批人解析不出 → 挂起」死单的兜底出口）。
   *
   * <p><b>为什么需要</b>：当某节点审批人解析不出时，引擎会把实例置 {@code SUSPENDED} 且<b>根本不创建任务</b> （见运行期语义 §4.3）；此时 {@code
   * reassignTask}（仅对 {@code PENDING} 任务生效）无任务可改派，实例只能等组织数据修复后 {@code resume}。
   * 本方法为管理员提供一个<b>直接注入任务并恢复</b>的通道：在 {@code SUSPENDED} 且无任务时新建 {@code PENDING} 任务并恢复为 {@code
   * RUNNING}； 对 {@code RUNNING} 实例也可补充注入某节点的待办（如指派规则漏解析）。
   *
   * <p><b>守卫</b>：实例须存在且非终态；目标办理人不能为空；无重复 {@code (node, assignee)} PENDING 时方新建。
   *
   * @param instanceId 流程实例 id
   * @param nodeCode 目标节点（可空；空则取实例当前节点 {@code currentNodeCode}）
   * @param assigneeId 被强制指派的办理人账户 id
   * @param operatorId 操作人（管理员）
   * @param reason 指派原因（审计用）
   * @return 指派后的流程实例（可能因恢复而变为 {@code RUNNING}）
   */
  WorkflowInstance assignTask(
      String instanceId, String nodeCode, String assigneeId, String operatorId, String reason);

  /**
   * 条件求值的上下文数据来源：把业务单据数据取给引擎（由宿主提供）。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   */
  Map<String, Object> loadRecordData(String entityCode, String recordId);

  /**
   * 管理员强制终止流程（不可逆干预）。
   *
   * <p><b>与 {@code withdraw} 区分</b>：撤回是发起人收回应办、单据可重新编辑重提；终止是管理员对卡死 / 错提单据的处置， 进入 {@code TERMINATED}
   * 终态，单据不再可重提。
   *
   * <p><b>守卫</b>：实例须存在且非终态；阶段 CAS 保证并发安全（与撤回 / 驳回互斥）。
   *
   * @param instanceId 流程实例 id
   * @param operatorId 操作人（管理员）
   * @param reason 终止原因（审计用）
   * @return 终止后的流程实例
   */
  WorkflowInstance terminateInstance(String instanceId, String operatorId, String reason);
}
