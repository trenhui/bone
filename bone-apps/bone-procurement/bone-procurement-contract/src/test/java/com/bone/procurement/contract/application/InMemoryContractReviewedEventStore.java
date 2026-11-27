package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.gateway.ContractReviewedEventStore;
import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
import com.bone.procurement.contract.domain.model.review.ContractReviewedDlqEntry;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ContractReviewedOutboxMessage;
import com.bone.procurement.contract.domain.model.review.CrossTenantAccessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 测试用事件闭环内存实现：共享同一实例即可模拟重启后状态不丢。 */
final class InMemoryContractReviewedEventStore implements ContractReviewedEventStore {

  private final Map<String, ContractReviewedEvent> outbox = new LinkedHashMap<>();
  private final Map<String, ContractReviewedConclusion> inbox = new LinkedHashMap<>();
  private final Map<String, ContractReviewedDlqEntry> dlq = new LinkedHashMap<>();
  private final List<String> deliveries = new ArrayList<>();

  private static String key(String tenantId, String reviewId) {
    return tenantId + "|" + reviewId;
  }

  @Override
  public void publishOutbox(String tenantId, ContractReviewedEvent event) {
    String k = key(tenantId, event.reviewId());
    outbox.putIfAbsent(k, event);
  }

  @Override
  public List<ContractReviewedOutboxMessage> scanPending(String tenantId) {
    return outbox.entrySet().stream()
        .filter(e -> e.getKey().startsWith(tenantId + "|"))
        .map(
            e ->
                new ContractReviewedOutboxMessage(
                    tenantId, e.getValue().reviewId(), e.getValue(), false))
        .toList();
  }

  @Override
  public Optional<ContractReviewedOutboxMessage> findOutbox(String tenantId, String reviewId) {
    return Optional.ofNullable(outbox.get(key(tenantId, reviewId)))
        .map(
            e ->
                new ContractReviewedOutboxMessage(
                    tenantId, reviewId, e, deliveries.contains(key(tenantId, reviewId))));
  }

  @Override
  public void markOutboxDelivered(String tenantId, String reviewId) {
    deliveries.add(key(tenantId, reviewId));
  }

  @Override
  public boolean inboxExists(String tenantId, String reviewId) {
    return inbox.containsKey(key(tenantId, reviewId));
  }

  @Override
  public int inboxCount(String tenantId) {
    return (int) inbox.keySet().stream().filter(k -> k.startsWith(tenantId + "|")).count();
  }

  @Override
  public boolean claimInbox(ContractReviewedConclusion conclusion) {
    String k = key(conclusion.tenantId(), conclusion.reviewId());
    if (inbox.containsKey(k)) {
      return false;
    }
    boolean otherTenant =
        inbox.keySet().stream()
            .anyMatch(x -> x.endsWith("|" + conclusion.reviewId()) && !x.equals(k));
    if (otherTenant) {
      throw new CrossTenantAccessException("跨租户消费拒绝: reviewId=" + conclusion.reviewId());
    }
    inbox.put(k, conclusion);
    return true;
  }

  @Override
  public Optional<ContractReviewedConclusion> findInbox(String tenantId, String reviewId) {
    return Optional.ofNullable(inbox.get(key(tenantId, reviewId)));
  }

  @Override
  public void appendDlq(String tenantId, String reviewId, String failureReason, String outboxRef) {
    dlq.put(
        key(tenantId, reviewId),
        new ContractReviewedDlqEntry(tenantId, reviewId, failureReason, outboxRef, false));
  }

  @Override
  public Optional<ContractReviewedDlqEntry> findDlq(String tenantId, String reviewId) {
    return Optional.ofNullable(dlq.get(key(tenantId, reviewId)));
  }

  @Override
  public void resolveDlq(String tenantId, String reviewId) {
    String k = key(tenantId, reviewId);
    ContractReviewedDlqEntry e = dlq.get(k);
    if (e != null) {
      dlq.put(
          k,
          new ContractReviewedDlqEntry(
              e.tenantId(), e.reviewId(), e.reason(), e.outboxRef(), true));
    }
  }
}
