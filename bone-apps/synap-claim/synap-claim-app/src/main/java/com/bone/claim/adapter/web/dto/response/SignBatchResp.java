package com.bone.claim.adapter.web.dto.response;

import java.time.Instant;
import java.util.List;

/** 签收批次响应 DTO（显式，不直出领域对象；ID 用 String 防雪花 ID 精度丢失）。 */
public record SignBatchResp(
    String batchId,
    String batchNo,
    String source,
    String signType,
    String signChannel,
    String insuranceCompany,
    String insuranceSubsidiary,
    String insuringAgency,
    String emergency,
    String signStatus,
    Integer claimCount,
    Integer imageReadyCount,
    String expressCompany,
    String expressNo,
    String sender,
    Instant signTime,
    Instant createdAt,
    String signOperator,
    String signRemark,
    int createdCaseCount,
    int skippedCaseCount,
    int manualCaseCount,
    List<SignCaseItemResp> caseItems) {}
