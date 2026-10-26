package com.bone.workflow.starter.outbox;

import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 状态投影意图的<b>执行器</b>：把意图翻译成平台状态机动作调用。
 *
 * <p><b>为什么经动作端点而不是直写 status</b>：{@code status} 由平台状态机（动作定义 + {@code
 * RuntimeStatusGuard}）管辖，服务端直写会被守卫拒绝，且绕开合法流转与留痕，造成「流程说批了、单据状态没动」的双真源。
 *
 * <p><b>幂等键 = 发件箱行 id（每行唯一、确定）</b>。同一行意图被重试（claim 超时重投 / 退避重试）时复用同一 id ⇒ 平台据此去重，避免「重试一次 =
 * 业务状态被推进两次」；而<b>同一实例的不同意图</b>（如 suspend→resume→suspend 产生的两条 {@code SUSPENDED}）行 id 不同 ⇒
 * 不会被错误折叠，各自的业务侧投影都生效。这是发件箱能安全重试、又不丢失合法重复意图的前提。 <b>反例（诊断 P0-2）</b>：曾用 {@code {instanceId}:{intent}}
 * 作键，会把重复出现的 {@code SUSPENDED}/{@code RESUMED} 折叠成「同一操作」，导致第二次挂起的业务侧投影丢失。
 *
 * <p><b>操作人来源</b>：审批动作的 operator 语义上应是流程发起人（而非中继进程）——经 {@link WorkflowInstanceRepository} 读实例的
 * {@code startedBy}。2026-10-24 前此处直查 {@code wf_instance}（裸 JDBC， HC-006
 * 豁免项之一），现改走引擎端口实例仓储，执行器不再持有任何 JDBC 通道。
 */
public class RuntimeActionIntentExecutor implements StatusIntentExecutor {

  private static final Logger log = LoggerFactory.getLogger(RuntimeActionIntentExecutor.class);
  private final RuntimeActionInvoker actionInvoker;
  private final WorkflowInstanceRepository instanceRepository;

  public RuntimeActionIntentExecutor(
      RuntimeActionInvoker actionInvoker, WorkflowInstanceRepository instanceRepository) {
    this.actionInvoker = Objects.requireNonNull(actionInvoker, "actionInvoker");
    this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository");
  }

  @Override
  public void execute(StatusIntent intent) {
    if (intent.actionCode() == null) {
      // SUSPENDED / RESUMED 没有对应业务动作（单据仍停在 SUBMITTED），只记录不执行。
      log.debug(
          "[workflow] 状态投影意图无需执行业务动作：intent={} instance={}", intent.intent(), intent.instanceId());
      return;
    }
    Long tenantId = intent.tenantId();
    if (tenantId == null) {
      throw new IllegalStateException("审批流状态推进缺少租户上下文——拒绝推进业务单据状态");
    }
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("workflowInstanceId", intent.instanceId());
    params.put("intent", intent.intent());

    // 幂等键 = 发件箱行 id（每个意图一行、全局唯一）。
    // 同一行重试（claim 超时 / 退避重试）复用同一 id ⇒ 平台去重；同一实例的<b>不同</b>意图（如 suspend→resume→suspend
    // 的两条 SUSPENDED）id 不同 ⇒ 不被折叠，业务侧投影各自生效。曾用 {instanceId}:{intent} 会把重复意图错误折叠（P0-2）。
    String idempotencyKey = intent.id();

    actionInvoker.invoke(
        intent.entityCode(),
        tenantId,
        intent.recordId(),
        intent.actionCode(),
        params,
        operatorOf(intent),
        idempotencyKey,
        null);
    log.info(
        "[workflow] 状态投影已落地：instance={} intent={} action={}",
        intent.instanceId(),
        intent.intent(),
        intent.actionCode());
  }

  /** 操作人取实例发起人（审批动作的 operator 语义上应是流程发起人/系统，而非中继进程）。 */
  private String operatorOf(StatusIntent intent) {
    try {
      return instanceRepository
          .findInstance(intent.instanceId())
          .map(WorkflowInstance::startedBy)
          .orElse("workflow-relay");
    } catch (RuntimeException ex) {
      return "workflow-relay";
    }
  }
}
