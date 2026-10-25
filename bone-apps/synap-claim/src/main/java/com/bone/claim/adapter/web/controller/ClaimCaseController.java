package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.assembler.ClaimCaseAssembler;
import com.bone.claim.adapter.web.dto.request.ClaimDecisionReq;
import com.bone.claim.adapter.web.dto.request.ClaimPageReq;
import com.bone.claim.adapter.web.dto.request.SubmitClaimReq;
import com.bone.claim.adapter.web.dto.response.ClaimCaseResp;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.HangUpType;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 赔案 Web 接口（员工端报案 + 核赔工作台）。
 *
 * <p>授权 scope：员工端 {@code claim:claims:write}（报案）、工作台 {@code claim:review:write}（审核决策）； 幂等放
 * adapter、机制在 application（E-10.1）。
 */
@Tag(name = "智能理赔-赔案", description = "报案受理、核赔决策、待办查询")
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimCaseController {

  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final ClaimCaseAssembler claimCaseAssembler;
  private final IdempotencyService idempotencyService;

  @Operation(summary = "提交理赔申请", description = "报案建案并受理签收（DRAFT→7）；可带 Idempotency-Key 幂等")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "受理成功"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "参数校验失败"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "被保人不匹配"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "COMMON_IDEMPOTENCY_CONFLICT: 同幂等键不同请求体"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "SC_CLAIM_POLICY_NOT_ACTIVE / SC_CLAIM_OUT_OF_POLICY_PERIOD")
  })
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<ClaimCaseResp> submit(
      @Parameter(description = "幂等键（UUID v4）；不传则不启用幂等")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false)
          String idempotencyKey,
      @Parameter(description = "报案请求") @Valid @RequestBody SubmitClaimReq request,
      HttpServletRequest httpRequest) {
    SubmitClaim command = claimCaseAssembler.toSubmitClaim(request);
    // 幂等键来自请求头（MapStruct 从体映射不到），补进命令供域层查重与 NOT NULL 列落库
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      command = command.withIdempotencyKey(idempotencyKey);
    }
    String method = httpRequest.getMethod();
    String path = httpRequest.getRequestURI();

    Optional<IdempotencyService.ReplayedResponse> replayed =
        idempotencyService.replay(idempotencyKey, MDC.get("userId"), method, path, command);
    if (replayed.isPresent()) {
      return toReplayedResponse(replayed.get());
    }

    ClaimCaseApplicationService.SubmittedClaim submitted =
        claimCaseApplicationService.submit(command);
    ApiResponse<ClaimCaseResp> body =
        ApiResponse.success(
            HttpStatus.CREATED.value(),
            ClaimCaseResp.of(submitted.claimId(), submitted.claimNo(), "PENDING_EVIDENCE"));
    idempotencyService.remember(
        idempotencyKey,
        MDC.get("userId"),
        method,
        path,
        command,
        new IdempotencyService.ReplayedResponse(
            HttpStatus.CREATED.value(), "/api/v1/claims/" + submitted.claimId(), body));
    return body;
  }

  @Operation(summary = "待办/列表分页", description = "工作台待办（默认待核赔 40）；支持按状态集/渠道过滤")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<PageResult<ClaimHeadProjection>> page(
      @Valid @ModelAttribute ClaimPageReq qry) {
    List<ClaimStatus> statuses = claimCaseAssembler.statusesOf(qry);
    return ApiResponse.success(
        claimCaseApplicationService.page(statuses, qry.sourceChannel(), qry.page(), qry.size()));
  }

  @Operation(
      summary = "案件详情（进度追踪）",
      description = "赔案头 + 费用明细（§7.2 员工端进度追踪 / 工作台查看详情共用）；SQL 带租户锚点，查不到即 404（防枚举）")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "赔案头 + 明细行（每明细一行）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_NOT_FOUND")
  })
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{id}")
  public ApiResponse<List<ClaimWithItemsProjection>> detail(
      @Parameter(description = "案件ID") @PathVariable long id) {
    return ApiResponse.success(claimCaseApplicationService.getClaimWithItems(id));
  }

  @Operation(summary = "分配初审", description = "材料完整 → 分配初审（7→10）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/allocate")
  public ApiResponse<Void> allocate(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    claimCaseApplicationService.allocate(id, operatorId == null ? MDC.get("userId") : operatorId);
    return ApiResponse.success();
  }

  @Operation(summary = "开始初审", description = "进入初审（10→11 人工 / 13 自动化）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/pre-exam/start")
  public ApiResponse<Void> startPreExam(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "是否自动化路径")
          @RequestHeader(value = "X-Automated", defaultValue = "false")
          boolean automated,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    claimCaseApplicationService.startPreExam(
        id, automated, operatorId == null ? MDC.get("userId") : operatorId);
    return ApiResponse.success();
  }

  @Operation(summary = "决策图完成", description = "初审完成并回填理算核定金额（11/13→12）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/pre-exam/complete")
  public ApiResponse<Void> completePreExam(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "核定金额") @RequestHeader(value = "X-Approved-Amount")
          java.math.BigDecimal approvedAmount,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    claimCaseApplicationService.completePreExam(
        id, approvedAmount, operatorId == null ? MDC.get("userId") : operatorId);
    return ApiResponse.success();
  }

  @Operation(summary = "转人工核赔", description = "系统建议/强制转人工（12→40）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/escalate")
  public ApiResponse<Void> escalate(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    claimCaseApplicationService.escalate(id, operatorId == null ? MDC.get("userId") : operatorId);
    return ApiResponse.success();
  }

  @Operation(summary = "领取核赔任务", description = "核赔员领取（40→41）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/adjust/claim")
  public ApiResponse<Void> claimAdjustTask(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "核赔人（= 当前登录用户）")
          @RequestHeader(value = "X-Operator-Id", required = false)
          String reviewerId) {
    claimCaseApplicationService.claimAdjustTask(
        id, reviewerId == null ? MDC.get("userId") : reviewerId);
    return ApiResponse.success();
  }

  @Operation(
      summary = "人工核赔决策",
      description = "41→42 通过 / 45 拒赔生效（必填五要素+受控原因码）/ 21 补件；通过且≥1万由工作台显式发起双人复核")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "决策生效"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "SC_CLAIM_REJECT_REASON_REQUIRED: 拒赔五要素缺失"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_NOT_FOUND"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "SC_CLAIM_STATUS_CONFLICT / SC_CLAIM_REVIEWER_CONFLICT")
  })
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/decision")
  public ApiResponse<Void> decide(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "审核决策") @Valid @RequestBody ClaimDecisionReq request) {
    claimCaseApplicationService.decide(
        id,
        request.decision(),
        request.reviewerId(),
        request.comment(),
        request.rejectType(),
        request.approvedAmount());
    return ApiResponse.success();
  }

  @Operation(summary = "发起双人复核", description = "42→44（≥1 万；复核人须非第一审核人）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/second-review")
  public ApiResponse<Void> submitForSecondReview(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "复核人") @RequestHeader(value = "X-Operator-Id") String reviewerId) {
    claimCaseApplicationService.submitForSecondReview(id, reviewerId);
    return ApiResponse.success();
  }

  @Operation(summary = "复核结论", description = "44→50 通过 / 45 拒赔")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/second-review/complete")
  public ApiResponse<Void> completeSecondReview(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "是否通过") @RequestHeader(value = "X-Approved", defaultValue = "true")
          boolean approved,
      @Parameter(description = "复核人") @RequestHeader(value = "X-Operator-Id") String reviewerId) {
    claimCaseApplicationService.completeSecondReview(id, approved, reviewerId);
    return ApiResponse.success();
  }

  @Operation(summary = "自动通过", description = "12→50（R-FAST-01 快赔路径；仍走资金 Saga）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/auto-approve")
  public ApiResponse<Void> autoApprove(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    claimCaseApplicationService.autoApprove(
        id, operatorId == null ? MDC.get("userId") : operatorId);
    return ApiResponse.success();
  }

  @Operation(summary = "挂起/恢复", description = "正交挂起机制（不改状态码）；FRAUD 仅风控岗可恢复")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/hangup")
  public ApiResponse<Void> hangUp(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "动作：HANG_UP / RESUME") @RequestHeader("X-Hangup-Action")
          String action,
      @Parameter(description = "挂起类型（HANG_UP 必填）")
          @RequestHeader(value = "X-Hangup-Type", required = false)
          HangUpType type,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    String operator = operatorId == null ? MDC.get("userId") : operatorId;
    if ("RESUME".equalsIgnoreCase(action)) {
      claimCaseApplicationService.resume(id, operator);
    } else {
      claimCaseApplicationService.hangUp(id, type, operator);
    }
    return ApiResponse.success();
  }

  @SuppressWarnings("unchecked")
  private static ApiResponse<ClaimCaseResp> toReplayedResponse(
      IdempotencyService.ReplayedResponse snapshot) {
    return (ApiResponse<ClaimCaseResp>) snapshot.body();
  }
}
