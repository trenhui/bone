package com.bone.workflow.ports.spi;

import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import java.time.Instant;
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

  /**
   * 保存（新建或更新）流程实例。
   *
   * @param instance 待保存的实例
   * @param operatorId 触发本次保存的操作人（驳回/撤回/恢复/审批通过/发起的当事人）——写 {@code updated_by}（新建时亦写 {@code
   *     created_by}）， 不得用租户 id 或 {@code "system"} 冒充。实例行的审计列要能回答「谁最后动了这单」，而非恒为发起人。
   */
  void saveInstance(WorkflowInstance instance, String operatorId);

  /**
   * <b>原子地</b>抢占活动槽位并落库实例：先 {@link #claimActiveInstance} 再 {@link
   * #saveInstance}，两操作必须在<b>同一事务</b>内完成， 否则「指针已插、实例未落」的中间态会让并发启动出现指针存在但实例行缺失的竞态（退化成双份审批、互相看不见）。
   *
   * <p>实现方须用 {@code @Transactional} 保证两步骤同生同死（或等价的存储层原子手段）。
   *
   * @param instance 待启动的实例（尚未落库）
   * @return true = 抢占并落库成功，调用方应继续推进；false = 已有活动实例或抢占失败（调用方应复用既有实例或报错）
   */
  boolean claimAndSaveInstance(WorkflowInstance instance);

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

  /**
   * <b>条件插入</b>任务：仅当同一 {@code (instanceId, nodeCode, assigneeId)} 下没有 {@code PENDING} 任务时才插入。
   *
   * <p><b>为什么不能「先查后插」</b>：管理员强制指派（{@code assignTask}）是可重试操作，两个管理员（或用户连点）并发对同一实例同一办理人
   * 指派时，「先查无待办再插」会双插——同人同节点两条 PENDING。会签（ALL）节点下，多余的那条 PENDING 让节点<b>永远等不齐</b> （有人已表态，幽灵任务永
   * PENDING）。判据必须由存储层在同一条语句里依据最新行状态计算（与 {@link #compareAndAdvanceNode} 同纪律）。
   *
   * @param task 任务（尚未落库）
   * @param operatorId 操作人——写审计列
   * @return true = 插入成功；false = 已有同人同节点待办（跳过，调用方不得再派单/通知）
   */
  boolean saveTaskIfAbsent(WorkflowTask task, String operatorId);

  /** 按 id 取任务。 */
  Optional<WorkflowTask> findTask(String taskId);

  /** 取某实例的全部待办任务。 */
  List<WorkflowTask> listPendingTasks(String instanceId);

  /** 取某实例某节点的全部任务（历史轨迹 / 作废待办用，<b>跨轮次</b>）。 */
  List<WorkflowTask> listTasksByNode(String instanceId, String nodeCode);

  /**
   * 取某实例某节点<b>当前轮次</b>的任务（会签完成判定的应用层复核用）。
   *
   * <p><b>为什么必须区分轮次</b>：驳回退回（{@code rejectToNode}）会让同一 {@code (instance, node)} 出现<b>多批</b>任务。
   * 若判据按全量统计，上一轮遗留的 {@code APPROVED} 会让「本轮只要一个人同意」就凑满法定人数—— 比例会签（RATIO）可被直接绕过，其余 4 人从未表态。
   *
   * <p>存储层的原子推进（{@link #compareAndAdvanceNode}）已按轮次过滤；本方法的存在是为了让引擎在「未推进」时用<b>同一口径</b>
   * 解释原因（是等人、还是该整单驳回），两处口径不一致会得出与推进结论矛盾的解释。
   */
  List<WorkflowTask> listTasksByNodeInCurrentRound(String instanceId, String nodeCode);

  /**
   * 登记一次「驳回退回」并返回<b>累计轮次</b>（循环防护计数）。
   *
   * <p><b>为什么不用「REJECTED 任务条数」充当轮次</b>：一个 5 人会签节点上有 3 人驳回会留下 3 条 {@code REJECTED}，按条数计就当成了 3
   * 轮——<b>一轮驳回 3 次</b>被当成 3 次往返，上限提前触发把单据误终止；反之同一节点被驳回 10 次但只要每次只有 1 人驳回， 又恰好卡在上限边缘。
   * 计数口径必须是「往返次数」，由存储层原子累加。
   *
   * <p>累加与读取必须原子（同一条 SQL 的 {@code RETURNING} 语义或同事务内的 UPDATE + SELECT），否则并发驳回会少计。
   *
   * @param instanceId 流程实例 id
   * @param operatorId 发起驳回退回的操作人——写 {@code updated_by}
   * @return 累加后的总轮次（≥1）
   */
  long registerRejectRound(String instanceId, String operatorId);

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
   * APPROVED/REJECTED/CANCELLED} <b>显式集</b>，不得用「无 PENDING」近似——那会在 v2 转办/委派状态下静默放行）且无人驳回； 比例会签 =
   * 已通过人数 ≥ {@code quorum}（且容忍单条驳回，只要法定人数仍可达）。
   *
   * @param instanceId 流程实例 id
   * @param nodeCode 当前所在审批节点
   * @param nextNodeCode 下一节点
   * @param mode 审批完成判定方式
   * @param quorum 比例会签法定人数（仅 {@link WorkflowDefinition.ApprovalMode#RATIO} 有意义；其余模式传 {@code null}）
   * @param operatorId 操作人（触发推进的决策人）——写 {@code updated_by}
   * @return 是否推进成功
   */
  boolean compareAndAdvanceNode(
      String instanceId,
      String nodeCode,
      String nextNodeCode,
      WorkflowDefinition.ApprovalMode mode,
      Integer quorum,
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
   * 我的待办<b>分页</b>（{@code PENDING}，创建时间倒序，limit/offset 由存储层下推）。
   *
   * <p>与 {@link #listTasksByAssignee} 的区别是「按页取」而非「全量取」：审批中心的待办列表若全量载入内存再裁页，
   * 数据量随使用时长线性膨胀，且每条还要回表取单据摘要（N+1）。排序口径由存储层统一（{@code created_at DESC, id
   * DESC}），应用层不得再重排——两处排序不一致会让翻页出现重复/漏行。
   *
   * @param assigneeId 办理人账户 id
   * @param limit 本页条数（调用方负责夹取上限）
   * @param offset 跳过条数（{@code (page-1)*size}）
   */
  List<WorkflowTask> listPendingTasksByAssignee(String assigneeId, int limit, int offset);

  /** 我的待办总数（与 {@link #listPendingTasksByAssignee} 同判据，供分页 count）。 */
  long countPendingTasksByAssignee(String assigneeId);

  /**
   * 我的已办<b>分页</b>（非 {@code PENDING}，决策时间倒序，limit/offset 下推）。
   *
   * <p>作废任务（{@code CANCELLED}）无决策时间，排序回落创建时间——口径由存储层 SQL 统一。
   */
  List<WorkflowTask> listDecidedTasksByAssignee(String assigneeId, int limit, int offset);

  /** 我的已办总数（与 {@link #listDecidedTasksByAssignee} 同判据）。 */
  long countDecidedTasksByAssignee(String assigneeId);

  /**
   * 按阶段列举实例（问题单据排障入口：{@code SUSPENDED} 死单、{@code REJECTED} 驳回单），更新时间倒序。
   *
   * <p><b>只截断不计数</b>：排障入口不是分页浏览——相位随处置实时变化，count 与列表两次查询天然不一致； 超限由上层提示「不止这些」比维护一个立刻过期的总数更诚实。
   *
   * @param phase 实例阶段
   * @param limit 最大返回条数（调用方负责夹取上限）
   */
  List<WorkflowInstance> listInstancesByPhase(WorkflowInstance.Phase phase, int limit);

  /**
   * 取某实例的<b>全部任务</b>（含已决策 / 已取消），按创建序——即该实例的审批轨迹（详情页时间线用）。
   *
   * <p>与 {@link #listPendingTasks}（只取 PENDING）不同，此处取全部，用以还原「谁在何时批了什么、走了哪步」 的完整历史。
   */
  List<WorkflowTask> listTasks(String instanceId);

  /**
   * 卡死实例检测（看门狗）：返回「仍处于 {@code RUNNING}、但当前节点已停留超过 {@code cutoff}、且<b>没有任何 PENDING 任务</b>」的实例 id。
   *
   * <p><b>典型成因</b>：节点推进后派单失败（任务 CAS 后崩溃）、或 {@code assignTask} 异常导致节点永远等不齐——
   * 这类实例<b>不会自愈</b>，会一直占着活动指针、挡住同一单据重新提交（陈旧指针把门焊死）。
   *
   * <p><b>判据特意排除「有 PENDING 任务」的实例</b>：后者是「人迟迟不审」的正常业务等待（SLA 范畴，由挂起/催办处理），
   * 看门狗不应把慢审批当成死实例告警。只报「节点停留超阈值却连一个待办都没有」的实例。
   *
   * <p><b>调用方纪律</b>：看门狗作业（{@code WorkflowInstanceWatchdog}）跨租户调用本方法，由调度线程周期性触发；
   * 本方法不得触达任何业务动作，只做只读检测。
   *
   * @param cutoff 节点进入时间（{@code node_entered_at}）不晚于此刻的实例才被视为「停留过久」
   */
  List<String> findStuckInstances(Instant cutoff);

  /**
   * 改派待办任务（管理员干预）：仅当任务仍为 {@code PENDING} 时才改办理人。
   *
   * <p><b>为什么必须是条件更新而不是先查后改</b>：办理人可能正在点「同意」。若先读到 PENDING、改完办理人，
   * 而对方几乎同时也完成了决策，就会出现「任务已办结却被改派」——新办理人拿到一个已 APPROVED 的任务，无从下手。 条件更新（{@code WHERE status =
   * 'PENDING'}）让两者互斥：改派失败即说明任务已被他人处理。
   *
   * @param taskId 任务 id
   * @param newAssigneeId 新办理人账户 id
   * @param operatorId 操作人（管理员）——写 {@code updated_by}
   * @return 是否改派成功（false = 任务已非 PENDING，已被他人处理）
   */
  boolean reassignTask(String taskId, String newAssigneeId, String operatorId);

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
