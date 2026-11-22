package com.bone.claim.adapter.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 报案登记/分案响应。 */
@Schema(description = "报案响应")
public record ReportResp(
    @Schema(description = "报表ID：报案ID或赔案ID（依端点而定）") Long id,
    @Schema(description = "业务编号：报案号RP / 赔案号CL（依端点而定）") String no) {}
