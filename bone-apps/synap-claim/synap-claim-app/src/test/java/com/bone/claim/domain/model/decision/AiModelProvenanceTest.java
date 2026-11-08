package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模型来源判定：决定一条「AI 结论」能不能归因到模型。
 *
 * <p>这组用例守的是「桩被当成模型」这一类假绿：一旦桩自报的标识不再被判为 {@link AiModelProvenance#STUB}，运行期参与率
 * 与评测一致率会同时变好看，而事实上没有一个模型跑过。
 */
class AiModelProvenanceTest {

  @Test
  @DisplayName("本地桩标识判为 STUB（大小写与首尾空白不影响判定）")
  void localStubIsStub() {
    assertThat(AiModelProvenance.of("local-stub")).isEqualTo(AiModelProvenance.STUB);
    assertThat(AiModelProvenance.of("LOCAL-STUB")).isEqualTo(AiModelProvenance.STUB);
    assertThat(AiModelProvenance.of("  Local-Stub  ")).isEqualTo(AiModelProvenance.STUB);
  }

  @Test
  @DisplayName("网关内置替身按 mock 前缀判为 STUB")
  void mockModelIsStub() {
    assertThat(AiModelProvenance.of("mock-llm-client")).isEqualTo(AiModelProvenance.STUB);
    assertThat(AiModelProvenance.of("Mock")).isEqualTo(AiModelProvenance.STUB);
  }

  @Test
  @DisplayName("真实模型标识判为 PROVIDER")
  void providerModelIsProvider() {
    assertThat(AiModelProvenance.of("qwen-max")).isEqualTo(AiModelProvenance.PROVIDER);
    // 逻辑别名（网关未回传实际模型时的回落值）同样算可归因：它由适配器保证非空。
    assertThat(AiModelProvenance.of("synap-claim-ai")).isEqualTo(AiModelProvenance.PROVIDER);
  }

  @Test
  @DisplayName("缺失 / 空白模型标识判为 UNKNOWN，绝不乐观当成 PROVIDER")
  void blankModelIsUnknown() {
    assertThat(AiModelProvenance.of(null)).isEqualTo(AiModelProvenance.UNKNOWN);
    assertThat(AiModelProvenance.of("")).isEqualTo(AiModelProvenance.UNKNOWN);
    assertThat(AiModelProvenance.of("   ")).isEqualTo(AiModelProvenance.UNKNOWN);
  }

  @Test
  @DisplayName("只有 PROVIDER 能证明有模型参与")
  void onlyProviderCounts() {
    assertThat(AiModelProvenance.PROVIDER.isProvider()).isTrue();
    assertThat(AiModelProvenance.STUB.isProvider()).isFalse();
    assertThat(AiModelProvenance.UNKNOWN.isProvider()).isFalse();
  }
}
