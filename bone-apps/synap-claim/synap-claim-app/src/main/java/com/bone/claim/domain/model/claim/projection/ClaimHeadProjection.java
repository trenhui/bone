package com.bone.claim.domain.model.claim.projection;

import com.bone.claim.domain.model.claim.ClaimCase;

/** 赔案头投影（列表/待办行；不含明细）。 */
public record ClaimHeadProjection(
    Long claimId,
    String claimNo,
    String policyNo,
    Long planId,
    String insuredIdHash,
    String claimType,
    String status,
    String externalStatus,
    int hangUpStatus,
    String sourceChannel,
    java.math.BigDecimal claimAmount,
    java.math.BigDecimal approvedAmount,
    String firstReviewerId,
    java.time.Instant createdAt) {

  public static ClaimHeadProjection from(ClaimCase c) {
    return new ClaimHeadProjection(
        c.getId(),
        c.getClaimNo(),
        c.getPolicyNo(),
        c.getPlanId(),
        c.getInsuredIdHash(),
        c.getClaimType(),
        c.status().name(),
        c.externalStatus(),
        c.getHangUpStatus(),
        c.getSourceChannel(),
        c.getClaimAmount(),
        c.getApprovedAmount(),
        c.getFirstReviewerId(),
        c.getCreatedAt());
  }
}
