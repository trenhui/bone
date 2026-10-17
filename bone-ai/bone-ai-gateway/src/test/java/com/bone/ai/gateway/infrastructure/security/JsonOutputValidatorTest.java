package com.bone.ai.gateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.core.exception.BizException;
import org.junit.jupiter.api.Test;

/** 结构化输出校验：合法 JSON 放行，空 / 截断 / 围栏 / 非 JSON 拒绝并带稳定码。 */
class JsonOutputValidatorTest {

  private final JsonOutputValidator validator = new JsonOutputValidator();

  @Test
  void accepts_valid_json() {
    assertThatCode(() -> validator.validate("{\"decision\":\"APPROVE\",\"confidence\":0.9}", "t"))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validate("{\"a\":[1,2,3],\"b\":null}", "t"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejects_truncated_json() {
    // 典型故障：截断于 max_tokens
    assertThatThrownBy(() -> validator.validate("{\"decision\":\"APPRO", "t"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.OUTPUT_INVALID));
  }

  @Test
  void rejects_plain_text() {
    assertThatThrownBy(() -> validator.validate("建议赔付 30000 元", "t"))
        .isInstanceOf(BizException.class);
  }

  @Test
  void rejects_markdown_fenced_json() {
    assertThatThrownBy(() -> validator.validate("```json\n{\"a\":1}\n```", "t"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("围栏");
  }

  @Test
  void rejects_blank_content() {
    assertThatThrownBy(() -> validator.validate("  ", "t")).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> validator.validate(null, "t")).isInstanceOf(BizException.class);
  }

  @Test
  void rejects_json_null_literal() {
    assertThatThrownBy(() -> validator.validate("null", "t")).isInstanceOf(BizException.class);
  }
}
