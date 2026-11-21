package com.bone.claim.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayPort.EvidenceLocator;
import com.bone.claim.application.port.out.AiGatewayPort.Fact;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.ClaimContextPort.EvidenceFact;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeContext;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeOutcome;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.NotificationPort;
import com.bone.claim.infrastructure.evidence.EvidenceFieldReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P0-B 判别力测试：证据定位只在材料真有坐标时产出。
 *
 * <p>变异验证：把"无坐标时返回空列表"改成填充默认页 1，末两条用例立即转红；把 extracted_json 的坐标解析删掉， 解析用例转红。
 */
class EvidenceLocatorTest {

  private static final long CLAIM = 5001L;

  private static final KnowledgeRetrievalPort KNOWLEDGE_OK =
      ctx -> new KnowledgeRetrievalPort.KnowledgeResult(true, BigDecimal.ZERO, "V", true);
  private static final NotificationPort NOTIFY = (level, topic, ctx, detail) -> {};

  @Test
  @DisplayName("AI 节点：fact 带页码/行号 → evidenceLocators 含真实坐标，aiFacts 也带坐标")
  void aiNodeEmitsLocatorsWhenCoordinatesPresent() {
    Fact fact = new Fact("invoice_no", "INV-1", "text@12:5", 3, 12);
    DecisionNodeOutcome outcome = runAi(List.of(fact));

    assertThat(outcome.output().get(AiAssessNodeHandler.KEY_EVIDENCE_LOCATORS))
        .isEqualTo(List.of(new EvidenceLocator("text@12:5", 3, 12)));
    @SuppressWarnings("unchecked")
    List<Fact> facts = (List<Fact>) outcome.output().get("aiFacts");
    assertThat(facts).hasSize(1);
    assertThat(facts.get(0).pageNo()).isEqualTo(3);
    assertThat(facts.get(0).lineNo()).isEqualTo(12);
  }

  @Test
  @DisplayName("AI 节点：无坐标 → evidenceLocators 为空，绝不编造默认页")
  void aiNodeDoesNotFabricateLocators() {
    DecisionNodeOutcome outcome = runAi(List.of(new Fact("invoice_no", "INV-1", "text@12:5")));

    assertThat(outcome.output().get(AiAssessNodeHandler.KEY_EVIDENCE_LOCATORS))
        .isEqualTo(List.of());
  }

  @Test
  @DisplayName("证据节点：从 extracted_json 解析出坐标 → evidenceLocators 含 pageNo")
  void evidenceNodeParsesCoordinatesFromExtractedJson() {
    String json =
        "{\"fields\":[{\"code\":\"invoice_no\",\"value\":\"INV-1\","
            + "\"evidenceRef\":\"text@12:5\",\"pageNo\":2,\"lineNo\":8}]}";
    List<EvidenceFieldReader.ParsedField> parsed = EvidenceFieldReader.read(json);
    assertThat(parsed).hasSize(1);
    assertThat(parsed.get(0).pageNo()).isEqualTo(2);
    assertThat(parsed.get(0).lineNo()).isEqualTo(8);

    ClaimContextPort.ClaimContextView view =
        view(List.of(new EvidenceFact("INVOICE", "invoice_no", "INV-1", "text@12:5", 2, 8)));
    DecisionNodeOutcome outcome =
        new EvidenceLoadNodeHandler(claimId -> view)
            .execute(new DecisionNodeContext(CLAIM, "trace-ev", null, Map.of()));

    assertThat(outcome.output().get("evidenceLocators"))
        .isEqualTo(List.of(new EvidenceLocator("text@12:5", 2, 8)));
  }

  @Test
  @DisplayName("证据节点：extracted_json 无坐标 / 非法坐标 → 空列表，不猜页")
  void evidenceNodeKeepsEmptyWhenCoordinatesMissingOrInvalid() {
    String json =
        "{\"fields\":[{\"code\":\"invoice_no\",\"value\":\"INV-1\","
            + "\"evidenceRef\":\"text@12:5\",\"pageNo\":0,\"lineNo\":\"x\"}]}";
    List<EvidenceFieldReader.ParsedField> parsed = EvidenceFieldReader.read(json);
    assertThat(parsed.get(0).pageNo()).isNull();
    assertThat(parsed.get(0).lineNo()).isNull();

    ClaimContextPort.ClaimContextView view =
        view(List.of(new EvidenceFact("INVOICE", "invoice_no", "INV-1", "text@12:5")));
    DecisionNodeOutcome outcome =
        new EvidenceLoadNodeHandler(claimId -> view)
            .execute(new DecisionNodeContext(CLAIM, "trace-ev2", null, Map.of()));

    assertThat(outcome.output().get("evidenceLocators")).isEqualTo(List.of());
  }

  private static DecisionNodeOutcome runAi(List<Fact> facts) {
    AiGatewayPort ai =
        ctx ->
            new AiGatewayPort.AiAssessment(
                true,
                false,
                new BigDecimal("0.93"),
                List.of(),
                "model-v",
                "prompt-v",
                facts,
                List.of(),
                List.of(),
                List.of(),
                0);
    return new AiAssessNodeHandler(claimId -> view(List.of()), ai, KNOWLEDGE_OK, NOTIFY)
        .execute(new DecisionNodeContext(CLAIM, "trace-ai", null, Map.of()));
  }

  private static ClaimContextPort.ClaimContextView view(List<EvidenceFact> facts) {
    return new ClaimContextPort.ClaimContextView(
        CLAIM,
        "OUTPATIENT",
        new BigDecimal("500.00"),
        true,
        true,
        new BigDecimal("0.95"),
        true,
        facts);
  }
}
