package com.bone.workflow.starter.outbox;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.sdk.WfStatusOutboxRow;
import com.bone.workflow.starter.sdk.WfStatusOutboxSdkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 状态投影发件箱仓储（SDK {@code @Sql} 通道 → {@code wf_status_outbox}）。
 *
 * <p>2026-10-24 自裸 JDBC 改写为 SDK {@code @Sql}（HC-006 豁免随之移除）。SQL 与并发口径（原子 claim / 队头守卫 / 对账判据 /
 * 保留期）随 {@link WfStatusOutboxSdkRepository} 的方法文档沉淀。
 *
 * <p>租户纪律同实例仓储：写路径由 {@link TenantContext} 显式取租户（缺失即失败关闭）；轮询 / 对账 / 清理三条
 * 跑在无上下文的调度线程，<b>刻意跨租户</b>，租户由行内 {@code tenant_id} 携带，由 {@link StatusProjectionRelay} 逐条重建上下文。
 */
public class WorkflowStatusOutboxRepository {

  /** 抢占超时阈值：超过该时长仍未落地的 CLAIMED 行视为抢占副本崩溃遗留，可被其它副本重新抢占补执行。 */
  private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

  /**
   * 对账窗口：只对「最近终态」的实例做缺投影补投。
   *
   * <p>对账补救的是「崩溃在 phase CAS 与写意图之间」这类毫秒级窗口的意图丢失；更早的丢失是数据缺陷，转人工。窗口同时是 {@link #DONE_RETENTION}
   * 的下界保护——见 {@link #purgeDone(Duration)}。
   */
  public static final Duration RECONCILE_WINDOW = Duration.ofHours(6);

  /**
   * DONE 行保留期：必须<b>大于</b> {@link #RECONCILE_WINDOW}。
   *
   * <p>DONE 行是「投影已落地」的<b>唯一证据</b>（markDone 刻意不软删）。若保留期 ≤ 对账窗口，窗口内实例的 DONE
   * 行被清后，对账会把已投影实例重判「缺投影」并<b>重复执行业务动作</b>。
   */
  public static final Duration DONE_RETENTION = Duration.ofDays(7);

  private final WfStatusOutboxSdkRepository outbox;

  public WorkflowStatusOutboxRepository(WfStatusOutboxSdkRepository outbox) {
    this.outbox = Objects.requireNonNull(outbox, "outbox");
  }

  public void insert(StatusIntent intent) {
    outbox.insertStatusIntent(
        intent.id(),
        requireTenant(),
        intent.instanceId(),
        intent.entityCode(),
        intent.recordId(),
        intent.operatorId(),
        intent.intent(),
        intent.actionCode(),
        intent.paramsJson(),
        Instant.now());
  }

  /**
   * 原子抢占一条意图：把 NEW / 到期的 FAILED / 超时的 CLAIMED 改成 CLAIMED，返回是否抢到。
   *
   * <p><b>多副本部署必須</b>：Bone 多副本下两个副本同时 poll 同一条意图会重复执行；只有 {@code claim} 成功的副本才执行， 其余副本拿到 false
   * 立即跳过——业务动作（approve/reject/…）不会被调两次。单副本崩溃在 {@code execute} 与 {@code markDone} 之间时，该行停留在
   * CLAIMED，超过 {@link #CLAIM_TIMEOUT} 后由本方法重新抢占补执行。
   */
  public boolean claim(String intentId) {
    Instant now = Instant.now();
    return outbox.claimStatusIntent(intentId, requireTenant(), now, now.minus(CLAIM_TIMEOUT)) > 0;
  }

  /**
   * 取到期意图（NEW 或已到退避时刻的 FAILED 或超时的 CLAIMED），按 {@code created_at} 升序。
   *
   * <p><b>跨租户查询</b>：中继跑在无 TenantContext 的调度线程，租户由行内 {@code tenant_id} 携带， 由 {@code
   * StatusProjectionRelay} 逐条重建上下文后再执行/落标。本方法因此不要求租户上下文。
   */
  public List<StatusIntent> pollDue(int limit) {
    Instant now = Instant.now();
    return outbox.pollDueStatusIntents(now, now.minus(CLAIM_TIMEOUT), limit).stream()
        .map(WorkflowStatusOutboxRepository::toIntent)
        .toList();
  }

  public void markDone(String intentId) {
    outbox.markStatusIntentDone(intentId, requireTenant(), Instant.now());
  }

  /**
   * 标记失败并排定下次重试；返回是否真的排上了（false = 已达重试上限，不再排期）。
   *
   * <p>达上限后留在 FAILED 交由对账/人工介入——不能无限重试，否则一个永久失败的动作会让中继空转。
   */
  public boolean markFailed(String intentId, int retryCount, int maxRetries, Instant nextRetryAt) {
    return outbox.markStatusIntentFailed(
            intentId, requireTenant(), nextRetryAt, maxRetries, Instant.now())
        > 0;
  }

  /**
   * 对账窗口内的终态实例中，<b>完全没有任何同 intent 的意图行</b>的（意图丢失，需补投）。
   *
   * <p><b>判据是「一行都没有」而不是「没有 DONE 行」</b>：意图行只要存在（NEW / CLAIMED / FAILED / DONE 任一状态），
   * 就说明投影链路已接管——在途的由中继落地，FAILED 达上限的按设计转人工。若按「没有 DONE」判定，FAILED 意图的实例会被
   * 每轮对账重复补投，把「有上限的重试」悄悄变成「无限重试」。真正需要补投的只有一种情况：phase 已写终态、但意图行 根本没落库（崩溃在两者之间）。
   *
   * <p><b>窗口</b>：{@code updated_at >= :horizon}（见 {@link #RECONCILE_WINDOW}）——DONE 行按保留期物理清理，
   * 不设窗口会把保留期外的实例永久重判缺投影。
   *
   * <p>同 {@link #pollDue(int)}：跨租户，租户由行内 {@code tenant_id} 携带。
   */
  public List<StatusIntent> findTerminalMissingProjection(int limit, Duration horizon) {
    return outbox.findTerminalMissingProjection(limit, Instant.now().minus(horizon)).stream()
        .map(WorkflowStatusOutboxRepository::toReconcileIntent)
        .toList();
  }

  /**
   * 对账补投意图的行映射——查询返回的是 {@code wf_instance} 行（<b>不是</b>发件箱行），与旧裸 JDBC 版逐字同口径：
   *
   * <ul>
   *   <li>{@code instanceId} = 实例 id（{@code i.id}；该 SELECT 不产生 instance_id 列，补投意图挂在实例自身上）；
   *   <li>{@code actionCode} 由终态 phase 经 {@link #actionCodeOf} 派生（与写入侧 {@code OutboxStatusPort} 一致，
   *       {@code TERMINATED} 显式为 {@code null}——无对应业务动作）；
   *   <li>state / retryCount / createdAt 硬编码 {@code NEW} / 0 / {@code null}（尚未入箱；{@code
   *       StatusProjectionRelay#runReconcile} 补插时再赋确定性 id）。
   * </ul>
   */
  private static StatusIntent toReconcileIntent(WfStatusOutboxRow row) {
    return new StatusIntent(
        row.getId(),
        row.getId(),
        row.getEntityCode(),
        row.getRecordId(),
        row.getTenantId(),
        null, // 对账补投没有决策人；由 RuntimeActionIntentExecutor 落成系统身份，绝不冒用发起人
        row.getIntent(),
        actionCodeOf(row.getIntent()),
        null,
        "NEW",
        0,
        null);
  }

  /**
   * 按保留期物理清理 DONE 行（防表无限增长）。
   *
   * <p>保留期必须 &gt; {@link #RECONCILE_WINDOW}（见 {@link #DONE_RETENTION}）。跨租户（调度线程无上下文）。
   */
  public int purgeDone(Duration retention) {
    return outbox.purgeDoneStatusIntents(Instant.now().minus(retention));
  }

  /**
   * 终态 → 业务动作码映射（与 {@code OutboxStatusPort} 写入侧逐字一致）。
   *
   * <p>{@code TERMINATED} 显式映射为 {@code null}（无对应业务动作，单据处置由宿主状态机决定）——与 {@code
   * OutboxStatusPort.markTerminated} 的 actionCode 一致，对账补投因此不会凭空造出一个宿主没有定义的动作。
   */
  private static String actionCodeOf(String phase) {
    return switch (phase) {
      case "APPROVED" -> "approve";
      case "REJECTED" -> "reject";
      case "WITHDRAWN" -> "withdraw";
      case "TERMINATED", "SUSPENDED", "RESUMED" -> null;
      default -> null;
    };
  }

  private Long requireTenant() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流发件箱缺少租户上下文（TenantContext）——拒绝在未知租户下读写 wf_status_outbox");
    }
    return tenantId;
  }

  private static StatusIntent toIntent(WfStatusOutboxRow row) {
    return new StatusIntent(
        row.getId(),
        row.getInstanceId(),
        row.getEntityCode(),
        row.getRecordId(),
        row.getTenantId(),
        row.getOperatorId(),
        row.getIntent(),
        row.getActionCode(),
        row.getParamsJson(),
        row.getState(),
        row.getRetryCount(),
        row.getNextRetryAt());
  }
}
