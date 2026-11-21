package com.bone.procurement.contract.adapter.web.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提交审查请求体：租户与 reviewId 不从请求体取，前者取自登录态，后者由服务端生成。 */
public record ContractReviewSubmitRequest(
    @NotBlank String contractNo,
    @Size(max = 128) String idempotencyKey,
    String sourceRef,
    String docId) {}
