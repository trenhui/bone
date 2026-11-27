package com.bone.procurement.governance.infrastructure;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 人工终决台账持久化适配器：AI output 原文与 PII 不落库，写操作只在此处调用 SDK repo。 */
@Component
@ConditionalOnAiGovernanceEnabled
public class HumanDecisionLedgerAdapter implements HumanDecisionLedger {

  private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
  private static final Pattern BANK_ACCOUNT = Pattern.compile("\\d{16,19}");
  private static final String REDACTED = "[REDACTED_BY_GUARD]";

  private final HumanDecisionSdkRepository repository;

  public HumanDecisionLedgerAdapter(HumanDecisionSdkRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public void append(HumanFinalDecision decision) {
    String tenantId = GovernanceTenant.current();
    List<HumanDecisionEntity> existing =
        repository.findByCriteria(
            Criteria.<HumanDecisionEntity>create()
                .entityClass(HumanDecisionEntity.class)
                .eq("tenantId", tenantId)
                .eq("decisionId", decision.decisionId()));
    HumanDecisionEntity entity = new HumanDecisionEntity();
    if (!existing.isEmpty()) {
      entity.setId(existing.get(0).getId());
      entity.setVersion(existing.get(0).getVersion());
    } else {
      entity.setVersion(0);
    }
    entity.setTenantId(tenantId);
    entity.setDecisionId(decision.decisionId());
    entity.setCapabilityCode(decision.capabilityCode().value());
    entity.setSubjectRef(decision.subjectRef());
    entity.setAiConclusion(redact(decision.aiConclusion()));
    entity.setAiConfidence(decision.aiConfidence());
    entity.setFinalConclusion(redact(decision.finalConclusion()));
    entity.setReviewer(decision.reviewer());
    entity.setReason(redact(decision.reason()));
    entity.setReviewDurationMs(decision.reviewDurationMs());
    entity.setOverrideAccepted(decision.overrideAccepted());
    entity.setEvaluationFeedback(decision.evaluationFeedbackRaised());
    entity.setDecidedAt(Date.from(decision.decidedAt()));
    Date now = new Date();
    entity.setCreatedAt(existing.isEmpty() ? now : existing.get(0).getCreatedAt());
    entity.setUpdatedAt(now);
    repository.save(entity);
  }

  @Override
  public Optional<HumanFinalDecision> find(String decisionId) {
    return query("decisionId", decisionId).stream()
        .findFirst()
        .map(HumanDecisionLedgerAdapter::toDomain);
  }

  @Override
  public List<HumanFinalDecision> findBySubject(String subjectRef) {
    return query("subjectRef", subjectRef).stream()
        .map(HumanDecisionLedgerAdapter::toDomain)
        .toList();
  }

  @Override
  public long countOverrides(CapabilityCode code) {
    return query("capabilityCode", code.value()).stream()
        .map(HumanDecisionLedgerAdapter::toDomain)
        .filter(HumanFinalDecision::isOverride)
        .count();
  }

  @Override
  public List<HumanFinalDecision> findByCapability(CapabilityCode code) {
    return query("capabilityCode", code.value()).stream()
        .map(HumanDecisionLedgerAdapter::toDomain)
        .toList();
  }

  private List<HumanDecisionEntity> query(String field, Object value) {
    return repository.findByCriteria(
        Criteria.<HumanDecisionEntity>create()
            .entityClass(HumanDecisionEntity.class)
            .eq("tenantId", GovernanceTenant.current())
            .eq(field, value));
  }

  private static HumanFinalDecision toDomain(HumanDecisionEntity e) {
    return new HumanFinalDecision(
        e.getDecisionId(),
        new CapabilityCode(e.getCapabilityCode()),
        e.getSubjectRef(),
        e.getAiConclusion(),
        e.getAiConfidence() == null ? 0.0 : e.getAiConfidence(),
        e.getFinalConclusion(),
        e.getReviewer(),
        e.getReason(),
        e.getReviewDurationMs() == null ? 0L : e.getReviewDurationMs(),
        Boolean.TRUE.equals(e.getOverrideAccepted()),
        Boolean.TRUE.equals(e.getEvaluationFeedback()),
        e.getDecidedAt().toInstant());
  }

  private static String redact(String text) {
    if (text == null || text.isBlank()) return text;
    return (ID_CARD.matcher(text).find() || BANK_ACCOUNT.matcher(text).find()) ? REDACTED : text;
  }
}
