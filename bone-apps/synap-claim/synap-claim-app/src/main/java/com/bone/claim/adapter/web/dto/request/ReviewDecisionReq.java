package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.domain.model.claim.valueobject.Decision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 工作台任务中心决策请求（delivery/05 §2.2 契约形状）。
 *
 * <p><b>与 {@link ClaimDecisionReq} 的差异</b>：契约用结构化 {@link Rejection} 描述拒赔五要素，并额外带 {@code
 * reviewerId}/{@code secondReviewerId}/{@code evidenceRefs} 三个字段——审核人字段<b>一律忽略</b>（取认证主体，理由见 {@link
 * ClaimDecisionReq} 类注释）；{@code rejectType} 为内部扩展字段（契约未定义，但受控原因码是拒赔统计与差异归因的 聚合口径，缺失即 400
 * 而不是自由文本兜底）。
 */
@Schema(description = "工作台审核决策请求（契约 2.2 形状）")
public record ReviewDecisionReq(
    @Schema(description = "决策", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "决策不能为空")
        Decision decision,
    @Schema(description = "契约字段：审核人（服务端忽略，取认证主体）") String reviewerId,
    @Schema(description = "契约字段：复核人（服务端忽略；双人复核走 /claims/{id}/second-review）")
        String secondReviewerId,
    @Schema(description = "审核意见（拒赔时为拒赔说明；结构化五要素优先）") String comment,
    @Schema(description = "引用证据ID") List<String> evidenceRefs,
    @Schema(description = "拒赔结构化五要素（REJECTED 必填）") Rejection rejection,
    @Schema(description = "内部扩展：受控拒赔原因码（REJECTED 必填）") String rejectType,
    @Schema(description = "核定金额（APPROVED 时回填，≤ 申请金额）") BigDecimal approvedAmount) {

  /** 拒赔结构化五要素（R-MAN-03：事实认定 + 条款依据 + 证据位置 + 申诉渠道 + 法定时限）。 */
  @Schema(description = "拒赔结构化五要素")
  public record Rejection(
      @Schema(description = "事实认定") String factFindings,
      @Schema(description = "条款依据") String clauseBasis,
      @Schema(description = "证据位置") List<String> evidenceRefs,
      @Schema(description = "申诉渠道") String appealChannel,
      @Schema(description = "法定时限") String legalDeadline) {}
}
