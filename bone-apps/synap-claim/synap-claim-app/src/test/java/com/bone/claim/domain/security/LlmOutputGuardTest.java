package com.bone.claim.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AI 安全③ 输出校验：白名单过滤 + 必填 + 与 OCR 交叉验证。 */
class LlmOutputGuardTest {

  @Test
  @DisplayName("字段白名单：丢弃白名单外字段")
  void whitelist() {
    Map<String, Object> out = Map.of("amount", "100.00", "hospital", "人民医院", "evil", "drop table");
    Map<String, Object> filtered =
        LlmOutputGuard.filterWhitelist(out, Set.of("amount", "hospital"));
    assertThat(filtered).containsKeys("amount", "hospital").doesNotContainKey("evil");
  }

  @Test
  @DisplayName("必填校验：缺字段被识别")
  void required() {
    Map<String, Object> out = Map.of("amount", "100.00");
    assertThat(LlmOutputGuard.requireFields(out, Set.of("amount", "hospital")))
        .containsExactly("hospital");
    assertThat(LlmOutputGuard.requireFields(out, Set.of("amount"))).isEmpty();
  }

  @Test
  @DisplayName("交叉验证：LLM 金额与 OCR 不一致 → 标记冲突（以 OCR 为准）")
  void crossCheckConflict() {
    Map<String, Object> model = Map.of("amount", "99999.00", "hospital", "人民医院");
    Map<String, Object> ocr = Map.of("amount", "100.00", "hospital", "人民医院");
    List<LlmOutputGuard.Conflict> conflicts =
        LlmOutputGuard.crossCheck(model, ocr, List.of("amount", "hospital"));
    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).field()).isEqualTo("amount");
    assertThat(conflicts.get(0).modelValue()).isEqualTo("99999.00");
    assertThat(conflicts.get(0).ocrValue()).isEqualTo("100.00");
  }

  @Test
  @DisplayName("交叉验证：数值一致（不同形态）无冲突")
  void crossCheckConsistent() {
    Map<String, Object> model = Map.of("amount", 100.0, "hospital", "人民医院");
    Map<String, Object> ocr = Map.of("amount", "100.00", "hospital", "人民医院");
    assertThat(LlmOutputGuard.crossCheck(model, ocr, List.of("amount", "hospital"))).isEmpty();
  }
}
