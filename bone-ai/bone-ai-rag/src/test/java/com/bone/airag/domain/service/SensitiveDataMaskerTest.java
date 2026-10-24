package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 写入侧 PII 字段级掩码（2026-10-24 P0-3）：等长（span 锚点不漂移）+ 局部保留（检索语义不全丢）+ Luhn 防误伤。
 *
 * <p>回归要点：任何模式的掩码都必须保持文本总长不变——否则 {@code spanStart/spanEnd} 引用锚点漂移， 「引用定位原文」契约 静默失效（最隐蔽的质量退化形态）。
 */
class SensitiveDataMaskerTest {

  private final SensitiveDataMasker masker = new SensitiveDataMasker();

  @Test
  void id_card_keeps_prefix6_and_suffix4_with_equal_length() {
    String text = "报案人身份证 11010519491231002X 住址北京";
    String masked = masker.mask(text);
    assertThat(masked).isEqualTo("报案人身份证 110105********002X 住址北京");
    assertThat(masked.length()).isEqualTo(text.length()); // 等长：span 锚点不漂移
  }

  @Test
  void phone_keeps_prefix3_and_suffix4_with_equal_length() {
    String text = "联系电话 13812345678 备用";
    String masked = masker.mask(text);
    assertThat(masked).isEqualTo("联系电话 138****5678 备用");
    assertThat(masked.length()).isEqualTo(text.length()); // 等长：span 锚点不漂移
  }

  @Test
  void bank_card_masked_only_when_luhn_passes() {
    // 4111111111111111 是 Luhn 合法卡号 → 掩码保留后 4
    String valid = "卡号 4111111111111111 到期";
    assertThat(masker.mask(valid)).isEqualTo("卡号 ************1111 到期");
    // 16 位但 Luhn 不合法（订单号 / 编号类）→ 不误伤
    String invalid = "单号 1234567890123456 已受理";
    assertThat(masker.mask(invalid)).isEqualTo("单号 1234567890123456 已受理");
  }

  @Test
  void email_masks_local_part_except_first_char_and_keeps_domain() {
    String text = "邮箱 zhangsan@example.com 请查收";
    String masked = masker.mask(text);
    assertThat(masked).isEqualTo("邮箱 z*******@example.com 请查收");
    assertThat(masked.length()).isEqualTo(text.length()); // 等长：span 锚点不漂移
  }

  @Test
  void mixed_text_masks_all_patterns_and_preserves_length() {
    String text =
        "张三 手机 13912345678 身份证 11010519491231002X 邮箱 zhangsan@example.com 卡号 4111111111111111";
    String masked = masker.mask(text);
    assertThat(masked.length()).isEqualTo(text.length()); // 等长：span 锚点不漂移
    assertThat(masked).contains("139****5678");
    assertThat(masked).contains("110105********002X");
    assertThat(masked).contains("z*******@example.com");
    assertThat(masked).contains("************1111");
    assertThat(masked).doesNotContain("13912345678");
  }

  @Test
  void plain_text_passes_through_unchanged() {
    String text = "甲方应按时付款，逾期按日千分之一计违约金。";
    assertThat(masker.mask(text)).isEqualTo(text);
    assertThat(masker.countHits(text)).isZero();
  }

  @Test
  void masked_output_creates_no_new_matches() {
    // 防级联放大：掩码段（含 *）不得再被后续模式命中而扩大掩码范围
    String text = "手机 13812345678";
    String once = masker.mask(text);
    assertThat(masker.mask(once)).isEqualTo(once);
    assertThat(masker.countHits(once)).isZero();
  }

  @Test
  void disabled_masker_is_identity() {
    SensitiveDataMasker off = SensitiveDataMasker.disabled();
    String text = "身份证 11010519491231002X";
    assertThat(off.mask(text)).isEqualTo(text);
    assertThat(off.countHits(text)).isZero();
  }

  @Test
  void count_hits_reports_per_pattern() {
    assertThat(masker.countHits("手机 13812345678 身份证 11010519491231002X")).isEqualTo(2);
    assertThat(masker.countHits("邮箱 a@b.co")).isEqualTo(1);
  }
}
