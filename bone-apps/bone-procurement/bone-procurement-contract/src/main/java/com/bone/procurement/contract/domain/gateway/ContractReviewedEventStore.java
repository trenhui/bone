package com.bone.procurement.contract.domain.gateway;

import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
import com.bone.procurement.contract.domain.model.review.ContractReviewedDlqEntry;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ContractReviewedOutboxMessage;
import java.util.List;
import java.util.Optional;

/**
 * ContractReviewed 事件闭环出站端口：Outbox 发布/待投递扫描、Inbox 条件插入去重、DLQ 写入/重放查询。
 *
 * <p>所有操作以消费/发布方 tenantId 定位；application 只依赖本端口，SDK 仓储写入只允许在 infrastructure Adapter 内（E-5.4）。
 */
public interface ContractReviewedEventStore {

  /** 事务性发布：同租户同 review_id 幂等，跨租户覆写拒绝。 */
  void publishOutbox(String tenantId, ContractReviewedEvent event);

  /** 显式驱动扫描待投递消息（不接生产 MQ）。 */
  List<ContractReviewedOutboxMessage> scanPending(String tenantId);

  Optional<ContractReviewedOutboxMessage> findOutbox(String tenantId, String reviewId);

  void markOutboxDelivered(String tenantId, String reviewId);

  boolean inboxExists(String tenantId, String reviewId);

  int inboxCount(String tenantId);

  /** 条件插入：true=首次消费落库，false=唯一键冲突（重复投递）。 */
  boolean claimInbox(ContractReviewedConclusion conclusion);

  Optional<ContractReviewedConclusion> findInbox(String tenantId, String reviewId);

  void appendDlq(String tenantId, String reviewId, String failureReason, String outboxRef);

  Optional<ContractReviewedDlqEntry> findDlq(String tenantId, String reviewId);

  void resolveDlq(String tenantId, String reviewId);
}
