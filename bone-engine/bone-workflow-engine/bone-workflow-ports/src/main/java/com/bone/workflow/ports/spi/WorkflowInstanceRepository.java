package com.bone.workflow.ports.spi;

import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import java.util.List;
import java.util.Optional;

/**
 * 流程实例与任务仓储（端口）。
 *
 * <p>由宿主实现：实例与任务建议落在平台实体上（{@code wf_instance} / {@code wf_task}），从而复用平台统一的租户隔离、 审计与乐观锁——隔离形态是
 * {@code @TenantScope} + 显式租户谓词 + 应用层过滤，<b>不是</b>数据库行级安全策略（RLS）。
 *
 * <p><b>审计归属纪律</b>：凡写审计列（{@code created_by} / {@code updated_by}）的方法一律由引擎把<b>操作人</b>作为显式参数
 * 传入（引擎用例签名均持有 {@code operatorId}）。实现方<b>不得</b>从 {@code TenantContext} 取租户 id 冒充操作人——审计列要能 回答「谁」，租户
 * id 回答的是「哪个租户」。
 *
 * <p><b>并发纪律</b>：任务决策必须走 {@link #compareAndSetTaskStatus} 的 CAS 语义，不得「读-判-写」。两个审批人 同时点「同意」时，若无
 * CAS，会出现双写、重复推进流程的缺陷。
 */
public interface WorkflowInstanceRepository {

  /** 保存（新建或更新）流程实例。 */
  void saveInstance(WorkflowInstance instance);

  /**
   * <b>原子抢占「一单据一活动实例」槽位</b>。
   *
   * <p>「同一单据同时只允许一个未终结实例」是幂等启动的数据库层保证。应用层「先查有没有、再建」在并发下会双建——
   * 两个提交请求同时查到「无实例」，于是各自建一个，得到<b>两个并行实例、双份审批、互相看不见</b>。
   *
   * <p>实现方应把它落成<b>唯一键插入</b>（典型：以 {@code (tenant_id, entity_code, record_id)} 为主键的指针表）： 插入成功 =
   * 抢占成功；撞唯一键 = 已被占用。若指针指向的实例已终态，应视为<b>陈旧指针</b>并清理后重试一次 （否则上一次流程结束后永久无法重新提交）。
   *
   * @param instance 待启动的实例（尚未落库）
   * @return true = 抢占成功，调用方应继续 {@link #saveInstance}；false = 已有活动实例或抢占失败
   */
  boolean claimActiveInstance(WorkflowInstance instance);

  /**
   * 释放活动实例槽位（流程进入终态时释放，允许同一单据重新提交）。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   */
  void releaseActiveInstance(String entityCode, String recordId);

  /**
   * <b>调用顺序纪律（实现方与调用方都必须知道）</b>：新建实例必须<b>先</b> {@link #claimActiveInstance} <b>再</b> {@link
   * #saveInstance}。
   *
   * <p>{@link #saveInstance} 本身<b>不</b>写活动指针——它同时承担「新建」与「推进后更新」两种职责，无法区分。 因此只调 {@code saveInstance}
   * 而不先 claim，等于绕过了「一单据一活动实例」的数据库闸门， 并发下会双建实例。 {@link #compareAndSetPhase} 在把实例改成终态时应自行释放指针（实现方职责）。
   */

  /** 按 id 取实例。 */
  Optional<WorkflowInstance> findInstance(String instanceId);

  /** 按业务单据取当前实例（一张单据同时只允许一个未终结实例）。 */
  Optional<WorkflowInstance> findInstanceByRecord(String entityCode, String recordId);

  /**
   * 新建任务。
   *
   * @param task 任务（尚未落库）
   * @param operatorId 操作人（触发建单的引擎用例入参）——写审计列，不得用租户 id 或 {@code "system"} 冒充
   */
  void saveTask(WorkflowTask task, String operatorId);

  /** 按 id 取任务。 */
  Optional<WorkflowTask> findTask(String taskId);

  /** 取某实例的全部待办任务。 */
  List<WorkflowTask> listPendingTasks(String instanceId);

  /** 取某实例某节点的全部任务（会签完成判定用）。 */
  List<WorkflowTask> listTasksByNode(String instanceId, String nodeCode);

  /**
   * 任务状态 CAS 更新：仅当当前状态为 {@code expected} 时才改。
   *
   * @param taskId 任务 id
   * @param expected 期望的当前状态
   * @param next 新状态
   * @param decision 审批结论（可空）
   * @param comment 审批意见（可空）
   * @param operatorId 操作人（审批人本人）——写 {@code updated_by}
   * @return 是否更新成功（false = 已被他人抢先改过）
   */
  boolean compareAndSetTaskStatus(
      String taskId,
      WorkflowTask.Status expected,
      WorkflowTask.Status next,
      WorkflowTask.Decision decision,
      String comment,
      String operatorId);

  /**
   * <b>节点级原子推进</b>（会签场景的关键并发点）。
   *
   * <p>把「该节点是否已满足完成条件」的判定，与「把实例推进到下一节点」放进<b>同一条原子操作</b>：只有当实例仍停在 {@code nodeCode}、且完成条件已满足时，才把
   * {@code currentNodeCode} 改为 {@code nextNodeCode} 并返回 {@code true}； 否则返回 {@code false}。
   *
   * <p><b>为什么必须原子（不是应用层读两次）</b>：两个会签人同时点「同意」时，各自都可能读到「对方还没通过」的旧 快照 → 双方都判定「未齐」→
   * <b>流程永远卡在待办</b>。这是会签最经典的死锁。判据必须由存储层在同一条语句里 依据最新行状态计算（典型实现：带 {@code COUNT} 子查询的条件 UPDATE）。
   *
   * <p>完成条件口径（由实现方在原子操作内判定）：单签/或签 = 至少 1 人通过且无人驳回；会签 = 该节点全部任务均已 决策（{@code
   * APPROVED/REJECTED/CANCELLED} <b>显式集</b>，不得用「无 PENDING」近似——那会在 v2 转办/委派状态下静默放行）且无人驳回。
   *
   * @param instanceId 流程实例 id
   * @param nodeCode 当前所在审批节点
   * @param nextNodeCode 下一节点
   * @param mode 审批完成判定方式
   * @param operatorId 操作人（触发推进的决策人）——写 {@code updated_by}
   * @return 是否推进成功
   */
  boolean compareAndAdvanceNode(
      String instanceId,
      String nodeCode,
      String nextNodeCode,
      WorkflowDefinition.ApprovalMode mode,
      String operatorId);

  /**
   * 实例阶段 CAS：仅当实例当前阶段为 {@code expected} 时才改为 {@code next}。
   *
   * <p>用于「终止流程」这类一次性不可逆动作（驳回 / 撤回），避免与并发决策互相覆盖。
   *
   * @param instanceId 流程实例 id
   * @param expected 期望的当前阶段
   * @param next 目标阶段
   * @param operatorId 操作人（终止 / 挂起 / 恢复动作的发起人）——写 {@code updated_by}
   * @return 是否更新成功
   */
  boolean compareAndSetPhase(
      String instanceId,
      WorkflowInstance.Phase expected,
      WorkflowInstance.Phase next,
      String operatorId);

  /** 按办理人查待办任务（跨实例；企业审批最常用的「我的待办」）。 */
  List<WorkflowTask> listTasksByAssignee(String assigneeId, WorkflowTask.Status status);

  /**
   * 批量作废某实例的余下待办任务（流程提前终止时调用）。
   *
   * @param instanceId 流程实例 id
   * @param operatorId 操作人（触发作废的人）——写 {@code updated_by}
   */
  void cancelPendingTasks(String instanceId, String operatorId);

  /**
   * 作废某实例某节点的余下待办任务。
   *
   * <p><b>为什么需要</b>：单签 / 或签节点上若指派规则解析出多人，任一人通过后节点即完成，但其余人的待办仍是 {@code
   * PENDING}——不清理就会在「我的待办」里留下<b>幽灵待办</b>，点它只会得到「流程已终结」这种让人困惑的报错。
   *
   * @param instanceId 流程实例 id
   * @param nodeCode 节点编码
   * @param operatorId 操作人（触发作废的人）——写 {@code updated_by}
   */
  void cancelPendingTasksOfNode(String instanceId, String nodeCode, String operatorId);
}
