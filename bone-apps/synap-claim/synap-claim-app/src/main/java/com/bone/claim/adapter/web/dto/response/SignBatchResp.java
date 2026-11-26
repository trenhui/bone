package com.bone.claim.adapter.web.dto.response;

import java.time.Instant;

/** 签收批次响应 DTO（显式，不直出领域对象；ID 用 String 防雪花 ID 精度丢失）。 */
public record SignBatchResp(
    String batchId,
    String batchNo,
    String source,
    String signType,
    String signChannel,
    String signStatus,
    Integer claimCount,
    Integer imageReadyCount,
    String expressCompany,
    String expressNo,
    String sender,
    Instant signTime,
    String signOperator,
    String signRemark) {}
