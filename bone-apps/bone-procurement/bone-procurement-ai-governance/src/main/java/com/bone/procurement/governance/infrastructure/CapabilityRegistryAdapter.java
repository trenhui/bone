package com.bone.procurement.governance.infrastructure;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.model.capability.AiCapability;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityState;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import com.bone.procurement.governance.domain.model.capability.OwnerRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 能力登记持久化适配器：实现原端口签名，写操作只在此处调用 SDK repo。 */
@Component
@ConditionalOnAiGovernanceEnabled
public class CapabilityRegistryAdapter implements CapabilityRegistry {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CollectionType TRACE_TYPE =
      MAPPER.getTypeFactory().constructCollectionType(List.class, TraceSnapshot.class);
  private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
  private static final Pattern BANK_ACCOUNT = Pattern.compile("\\d{16,19}");
  private static final String REDACTED = "[REDACTED_BY_GUARD]";

  private final AiCapabilitySdkRepository repository;

  public CapabilityRegistryAdapter(AiCapabilitySdkRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public void save(AiCapability capability) {
    String tenantId = GovernanceTenant.current();
    List<AiCapabilityEntity> existing =
        repository.findByCriteria(
            Criteria.<AiCapabilityEntity>create()
                .entityClass(AiCapabilityEntity.class)
                .eq("tenantId", tenantId)
                .eq("code", capability.code().value()));
    AiCapabilityEntity entity = new AiCapabilityEntity();
    if (!existing.isEmpty()) entity.setId(existing.get(0).getId());
    entity.setTenantId(tenantId);
    entity.setCode(capability.code().value());
    entity.setDisplayName(capability.displayName());
    entity.setState(capability.state().name());
    entity.setOwnerUserId(capability.owner().userId());
    entity.setOwnerDisplayName(capability.owner().displayName());
    List<TraceSnapshot> snapshots = new ArrayList<>();
    for (GovernanceTraceEntry entry : capability.trace()) {
      snapshots.add(
          new TraceSnapshot(
              entry.eventType(), redact(entry.detail()), entry.occurredAt().toEpochMilli()));
    }
    entity.setTraceJson(writeJson(snapshots));
    repository.save(entity);
  }

  @Override
  public Optional<AiCapability> find(CapabilityCode code) {
    String tenantId = GovernanceTenant.current();
    return repository
        .findByCriteria(
            Criteria.<AiCapabilityEntity>create()
                .entityClass(AiCapabilityEntity.class)
                .eq("tenantId", tenantId)
                .eq("code", code.value()))
        .stream()
        .findFirst()
        .map(CapabilityRegistryAdapter::toDomain);
  }

  @Override
  public boolean exists(CapabilityCode code) {
    return find(code).isPresent();
  }

  @Override
  public List<AiCapability> listAll() {
    return repository
        .findByCriteria(
            Criteria.<AiCapabilityEntity>create()
                .entityClass(AiCapabilityEntity.class)
                .eq("tenantId", GovernanceTenant.current()))
        .stream()
        .map(CapabilityRegistryAdapter::toDomain)
        .toList();
  }

  private static AiCapability toDomain(AiCapabilityEntity e) {
    List<GovernanceTraceEntry> trace = new ArrayList<>();
    for (TraceSnapshot s : readJson(e.getTraceJson())) {
      trace.add(
          new GovernanceTraceEntry(
              e.getCode(),
              s.eventType(),
              s.detail(),
              java.time.Instant.ofEpochMilli(s.occurredAtEpochMs())));
    }
    return AiCapability.rehydrate(
        new CapabilityCode(e.getCode()),
        e.getDisplayName(),
        CapabilityState.valueOf(e.getState()),
        OwnerRef.of(e.getOwnerUserId(), e.getOwnerDisplayName()),
        trace);
  }

  private static String redact(String text) {
    if (text == null || text.isBlank()) return text;
    return (ID_CARD.matcher(text).find() || BANK_ACCOUNT.matcher(text).find()) ? REDACTED : text;
  }

  private static String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("治理追溯序列化失败", e);
    }
  }

  private static List<TraceSnapshot> readJson(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return MAPPER.readValue(json, TRACE_TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("治理追溯反序列化失败", e);
    }
  }

  record TraceSnapshot(String eventType, String detail, long occurredAtEpochMs) {}
}
