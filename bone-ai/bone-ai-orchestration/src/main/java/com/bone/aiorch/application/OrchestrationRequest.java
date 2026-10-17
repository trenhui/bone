package com.bone.aiorch.application;

import com.bone.ai.gateway.domain.LlmRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 编排请求（声明式）：规则预检集 + 可选 LLM 步骤 + 追踪开关 + 全链 traceId。
 *
 * <p>第一批最小语义——顺序流水线：规则预检 → 短路判定 → LLM（未短路且声明时）。HITL 子域由 {@code HitlApplicationService} 承载 （五态 / 裁决
 * / 处置链），本记录负责把 {@code traceId} 贯穿规则灰度分桶 → LLM 调用 → 证据快照 → HITL 关联。
 *
 * @param tenantId 租户
 * @param ruleCodes 要评估的规则码；为空 = 评估租户内全部 ACTIVE 规则
 * @param context 求值上下文（字段值等，键为变量名）
 * @param llmRequest 可空；非空且未被规则短路时执行 LLM 步骤
 * @param trace 是否采集判定路径追踪（回放 / 审计）
 * @param traceId 贯穿一次业务动作全链的回放关联键；为空时编排生成（灰度分桶 / 证据回放 / HITL 关联共用同一 ID）
 */
public record OrchestrationRequest(
    String tenantId,
    List<String> ruleCodes,
    Map<String, Object> context,
    LlmRequest llmRequest,
    boolean trace,
    String traceId) {

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
    this(tenantId, ruleCodes, context, llmRequest, trace, null);
  }

  /** 生效的全链追踪 ID：调用方提供优先，缺省生成。 */
  public String effectiveTraceId() {
    return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
  }
}
