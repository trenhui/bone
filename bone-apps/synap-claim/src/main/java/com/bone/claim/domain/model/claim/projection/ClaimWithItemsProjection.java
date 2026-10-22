package com.bone.claim.domain.model.claim.projection;

import java.math.BigDecimal;

/**
 * 赔案 + 明细扁平投影（SQL 每明细一行）。
 *
 * <p>{@code findById} 不回填聚合内集合（SDK 语义），明细一律经本投影获取——与 blueprint Order 同款纪律。
 */
public record ClaimWithItemsProjection(
    Long claimId,
    String claimNo,
    String policyNo,
    Long planId,
    String insuredIdHash,
    String claimType,
    String status,
    String externalStatus,
    String stage,
    int hangUpStatus,
    String invoiceCode,
    String invoiceNo,
    java.time.Instant outInsureTime,
    String sourceChannel,
    String sourceSystem,
    BigDecimal claimAmount,
    BigDecimal approvedAmount,
    String firstReviewerId,
    String secondReviewerId,
    String rejectComment,
    String rejectType,
    String planVersion,
    java.time.Instant createdAt,
    // ---- 明细列（无明细行为 null）----
    Long itemId,
    String itemName,
    String itemCode,
    String itemCategory,
    BigDecimal itemQuantity,
    BigDecimal itemUnitPrice,
    BigDecimal itemAmount,
    String itemSocialFlag,
    String itemMedicalType,
    BigDecimal itemPoolingAmount,
    BigDecimal itemSelfPay1,
    BigDecimal itemSelfPay2,
    BigDecimal itemSelfPayC,
    BigDecimal itemValidAmount,
    BigDecimal itemInvalidAmount) {}
