package com.bone.ai.gateway.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import org.junit.jupiter.api.Test;

class JsonFieldConfidenceTest {

  private final JsonFieldConfidence extractor = new JsonFieldConfidence();

  private LlmResponse resp(String content) {
    return new LlmResponse(content, "m", "stop", LlmUsage.empty());
  }

  @Test
  void extracts_confidence_from_json_field() {
    assertThat(extractor.confidenceOf(resp("{\"content\":\"ok\",\"confidence\":0.42}")))
        .isEqualTo(0.42);
  }

  @Test
  void non_json_or_missing_field_is_undeterminable() {
    // 无 confidence 字段 / 非 JSON / 空 → -1（无法判定，级联据此不升级）
    assertThat(extractor.confidenceOf(resp("{\"answer\":\"yes\"}"))).isEqualTo(-1);
    assertThat(extractor.confidenceOf(resp("plain text"))).isEqualTo(-1);
    assertThat(extractor.confidenceOf(resp(null))).isEqualTo(-1);
  }

  @Test
  void out_of_range_confidence_is_undeterminable() {
    assertThat(extractor.confidenceOf(resp("{\"confidence\":1.5}"))).isEqualTo(-1);
    assertThat(extractor.confidenceOf(resp("{\"confidence\":-0.2}"))).isEqualTo(-1);
  }
}
