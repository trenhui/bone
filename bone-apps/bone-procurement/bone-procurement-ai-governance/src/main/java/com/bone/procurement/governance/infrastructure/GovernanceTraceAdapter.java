package com.bone.procurement.governance.infrastructure;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.model.GovernanceTraceEntry;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 治理追溯持久化适配器：只追加，detail 经 PII 扫描脱敏后落库。 */
@Component
@ConditionalOnAiGovernanceEnabled
public class GovernanceTraceAdapter implements GovernanceTracePort {

  private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
  private static final Pattern BANK_ACCOUNT = Pattern.compile("\\d{16,19}");
  private static final String REDACTED = "[REDACTED_BY_GUARD]";

  private final GovernanceTraceSdkRepository repository;

  public GovernanceTraceAdapter(GovernanceTraceSdkRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public void append(GovernanceTraceEntry entry) {
    GovernanceTraceEntity entity = new GovernanceTraceEntity();
    entity.setTenantId(GovernanceTenant.current());
    entity.setCapabilityCode(entry.capabilityCode());
    entity.setEventType(entry.eventType());
    entity.setDetail(redact(entry.detail()));
    entity.setOccurredAt(Date.from(entry.occurredAt()));
    repository.save(entity);
  }

  @Override
  public List<GovernanceTraceEntry> listByCapability(String capabilityCode) {
    return repository
        .findByCriteria(
            Criteria.<GovernanceTraceEntity>create()
                .entityClass(GovernanceTraceEntity.class)
                .eq("tenantId", GovernanceTenant.current())
                .eq("capabilityCode", capabilityCode))
        .stream()
        .sorted(Comparator.comparing(e -> e.getOccurredAt().toInstant()))
        .map(
            e ->
                new GovernanceTraceEntry(
                    e.getCapabilityCode(),
                    e.getEventType(),
                    e.getDetail(),
                    e.getOccurredAt().toInstant()))
        .toList();
  }

  private static String redact(String text) {
    if (text == null || text.isBlank()) return text;
    return (ID_CARD.matcher(text).find() || BANK_ACCOUNT.matcher(text).find()) ? REDACTED : text;
  }
}
