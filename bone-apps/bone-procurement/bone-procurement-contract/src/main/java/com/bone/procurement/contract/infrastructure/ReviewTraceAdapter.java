package com.bone.procurement.contract.infrastructure;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ContractReviewTenantPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.ReviewTraceEntry;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 审查追溯持久化适配器：实现原端口签名，追加只走 SDK repo，application 不直连仓储。 */
@Component
@ConditionalOnContractAiReviewEnabled
public class ReviewTraceAdapter implements ReviewTracePort {

  private final ReviewTraceSdkRepository repository;
  private final ContractReviewTenantPort tenantPort;

  public ReviewTraceAdapter(
      ReviewTraceSdkRepository repository, ContractReviewTenantPort tenantPort) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.tenantPort = Objects.requireNonNull(tenantPort, "tenantPort");
  }

  @Override
  public void append(ReviewTraceEntry entry) {
    String tenantId = tenantPort.currentTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalStateException("追溯留痕需要登录态租户，拒绝匿名写入");
    }
    ReviewTraceEntity entity = new ReviewTraceEntity();
    entity.setTenantId(tenantId);
    entity.setReviewId(entry.reviewId());
    entity.setEventType(entry.eventType());
    entity.setDetail(entry.detail());
    entity.setOccurredAt(Date.from(entry.occurredAt()));
    repository.save(entity);
  }

  @Override
  public List<ReviewTraceEntry> findByReviewId(String reviewId) {
    return repository
        .findByCriteria(
            Criteria.<ReviewTraceEntity>create()
                .entityClass(ReviewTraceEntity.class)
                .eq("reviewId", reviewId))
        .stream()
        .sorted(Comparator.comparing(e -> e.getOccurredAt().toInstant()))
        .map(
            e ->
                new ReviewTraceEntry(
                    e.getReviewId(),
                    e.getEventType(),
                    e.getDetail(),
                    e.getOccurredAt().toInstant()))
        .toList();
  }
}
