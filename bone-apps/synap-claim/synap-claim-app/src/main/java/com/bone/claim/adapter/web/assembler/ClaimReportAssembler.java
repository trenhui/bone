package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.request.AppendCaseReq;
import com.bone.claim.adapter.web.dto.response.ClaimCaseResp;
import com.bone.claim.adapter.web.dto.response.ClaimReportResp;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.ClaimReportApplicationService.AppendCaseCommand;
import com.bone.claim.application.ClaimReportApplicationService.ReportDetail;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 报案协议装配（报案详情响应、追加赔案命令、建案响应）。
 *
 * <p>装配集中在此而不是 Controller 内联：同类响应的构造点必须唯一，否则字段增删时必然漏改一处 （`claim/web-response-contract` 规格要求）。赔案明细
 * DTO 的映射复用 {@link ClaimCaseAssembler}， 不另立第二套转换规则。
 */
@Component
@RequiredArgsConstructor
public class ClaimReportAssembler {

  private final ClaimCaseAssembler claimCaseAssembler;

  /** 报案详情响应。 */
  public ClaimReportResp toResp(ReportDetail detail) {
    return new ClaimReportResp(
        detail.report().getReportNo(),
        detail.report().getPolicyNo(),
        detail.report().getPlanId(),
        detail.report().getClaimType(),
        detail.report().getOutInsureTime(),
        detail.report().status().name(),
        detail.report().getCreatedAt(),
        detail.cases().size(),
        detail.allCasesFinished(),
        detail.cases().stream().map(ClaimReportAssembler::toBrief).toList());
  }

  /** 追加赔案命令（幂等键来自请求头，不从请求体取）。 */
  public AppendCaseCommand toAppendCommand(
      String reportNo, String idempotencyKey, AppendCaseReq req) {
    return new AppendCaseCommand(
        reportNo,
        idempotencyKey,
        req.getClaimType(),
        req.getInvoiceCode(),
        req.getInvoiceNo(),
        req.getInvoiceDate(),
        req.getHospitalCode(),
        req.getDiagnosisCode(),
        req.getBatchNo(),
        req.getConsentRecordId(),
        req.getInsurerCode(),
        claimCaseAssembler.toClaimItemDtos(req.getItems()));
  }

  /** 建案响应（首案与追加案共用）。 */
  public ClaimCaseResp toCaseResp(SubmittedClaim submitted) {
    return ClaimCaseResp.of(
        submitted.claimId(), submitted.claimNo(), submitted.reportNo(), newlyAcceptedStatus());
  }

  /**
   * 新建赔案的对外状态。
   *
   * <p><b>为什么从枚举派生而不是写字面量</b>：建案管线末端恒为 {@code ACCEPTED}（报案即受理签收），其对外投影当前是 {@code
   * PENDING_EVIDENCE}。这个字面量原先在首案与追加案两处各写了一遍——一旦 {@code ClaimStatus#toExternal()} 的映射调整，两处会各自漂移，
   * 且漂移后没有任何编译期或测试期信号（响应体看着仍然"有值"）。派生后只有一处真源。
   */
  public static String newlyAcceptedStatus() {
    return ClaimStatus.ACCEPTED.toExternal();
  }

  private static ClaimReportResp.CaseBrief toBrief(ClaimCase claim) {
    return new ClaimReportResp.CaseBrief(
        // 19 位雪花超出 JS 安全整数范围：协议层一律字符串，避免前端静默丢精度
        String.valueOf(claim.getId()),
        claim.getClaimNo(),
        claim.status().name(),
        claim.externalStatus(),
        claim.stageEnum().name(),
        claim.getClaimAmount(),
        claim.getApprovedAmount(),
        claim.getCreatedAt());
  }
}
