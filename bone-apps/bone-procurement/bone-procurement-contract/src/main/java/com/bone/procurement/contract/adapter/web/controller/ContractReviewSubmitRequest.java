package com.bone.procurement.contract.adapter.web.controller;

import jakarta.validation.constraints.NotBlank;

/** 提交审查请求体：租户不从请求体取，由登录态端口取。 */
public record ContractReviewSubmitRequest(
    @NotBlank String reviewId, @NotBlank String contractNo, String sourceRef, String docId) {}
