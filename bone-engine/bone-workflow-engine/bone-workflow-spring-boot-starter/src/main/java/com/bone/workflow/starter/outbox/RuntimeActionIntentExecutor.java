package com.bone.workflow.starter.outbox;

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

  /** 无真实决策人可用时的系统身份（对账补投、历史行）。 */
  public static final String SYSTEM_ACTOR = "workflow-projection";

  private static final Logger log = LoggerFactory.getLogger(RuntimeActionIntentExecutor.class);
  private final RuntimeActionInvoker actionInvoker;

  /**
   * 只需动作调用器。
   *
   * <p>曾依赖 {@code WorkflowInstanceRepository} 反查发起人当操作人——那既<b>伪造了操作人</bf>，又给每条意图多一次
   * 数据库往返。操作人改为随意图行携带（{@code wf_status_outbox.operator_id}），该依赖随之删除。
   */
  public RuntimeActionIntentExecutor(RuntimeActionInvoker actionInvoker) {
    this.actionInvoker = Objects.requireNonNull(actionInvoker, "actionInvoker");
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
    // 来源标记：动作码与「用户点审批按钮」完全相同（宿主动作注册表只有 approve/reject 这几个），
    // 处理器必须能区分「用户在页面上的操作」与「引擎补投影」。不区分时，一个「onApprove 里再调工作流」的处理器
    // 就是自调用回路——现在靠幂等碰巧收敛，不是设计安全。
    params.put("source", "workflow-projection");

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

  /**
   * 投影动作的操作人。<b>必须是真实决策人</b>，不得回退到发起人。
   *
   * <p>曾用 {@code instance.startedBy()} 兜底：业务台账上于是出现「<b>发起人</b>执行了 approve」，而 {@code wf_task} 里
   * 记录的是另一个审批人——两处审计对不上，且错的方向恰好是最敏感的（看起来像自己批了自己的单）。
   *
   * <p>因此优先级是：行内真实操作人 → 系统身份 {@value #SYSTEM_ACTOR}。对账补投的行本来就没有决策人（对账线程只看到终态），
   * 记成系统身份是<b>诚实</b>的；记成发起人是<b>伪造</b>的。宁可让台账显示「由系统补投影」，也不能让它显示一个没做过该动作的人。
   */
  private String operatorOf(StatusIntent intent) {
    String operator = intent.operatorId();
    if (operator != null && !operator.isBlank()) {
      return operator;
    }
    return SYSTEM_ACTOR;
  }
}
