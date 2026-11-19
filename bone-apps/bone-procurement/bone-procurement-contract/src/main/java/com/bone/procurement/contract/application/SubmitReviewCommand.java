package com.bone.procurement.contract.application;

import java.io.InputStream;

/** 提交审查命令：租户不在此command内，由登录态端口取，杜绝自报租户。 */
public record SubmitReviewCommand(
    String reviewId, String contractNo, InputStream document, String sourceRef, String docId) {}
