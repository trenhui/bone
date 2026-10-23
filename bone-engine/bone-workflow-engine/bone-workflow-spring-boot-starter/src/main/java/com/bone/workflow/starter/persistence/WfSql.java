package com.bone.workflow.starter.persistence;

/**
 * 审批流持久化 SQL（{@code wf_*} 三表）。
 *
 * <p><b>为什么单独抽成常量类</b>：桥接层最关键的正确性判据是「会签完成判定与节点推进在同一条原子操作里完成」。这条 SQL 必须被测试<b>原样</b>执行到（H2
 * 真实跑），而不是在测试里另写一份「看起来一样」的 SQL——否则测试绿、生产死锁， 而没有任何机制能发现。故所有 SQL 集中在此，测试直接引用本类常量。
 *
 * <p><b>列约定</b>：遵循平台通则——主键 {@code id}、{@code tenant_id}、审计列（created_by/created_at/updated_by/
 * updated_at/deleted）、乐观锁 {@code version}。租户过滤一律显式写入 WHERE（引擎 API 不带 tenantId，由桥接层经 {@code
 * TenantContext} 补齐，保持租户隔离不因「引擎无租户概念」而失效）。
 */
public final class WfSql {

  private WfSql() {}

  // ====================== wf_instance ======================

  public static final String INSERT_INSTANCE =
      "INSERT INTO wf_instance (id, tenant_id, entity_code, record_id, def_key, def_version,"
          + " current_node_code, phase, started_by, started_at, updated_at,"
          + " created_by, created_at, updated_by, deleted, version)"
          + " VALUES (:id, :tenantId, :entityCode, :recordId, :defKey, :defVersion,"
          + " :currentNodeCode, :phase, :startedBy, :startedAt, :updatedAt,"
          + " :operatorId, :now, :operatorId, 0, 0)";

  public static final String UPDATE_INSTANCE =
      "UPDATE wf_instance SET entity_code = :entityCode, record_id = :recordId, def_key = :defKey,"
          + " def_version = :defVersion, current_node_code = :currentNodeCode, phase = :phase,"
          + " started_by = :startedBy, started_at = :startedAt, updated_at = :updatedAt,"
          + " updated_by = :operatorId, version = version + 1"
          + " WHERE id = :id AND tenant_id = :tenantId";

  public static final String SELECT_INSTANCE_BY_ID =
      "SELECT id, tenant_id, entity_code, record_id, def_key, def_version, current_node_code, phase,"
          + " started_by, started_at, updated_at"
          + " FROM wf_instance WHERE id = :id AND tenant_id = :tenantId AND deleted = 0";

  public static final String SELECT_INSTANCE_BY_RECORD =
      "SELECT id, tenant_id, entity_code, record_id, def_key, def_version, current_node_code, phase,"
          + " started_by, started_at, updated_at"
          + " FROM wf_instance"
          + " WHERE entity_code = :entityCode AND record_id = :recordId AND tenant_id = :tenantId"
          + " AND deleted = 0 ORDER BY id DESC LIMIT 1";

  /**
   * 实例阶段 CAS：仅当仍停在 {@code :expected} 阶段时才改，避免与并发决策互相覆盖。
   *
   * <p>用于「终止流程」这类一次性不可逆动作（驳回 / 撤回 / 挂起 / 恢复）。
   */
  public static final String COMPARE_AND_SET_PHASE =
      "UPDATE wf_instance SET phase = :next, updated_at = :updatedAt, updated_by = :operatorId,"
          + " version = version + 1"
          + " WHERE id = :id AND tenant_id = :tenantId AND phase = :expected";

  /**
   * <b>节点级原子推进</b>——桥接层最关键的一条 SQL。
   *
   * <p>把「该节点是否已满足完成条件」的判定，与「把实例推进到下一节点」放进<b>同一条 UPDATE</b>：只有当实例仍停在 {@code :nodeCode}、仍是
   * RUNNING、且完成条件成立时，才改写 {@code current_node_code} 并返回 1 行；否则返回 0 行。
   *
   * <p><b>为什么不能拆成「先查任务、判齐、再改实例」</b>：两个会签人同时点「同意」时，各自都会读到「对方还没通过」的旧 快照 → 双方都判「未齐」→
   * <b>流程永远卡在待办</b>（会签经典死锁，且无任何报错）。判据必须由数据库在同一条语句里 依据<b>最新</b>行状态计算。数学上可证不死锁：{@code adv} 严格晚于自己的
   * {@code taskCAS}，双方互等会导出矛盾。
   *
   * <p>完成条件口径（与端口契约一致）：
   *
   * <ul>
   *   <li>{@code AUTO}（引擎对 START / CONDITION / CC 等非审批节点传 {@code completion=null}，适配器映射为本 token）：
   *       <b>无条件移动</b>——这类节点不产生任务，任何「至少一人 APPROVED」式判据都恒不成立；
   *   <li>{@code ALL}（会签）：该节点<b>全部任务均落在已决策显式集</b>（{@code APPROVED} / {@code REJECTED} / {@code
   *       CANCELLED}）且无人驳回；
   *   <li>{@code SINGLE} / {@code ANY}（单签 / 或签）：至少 1 人 APPROVED 且无人 REJECTED （其余人的待办事后由 {@code
   *       cancelPendingTasksOfNode} 作废，不在此语句内处理）。
   * </ul>
   *
   * <p><b>为什么 ALL 用显式集而不是「无 PENDING」</b>：「无 PENDING」是 fail-open 口径——v2 引入转办 / 委派后，任务会出现 {@code
   * TRANSFERRED} 等中间态，它们不是 PENDING，于是会被当成「已决策」而<b>静默推进会签节点</b>（审批系统最严重的
   * 事故形态：有人还没表态，流程继续往下走）。显式集让未知状态落在 fail-closed：节点卡住 + 对账告警，人来处理。 代价仅仅是 多两条子查询，换的是「未知状态永不越权放行」。
   *
   * <p><b>为什么非审批节点用显式 AUTO 而不是「节点无任务即推进」</b>：后者是从数据缺失<b>推断</b>意图——审批节点的任务若被全部软删
   * （或指派解析缺陷漏建），「无任务」会让会签节点无条件放行，正是最危险的 fail-open 形态。AUTO 由引擎显式声明「本节点无完成条件」，
   * 数据缺失再也无法伪装成「可以推进」。漏了这条分支的代价见 {@code JdbcWorkflowInstanceRepositoryH2Test} 的接线用例： START 因找不到
   * APPROVED 任务而永远推不动，流程启动即静默卡死。
   */
  public static final String COMPARE_AND_ADVANCE_NODE =
      "UPDATE wf_instance SET current_node_code = :nextNodeCode, updated_at = :updatedAt,"
          + " updated_by = :operatorId, version = version + 1"
          + " WHERE id = :instanceId AND tenant_id = :tenantId"
          + "   AND current_node_code = :nodeCode"
          + "   AND phase = 'RUNNING'"
          // 任何形态下，节点内出现驳回都不得继续推进（驳回由引擎另行终止流程）
          + "   AND NOT EXISTS (SELECT 1 FROM wf_task t"
          + "       WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "         AND t.node_code = :nodeCode AND t.deleted = 0 AND t.status = 'REJECTED')"
          + "   AND ("
          + "     :mode = 'AUTO'"
          + "     OR (:mode = 'ALL'"
          + "       AND EXISTS (SELECT 1 FROM wf_task t"
          + "           WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "             AND t.node_code = :nodeCode AND t.deleted = 0)"
          + "       AND NOT EXISTS (SELECT 1 FROM wf_task t"
          + "           WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "             AND t.node_code = :nodeCode AND t.deleted = 0"
          + "             AND t.status NOT IN ('APPROVED', 'REJECTED', 'CANCELLED')))"
          + "     OR (:mode <> 'ALL' AND EXISTS (SELECT 1 FROM wf_task t"
          + "         WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "           AND t.node_code = :nodeCode AND t.deleted = 0 AND t.status = 'APPROVED'))"
          + "   )";

  // ====================== wf_status_outbox（状态投影发件箱） ======================

  /**
   * 写入一条状态投影意图。
   *
   * <p><b>为什么需要发件箱</b>：引擎改完 {@code wf_instance.phase} 后要通知平台推进业务 {@code status}。若直接同步调用
   * 动作端点，调用失败（平台动作报错 / 网络抖动 / 进程重启）就会丢失这次投影——于是出现「引擎说批了、单据状态没动」，
   * 且<b>没有任何记录可追</b>。发件箱把意图先落库，由中继重试落地，把「同步双写」变成「最终一致 + 可观测」。
   */
  public static final String INSERT_STATUS_OUTBOX =
      "INSERT INTO wf_status_outbox (id, tenant_id, instance_id, entity_code, record_id,"
          + " intent, action_code, params_json, state, retry_count, next_retry_at,"
          + " created_at, updated_at)"
          + " VALUES (:id, :tenantId, :instanceId, :entityCode, :recordId,"
          + " :intent, :actionCode, :paramsJson, 'NEW', 0, NULL, :now, :now)";

  /**
   * 取到期意图（NEW，或 FAILED 且已到退避时刻，或 CLAIMED 但已超过抢占超时阈值——视为上一次抢占副本崩溃遗留），按 id 升序——
   * <b>同实例的意图必须按序落地</b>，否则「先落地 REJECTED 再落地 APPROVED」会把终态投影颠倒。
   *
   * <p><b>原子抢占（多副本部署必須）</b>：{@code StatusProjectionRelay} 在 {@code execute} 前先调 {@link
   * WorkflowStatusOutboxRepository#claim} 把意图从 NEW/FAILED/stale-CLAIMED 改成 CLAIMED，抢到的副本才执行， 其余副本
   * {@code claim} 返回 false 即跳过——否则 Bone 多副本下两个副本同时 poll 同一条意图会<b>重复执行业务动作</b>。 单副本下若 {@code
   * execute} 成功、`markDone` 前崩溃重启，下一次 poll 会把这条 stale CLAIMED 重新抢占补执行， 故 CLAIMED 也纳入「到期」范畴（但仅限
   * {@code updated_at} 早于 {@code :staleCutoff}，避免抢占中的在途行被另一副本抢走）。
   *
   * <p><b>刻意不按租户过滤</b>：中继跑在 {@code @Scheduled} 调度线程上，该线程没有 TenantContext（ThreadLocal 不跨线程）。
   * 租户隔离改由「行内 {@code tenant_id} + 中继逐条重建上下文」保证（见 {@code StatusProjectionRelay}）——
   * 每条意图在哪个租户的上下文里执行、落标，都显式可查。若在此按单租户过滤， 无上下文线程要么抛错要么只投影一个租户，其余租户的意图永久堆积。
   */
  public static final String SELECT_DUE_STATUS_INTENTS =
      "SELECT id, tenant_id, instance_id, entity_code, record_id, intent, action_code, params_json,"
          + " state, retry_count, next_retry_at"
          + " FROM wf_status_outbox"
          + " WHERE deleted = 0"
          + "   AND (state = 'NEW'"
          + "        OR (state = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= :now))"
          + "        OR (state = 'CLAIMED' AND updated_at <= :staleCutoff))"
          + " ORDER BY id LIMIT :limit";

  /**
   * 原子抢占：把意图从 {@code NEW} / 到期的 {@code FAILED} / 超时的 {@code CLAIMED} 改成 {@code CLAIMED}。
   *
   * <p>返回影响行数 = 1 才算抢到；多副本同时 poll 同一条时仅一个副本能抢到，其余副本 {@code claim} 返回 false 即跳过，
   * 杜绝「双副本重复执行业务动作」。{@code updated_at} 在抢占时刷新，故超时判定以它为准（复用审计列，无需新增列）。
   */
  public static final String CLAIM_STATUS_INTENT =
      "UPDATE wf_status_outbox SET state = 'CLAIMED', updated_at = :now"
          + " WHERE id = :id AND tenant_id = :tenantId AND deleted = 0"
          + "   AND (state = 'NEW'"
          + "        OR (state = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= :now))"
          + "        OR (state = 'CLAIMED' AND updated_at <= :staleCutoff))";

  public static final String MARK_STATUS_INTENT_DONE =
      "UPDATE wf_status_outbox SET state = 'DONE', updated_at = :now"
          + " WHERE id = :id AND tenant_id = :tenantId AND deleted = 0";

  /**
   * 标记失败并排定下次重试（指数退避，封顶见 {@code StatusProjectionRelay}）。
   *
   * <p>用 {@code retry_count < :max} 作条件：达到上限就不再排期（留在 FAILED），交由对账作业人工介入——
   * <b>不能无限重试</b>，否则一个永久失败的动作（如动作定义被删）会让中继空转。
   *
   * <p>状态条件含 {@code CLAIMED}：执行中崩溃留下的 CLAIMED 行经 {@code claim} 重新抢占并执行失败后，由此回到 FAILED 继续退避。
   */
  public static final String MARK_STATUS_INTENT_FAILED =
      "UPDATE wf_status_outbox SET state = 'FAILED', retry_count = retry_count + 1,"
          + " next_retry_at = :nextRetryAt, updated_at = :now"
          + " WHERE id = :id AND tenant_id = :tenantId AND deleted = 0"
          + "   AND state IN ('FAILED', 'CLAIMED') AND retry_count < :maxRetries";

  /**
   * 终态实例里没有任何已落地（DONE）的终态意图 ⇒ 意图丢失（进程崩溃在 phase CAS 与写意图之间），需补投。
   *
   * <p>同 {@link #SELECT_DUE_STATUS_INTENTS}：不按租户过滤，租户由行内 {@code tenant_id} 携带（对账作业同样跑在无上下文线程）。
   */
  public static final String SELECT_TERMINAL_INSTANCES_MISSING_PROJECTION =
      "SELECT i.id, i.tenant_id, i.entity_code, i.record_id, i.phase FROM wf_instance i"
          + " WHERE i.deleted = 0"
          + "   AND i.phase IN ('APPROVED', 'REJECTED', 'WITHDRAWN')"
          + "   AND NOT EXISTS (SELECT 1 FROM wf_status_outbox o"
          + "       WHERE o.tenant_id = i.tenant_id AND o.instance_id = i.id"
          + "         AND o.state = 'DONE' AND o.intent = i.phase)"
          + " LIMIT :limit";

  // ====================== wf_active_instance（活动实例指针） ======================

  /**
   * 抢占活动实例槽位：以 {@code (tenant_id, entity_code, record_id)} 为主键插入。
   *
   * <p>插入成功 = 抢占成功；撞主键（{@code DuplicateKeyException}）= 已有活动实例。这是「重复提交不产生双实例」的
   * 数据库层保证——应用层「先查再建」在两个提交请求同时到达时会双建。
   */
  public static final String INSERT_ACTIVE_INSTANCE =
      "INSERT INTO wf_active_instance (tenant_id, entity_code, record_id, instance_id,"
          + " created_at, updated_at, deleted)"
          + " VALUES (:tenantId, :entityCode, :recordId, :instanceId, :now, :now, 0)";

  /** 释放活动实例槽位（流程进入终态，允许同一单据重新提交）。 */
  public static final String DELETE_ACTIVE_INSTANCE =
      "DELETE FROM wf_active_instance"
          + " WHERE tenant_id = :tenantId AND entity_code = :entityCode AND record_id = :recordId";

  /** 同上，但按 instance_id 删（阶段 CAS 时只知道实例 id，省两次回查）。 */
  public static final String DELETE_ACTIVE_INSTANCE_BY_INSTANCE =
      "DELETE FROM wf_active_instance WHERE tenant_id = :tenantId AND instance_id = :instanceId";

  // ====================== wf_task ======================

  public static final String INSERT_TASK =
      "INSERT INTO wf_task (id, tenant_id, instance_id, node_code, assignee_id, status,"
          + " decision, comment, due_at, created_at, updated_at, created_by, updated_by, deleted, version)"
          + " VALUES (:id, :tenantId, :instanceId, :nodeCode, :assigneeId, :status,"
          + " :decision, :comment, :dueAt, :now, :now, :operatorId, :operatorId, 0, 0)";

  public static final String SELECT_TASK_BY_ID =
      "SELECT id, tenant_id, instance_id, node_code, assignee_id, status, decision, comment,"
          + " due_at, created_at, decided_at"
          + " FROM wf_task WHERE id = :id AND tenant_id = :tenantId AND deleted = 0";

  public static final String SELECT_PENDING_TASKS =
      "SELECT id, tenant_id, instance_id, node_code, assignee_id, status, decision, comment,"
          + " due_at, created_at, decided_at"
          + " FROM wf_task"
          + " WHERE instance_id = :instanceId AND tenant_id = :tenantId AND deleted = 0"
          + "   AND status = 'PENDING' ORDER BY id";

  public static final String SELECT_TASKS_BY_NODE =
      "SELECT id, tenant_id, instance_id, node_code, assignee_id, status, decision, comment,"
          + " due_at, created_at, decided_at"
          + " FROM wf_task"
          + " WHERE instance_id = :instanceId AND tenant_id = :tenantId AND deleted = 0"
          + "   AND node_code = :nodeCode ORDER BY id";

  public static final String SELECT_TASKS_BY_ASSIGNEE =
      "SELECT id, tenant_id, instance_id, node_code, assignee_id, status, decision, comment,"
          + " due_at, created_at, decided_at"
          + " FROM wf_task"
          + " WHERE assignee_id = :assigneeId AND tenant_id = :tenantId AND deleted = 0"
          + "   AND status = :status ORDER BY id";

  /**
   * 任务状态 CAS：仅当仍是 {@code :expected} 时才改。
   *
   * <p>两个审批人同时点「同意」时，只有一方能把 PENDING 改成 APPROVED，另一方拿到 0 行——避免双写与重复推进。
   *
   * <p><b>实例终态守卫</b>：{@code completeTask} 是「读实例判终态 → 任务 CAS → 推进」三步，两步之间实例可能被并发 终止（撤回 / 驳回）。 少了这条
   * {@code EXISTS phase='RUNNING'} 守卫，已 REJECTED / WITHDRAWN 的流程上任务仍能写成 APPROVED——审计里
   * 出现「驳回流程里有人同意」，且推进 CAS 随后失败，留下永不收敛的中间态。守卫让迟到决策直接失败，引擎据此拒绝并收敛。
   */
  public static final String COMPARE_AND_SET_TASK_STATUS =
      "UPDATE wf_task SET status = :next, decision = :decision, comment = :comment,"
          + " decided_at = :decidedAt, updated_at = :updatedAt, updated_by = :operatorId,"
          + " version = version + 1"
          + " WHERE id = :id AND tenant_id = :tenantId AND status = :expected"
          + "   AND EXISTS (SELECT 1 FROM wf_instance i"
          + "       WHERE i.id = wf_task.instance_id AND i.tenant_id = wf_task.tenant_id"
          + "         AND i.deleted = 0 AND i.phase = 'RUNNING')";

  /**
   * 改派待办：仅当任务仍为 {@code PENDING} 时才改办理人。
   *
   * <p>条件更新而非先查后改：办理人可能正在点「同意」，两者交错时「改派一个已办结的任务」毫无意义， 必须让它们互斥（改派返回 0 行 = 任务已被处理）。
   */
  public static final String REASSIGN_TASK =
      "UPDATE wf_task SET assignee_id = :newAssigneeId, updated_at = :updatedAt,"
          + " updated_by = :operatorId, version = version + 1"
          + " WHERE id = :id AND tenant_id = :tenantId AND status = 'PENDING' AND deleted = 0";

  public static final String CANCEL_PENDING_TASKS =
      "UPDATE wf_task SET status = 'CANCELLED', updated_at = :updatedAt, updated_by = :operatorId,"
          + " version = version + 1"
          + " WHERE instance_id = :instanceId AND tenant_id = :tenantId AND deleted = 0"
          + "   AND status = 'PENDING'";

  public static final String CANCEL_PENDING_TASKS_OF_NODE =
      "UPDATE wf_task SET status = 'CANCELLED', updated_at = :updatedAt, updated_by = :operatorId,"
          + " version = version + 1"
          + " WHERE instance_id = :instanceId AND tenant_id = :tenantId AND deleted = 0"
          + "   AND node_code = :nodeCode AND status = 'PENDING'";

  // ====================== wf_process_def ======================

  /**
   * 取当前租户下指定流程 key 的<b>最新可用发布版</b>。
   *
   * <p>{@code enabled = 1} 是确定性规则的一半（见设计文档 §3）：运营把定义下线后，新提交不得再被它接管——
   * 漏掉这个过滤，「下线」只会影响展示，运行期仍按旧定义走，属「配了不生效」的镜像事故。
   */
  public static final String SELECT_DEF_LATEST_PUBLISHED =
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = :defKey AND tenant_id = :tenantId AND status = 'PUBLISHED'"
          + "   AND enabled = 1 AND deleted = 0 ORDER BY def_version DESC LIMIT 1";

  public static final String SELECT_DEF_BY_VERSION =
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = :defKey AND def_version = :version AND tenant_id = :tenantId"
          + "   AND deleted = 0";
}
