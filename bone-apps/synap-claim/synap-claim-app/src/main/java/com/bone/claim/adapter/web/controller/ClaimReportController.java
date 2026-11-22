package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.assembler.ClaimReportAssembler;
import com.bone.claim.adapter.web.dto.request.AppendCaseReq;
import com.bone.claim.adapter.web.dto.response.ClaimCaseResp;
import com.bone.claim.adapter.web.dto.response.ClaimReportResp;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.ClaimReportApplicationService;
import com.bone.claim.application.ClaimReportApplicationService.AppendCaseCommand;
import com.bone.claim.application.ClaimReportApplicationService.ReportDetail;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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
 * 报案 Web 接口（报案详情 + 追加赔案）。
 *
 * <p><b>为什么单开 Controller 而不是塞进 {@code ClaimCaseController}</b>：本组端点的主体是「一次事故告知」， 路径也在 {@code
 * /api/v1/claims/reports/**} 下；混进赔案控制器会让两条路径语义在同一类里纠缠。
 *
 * <p>授权沿用赔案资源权限（{@code claim:claims:read} / {@code claim:claims:write}）：追加赔案本质仍是建案，
 * 单独开一个权限码会让「谁能建首案」与「谁能追加」变成两套需要同步维护的授权面。
 */
@Tag(name = "智能理赔-报案", description = "报案详情、追加赔案（一次告知多笔赔付）")
@RestController
@RequestMapping("/api/v1/claims/reports")
@RequiredArgsConstructor
public class ClaimReportController {

  private final ClaimReportApplicationService claimReportApplicationService;
  private final ClaimReportAssembler claimReportAssembler;
  private final IdempotencyService idempotencyService;
  private final ClaimIdempotencyProperties idempotencyProperties;

  @Operation(summary = "报案详情", description = "报案头 + 名下赔案清单；不存在或跨租户一律 404（防枚举）")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "查询成功"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_REPORT_NOT_FOUND：报案不存在或不属于当前租户")
  })
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{reportNo}")
  public ApiResponse<ClaimReportResp> detail(
      @Parameter(description = "报案号（RP 前缀，24 字符）") @PathVariable String reportNo) {
    ReportDetail detail = claimReportApplicationService.detail(reportNo);
    return ApiResponse.success(claimReportAssembler.toResp(detail));
  }

  @Operation(summary = "追加赔案", description = "在同一报案下新建赔案；保单/计划/被保人/出险时间一律继承报案，请求体出现这些字段即 400")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "追加成功"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "参数校验失败 / SC_CLAIM_REPORT_CONTEXT_MISMATCH（请求携带保单维度字段）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_REPORT_NOT_FOUND：报案不存在或不属于当前租户"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "SC_CLAIM_REPORT_CLOSED：报案已关闭，不再受理新赔案")
  })
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/{reportNo}/cases")
  public ApiResponse<ClaimCaseResp> appendCase(
      @Parameter(description = "报案号（RP 前缀）") @PathVariable String reportNo,
      @Parameter(description = "幂等键（UUID 或稳定业务键），必传（缺失即 400，服务端不代生成）")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = true)
          String idempotencyKey,
      @Parameter(description = "追加赔案请求") @Valid @RequestBody AppendCaseReq request,
      HttpServletRequest httpRequest) {
    rejectContextOverride(request);

    AppendCaseCommand command =
        claimReportAssembler.toAppendCommand(reportNo, idempotencyKey, request);
    String method = httpRequest.getMethod();
    String path = httpRequest.getRequestURI();
    Optional<IdempotencyService.ReplayedResponse> replayed =
        idempotencyService.replay(idempotencyKey, MDC.get("userId"), method, path, command);
    if (replayed.isPresent()) {
      return replayedBody(replayed.get());
    }

    SubmittedClaim submitted = claimReportApplicationService.appendCase(command);
    ApiResponse<ClaimCaseResp> body =
        ApiResponse.success(HttpStatus.CREATED.value(), claimReportAssembler.toCaseResp(submitted));
    idempotencyService.remember(
        idempotencyKey,
        MDC.get("userId"),
        method,
        path,
        command,
        new IdempotencyService.ReplayedResponse(
            HttpStatus.CREATED.value(), "/api/v1/claims/" + submitted.claimId(), body),
        idempotencyProperties.getTtl());
    return body;
  }

  /**
   * 拒绝携带事故上下文的追加请求。
   *
   * <p>不静默忽略的理由：调用方传了 {@code policyNo} 却仍按原保单赔付，会让人以为覆盖成功—— 「看着生效、其实没生效」比报错危险。错误信息里点名具体字段，便于对方一次改对。
   */
  private static void rejectContextOverride(AppendCaseReq request) {
    List<String> forbidden = request.forbiddenContextKeys();
    if (!forbidden.isEmpty()) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_REPORT_CONTEXT_MISMATCH, String.join(", ", forbidden));
    }
  }

  @SuppressWarnings("unchecked")
  private static ApiResponse<ClaimCaseResp> replayedBody(
      IdempotencyService.ReplayedResponse snapshot) {
    return (ApiResponse<ClaimCaseResp>) snapshot.body();
  }
}
