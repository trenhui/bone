package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.domain.model.claim.valueobject.Decision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 人工核赔决策请求（decision = APPROVED / REJECTED / REQUEST_MORE_EVIDENCE）。
 *
 * <p>REJECTED 必填拒赔五要素（{@code comment}）与受控原因码（{@code rejectType}）； APPROVED 必填 {@code
 * approvedAmount}（≤ 申请金额由聚合校验）。
 *
 * <p><b>不含审核人字段</b>：审核人取自已验签 token（{@code CurrentOperator}）。身份若由请求体声明， 「复核人 ≠
 * 第一审核人」的职责分离校验就退化成调用方的自我声明。
 */
@Schema(description = "人工核赔决策请求")
public record ClaimDecisionReq(
    @Schema(description = "决策", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "决策不能为空")
        Decision decision,
    @Schema(description = "拒赔五要素（事实认定+条款依据+证据位置+申诉渠道+法定时限；REJECTED 必填）") String comment,
    @Schema(description = "受控拒赔原因码（01-13 + 20/30/40；REJECTED 必填）") String rejectType,
    @Schema(description = "核定金额（APPROVED 必填，≤ 申请金额）") @DecimalMin(value = "0", message = "核定金额不能为负")
        BigDecimal approvedAmount) {}
