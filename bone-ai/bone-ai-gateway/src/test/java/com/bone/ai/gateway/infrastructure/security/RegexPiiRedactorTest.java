package com.bone.ai.gateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * PII 脱敏规则（OWASP LLM02）：掩码正确性 + 校验位误伤控制 + 开关直通。
 *
 * <p>测试号码均为<b>校验位合法</b>的真实格式样例——校验位闸门不通过时不脱敏，故号码本身必须合法才能测到掩码路径。
 */
class RegexPiiRedactorTest {

  /** ISO 7064 MOD 11-2 校验通过的中国居民身份证样例。 */
  private static final String ID_CARD = "11010519491231002X";

  /** Luhn 校验通过的卡号样例。 */
  private static final String CARD = "4111111111111111";

  private final RegexPiiRedactor redactor = new RegexPiiRedactor(true);

  @Test
  void masks_chinese_id_card_keeping_keystroke() {
    String out = redactor.redact("张先生身份证 " + ID_CARD + " 已核验");
    assertThat(out).isEqualTo("张先生身份证 [身份证]110105********002X 已核验");
    assertThat(out).doesNotContain(ID_CARD);
  }

  @Test
  void masks_mobile_phone_keeping_operator_hint() {
    String out = redactor.redact("联系方式 13812348000 请回电");
    assertThat(out).isEqualTo("联系方式 [手机号]138****8000 请回电");
    assertThat(out).doesNotContain("13812348000");
  }

  @Test
  void masks_card_number_keeping_head_and_tail() {
    String out = redactor.redact("扣款卡号 " + CARD + " 已冻结");
    assertThat(out).doesNotContain(CARD);
    assertThat(out).contains("[卡号]");
  }

  @Test
  void masks_email_keeping_local_head() {
    String out = redactor.redact("邮箱 zhangsan@example.com 已验证");
    assertThat(out).isEqualTo("邮箱 [邮箱]z*** 已验证");
    assertThat(out).doesNotContain("zhangsan@example.com");
  }

  @Test
  void masks_ipv4_keeping_first_octet() {
    String out = redactor.redact("来源 IP 192.168.13.37 已拦截");
    assertThat(out).isEqualTo("来源 IP [IP]192.***.***.*** 已拦截");
  }

  @Test
  void keeps_order_id_and_amount_untouched() {
    // 误伤控制：订单号/金额虽位数落在卡号区间，但 Luhn 不通过 → 不脱敏（否则模型失去判定依据）
    String input = "订单 SO20261017123456789 金额 1234567890123456 页码 12";
    assertThat(redactor.redact(input)).isEqualTo(input);
  }

  @Test
  void keeps_id_with_broken_checksum_untouched() {
    // 校验位错误 → 不当作身份证处理（避免把业务编号误标为身份证）
    String input = "编号 110101199001011234";
    assertThat(redactor.redact(input)).isEqualTo(input);
  }

  @Test
  void leaves_text_without_pii_unchanged() {
    assertThat(redactor.redact("本次核赔建议人工复核")).isEqualTo("本次核赔建议人工复核");
  }

  @Test
  void disabled_redactor_is_passthrough() {
    RegexPiiRedactor off = new RegexPiiRedactor(false);
    assertThat(off.enabled()).isFalse();
    assertThat(off.redact("身份证 " + ID_CARD)).isEqualTo("身份证 " + ID_CARD);
  }

  @Test
  void handles_null_and_empty() {
    assertThat(redactor.redact(null)).isNull();
    assertThat(redactor.redact("")).isEmpty();
  }

  @Test
  void masks_multiple_pii_in_one_text() {
    String out = redactor.redact("联系人张三，手机 13812348000，证件 " + ID_CARD);
    assertThat(out).doesNotContain("13812348000").doesNotContain(ID_CARD);
  }
}
