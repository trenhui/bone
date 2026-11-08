package com.bone.claim.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeContext;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeOutcome;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.NotificationPort;
import com.bone.claim.domain.model.decision.AiDegradationReason;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 幻觉计量的落库：{@code aiUnverifiableLocators} 必须进决策轨迹，且降级路径也带这个键。
 *
 * <p><b>为什么降级路径也要带 0</b>：读侧按 schema 稳定取数。若降级行缺这个键，「模型没跑」与「老数据没这字段」 在读侧就无法区分，健康度的 unattributed
 * 桶会被降级行污染，运维会去查一个不存在的采集缺口。
 *
 * <p>变异验证：删掉成功路径的 {@code out.put(KEY_UNVERIFIABLE_LOCATORS, ...)} ⇒ 首条用例红； 删掉降级路径的 0 ⇒ 次条用例红。
 */
class AiAssessNodeHandlerLocatorTest {

  private static final long CLAIM = 3003L;

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

  private static final KnowledgeRetrievalPort KNOWLEDGE_OK =
      ctx -> new KnowledgeRetrievalPort.KnowledgeResult(true, BigDecimal.ZERO, "V", true);

  @Test
  @DisplayName("成功路径：适配器丢弃的不可核定位条数随节点输出落库")
  void unverifiableLocatorsArePersisted() {
    AiGatewayPort ai =
        ctx ->
            new AiGatewayPort.AiAssessment(
                true,
                false,
                new BigDecimal("0.93"),
                List.of(),
                "deepseek-flash",
                "claim-assess-v5",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                2);

    DecisionNodeOutcome outcome = run(ai);

    assertThat(outcome.output().get(AiAssessNodeHandler.KEY_UNVERIFIABLE_LOCATORS)).isEqualTo(2);
  }

  @Test
  @DisplayName("降级路径：键必须出现且为 0（模型没跑≠编造 0 条，但读侧需要稳定 schema）")
  void degradedPathCarriesZero() {
    AiGatewayPort ai =
        ctx -> {
          throw new AiGatewayUnavailableException(
              "provider down", AiDegradationReason.GATEWAY_ERROR);
        };

    DecisionNodeOutcome outcome = run(ai);

    assertThat(outcome.output().get(AiAssessNodeHandler.KEY_UNVERIFIABLE_LOCATORS)).isEqualTo(0);
    assertThat(outcome.output().get("aiAvailable")).isEqualTo(false);
  }

  private static DecisionNodeOutcome run(AiGatewayPort ai) {
    return new AiAssessNodeHandler(CONTEXT, ai, KNOWLEDGE_OK, NOTIFY)
        .execute(new DecisionNodeContext(CLAIM, "trace-loc", null, Map.of()));
  }
}
