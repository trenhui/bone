package com.bone.workflow.starter.outbox;

import com.bone.workflow.ports.spi.WorkflowStatusPort;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 状态推进端口的<b>发件箱实现</b>：把引擎的状态意图写入 {@code wf_status_outbox}，由中继异步落地。
 *
 * <p><b>与直接调用实现的取舍</b>：直接同步调用更简单，但平台动作失败（报错 / 网络抖动 / 进程重启）会让这次投影
 * <b>静默丢失</b>——出现「引擎说批了、单据状态没动」且无迹可查。发件箱把意图先落库，失败可重试、可观测、可对账。
 *
 * <p><b>代价（必须知道）</b>：状态投影变成<b>最终一致</b>，不再是同步生效。审批通过到单据状态变更之间有一个中继周期
 * （秒级）。对「审批通过后立刻要能收款」这类强同步诉求，应改为直接调用实现。
 */
public class OutboxStatusPort implements WorkflowStatusPort {

  private static final Logger log = LoggerFactory.getLogger(OutboxStatusPort.class);

  private final WorkflowStatusOutboxRepository outbox;
  private final java.util.function.Supplier<String> idGenerator;

  public OutboxStatusPort(
      WorkflowStatusOutboxRepository outbox, java.util.function.Supplier<String> idGenerator) {
    this.outbox = Objects.requireNonNull(outbox, "outbox");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  @Override
  public void markApproved(
      String entityCode, String recordId, String instanceId, String operatorId) {
    enqueue(instanceId, entityCode, recordId, operatorId, "APPROVED", "approve", null);
  }

  @Override
  public void markRejected(
      String entityCode, String recordId, String instanceId, String operatorId, String reason) {
    enqueue(instanceId, entityCode, recordId, operatorId, "REJECTED", "reject", reason);
  }

  @Override
  public void markWithdrawn(
      String entityCode, String recordId, String instanceId, String operatorId) {
    enqueue(instanceId, entityCode, recordId, operatorId, "WITHDRAWN", "withdraw", null);
  }

  @Override
  public void markSuspended(String entityCode, String recordId, String instanceId, String reason) {
    // 无对应业务动作（单据仍停在 SUBMITTED），仅记录意图供审计与对账。
    enqueue(instanceId, entityCode, recordId, null, "SUSPENDED", null, reason);
    log.warn(
        "[workflow] 流程挂起需人工介入：entity={} record={} instance={} reason={}",
        entityCode,
        recordId,
        instanceId,
        reason);
  }

  @Override
  public void markResumed(String entityCode, String recordId, String instanceId) {
    enqueue(instanceId, entityCode, recordId, null, "RESUMED", null, null);
  }

  @Override
  public void markTerminated(
      String entityCode, String recordId, String instanceId, String operatorId, String reason) {
    // 无对应业务动作（单据处置由宿主状态机决定）；仅记录意图供审计与对账。
    enqueue(instanceId, entityCode, recordId, operatorId, "TERMINATED", null, reason);
    log.warn(
        "[workflow] 流程被管理员强制终止：entity={} record={} instance={} operator={} reason={}",
        entityCode,
        recordId,
        instanceId,
        operatorId,
        reason);
  }

  /**
   * 入箱一条意图。
   *
   * @param operatorId <b>真实操作人</b>（做出该决策的人）；挂起 / 恢复没有操作人语义，传 {@code null}
   */
  private void enqueue(
      String instanceId,
      String entityCode,
      String recordId,
      String operatorId,
      String intent,
      String actionCode,
      String paramsJson) {
    outbox.insert(
        new StatusIntent(
            idGenerator.get(),
            instanceId,
            entityCode,
            recordId,
            com.bone.core.tenant.context.TenantContext.getTenantIdAsLong(),
            operatorId,
            intent,
            actionCode,
            paramsJson,
            "NEW",
            0,
            null));
    log.info(
        "[workflow] 状态投影意图已入箱：instance={} intent={} action={}", instanceId, intent, actionCode);
  }
}
