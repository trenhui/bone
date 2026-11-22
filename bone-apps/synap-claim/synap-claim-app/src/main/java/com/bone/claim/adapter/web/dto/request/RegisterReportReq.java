package com.bone.claim.adapter.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** 报案登记请求（FNOL：一次事故告知事件）。幂等键走请求头 Idempotency-Key。 */
@Schema(description = "报案登记请求")
public record RegisterReportReq(
    @Schema(description = "平台保单号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "保单号不能为空")
        String policyNo,
    @Schema(description = "被保人假名化ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "被保人假名化ID不能为空")
        String insuredIdHash,
    @Schema(description = "出险时间", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "出险时间不能为空")
        Instant outInsureTime,
    @Schema(description = "事故简述（PII 不落明文）") String lossDescription,
    @Schema(description = "渠道") String sourceChannel,
    @Schema(description = "来源系统") String sourceSystem) {}
