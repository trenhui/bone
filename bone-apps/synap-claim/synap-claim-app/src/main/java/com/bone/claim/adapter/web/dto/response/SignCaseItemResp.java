package com.bone.claim.adapter.web.dto.response;

/** 签收明细自动建案结果（ID 用 String 防雪花精度丢失）。 */
public record SignCaseItemResp(
    String itemId, String claimNo, String caseId, String caseStatus, String result) {}
