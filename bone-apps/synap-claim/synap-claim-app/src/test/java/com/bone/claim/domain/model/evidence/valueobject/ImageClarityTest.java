package com.bone.claim.domain.model.evidence.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 清晰度受控码解析（P0-A）判别力用例。
 *
 * <p>变异验证：把 {@link ImageClarity#fromCode} 的未知码分支改成返回 CLEAR ⇒ fail-closed 用例红； 把 null/空白回落改成抛异常或
 * UNCLEAR ⇒ 默认态用例红。
 */
class ImageClarityTest {

  @Test
  @DisplayName("三态受控码逐一可解析，别名/大小写不串味")
  void controlledCodesParseExactly() {
    assertThat(ImageClarity.fromCode("CLEAR")).isEqualTo(ImageClarity.CLEAR);
    assertThat(ImageClarity.fromCode("UNCLEAR")).isEqualTo(ImageClarity.UNCLEAR);
    assertThat(ImageClarity.fromCode("INCOMPLETE")).isEqualTo(ImageClarity.INCOMPLETE);
    assertThat(ImageClarity.values())
        .containsExactly(ImageClarity.CLEAR, ImageClarity.UNCLEAR, ImageClarity.INCOMPLETE);
  }

  @Test
  @DisplayName("null/空白回落 CLEAR：对齐 DDL DEFAULT 与存量未标注口径")
  void nullAndBlankDefaultToClear() {
    assertThat(ImageClarity.fromCode(null)).isEqualTo(ImageClarity.CLEAR);
    assertThat(ImageClarity.fromCode("")).isEqualTo(ImageClarity.CLEAR);
    assertThat(ImageClarity.fromCode("   ")).isEqualTo(ImageClarity.CLEAR);
  }

  @Test
  @DisplayName("大小写与首尾空白归一不影响受控码判定")
  void codeNormalizationIsCaseAndWhitespaceInsensitive() {
    assertThat(ImageClarity.fromCode(" clear ")).isEqualTo(ImageClarity.CLEAR);
    assertThat(ImageClarity.fromCode("unClear")).isEqualTo(ImageClarity.UNCLEAR);
    assertThat(ImageClarity.fromCode("\tIncomplete\n")).isEqualTo(ImageClarity.INCOMPLETE);
  }

  @Test
  @DisplayName("未知受控码 fail-closed：归 INCOMPLETE 而非 CLEAR，锁定守卫会阻断")
  void unknownCodeFailsClosed() {
    for (String unknown : new String[] {"BLURRY", "CLEARISH", "1", "PASS"}) {
      assertThat(ImageClarity.fromCode(unknown))
          .as("未知码 %s 绝不能静默归为可入理算态", unknown)
          .isEqualTo(ImageClarity.INCOMPLETE)
          .isNotEqualTo(ImageClarity.CLEAR);
    }
  }
}
