package com.bone.aiorch.application;

import com.bone.ai.gateway.domain.LlmRequest;
import java.util.List;
import java.util.Map;

/**
 * 编排请求（声明式）：规则预检集 + 可选 LLM 步骤 + 追踪开关。
 *
 * <p>第一批最小语义——顺序流水线：规则预检 → 短路判定 → LLM（未短路且声明时）。 状态机 / HITL 为后续批次，不在本记录内引入。
 *
 * @param tenantId 租户
 * @param ruleCodes 要评估的规则码；为空 = 评估租户内全部 ACTIVE 规则
 * @param context 求值上下文（字段值等，键为变量名；{@code traceId} 参与灰度分桶）
 * @param llmRequest 可空；非空且未被规则短路时执行 LLM 步骤
 * @param trace 是否采集判定路径追踪（回放 / 审计）
 */
public record OrchestrationRequest(
    String tenantId,
    List<String> ruleCodes,
    Map<String, Object> context,
    LlmRequest llmRequest,
    boolean trace) {

  public OrchestrationRequest {
    ruleCodes = (ruleCodes == null) ? List.of() : List.copyOf(ruleCodes);
    context = (context == null) ? Map.of() : Map.copyOf(context);
  }
}
