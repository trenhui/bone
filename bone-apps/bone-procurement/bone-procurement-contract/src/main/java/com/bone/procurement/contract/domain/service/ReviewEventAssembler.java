package com.bone.procurement.contract.domain.service;

import com.bone.procurement.contract.domain.model.AiParticipation;
import com.bone.procurement.contract.domain.model.ContractReview;
import com.bone.procurement.contract.domain.model.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.RiskItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 把审查聚合映射为 7 字段事件：风险明细只出摘要哈希，快照只出引用。 */
public final class ReviewEventAssembler {

  private ReviewEventAssembler() {}

  public static ContractReviewedEvent assemble(ContractReview review) {
    String snapshotRef =
        review.reviewSnapshotRef() == null ? null : review.reviewSnapshotRef().ref();
    return new ContractReviewedEvent(
        review.reviewId(),
        review.contractNo(),
        review.riskLevel(),
        hashRiskItems(review.riskItems()),
        snapshotRef,
        review.aiParticipation() == null ? AiParticipation.UNKNOWN : review.aiParticipation(),
        review.modelVersionSnapshot());
  }

  private static String hashRiskItems(List<RiskItem> items) {
    if (items == null || items.isEmpty()) {
      return sha256("EMPTY");
    }
    StringBuilder canonical = new StringBuilder();
    for (RiskItem item : items) {
      canonical
          .append(item.clauseNo())
          .append('|')
          .append(item.page())
          .append('|')
          .append(item.spanStart())
          .append('|')
          .append(item.spanEnd())
          .append('|')
          .append(item.severity())
          .append('|')
          .append(item.basis())
          .append('|')
          .append(item.confidence())
          .append(';');
    }
    return sha256(canonical.toString());
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }
}
