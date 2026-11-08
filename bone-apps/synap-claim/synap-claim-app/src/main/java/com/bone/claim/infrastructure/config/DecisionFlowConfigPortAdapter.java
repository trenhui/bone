package com.bone.claim.infrastructure.config;

import com.bone.claim.application.port.out.DecisionFlowConfigPort;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.repository.ClaimFlowConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 从 {@code t_claim_flow_config} 读取决策图流程配置。
 *
 * <p><b>为什么不改仓储/DDL</b>：{@code findByFlowKey} 已按 {@code active_flag=1} 取最大 {@code
 * config_version}，表与唯一键均已存在（M2 的 DDL 属 L4，本轮不碰）。
 *
 * <p><b>白名单</b>：只有 {@link DecisionGraph#AI_ASSESS}（唯一的 LLM 节点）可跳过； 其余节点名一律忽略并留 WARN——尤其 {@code
 * Adjust} 涉及金额，绝不外放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionFlowConfigPortAdapter implements DecisionFlowConfigPort {

  private final ClaimFlowConfigRepository claimFlowConfigRepository;
  private final ObjectMapper objectMapper;

  @Override
  public Set<String> skippableNodes(long tenantId) {
    try {
      var config = claimFlowConfigRepository.findByFlowKey(tenantId, FLOW_KEY);
      if (config == null || config.getConfigJson() == null || config.getConfigJson().isBlank()) {
        return Set.of();
      }
      Map<String, Object> parsed =
          objectMapper.readValue(
              config.getConfigJson(), new TypeReference<Map<String, Object>>() {});
      Object raw = parsed.get("skipNodes");
      if (!(raw instanceof Iterable<?> nodes)) {
        return Set.of();
      }
      Set<String> safe = new HashSet<>();
      for (Object node : nodes) {
        if (DecisionGraph.isSkippable(String.valueOf(node))) {
          safe.add(DecisionGraph.AI_ASSESS);
        } else {
          log.warn("[流程配置] 忽略白名单外的跳过节点 {}（仅允许 {}）", node, DecisionGraph.AI_ASSESS);
        }
      }
      return safe;
    } catch (Exception e) {
      log.warn("[流程配置] 读取或解析失败，回落 canonical 图（不跳过任何节点）", e);
      return Set.of();
    }
  }
}
