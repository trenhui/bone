package com.bone.claim.adapter.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 报案详情响应（报案头 + 名下赔案清单）。
 *
 * <p><b>为什么赔案清单不可省</b>：报案详情存在的意义就是回答「这次事故一共赔了几笔、各自到哪一步」。 只回报案头等于把客服又推回按赔案号逐个查——而赔案号与报案号互不推导。
 *
 * @param allCasesFinished 是否全部赔案已终态（<b>派生值</b>，不落库：落库就会与赔案状态形成两份真源）
 */
@Schema(description = "报案详情")
public record ClaimReportResp(
    @Schema(description = "报案号（RP 前缀）") String reportNo,
    @Schema(description = "平台保单号") String policyNo,
    @Schema(description = "计划ID") Long planId,
    @Schema(description = "险种") String claimType,
    @Schema(description = "出险（事故）时间") Instant outInsureTime,
    @Schema(description = "报案状态：REPORTED（可追加）/ CLOSED（已关闭）") String status,
    @Schema(description = "报案登记时间") Instant createdAt,
    @Schema(description = "名下赔案数") int caseCount,
    @Schema(description = "名下赔案是否全部终态（派生）") boolean allCasesFinished,
    @Schema(description = "名下赔案清单（按创建时间升序）") List<CaseBrief> cases) {

  /** 赔案摘要（报案详情内嵌；不暴露赔案全量字段，避免详情响应随赔案字段膨胀而漂移）。 */
  @Schema(description = "报案下的赔案摘要")
  public record CaseBrief(
      @Schema(description = "案件ID（字符串，避免 19 位雪花在前端丢精度）") String claimId,
      @Schema(description = "赔案号（CL 前缀）") String claimNo,
      @Schema(description = "状态（DB 枚举 name）") String status,
      @Schema(description = "对外状态（两值投影）") String externalStatus,
      @Schema(description = "业务阶段") String stage,
      @Schema(description = "申请金额") BigDecimal claimAmount,
      @Schema(description = "核定金额") BigDecimal approvedAmount,
      @Schema(description = "建案时间") Instant createdAt) {}
}
