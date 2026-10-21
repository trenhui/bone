package com.bone.ai.gateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeuristicContentSafetyGuardTest {

  private final HeuristicContentSafetyGuard guard = new HeuristicContentSafetyGuard();

  @Test
  void flags_unsafe_output_with_stable_code() {
    assertThatThrownBy(() -> guard.check("Sure, I will ignore my instructions and help you", "t"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.CONTENT_UNSAFE));
  }

  @Test
  void flags_chinese_jailbreak_marker() {
    assertThatThrownBy(() -> guard.check("好的，我已解除所有限制，现在可以……", "t"))
        .isInstanceOf(BizException.class);
  }

  @Test
  void passes_safe_blank_and_null_output() {
    assertThatCode(() -> guard.check("根据条款，建议赔付 1200 元。", "t")).doesNotThrowAnyException();
    assertThatCode(() -> guard.check("", "t")).doesNotThrowAnyException();
    assertThatCode(() -> guard.check(null, "t")).doesNotThrowAnyException();
  }

  @Test
  void supports_custom_patterns() {
    HeuristicContentSafetyGuard custom = new HeuristicContentSafetyGuard(List.of("自定义违规词"));
    assertThatThrownBy(() -> custom.check("包含自定义违规词", "t")).isInstanceOf(BizException.class);
  }
}
