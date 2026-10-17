package com.bone.aiorch.application;

import com.bone.ai.gateway.domain.LlmRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 编排请求（声明式）：规则预检集 + 可选 LLM 步骤 + 确定性闸门所需的业务事实 + 追踪开关 + 全链 traceId。
 *
 * <p>第一批最小语义——顺序流水线：规则预检 → 短路判定 → LLM（未短路且声明时）→ 确定性闸门裁决。HITL 子域由 {@code HitlApplicationService} 承载
 * （五态 / 裁决 / 处置链），本记录负责把 {@code traceId} 贯穿规则灰度分桶 → LLM 调用 → 证据快照 → HITL 关联。
 *
 * <p><b>为何把 amount / confidence 放在请求上</b>：确定性闸门（{@link
 * com.bone.aiorch.domain.decision.DecisionGate}）
 * 需要「这次涉及的金额」与「模型自评置信度」两个业务事实才能裁决能否自动放行。它们是业务输入（金额来自报案，置信度由调用方 从结构化输出解析后回填），不属于规则求值上下文，故独立建模而非塞进
 * {@code context} map。
 *
 * @param tenantId 租户
 * @param ruleCodes 要评估的规则码；为空 = 评估租户内全部 ACTIVE 规则
 * @param context 求值上下文（字段值等，键为变量名）
 * @param llmRequest 可空；非空且未被规则短路时执行 LLM 步骤
 * @param trace 是否采集判定路径追踪（回放 / 审计）
 * @param traceId 贯穿一次业务动作全链的回放关联键；为空时编排生成（灰度分桶 / 证据回放 / HITL 关联共用同一 ID）
 * @param amount 本次赔付金额（可空 = 非金额场景；超过阈值强制人工）
 * @param confidence 模型自评置信度 0-1（可空 = 不可得，跳过置信度闸门）
 */
public record OrchestrationRequest(
    String tenantId,
    List<String> ruleCodes,
    Map<String, Object> context,
    LlmRequest llmRequest,
    boolean trace,
    String traceId,
    BigDecimal amount,
    Double confidence) {

  public OrchestrationRequest {
    ruleCodes = (ruleCodes == null) ? List.of() : List.copyOf(ruleCodes);
    context = (context == null) ? Map.of() : Map.copyOf(context);
  }

  /** 兼容构造（无显式 traceId，编排将生成）。 */
  public OrchestrationRequest(
      String tenantId,
      List<String> ruleCodes,
      Map<String, Object> context,
      LlmRequest llmRequest,
      boolean trace) {
    this(tenantId, ruleCodes, context, llmRequest, trace, null, null, null);
  }

  /** 兼容构造（显式 traceId，无闸门事实）。 */
  public OrchestrationRequest(
      String tenantId,
      List<String> ruleCodes,
      Map<String, Object> context,
      LlmRequest llmRequest,
      boolean trace,
      String traceId) {
    this(tenantId, ruleCodes, context, llmRequest, trace, traceId, null, null);
  }

  /** 带闸门事实（金额 / 置信度），traceId 由编排生成。 */
  public OrchestrationRequest(
      String tenantId,
      List<String> ruleCodes,
      Map<String, Object> context,
      LlmRequest llmRequest,
      boolean trace,
      BigDecimal amount,
      Double confidence) {
    this(tenantId, ruleCodes, context, llmRequest, trace, null, amount, confidence);
  }

  /** 生效的全链追踪 ID：调用方提供优先，缺省生成。 */
  public String effectiveTraceId() {
    return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
  }
}
