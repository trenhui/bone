package com.bone.procurement.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** 就绪度 Web 行为测试：不反射注解，而是真实调用端点响应，证明默认不 AVAILABLE、评测未转正不声称达标、取数失败 UNKNOWN、未知判词原样透传。 */
class AiReadinessWebBehaviorTest {

  @Test
  void 默认配置不返回AVAILABLE() {
    AiReadinessApplicationService service = mock(AiReadinessApplicationService.class);
    when(service.readiness()).thenReturn(AiReadinessView.unknown("PROVIDER_NOT_CONFIGURED"));
    AiReadinessView view = new AiReadinessController(service).readiness().getData();

    assertThat(view.verdict()).isNotEqualTo(AiReadinessVerdict.AVAILABLE);
    assertThat(view.reasons()).contains("PROVIDER_NOT_CONFIGURED");
    assertThat(view.deploymentGatesPassed()).isFalse();
  }

  @Test
  void 评测未转正不声称达标() {
    AiReadinessApplicationService service = mock(AiReadinessApplicationService.class);
    when(service.readiness()).thenReturn(AiReadinessView.unknown("EVAL_NOT_FROZEN"));
    AiReadinessView view = new AiReadinessController(service).readiness().getData();

    assertThat(view.toString()).doesNotContain("效果达标", "模型准确");
    assertThat(view.authorizationNotice()).contains("不等于");
  }

  @Test
  void 取数失败判UNKNOWN() {
    AiReadinessApplicationService service = mock(AiReadinessApplicationService.class);
    when(service.readiness()).thenReturn(AiReadinessView.unknown("FACT_COLLECTION_FAILED"));
    AiReadinessView view = new AiReadinessController(service).readiness().getData();

    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.UNKNOWN);
    assertThat(view.verdictText()).isEqualTo("UNKNOWN");
    assertThat(view.reasons()).contains("FACT_COLLECTION_FAILED");
  }

  @Test
  void 未知判词原样透传() {
    AiReadinessApplicationService service = mock(AiReadinessApplicationService.class);
    AiReadinessView odd =
        new AiReadinessView(
            AiReadinessVerdict.UNKNOWN,
            "SOME_UNMAPPED_CODE",
            false,
            false,
            false,
            false,
            false,
            false,
            AiReadinessView.DEFAULT_NOTICE,
            java.util.List.of("RAW_CODE"));
    when(service.readiness()).thenReturn(odd);
    AiReadinessView view = new AiReadinessController(service).readiness().getData();

    assertThat(view.verdictText()).isEqualTo("SOME_UNMAPPED_CODE");
    assertThat(view.reasons()).containsExactly("RAW_CODE");
  }
}
