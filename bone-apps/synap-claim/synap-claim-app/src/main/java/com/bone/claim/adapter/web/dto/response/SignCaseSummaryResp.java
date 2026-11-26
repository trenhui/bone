package com.bone.claim.adapter.web.dto.response;

import java.util.List;

/** 自动建案结果摘要：新建 / 跳过 / 待人工。 */
public record SignCaseSummaryResp(
    int createdCount, int skippedCount, int manualCount, List<SignCaseItemResp> items) {}
