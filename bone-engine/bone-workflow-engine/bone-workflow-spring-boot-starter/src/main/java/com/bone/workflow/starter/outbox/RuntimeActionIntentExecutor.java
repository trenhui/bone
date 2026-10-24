package com.bone.workflow.starter.outbox;

import com.bone.metadata.runtime.application.RuntimeActionApplicationService;
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
 * <p><b>幂等键是确定性的</b>：{@code {instanceId}:{intent}}。中继重试同一条意图时会带上同一个键，平台据此去重， 避免「重试一次 =
 * 业务状态被推进两次」。这是发件箱能安全重试的前提——若键随机，重试就成了重复推进。
 *
 * <p><b>操作人来源</b>：审批动作的 operator 语义上应是流程发起人（而非中继进程）——经 {@link WorkflowInstanceRepository} 读实例的
 * {@code startedBy}。2026-10-24 前此处直查 {@code wf_instance}（裸 JDBC， HC-006
 * 豁免项之一），现改走引擎端口实例仓储，执行器不再持有任何 JDBC 通道。
 */
public class RuntimeActionIntentExecutor implements StatusIntentExecutor {

  private static final Logger log = LoggerFactory.getLogger(RuntimeActionIntentExecutor.class);
  private final RuntimeActionApplicationService actionService;
  private final WorkflowInstanceRepository instanceRepository;

  public RuntimeActionIntentExecutor(
      RuntimeActionApplicationService actionService,
      WorkflowInstanceRepository instanceRepository) {
    this.actionService = Objects.requireNonNull(actionService, "actionService");
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

    // 确定性幂等键：同一条意图重试共用，平台据此去重（重试 ≠ 重复推进）
    String idempotencyKey = intent.instanceId() + ":" + intent.intent();

    actionService.invokeAction(
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
