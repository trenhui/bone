package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.bone.claim.application.AiRuntimeHealthApplicationService.AiRuntimeHealth;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 能力缺失的二级归因：{@code KNOWLEDGE_UNAVAILABLE} 之下的四类成因必须分桶。
 *
 * <p><b>为什么单独成组用例</b>：只有一级码时，「解析没出事实」「只有票面事实」「目录未收录」「能力仍是桩」在报表上是同一行， 处置动作却互斥（改解析 / 换材料 / 补目录 /
 * 接能力）。这类「合并不影响计数、只影响可处置性」的缺陷不会被任何总量断言发现， 必须逐桶断言。
 *
 * <p><b>两个「不知道」桶刻意独立</b>：{@code knowledgeBasisUnattributed}（历史行没这个字段）与 {@code
 * UNRECOGNIZED}（有码但不认识）都是信息缺口，但成因不同（老数据 vs 版本错配）。把它们并进任何一个具名成因， 都会让运维照着一个不存在的原因去改东西。
 */
class AiRuntimeHealthKnowledgeBasisTest {

  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

  @Test
  @DisplayName("四类成因各自成桶，不被合并")
  void basisIsAggregatedPerCause() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(
                capability("NO_PARSED_FACTS"),
                capability("NO_PARSED_FACTS"),
                capability("CATALOG_UNKNOWN"),
                capability("INSUFFICIENT_CLINICAL_FACTS"),
                // 能力可用：不进任何基据桶
                node(
                    "{\"aiAvailable\":true,\"aiModel\":\"qwen-max\",\"capabilityMissing\":false}")),
            FROM,
            TO);

    assertThat(health.capabilityMissing()).isEqualTo(4);
    assertThat(health.knowledgeBasisByReason())
        .containsEntry("NO_PARSED_FACTS", 2)
        .containsEntry("CATALOG_UNKNOWN", 1)
        .containsEntry("INSUFFICIENT_CLINICAL_FACTS", 1)
        .hasSize(3);
    assertThat(health.knowledgeBasisUnattributed()).isZero();
  }

  @Test
  @DisplayName("历史行没基据字段 → 单列，不并入任何具名成因")
  void missingBasisGoesToItsOwnBucket() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(
                node(
                    "{\"aiAvailable\":true,\"aiModel\":\"qwen-max\",\"capabilityMissing\":true,"
                        + "\"capabilityReason\":\"KNOWLEDGE_UNAVAILABLE\"}")),
            FROM,
            TO);

    assertThat(health.knowledgeBasisUnattributed()).isEqualTo(1);
    assertThat(health.knowledgeBasisByReason()).isEmpty();
  }

  @Test
  @DisplayName("码不认识 → UNRECOGNIZED（证明写了码但版本错配），不是丢弃")
  void unrecognizedBasisIsKept() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(capability("FROM_A_NEWER_BUILD")), FROM, TO);

    assertThat(health.knowledgeBasisByReason()).containsEntry("UNRECOGNIZED", 1);
    assertThat(health.knowledgeBasisUnattributed()).isZero();
  }

  @Test
  @DisplayName("无能力缺失 → 映射为空（空集不等于某类成因计数为 0）")
  void emptyWhenNoCapabilityMissing() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(node("{\"aiAvailable\":true,\"aiModel\":\"qwen-max\"}")), FROM, TO);

    assertThat(health.capabilityMissing()).isZero();
    assertThat(health.knowledgeBasisByReason()).isEmpty();
    assertThat(health.knowledgeBasisUnattributed()).isZero();
  }

  /** 能力缺失节点替身（DecisionTraceNode 构造器包级私有，只能从 getter 注入）。 */
  private static DecisionTraceNode capability(String basis) {
    return node(
        "{\"aiAvailable\":true,\"aiModel\":\"qwen-max\",\"capabilityMissing\":true,"
            + "\"capabilityReason\":\"KNOWLEDGE_UNAVAILABLE\",\"knowledgeBasis\":\""
            + basis
            + "\"}");
  }

  private static DecisionTraceNode node(String output) {
    DecisionTraceNode node = mock(DecisionTraceNode.class);
    lenient().when(node.getOutput()).thenReturn(output);
    return node;
  }
}
