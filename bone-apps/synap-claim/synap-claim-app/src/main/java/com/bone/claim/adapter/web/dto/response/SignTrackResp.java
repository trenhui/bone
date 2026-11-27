package com.bone.claim.adapter.web.dto.response;

/** 签收轨迹响应 DTO（显式，不直出领域对象；ID 用 String 防雪花 ID 精度丢失）。 */
public record SignTrackResp(
    String id,
    String batchId,
    String claimId,
    String signStatus,
    String actionType,
    String message,
    String remark,
    String operator,
    java.time.Instant occurredAt) {}
