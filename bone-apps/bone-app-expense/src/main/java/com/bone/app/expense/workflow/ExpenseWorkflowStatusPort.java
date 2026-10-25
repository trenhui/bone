package com.bone.app.expense.workflow;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.starter.outbox.StatusIntent;
import com.bone.workflow.starter.outbox.WorkflowStatusOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 报销审批流的<b>状态投影端口</b>（覆盖 starter 默认的 {@code OutboxStatusPort}，经 {@code @ConditionalOnMissingBean}
 * 机制生效）。
 *
 * <p><b>为什么必须覆盖默认实现</b>：默认端口把 {@code APPROVED/REJECTED/WITHDRAWN}
 * 意图映射到平台动作码（approve/reject/withdraw）， 由中继异步 invokeAction 落地业务 status。但本集成中这些 phase
 * 迁移<b>全部由人工动作同链路触发</b>——平台在本模块 Handler 返回后 已统一把 status 流转到 to_status；中继稍后再 invokeAction 同一动作码会撞
 * {@code from_status} 校验（状态已流转）， 产生 FAILED 重试 8 次后留死信——每次审批都留下一行对账噪声，且没有任何正确性收益。
 *
 * <p><b>本实现口径（单一写者）</b>：业务 status 的写者是「人工动作 → 平台 updateState」这一条同步链路；本端口对全部意图<b>只记录、不
 * 调用动作</b>（{@code action_code = null}，中继 claim 后标记 DONE），让 {@code wf_status_outbox} 保持完整的投影时间线供对账，
 * 又不制造重复执行。代价：v2 若出现「无人工动作的引擎自动推进」（SLA 自动通过 / 规则自动批），需在本端口恢复对应动作码映射—— 已在方法注释钉死该前提。
 *
 * <p>发件箱仓储与 id 生成器经 {@link ObjectProvider} 惰性解析：starter 端口缺失时（宿主未接引擎基础设施）留痕退化为告警日志，
 * 不打挂上下文——留痕是尽力而为，审批主链路不依赖它。
 */
@Component
public class ExpenseWorkflowStatusPort implements WorkflowStatusPort {

  private static final Logger log = LoggerFactory.getLogger(ExpenseWorkflowStatusPort.class);

  private final ObjectProvider<WorkflowStatusOutboxRepository> outbox;
  private final ObjectProvider<WorkflowIdGenerator> idGenerator;

  public ExpenseWorkflowStatusPort(
      ObjectProvider<WorkflowStatusOutboxRepository> outbox,
      ObjectProvider<WorkflowIdGenerator> idGenerator) {
    this.outbox = outbox;
    this.idGenerator = idGenerator;
  }

  @Override
  public void markApproved(
      String entityCode, String recordId, String instanceId, String operatorId) {
    // 状态已由 approve 人工动作的平台流转落地；此处仅留痕。
    record(entityCode, recordId, instanceId, "APPROVED", null);
    log.info(
        "[workflow] 审批通过：instance={} record={}/{} operator={}（status 由人工动作同链路落地，本端口仅留痕）",
        instanceId,
        entityCode,
        recordId,
        operatorId);
  }

  @Override
  public void markRejected(
      String entityCode, String recordId, String instanceId, String operatorId, String reason) {
    // 同 markApproved：reject 人工动作已落地 status；驳回原因随意图留痕。
    record(entityCode, recordId, instanceId, "REJECTED", reason);
    log.info(
        "[workflow] 审批驳回：instance={} record={}/{} operator={} reason={}",
        instanceId,
        entityCode,
        recordId,
        operatorId,
        reason);
  }

  @Override
  public void markWithdrawn(
      String entityCode, String recordId, String instanceId, String operatorId) {
    // 同 markApproved：withdraw 人工动作已落地 status（SUBMITTED→DRAFT）。
    record(entityCode, recordId, instanceId, "WITHDRAWN", null);
    log.info(
        "[workflow] 审批撤回：instance={} record={}/{} operator={}",
        instanceId,
        entityCode,
        recordId,
        operatorId);
  }

  @Override
  public void markSuspended(String entityCode, String recordId, String instanceId, String reason) {
    // 挂起无对应业务动作（单据停在 SUBMITTED），仅留痕 + 告警——不通知运维就会变成无人知晓的死单。
    record(entityCode, recordId, instanceId, "SUSPENDED", reason);
    log.warn(
        "[workflow] 流程挂起需人工介入：entity={} record={} instance={} reason={}",
        entityCode,
        recordId,
        instanceId,
        reason);
  }

  @Override
  public void markResumed(String entityCode, String recordId, String instanceId) {
    record(entityCode, recordId, instanceId, "RESUMED", null);
    log.info("[workflow] 流程恢复续跑：instance={} record={}/{}", instanceId, entityCode, recordId);
  }

  @Override
  public void markTerminated(
      String entityCode, String recordId, String instanceId, String operatorId, String reason) {
    // 管理员强制终止：无对应业务动作（单据处置由宿主状态机决定），仅留痕 + 告警。
    record(entityCode, recordId, instanceId, "TERMINATED", reason);
    log.warn(
        "[workflow] 流程被管理员强制终止：entity={} record={} instance={} operator={} reason={}",
        entityCode,
        recordId,
        instanceId,
        operatorId,
        reason);
  }

  /** 记录一条不触发业务动作的投影意图（action_code = null，中继标记 DONE 即闭环）。 */
  private void record(
      String entityCode, String recordId, String instanceId, String intent, String paramsJson) {
    WorkflowStatusOutboxRepository repository = outbox.getIfAvailable();
    WorkflowIdGenerator generator = idGenerator.getIfAvailable();
    if (repository == null || generator == null) {
      // 留痕基础设施缺失：降级为日志（意图仍可由日志与 wf_instance.phase 对账还原），不阻断、不打挂。
      log.warn(
          "[workflow] 状态投影留痕基础设施缺失，仅记录日志：intent={} instance={} record={}/{}",
          intent,
          instanceId,
          entityCode,
          recordId);
      return;
    }
    repository.insert(
        new StatusIntent(
            generator.nextId(),
            instanceId,
            entityCode,
            recordId,
            TenantContext.getTenantIdAsLong(),
            intent,
            null,
            paramsJson,
            "NEW",
            0,
            null));
  }
}
