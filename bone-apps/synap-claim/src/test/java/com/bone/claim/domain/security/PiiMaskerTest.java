package com.bone.claim.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AI 安全① PII 遮蔽：身份证/手机号/银行卡可遮蔽且可还原，映射不落盘。 */
class PiiMaskerTest {

  @Test
  @DisplayName("身份证/手机号/银行卡被替换为占位符且可还原")
  void maskAndUnmask() {
    String text = "患者身份证 11010519491231002X 手机 13800138000 卡号 6222021234567890123 已就诊";
    MaskedText masked = PiiMasker.mask(text);

    assertThat(masked.masked()).doesNotContain("11010519491231002X");
    assertThat(masked.masked()).doesNotContain("13800138000");
    assertThat(masked.masked()).doesNotContain("6222021234567890123");
    assertThat(masked.masked()).contains("<PII:ID_CARD#");
    assertThat(masked.masked()).contains("<PII:PHONE#");
    assertThat(masked.masked()).contains("<PII:BANK_CARD#");

    // 映射表只存内存，且能完整还原
    assertThat(masked.tokenMap().size()).isEqualTo(3);
    assertThat(masked.unmask()).isEqualTo(text);
  }

  @Test
  @DisplayName("null 输入安全返回")
  void nullSafe() {
    MaskedText masked = PiiMasker.mask(null);
    assertThat(masked.masked()).isEmpty();
    assertThat(masked.unmask()).isEmpty();
  }
}
