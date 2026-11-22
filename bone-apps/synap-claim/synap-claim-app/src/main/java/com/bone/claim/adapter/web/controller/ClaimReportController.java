package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.request.RegisterReportReq;
import com.bone.claim.adapter.web.dto.request.SplitCaseReq;
import com.bone.claim.adapter.web.dto.response.CaseUnderReportResp;
import com.bone.claim.adapter.web.dto.response.ReportResp;
import com.bone.claim.application.ClaimReportApplicationService;
import com.bone.claim.application.ClaimReportApplicationService.RegisterReport;
import com.bone.claim.application.ClaimReportApplicationService.RegisteredCase;
import com.bone.claim.application.ClaimReportApplicationService.RegisteredReport;
import com.bone.claim.application.ClaimReportApplicationService.SplitCase;
import com.bone.claim.application.ClaimReportApplicationService.SplitCase.ClaimItemDto;
import com.bone.claim.application.ClaimSubmissionOrchestrator;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报案 Web 接口（1:N 的"一"）：登记报案、报案下分案、列报案下赔案、关闭报案。
 *
 * <p>报案号（RP）是一事故告知的检索键，赔案号（CL）是每笔赔付的处理单元编号。员工端「提交理赔」主链路仍走 {@link ClaimCaseController#submit}（经
 * {@link ClaimSubmissionOrchestrator} 编排为两个独立事务，对外行为不变）；本控制器提供报案维度的显式登记/分案/聚合查询。
 */
@Tag(name = "智能理赔-报案", description = "报案登记、分案、报案下赔案聚合")
@RestController
@RequestMapping("/api/v1/claim-reports")
@RequiredArgsConstructor
public class ClaimReportController {

  private final ClaimReportApplicationService claimReportApplicationService;
  private final ClaimSubmissionOrchestrator claimSubmissionOrchestrator;

  @Operation(summary = "登记报案", description = "登记一次事故告知（FNOL），返回报案号 RP；幂等键必传")
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping({"", "/"})
  public ApiResponse<ReportResp> register(
      @Parameter(description = "幂等键（UUID v4），必传")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = true)
          String idempotencyKey,
      @Valid @RequestBody RegisterReportReq request) {
    RegisteredReport result =
        claimReportApplicationService.register(
            new RegisterReport(
                idempotencyKey,
                request.policyNo(),
                request.insuredIdHash(),
                request.outInsureTime(),
                request.lossDescription(),
                request.sourceChannel(),
                request.sourceSystem()));
    return ApiResponse.success(
        HttpStatus.CREATED.value(), new ReportResp(result.reportId(), result.reportNo()));
  }

  @Operation(summary = "分案：报案下新建赔案", description = "继承母案主体/保单，明细与发票来自请求；新赔案号 CL；幂等键必传")
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/{reportNo}/cases")
  public ApiResponse<ReportResp> splitCase(
      @Parameter(description = "报案号（RP 前缀）") @PathVariable String reportNo,
      @Parameter(description = "幂等键（UUID v4），必传")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = true)
          String idempotencyKey,
      @Valid @RequestBody SplitCaseReq request) {
    List<ClaimItemDto> items =
        request.items().stream()
            .map(
                i ->
                    new ClaimItemDto(
                        i.itemName(),
                        i.itemCode(),
                        i.category(),
                        i.quantity(),
                        i.unitPrice(),
                        i.amount(),
                        i.department(),
                        i.feeCode()))
            .toList();
    SplitCase command =
        new SplitCase(
            reportNo,
            idempotencyKey,
            request.invoiceCode(),
            request.invoiceNo(),
            request.invoiceDate(),
            request.hospitalCode(),
            request.diagnosisCode(),
            items);
    // 经编排层：子案与报案计数分属两个独立事务（R9）。
    RegisteredCase result = claimSubmissionOrchestrator.splitCase(command, idempotencyKey);
    return ApiResponse.success(
        HttpStatus.CREATED.value(), new ReportResp(result.claimId(), result.claimNo()));
  }

  @Operation(summary = "报案下赔案列表", description = "按报案号列出其下全部赔案（1:N），时间升序母案在前")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{reportNo}/cases")
  public ApiResponse<List<CaseUnderReportResp>> listCases(
      @Parameter(description = "报案号（RP 前缀）") @PathVariable String reportNo) {
    List<CaseUnderReportResp> rows =
        claimReportApplicationService.listCases(reportNo).stream()
            .map(
                c ->
                    new CaseUnderReportResp(
                        c.claimId(), c.claimNo(), c.status(), c.externalStatus(), c.claimAmount()))
            .toList();
    return ApiResponse.success(rows);
  }

  @Operation(summary = "关闭报案", description = "关闭后不再接受新赔案；幂等：已关闭返回 data=false")
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/{reportNo}/close")
  public ApiResponse<Boolean> closeReport(
      @Parameter(description = "报案号（RP 前缀）") @PathVariable String reportNo) {
    return ApiResponse.success(claimReportApplicationService.closeReport(reportNo));
  }
}
