package com.bone.claim.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeContext;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeOutcome;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.NotificationPort;
import com.bone.claim.domain.model.decision.KnowledgeBasis;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 知识基据必须随决策轨迹落库：{@code capabilityReason} 只有一个码，成因有四个。
 *
 * <p>这组用例守的是「可归因性」：适配器算出了基据（改解析 / 换材料 / 补目录 / 接能力 四选一），若写侧把它丢掉，
 * 运维端点只能报「知识不可用」，等于把可处置事实降级成不可处置。变异验证：删掉 {@code out.put("knowledgeBasis", ...)} 一行，三条用例全红。
 */
class AiAssessNodeHandlerKnowledgeBasisTest {

  private static final long CLAIM = 2002L;

  private static final ClaimContextPort CONTEXT =
      claimId ->
          new ClaimContextPort.ClaimContextView(
              claimId,
              "OUTPATIENT",
              new BigDecimal("500.00"),
              true,
              true,
              new BigDecimal("0.95"),
              true);

  private static final NotificationPort NOTIFY = (level, topic, ctx, detail) -> {};

  private static final AiGatewayPort AI_OK =
      ctx ->
          new AiGatewayPort.AiAssessment(
              true, false, new BigDecimal("0.93"), List.of(), "deepseek-chat", "p");

  private static DecisionNodeOutcome run(KnowledgeRetrievalPort knowledge) {
    return new AiAssessNodeHandler(CONTEXT, AI_OK, knowledge, NOTIFY)
        .execute(new DecisionNodeContext(CLAIM, "trace-kb", null, Map.of()));
  }

  private static KnowledgeRetrievalPort unavailable(String basis) {
    return ctx -> new KnowledgeRetrievalPort.KnowledgeResult(false, BigDecimal.ZERO, basis, false);
  }

  @Test
  @DisplayName("能力不可用 → knowledgeBasis 落适配器给出的受控基据")
  void basisIsPersistedWhenUnavailable() {
    DecisionNodeOutcome outcome = run(unavailable(KnowledgeBasis.CATALOG_UNKNOWN.code()));

    assertThat(outcome.output().get("capabilityMissing")).isEqualTo(true);
    assertThat(outcome.output().get("capabilityReason")).isEqualTo("KNOWLEDGE_UNAVAILABLE");
    assertThat(outcome.output().get("knowledgeBasis")).isEqualTo("CATALOG_UNKNOWN");
  }

  @Test
  @DisplayName("基据是自由文本 / 未登记码 → 归一为 UNRECOGNIZED，不把自由文本带进审计面")
  void unknownBasisIsNormalized() {
    DecisionNodeOutcome outcome = run(unavailable("因为我觉得不行"));

    assertThat(outcome.output().get("knowledgeBasis")).isEqualTo("UNRECOGNIZED");
  }

  @Test
  @DisplayName("能力可用 → 不落 knowledgeBasis（缺失即「本节点没有能力缺失」，避免读侧误判）")
  void basisAbsentWhenAvailable() {
    DecisionNodeOutcome outcome =
        run(ctx -> new KnowledgeRetrievalPort.KnowledgeResult(true, BigDecimal.ZERO, "V", true));

    assertThat(outcome.output().get("capabilityMissing")).isEqualTo(false);
    assertThat(outcome.output().containsKey("knowledgeBasis")).isFalse();
  }
}
