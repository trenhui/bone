package com.bone.claim.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.DecisionFlowConfigPort;
import com.bone.claim.domain.model.config.ClaimFlowConfig;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.repository.ClaimFlowConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 流程配置读取的白名单与 fail-closed 回落钉子。 */
class DecisionFlowConfigPortAdapterTest {

  private static ClaimFlowConfig config(String json) {
    return ClaimFlowConfig.create(42L, DecisionFlowConfigPort.FLOW_KEY, 1, json, Instant.now());
  }

  private static DecisionFlowConfigPortAdapter adapter(ClaimFlowConfigRepository repo) {
    return new DecisionFlowConfigPortAdapter(repo, new ObjectMapper());
  }

  @Test
  @DisplayName("无配置 → 空集（canonical 图原样执行）")
  void noConfigReturnsEmpty() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class);
    when(repo.findByFlowKey(42L, DecisionFlowConfigPort.FLOW_KEY)).thenReturn(null);
    assertThat(adapter(repo).skippableNodes(42L)).isEmpty();
  }

  @Test
  @DisplayName("配置 skipNodes=[AiAssess] → 真的读到该节点（删掉读取即转红）")
  void configuredSkipIsRead() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class);
    when(repo.findByFlowKey(42L, DecisionFlowConfigPort.FLOW_KEY))
        .thenReturn(config("{\"skipNodes\":[\"AiAssess\"]}"));
    assertThat(adapter(repo).skippableNodes(42L)).containsExactly(DecisionGraph.AI_ASSESS);
  }

  @Test
  @DisplayName("配置跳 Adjust/未知节点 → 被白名单拒绝，绝不外放金额节点")
  void nonWhitelistedNodesAreIgnored() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class);
    when(repo.findByFlowKey(42L, DecisionFlowConfigPort.FLOW_KEY))
        .thenReturn(config("{\"skipNodes\":[\"Adjust\",\"Unknown\"]}"));
    assertThat(adapter(repo).skippableNodes(42L)).isEmpty();
  }

  @Test
  @DisplayName("坏 JSON / 读失败 → 回落到不跳过（跳过是放宽，回落方向不能反过来）")
  void badJsonAndFailuresFallBackToCanonical() {
    ClaimFlowConfigRepository bad = mock(ClaimFlowConfigRepository.class);
    when(bad.findByFlowKey(42L, DecisionFlowConfigPort.FLOW_KEY)).thenReturn(config("{not-json"));
    assertThat(adapter(bad).skippableNodes(42L)).isEmpty();

    ClaimFlowConfigRepository throwing = mock(ClaimFlowConfigRepository.class);
    when(throwing.findByFlowKey(42L, DecisionFlowConfigPort.FLOW_KEY))
        .thenThrow(new IllegalStateException("DB down"));
    assertThat(adapter(throwing).skippableNodes(42L)).isEmpty();
  }
}
