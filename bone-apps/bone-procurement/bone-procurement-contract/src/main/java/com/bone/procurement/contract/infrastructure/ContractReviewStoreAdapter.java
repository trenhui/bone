package com.bone.procurement.contract.infrastructure;

import com.bone.ai.gateway.infrastructure.security.HeuristicInjectionGuard;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ContractReviewStore;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReview;
import com.bone.procurement.contract.domain.model.review.CrossTenantAccessException;
import com.bone.procurement.contract.domain.model.review.HumanOverride;
import com.bone.procurement.contract.domain.model.review.ReviewSnapshotRef;
import com.bone.procurement.contract.domain.model.review.ReviewStatus;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskItem;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 合同审查持久化适配器（生产）：实现原端口签名，所有 SDK repo 写入只在本 Adapter 内发生， application 仍只依赖 {@link
 * ContractReviewStore}（E-5.4）。
 *
 * <p>护栏复用 {@link ContractReviewPortAdapter#containsPii} 与 {@link HeuristicInjectionGuard}：
 * 命中即在落库前脱敏，原文绝不进实体/追溯，并写 PII_REJECTED / INJECTION_REJECTED 留痕。
 */
@Component
@ConditionalOnContractAiReviewEnabled
public class ContractReviewStoreAdapter implements ContractReviewStore {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String REDACTED = "[REDACTED_BY_GUARD]";
  private static final CollectionType RISK_ITEMS_TYPE =
      MAPPER.getTypeFactory().constructCollectionType(List.class, RiskItem.class);
  private static final CollectionType OVERRIDES_TYPE =
      MAPPER.getTypeFactory().constructCollectionType(List.class, HumanOverride.class);

  private final ContractReviewSdkRepository repository;
  private final ReviewTracePort tracePort;
  private final HeuristicInjectionGuard injectionGuard = new HeuristicInjectionGuard();

  public ContractReviewStoreAdapter(
      ContractReviewSdkRepository repository, ReviewTracePort tracePort) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.tracePort = Objects.requireNonNull(tracePort, "tracePort");
  }

  @Override
  public void save(String tenantId, ContractReview review) {
    persist(tenantId, review, review.reviewId());
  }

  @Override
  public String saveIdempotent(String tenantId, String idempotencyKey, ContractReview review) {
    List<ContractReviewEntity> existing =
        query(
            Criteria.<ContractReviewEntity>create()
                .eq("tenantId", tenantId)
                .eq("idempotencyKey", idempotencyKey));
    if (!existing.isEmpty()) {
      // 重复投递：返回原 review_id，不覆盖原记录。
      return existing.get(0).getReviewId();
    }
    persist(tenantId, review, idempotencyKey);
    return review.reviewId();
  }

  @Override
  public Optional<ContractReview> find(String tenantId, String reviewId) {
    return query(
            Criteria.<ContractReviewEntity>create()
                .eq("tenantId", tenantId)
                .eq("reviewId", reviewId))
        .stream()
        .findFirst()
        .map(ContractReviewStoreAdapter::toDomain);
  }

  @Override
  public boolean existsGlobally(String reviewId) {
    return !query(Criteria.<ContractReviewEntity>create().eq("reviewId", reviewId)).isEmpty();
  }

  private void persist(String tenantId, ContractReview review, String idempotencyKey) {
    List<ContractReviewEntity> global =
        query(Criteria.<ContractReviewEntity>create().eq("reviewId", review.reviewId()));
    if (global.stream().anyMatch(e -> !tenantId.equals(e.getTenantId()))) {
      throw new CrossTenantAccessException("跨租户覆写拒绝: reviewId=" + review.reviewId());
    }
    ContractReviewEntity entity = new ContractReviewEntity();
    // 同租户二次写入（人工推翻 / 快照）必须复用原主键与版本，否则 SDK save 会 insert 并撞唯一键。
    ContractReviewEntity existing =
        global.stream().filter(e -> tenantId.equals(e.getTenantId())).findFirst().orElse(null);
    if (existing != null) {
      entity.setId(existing.getId());
      entity.setVersion(existing.getVersion());
    } else {
      entity.setVersion(0);
    }
    entity.setTenantId(tenantId);
    entity.setReviewId(review.reviewId());
    entity.setContractNo(review.contractNo());
    entity.setIdempotencyKey(idempotencyKey);
    entity.setStatus(review.status().name());
    entity.setAiParticipation(review.aiParticipation().name());
    entity.setRiskLevel(review.riskLevel().name());
    entity.setRiskItemsJson(writeSanitizedRiskItems(review, tenantId));
    entity.setAiConfidence(review.aiConfidence());
    entity.setModelVersion(review.modelVersionSnapshot());
    entity.setHumanReason(guard(review.humanReason(), review.reviewId(), "HUMAN_REASON", tenantId));
    entity.setFailureReason(
        guard(review.failureReason(), review.reviewId(), "FAILURE_REASON", tenantId));
    entity.setOverridesJson(writeOverrides(review.overrides()));
    entity.setSnapshotRef(
        review.reviewSnapshotRef() == null ? null : review.reviewSnapshotRef().ref());
    entity.setHumanFinalized(review.isHumanFinalized());
    Date now = new Date();
    entity.setCreatedAt(
        existing == null || existing.getCreatedAt() == null ? now : existing.getCreatedAt());
    entity.setUpdatedAt(now);
    repository.save(entity);
  }

  private String writeSanitizedRiskItems(ContractReview review, String tenantId) {
    List<RiskItem> sanitized = new ArrayList<>();
    for (RiskItem item : review.riskItems()) {
      String basis = guard(item.basis(), review.reviewId(), "RISK_BASIS", tenantId);
      sanitized.add(
          new RiskItem(
              item.clauseNo(),
              item.page(),
              item.spanStart(),
              item.spanEnd(),
              item.severity(),
              basis,
              item.confidence()));
    }
    return writeJson(sanitized);
  }

  private String writeOverrides(List<HumanOverride> overrides) {
    return writeJson(overrides);
  }

  /** 命中 PII / 注入特征即脱敏，并写明原因的追溯条目；原文不进库。 */
  private String guard(String text, String reviewId, String field, String tenantId) {
    if (text == null || text.isBlank()) {
      return text;
    }
    if (ContractReviewPortAdapter.containsPii(text)) {
      tracePort.append(
          ReviewTraceEntry.of(reviewId, "PII_REJECTED", "field=" + field + " 命中身份证/银行账号，已脱敏落库"));
      return REDACTED;
    }
    try {
      injectionGuard.check(text, reviewId);
    } catch (RuntimeException e) {
      tracePort.append(
          ReviewTraceEntry.of(reviewId, "INJECTION_REJECTED", "field=" + field + " 命中注入特征，已脱敏落库"));
      return REDACTED;
    }
    return text;
  }

  private static String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("审查事实序列化失败", e);
    }
  }

  private List<ContractReviewEntity> query(Criteria<ContractReviewEntity> criteria) {
    return repository.findByCriteria(criteria.entityClass(ContractReviewEntity.class));
  }

  private static ContractReview toDomain(ContractReviewEntity e) {
    return ContractReview.rehydrate(
        e.getReviewId(),
        e.getContractNo(),
        ReviewStatus.valueOf(e.getStatus()),
        e.getFailureReason(),
        e.getHumanReason(),
        AiParticipation.valueOf(e.getAiParticipation()),
        RiskLevel.valueOf(e.getRiskLevel()),
        readJson(e.getRiskItemsJson(), RISK_ITEMS_TYPE),
        e.getSnapshotRef() == null ? null : ReviewSnapshotRef.of(e.getSnapshotRef()),
        e.getModelVersion(),
        e.getAiConfidence() == null ? 0.0 : e.getAiConfidence(),
        readJson(e.getOverridesJson(), OVERRIDES_TYPE),
        Boolean.TRUE.equals(e.getHumanFinalized()));
  }

  private static <T> List<T> readJson(String json, CollectionType type) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("审查事实反序列化失败", e);
    }
  }
}
