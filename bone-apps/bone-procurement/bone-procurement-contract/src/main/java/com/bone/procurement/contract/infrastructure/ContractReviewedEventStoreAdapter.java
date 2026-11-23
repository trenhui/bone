package com.bone.procurement.contract.infrastructure;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ContractReviewedEventStore;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
import com.bone.procurement.contract.domain.model.review.ContractReviewedDlqEntry;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ContractReviewedOutboxMessage;
import com.bone.procurement.contract.domain.model.review.CrossTenantAccessException;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * ContractReviewed 事件闭环持久化适配器：唯一持有 SDK repo 写入（E-5.4），application 只依赖 {@link
 * ContractReviewedEventStore}。
 *
 * <p>租户一律用调用方传入的发布/消费上下文 tenantId 定位，绝不取事件自报；Outbox payload 只序列化既有 7 字段， 不夹带 output 原文 / PII /
 * 合同正文。
 */
@Component
@ConditionalOnContractAiReviewEnabled
public class ContractReviewedEventStoreAdapter implements ContractReviewedEventStore {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PENDING = "PENDING";
  private static final String DELIVERED = "DELIVERED";

  private final ContractReviewedOutboxSdkRepository outboxRepository;
  private final ContractReviewedInboxSdkRepository inboxRepository;
  private final ContractReviewedDlqSdkRepository dlqRepository;

  public ContractReviewedEventStoreAdapter(
      ContractReviewedOutboxSdkRepository outboxRepository,
      ContractReviewedInboxSdkRepository inboxRepository,
      ContractReviewedDlqSdkRepository dlqRepository) {
    this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
    this.inboxRepository = Objects.requireNonNull(inboxRepository, "inboxRepository");
    this.dlqRepository = Objects.requireNonNull(dlqRepository, "dlqRepository");
  }

  @Override
  public void publishOutbox(String tenantId, ContractReviewedEvent event) {
    requireTenant(tenantId);
    List<ContractReviewedOutboxEntity> global =
        query(outboxRepository, ContractReviewedOutboxEntity.class, "reviewId", event.reviewId());
    if (global.stream().anyMatch(e -> !tenantId.equals(e.getTenantId()))) {
      throw new CrossTenantAccessException("跨租户发布拒绝: reviewId=" + event.reviewId());
    }
    if (global.stream().anyMatch(e -> tenantId.equals(e.getTenantId()))) {
      return; // 同租户重复发布幂等：不产生第二条待投递
    }
    ContractReviewedOutboxEntity entity = new ContractReviewedOutboxEntity();
    entity.setVersion(0);
    entity.setTenantId(tenantId);
    entity.setReviewId(event.reviewId());
    entity.setPayloadJson(write(event));
    entity.setDeliveryStatus(PENDING);
    stamp(entity);
    outboxRepository.save(entity);
  }

  @Override
  public List<ContractReviewedOutboxMessage> scanPending(String tenantId) {
    requireTenant(tenantId);
    return query(
            outboxRepository,
            ContractReviewedOutboxEntity.class,
            "tenantId",
            tenantId,
            "deliveryStatus",
            PENDING)
        .stream()
        .map(
            e ->
                new ContractReviewedOutboxMessage(
                    tenantId, e.getReviewId(), read(e.getPayloadJson()), false))
        .toList();
  }

  @Override
  public Optional<ContractReviewedOutboxMessage> findOutbox(String tenantId, String reviewId) {
    return query(
            outboxRepository,
            ContractReviewedOutboxEntity.class,
            "tenantId",
            tenantId,
            "reviewId",
            reviewId)
        .stream()
        .findFirst()
        .map(
            e ->
                new ContractReviewedOutboxMessage(
                    e.getTenantId(),
                    e.getReviewId(),
                    read(e.getPayloadJson()),
                    DELIVERED.equals(e.getDeliveryStatus())));
  }

  @Override
  public void markOutboxDelivered(String tenantId, String reviewId) {
    findEntity(outboxRepository, ContractReviewedOutboxEntity.class, tenantId, reviewId)
        .ifPresent(
            e -> {
              e.setDeliveryStatus(DELIVERED);
              e.setDeliveredAt(new Date());
              e.setUpdatedAt(new Date());
              outboxRepository.save(e);
            });
  }

  @Override
  public boolean inboxExists(String tenantId, String reviewId) {
    return findEntity(inboxRepository, ContractReviewedInboxEntity.class, tenantId, reviewId)
        .isPresent();
  }

  @Override
  public int inboxCount(String tenantId) {
    requireTenant(tenantId);
    return query(inboxRepository, ContractReviewedInboxEntity.class, "tenantId", tenantId).size();
  }

  @Override
  public boolean claimInbox(ContractReviewedConclusion conclusion) {
    requireTenant(conclusion.tenantId());
    String reviewId = conclusion.reviewId();
    List<ContractReviewedInboxEntity> global =
        query(inboxRepository, ContractReviewedInboxEntity.class, "reviewId", reviewId);
    if (global.stream().anyMatch(e -> !conclusion.tenantId().equals(e.getTenantId()))) {
      throw new CrossTenantAccessException("跨租户消费拒绝: reviewId=" + reviewId);
    }
    if (global.stream().anyMatch(e -> conclusion.tenantId().equals(e.getTenantId()))) {
      return false; // 唯一键冲突 = 重复投递
    }
    ContractReviewedInboxEntity entity = new ContractReviewedInboxEntity();
    entity.setVersion(0);
    entity.setTenantId(conclusion.tenantId());
    entity.setReviewId(reviewId);
    entity.setRiskLevel(conclusion.riskLevel().name());
    entity.setAiParticipation(conclusion.aiAvailable().name());
    entity.setReviewSnapshotRef(conclusion.reviewSnapshotRef());
    entity.setQuarantined(conclusion.quarantined());
    stamp(entity);
    inboxRepository.save(entity);
    return true;
  }

  @Override
  public Optional<ContractReviewedConclusion> findInbox(String tenantId, String reviewId) {
    return findEntity(inboxRepository, ContractReviewedInboxEntity.class, tenantId, reviewId)
        .map(
            e ->
                new ContractReviewedConclusion(
                    e.getTenantId(),
                    e.getReviewId(),
                    RiskLevel.valueOf(e.getRiskLevel()),
                    AiParticipation.valueOf(e.getAiParticipation()),
                    e.getReviewSnapshotRef(),
                    Boolean.TRUE.equals(e.getQuarantined())));
  }

  @Override
  public void appendDlq(String tenantId, String reviewId, String failureReason, String outboxRef) {
    requireTenant(tenantId);
    ContractReviewedDlqEntity entity =
        findEntity(dlqRepository, ContractReviewedDlqEntity.class, tenantId, reviewId).orElse(null);
    if (entity == null) {
      entity = new ContractReviewedDlqEntity();
      entity.setVersion(0);
      entity.setTenantId(tenantId);
      entity.setReviewId(reviewId);
      stamp(entity);
    }
    entity.setFailureReason(clamp(failureReason));
    entity.setOutboxRef(outboxRef);
    entity.setResolved(false);
    entity.setOccurredAt(new Date());
    entity.setUpdatedAt(new Date());
    dlqRepository.save(entity);
  }

  @Override
  public Optional<ContractReviewedDlqEntry> findDlq(String tenantId, String reviewId) {
    return findEntity(dlqRepository, ContractReviewedDlqEntity.class, tenantId, reviewId)
        .map(
            e ->
                new ContractReviewedDlqEntry(
                    e.getTenantId(),
                    e.getReviewId(),
                    e.getFailureReason(),
                    e.getOutboxRef(),
                    Boolean.TRUE.equals(e.getResolved())));
  }

  @Override
  public void resolveDlq(String tenantId, String reviewId) {
    findEntity(dlqRepository, ContractReviewedDlqEntity.class, tenantId, reviewId)
        .ifPresent(
            e -> {
              e.setResolved(true);
              e.setUpdatedAt(new Date());
              dlqRepository.save(e);
            });
  }

  private static void requireTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalStateException("事件闭环需要登录态租户，拒绝匿名读写");
    }
  }

  private static String clamp(String text) {
    if (text == null) {
      return "";
    }
    return text.length() > 512 ? text.substring(0, 512) : text;
  }

  private static String write(ContractReviewedEvent event) {
    try {
      return MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("ContractReviewed payload 序列化失败", e);
    }
  }

  private static ContractReviewedEvent read(String json) {
    try {
      return MAPPER.readValue(json, ContractReviewedEvent.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("ContractReviewed payload 反序列化失败", e);
    }
  }

  private static <T extends AbstractEntity<Long>> Optional<T> findEntity(
      Repository<T, Long> repository, Class<T> type, String tenantId, String reviewId) {
    return query(repository, type, "tenantId", tenantId, "reviewId", reviewId).stream().findFirst();
  }

  private static <T extends AbstractEntity<Long>> List<T> query(
      Repository<T, Long> repository, Class<T> type, Object... pairs) {
    Criteria<T> criteria = Criteria.<T>create().entityClass(type);
    for (int i = 0; i < pairs.length; i += 2) {
      criteria = criteria.eq((String) pairs[i], pairs[i + 1]);
    }
    return repository.findByCriteria(criteria);
  }

  private static void stamp(ContractReviewedOutboxEntity entity) {
    Date now = new Date();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
  }

  private static void stamp(ContractReviewedInboxEntity entity) {
    Date now = new Date();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
  }

  private static void stamp(ContractReviewedDlqEntity entity) {
    Date now = new Date();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
  }
}
