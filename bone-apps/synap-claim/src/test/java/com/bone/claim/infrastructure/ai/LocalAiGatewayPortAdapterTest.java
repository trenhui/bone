package com.bone.claim.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.claim.application.port.out.AiGatewayPort.AiAssessment;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort.ClaimContextView;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * v1.3 AI 原生：本地桩 {@link LocalAiGatewayPortAdapter} 最小确定性认知验证。
 *
 * <p>本地模式无真实模型与单证 IR，仅从已有的结构化上下文派生最小 {@code facts}（使演示轨迹非空、AI 事实卡片可展示）； {@code evidenceRefs} /
 * {@code clauseRefs} / {@code discrepancies} 需单证解析结果，本地桩不伪造（保持空）。模型标 {@code local-stub}
 * 防误读为已接入真实模型。
 */
class LocalAiGatewayPortAdapterTest {

  private final LocalAiGatewayPortAdapter adapter = new LocalAiGatewayPortAdapter();

  private static ClaimContextView ctx(
      boolean hasEvidence, boolean allParsed, BigDecimal ocr, boolean allVerify) {
    return new ClaimContextView(
        1L, "MEDICAL", new BigDecimal("3000"), hasEvidence, allParsed, ocr, allVerify);
  }

  @Test
  void local_stub_derives_minimal_facts_and_reports_local_model() {
    AiAssessment a = adapter.assess(ctx(true, true, new BigDecimal("0.95"), true));

    assertThat(a.available()).isTrue();
    assertThat(a.hasModelTrace()).isTrue();
    assertThat(a.modelVersion()).isEqualTo("local-stub");
    assertThat(a.promptVersion()).isEqualTo("local-stub-v1");
    // 感知层：从结构化上下文派生最小事实，使演示轨迹非空
    assertThat(a.hasCognitiveOutput()).isTrue();
    assertThat(a.facts()).isNotEmpty();
    // 无单证 IR：不伪造证据定位 / 条款引用 / 差异提示
    assertThat(a.evidenceRefs()).isEmpty();
    assertThat(a.clauseRefs()).isEmpty();
    assertThat(a.discrepancies()).isEmpty();
  }

  @Test
  void evidence_not_all_verified_emits_evidence_verify_fact() {
    AiAssessment a = adapter.assess(ctx(true, true, new BigDecimal("0.95"), false));

    assertThat(a.facts())
        .anyMatch(f -> "EVIDENCE_VERIFY".equals(f.code()) && "NOT_ALL_PASSED".equals(f.value()));
  }

  @Test
  void disabled_stub_throws_unavailable() {
    adapter.setEnabled(false);

    assertThrows(
        AiGatewayUnavailableException.class, () -> adapter.assess(ctx(true, true, null, true)));
  }
}
