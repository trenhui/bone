package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.AiProviderProbeApplicationService.ProviderProbeReport;
import com.bone.claim.application.port.out.AiProviderProbePort.ProviderProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 探活判词与处置动作的对应关系：<b>判词必须是受控集合，动作必须能被执行</b>。
 *
 * <p>守两件事：
 *
 * <ol>
 *   <li>判词不空、不落自由文本——未知原因码一律归 {@code GATEWAY_ERROR}，否则前端会拿到一个没人认识的字符串；
 *   <li>动作不能是「请检查配置」这类废话——每个受控码都必须给出与该成因对应的、互不相同的下一步。
 * </ol>
 */
class AiProviderProbeApplicationServiceTest {

  private static ProviderProbeReport probeOf(ProviderProbe probe) {
    return new AiProviderProbeApplicationService(() -> probe).probe();
  }

  @Test
  @DisplayName("未配置 → NOT_CONFIGURED，动作里给出具体环境变量名")
  void notConfigured() {
    ProviderProbeReport report = probeOf(new ProviderProbe(false, false, null, 0L, null));

    assertThat(report.verdict())
        .isEqualTo(AiProviderProbeApplicationService.VERDICT_NOT_CONFIGURED);
    assertThat(report.configured()).isFalse();
    assertThat(report.provenance()).isNull();
    assertThat(report.action()).contains("BONE_AI_GATEWAY_BASE_URL");
  }

  @Test
  @DisplayName("通 → REACHABLE，来源按模型标识判定为 PROVIDER")
  void reachable() {
    ProviderProbeReport report =
        probeOf(new ProviderProbe(true, true, "deepseek-chat", 123L, null));

    assertThat(report.verdict()).isEqualTo(AiProviderProbeApplicationService.VERDICT_REACHABLE);
    assertThat(report.provenance()).isEqualTo("PROVIDER");
    assertThat(report.latencyMs()).isEqualTo(123L);
    assertThat(report.reason()).isNull();
  }

  @Test
  @DisplayName("替身模型标识即使 reachable 也标 STUB（口径与运行期健康度一致）")
  void stubModelIsLabelledStub() {
    ProviderProbeReport report = probeOf(new ProviderProbe(true, true, "mock-llm", 10L, null));

    assertThat(report.provenance()).isEqualTo("STUB");
  }

  @Test
  @DisplayName("各受控原因码给出互不相同的处置动作")
  void actionsAreDistinctPerReason() {
    String mock = probeOf(new ProviderProbe(true, false, null, 5L, "MOCK_NOT_ALLOWED")).action();
    String contract =
        probeOf(new ProviderProbe(true, false, null, 5L, "OUTPUT_CONTRACT_VIOLATION")).action();
    String timeout = probeOf(new ProviderProbe(true, false, null, 5L, "PROVIDER_TIMEOUT")).action();
    String other = probeOf(new ProviderProbe(true, false, null, 5L, "GATEWAY_ERROR")).action();

    assertThat(mock).contains("替身");
    assertThat(contract).contains("JSON");
    assertThat(timeout).contains("超时");
    assertThat(other).contains("网关");
    assertThat(java.util.Set.of(mock, contract, timeout, other)).hasSize(4);
  }

  @Test
  @DisplayName("原因码是自由文本 / 缺失 → 判词归 GATEWAY_ERROR，绝不把未知串透给前端")
  void unknownReasonFallsBackToGatewayError() {
    assertThat(probeOf(new ProviderProbe(true, false, null, 1L, "boom")).verdict())
        .isEqualTo("GATEWAY_ERROR");
    assertThat(probeOf(new ProviderProbe(true, false, null, 1L, null)).verdict())
        .isEqualTo("GATEWAY_ERROR");
  }

  @Test
  @DisplayName("口径声明随响应下发：通 ≠ 准")
  void scopeNoteIsShipped() {
    ProviderProbeReport report = probeOf(new ProviderProbe(true, true, "m", 1L, null));

    assertThat(report.note()).contains("不证明模型判断准确");
  }
}
