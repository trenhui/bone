package com.bone.claim.application;

import com.bone.claim.application.port.out.DecisionFlowConfigPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.config.ClaimFlowConfig;
import com.bone.claim.domain.model.config.ClaimFlowConfigChange;
import com.bone.claim.domain.model.config.event.ClaimFlowConfigChangedEvent;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.repository.ClaimFlowConfigRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 决策图流程配置的读写用例；写入期即拒绝白名单外的节点，避免运营配出「跳金额节点」。
 *
 * <p><b>E-5.4</b>：两个写入分支（create / overwrite）都在 {@code save} 后经 {@link ClaimFlowConfigChange} 载波发布
 * {@link ClaimFlowConfigChangedEvent}，不再使用 {@code @NoDomainEvent} 豁免——配置能改变赔案结论档，属一等领域事实。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimFlowConfigApplicationService {

  public record FlowConfigView(
      String flowKey, int configVersion, Set<String> skipNodes, Long version) {}

  private final ClaimFlowConfigRepository claimFlowConfigRepository;
  private final TenantPort tenantPort;
  private final ObjectMapper objectMapper;
  private final DomainEventPublisher domainEventPublisher;

  @Transactional(readOnly = true)
  public FlowConfigView current() {
    ClaimFlowConfig config =
        claimFlowConfigRepository.findByFlowKey(
            tenantPort.currentTenantId(), DecisionFlowConfigPort.FLOW_KEY);
    if (config == null) {
      return new FlowConfigView(DecisionFlowConfigPort.FLOW_KEY, 0, Set.of(), null);
    }
    return new FlowConfigView(
        DecisionFlowConfigPort.FLOW_KEY,
        config.getConfigVersion(),
        parse(config.getConfigJson()),
        config.getVersion());
  }

  @Transactional
  public FlowConfigView save(Set<String> skipNodes) {
    Set<String> safe = skipNodes == null ? Set.of() : new HashSet<>(skipNodes);
    for (String node : safe) {
      if (!DecisionGraph.isSkippable(node)) {
        throw ClaimErrors.of(
            ClaimErrorCodes.CLAIM_FLOW_CONFIG_INVALID,
            "流程配置只允许跳过 AI 评估节点（" + DecisionGraph.AI_ASSESS + "），收到：" + node);
      }
    }
    String json;
    try {
      json = objectMapper.writeValueAsString(Map.of("skipNodes", safe));
    } catch (Exception e) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_FLOW_CONFIG_INVALID, "流程配置序列化失败");
    }
    long tenantId = tenantPort.currentTenantId();
    Instant now = Instant.now();
    ClaimFlowConfig existing =
        claimFlowConfigRepository.findByFlowKey(tenantId, DecisionFlowConfigPort.FLOW_KEY);
    if (existing == null) {
      ClaimFlowConfig created =
          ClaimFlowConfig.create(tenantId, DecisionFlowConfigPort.FLOW_KEY, 1, json, now);
      claimFlowConfigRepository.save(created);
      publishChanged(created, ClaimFlowConfigChangedEvent.TYPE_CREATED, tenantId, json, now);
      return new FlowConfigView(DecisionFlowConfigPort.FLOW_KEY, 1, safe, created.getVersion());
    }
    existing.overwriteConfigJson(json, now);
    claimFlowConfigRepository.save(existing);
    publishChanged(existing, ClaimFlowConfigChangedEvent.TYPE_OVERWRITTEN, tenantId, json, now);
    return new FlowConfigView(
        DecisionFlowConfigPort.FLOW_KEY, existing.getConfigVersion(), safe, existing.getVersion());
  }

  private Set<String> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    try {
      Map<String, Object> parsed =
          objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
      Object nodes = parsed.get("skipNodes");
      if (!(nodes instanceof Iterable<?> it)) {
        return Set.of();
      }
      Set<String> result = new HashSet<>();
      for (Object n : it) {
        if (DecisionGraph.isSkippable(String.valueOf(n))) {
          result.add(DecisionGraph.AI_ASSESS);
        }
      }
      return result;
    } catch (Exception e) {
      log.warn("[流程配置] config_json 解析失败，按空集读取（不跳过任何节点）", e);
      return Set.of();
    }
  }

  /** 事件在 save 之后发布；新建时 SDK 可能未回填主键，允许 id 为 null（与 FeatureToggleChange 同）。 */
  private void publishChanged(
      ClaimFlowConfig config, String changeType, long tenantId, String json, Instant now) {
    Long id = config.getId();
    domainEventPublisher.publishFrom(
        ClaimFlowConfigChange.of(
            id,
            new ClaimFlowConfigChangedEvent(
                tenantId,
                id == null ? 0L : id,
                DecisionFlowConfigPort.FLOW_KEY,
                changeType,
                config.getConfigVersion(),
                json,
                "CONFIG_ADMIN",
                now)));
  }
}
