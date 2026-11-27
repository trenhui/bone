package com.bone.claim.adapter.web.dto.response;

import java.time.Instant;

/** 批次内赔案明细响应：对齐普康批次详情页的赔案表格列。 */
public record SignBatchItemResp(
    String itemId,
    String claimNo,
    String policyNo,
    String insuredName,
    String insuredIdNo,
    String identityType,
    Boolean imageReady,
    Integer countingResult,
    Integer imageCount,
    Integer imageUploadCount,
    Instant imageUploadTime,
    String imageUploader,
    String caseStatus,
    String caseId) {}
