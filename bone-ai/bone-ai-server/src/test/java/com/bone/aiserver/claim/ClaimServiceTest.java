package com.bone.aiserver.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import org.junit.jupiter.api.Test;

class ClaimServiceTest {

  private static final LlmResponse OK = new LlmResponse("ok", "m", "stop", LlmUsage.empty());

  private static LlmResponse resp(String content) {
    return new LlmResponse(content, "m", "stop", LlmUsage.empty());
  }

  @Test
  void extracts_confidence_from_json_field() {
    assertThat(ClaimService.extractConfidence(resp("{\"content\":\"ok\",\"confidence\":0.9}")))
        .isEqualTo(0.9);
  }

  @Test
  void extracts_confidence_nested_json() {
    assertThat(
            ClaimService.extractConfidence(resp("{\"result\":{\"confidence\":0.42},\"ok\":true}")))
        .isEqualTo(0.42);
  }

  @Test
  void falls_back_to_legacy_conf_text_when_no_json_field() {
    assertThat(ClaimService.extractConfidence(resp("评估结论：建议核赔 conf:0.85"))).isEqualTo(0.85);
  }

  @Test
  void missing_confidence_is_fail_closed_zero() {
    assertThat(ClaimService.extractConfidence(resp("建议核赔通过"))).isEqualTo(0.0);
    assertThat(ClaimService.extractConfidence(resp(null))).isEqualTo(0.0);
  }

  @Test
  void classifies_hitl_reason_by_deterministic_priority() {
    // AI 不可用优先于一切
    assertThat(ClaimService.classifyHitlReason(null, 0.95, "LOW"))
        .isEqualTo(ClaimReason.AI_UNAVAILABLE);
    // 高风险优先于置信度
    assertThat(ClaimService.classifyHitlReason(OK, 0.99, "HIGH")).isEqualTo(ClaimReason.HIGH_RISK);
    // 置信度分档：< 0.7 强制 / [0.7,0.85) 复核
    assertThat(ClaimService.classifyHitlReason(OK, 0.5, "LOW"))
        .isEqualTo(ClaimReason.CONFIDENCE_LOW);
    assertThat(ClaimService.classifyHitlReason(OK, 0.8, "LOW"))
        .isEqualTo(ClaimReason.CONFIDENCE_REVIEW);
  }
}
