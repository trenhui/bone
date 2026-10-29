package com.bone.claim.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeContext;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeOutcome;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.NotificationPort;
import com.bone.claim.domain.model.decision.DecisionNodeStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 决策节点 {@code AiAssess} 的三态能力契约测试。
 *
 * <p>锁定的不变量：
 *
 * <ol>
 *   <li><b>无法判定 ≠ 拒赔</b>：知识检索能力未接入（{@code available=false}）时，节点只能报 {@code capabilityMissing=true}，
 *       {@code rejectSuggested} 必须保持 {@code false}——拒赔会触发《保险法》24 条通知、申诉通道与监管投诉计量， 而转人工只是进队列；
 *   <li><b>不利结论必须有依据</b>：只有"能力可用且核验为临床不合理"或"AI 自身给出带理由的拒赔建议"才置 {@code rejectSuggested=true}；
 *   <li><b>降级不改变口径</b>：AI 网关挂掉（FALLBACK）同样不得把"知识未接入"写成拒赔。
 * </ol>
 *
 * <p>变异验证：把 {@code rejectSuggested} 的赋值改回 {@code a.rejectSuggested() || !knowledgeConfirmed}， 用例 1
 * 与 4 必红。
 */
class AiAssessNodeHandlerTest {

  private static final long CLAIM = 1001L;

  private static final ClaimContextPort CONTEXT =
      claimId ->
          new ClaimContextPort.ClaimContextView(
              claimId,
              "OUTPATIENT",
              new BigDecimal("800.00"),
              true,
              true,
              new BigDecimal("0.95"),
              true);

  /** AI 网关：不建议拒赔、置信度 0.93。 */
  private static AiGatewayPort aiNoReject() {
    return ctx ->
        new AiGatewayPort.AiAssessment(true, false, new BigDecimal("0.93"), List.of(), "m", "p");
  }

  /** AI 网关：明确建议拒赔并给出理由码。 */
  private static AiGatewayPort aiReject() {
    return ctx ->
        new AiGatewayPort.AiAssessment(
            true, true, new BigDecimal("0.93"), List.of("R-DIR-02"), "m", "p");
  }

  /** AI 网关：不可用（降级 FALLBACK）。 */
  private static AiGatewayPort aiDown() {
    return ctx -> {
      throw new com.bone.claim.application.port.out.AiGatewayUnavailableException("down");
    };
  }

  private static KnowledgeRetrievalPort knowledge(boolean available, boolean reasonable) {
    return ctx ->
        new KnowledgeRetrievalPort.KnowledgeResult(reasonable, BigDecimal.ZERO, "b", available);
  }

  /** 告警记录（每用例前清空，避免跨用例顺序依赖导致的假绿/假红）。 */
  private final List<String> alerts = new ArrayList<>();

  private final NotificationPort notify =
      (level, topic, ctx, detail) -> alerts.add(level + ":" + topic);

  @BeforeEach
  void clearAlerts() {
    alerts.clear();
  }

  private DecisionNodeOutcome run(AiGatewayPort ai, KnowledgeRetrievalPort k) {
    return new AiAssessNodeHandler(CONTEXT, ai, k, notify)
        .execute(new DecisionNodeContext(CLAIM, "trace-1", null, Map.of()));
  }

  @Test
  @DisplayName("知识能力未接入 → rejectSuggested=false + capabilityMissing（无法判定不等于拒赔）")
  void knowledgeMissingIsNotReject() {
    DecisionNodeOutcome outcome = run(aiNoReject(), knowledge(false, false));

    assertThat(outcome.status()).isEqualTo(DecisionNodeStatus.SUCCEEDED);
    assertThat(outcome.output().get("rejectSuggested")).isEqualTo(false);
    assertThat(outcome.output().get("knowledgeAvailable")).isEqualTo(false);
    assertThat(outcome.output().get("capabilityMissing")).isEqualTo(true);
    assertThat(outcome.output().get("capabilityReason")).isEqualTo("KNOWLEDGE_UNAVAILABLE");
    // 能力缺失不是风险：不得据此发"AI 风险"预警（否则告警口径被"没接线"污染）
    assertThat(alerts).doesNotContain("WARN:AI_RISK");
  }

  @Test
  @DisplayName("知识能力可用且临床不合理 → rejectSuggested=true（有依据的不利结论）+ capabilityMissing=false")
  void clinicallyUnreasonableIsReject() {
    DecisionNodeOutcome outcome = run(aiNoReject(), knowledge(true, false));

    assertThat(outcome.output().get("rejectSuggested")).isEqualTo(true);
    assertThat(outcome.output().get("capabilityMissing")).isEqualTo(false);
    assertThat(outcome.output().containsKey("capabilityReason")).isFalse();
    assertThat(alerts).contains("WARN:AI_RISK");
  }

  @Test
  @DisplayName("知识能力可用且临床合理 → 不放行拒赔、不标能力缺失")
  void reasonableIsClean() {
    DecisionNodeOutcome outcome = run(aiNoReject(), knowledge(true, true));

    assertThat(outcome.output().get("rejectSuggested")).isEqualTo(false);
    assertThat(outcome.output().get("capabilityMissing")).isEqualTo(false);
  }

  @Test
  @DisplayName("AI 建议拒赔（知识亦合理）→ 仍采信 AI 的有依据拒赔")
  void aiRejectIsHonouredEvenWhenKnowledgeClean() {
    DecisionNodeOutcome outcome = run(aiReject(), knowledge(true, true));

    assertThat(outcome.output().get("rejectSuggested")).isEqualTo(true);
    assertThat(outcome.output().get("rejectReasons")).isEqualTo(List.of("R-DIR-02"));
  }

  @Test
  @DisplayName("AI 网关不可用 + 知识未接入 → FALLBACK 且仍不得落成拒赔")
  void aiDownDoesNotTurnIntoReject() {
    DecisionNodeOutcome outcome = run(aiDown(), knowledge(false, false));

    assertThat(outcome.status()).isEqualTo(DecisionNodeStatus.FALLBACK);
    assertThat(outcome.output().get("rejectSuggested")).isEqualTo(false);
    assertThat(outcome.output().get("aiAvailable")).isEqualTo(false);
    assertThat(outcome.output().get("capabilityMissing")).isEqualTo(true);
  }
}
