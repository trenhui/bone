package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.assembler.ClaimCaseAssembler;
import com.bone.claim.adapter.web.dto.request.ClaimDecisionReq;
import com.bone.claim.adapter.web.dto.request.ClaimPageReq;
import com.bone.claim.adapter.web.dto.request.SubmitClaimReq;
import com.bone.claim.adapter.web.dto.response.ClaimCaseResp;
import com.bone.claim.application.AutoApprovalApplicationService;
import com.bone.claim.application.AutoApprovalApplicationService.AutoApprovalOutcome;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.HangUpType;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.security.auth.CurrentOperator;
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
 *
 * <p><b>操作人身份</b>：所有审核类动作的操作人一律取 {@link CurrentOperator}（已验签 token 的 userId）， <b>不接受 {@code
 * X-Operator-Id} 之类的客户端声明</b>——双人复核的「复核人 ≠ 第一审核人」校验若可被请求头改写， 职责分离（SoD）即形同虚设。网关同时剥离入站同名头，双重收口。
 */
@Tag(name = "智能理赔-赔案", description = "报案受理、核赔决策、待办查询")
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimCaseController {

  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final AutoApprovalApplicationService autoApprovalApplicationService;
  private final ClaimCaseAssembler claimCaseAssembler;
  private final IdempotencyService idempotencyService;
  private final ClaimIdempotencyProperties idempotencyProperties;

  @Operation(summary = "提交理赔申请", description = "报案建案并受理签收（DRAFT→7）；Idempotency-Key 必传（缺失 400）")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "受理成功"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "参数校验失败 / SC_CLAIM_POLICY_KEY_REQUIRED（平台保单号与保司保单号都未提供）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "被保人不匹配"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "COMMON_IDEMPOTENCY_CONFLICT: 同幂等键不同请求体"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description =
            "SC_CLAIM_POLICY_NOT_ACTIVE / SC_CLAIM_OUT_OF_POLICY_PERIOD / "
                + "SC_CLAIM_POLICY_AMBIGUOUS（保司保单号命中多张，须改传平台号）/ "
                + "SC_CLAIM_POLICY_NUMBER_CONFLICT（两个保单号指向不同保单）")
  })
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @ResponseStatus(HttpStatus.CREATED)
  // 显式登记 {"", "/"}：前端 createApiClient('/api/v1/claims') + axios url '/' 会拼出**带尾斜杠**的
  // /api/v1/claims/（combineURLs 恒补一个 '/'），而 Spring Boot 3 / PathPatternParser 默认**不做**
  // 尾斜杠匹配 ⇒ 报案提交与待办列表全数 404「请求地址不存在」（2026-10-28 全模块联调实测确诊）。
  @PostMapping({"", "/"})
  public ApiResponse<ClaimCaseResp> submit(
      @Parameter(description = "幂等键（UUID v4），必传（P0-3：缺失即 400，服务端不代生成）")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = true)
          String idempotencyKey,
      @Parameter(description = "报案请求") @Valid @RequestBody SubmitClaimReq request,
      HttpServletRequest httpRequest) {
    SubmitClaim command = claimCaseAssembler.toSubmitClaim(request);
    // 幂等键来自请求头（MapStruct 从体映射不到），补进命令供域层查重与 NOT NULL 列落库
    command = command.withIdempotencyKey(idempotencyKey);
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
            HttpStatus.CREATED.value(), "/api/v1/claims/" + submitted.claimId(), body),
        idempotencyProperties.getTtl());
    return body;
  }

  @Operation(summary = "待办/列表分页", description = "工作台待办（默认待核赔 40）；支持按状态集/渠道过滤")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  // 同上：前端 axios 会把 base + '/' 拼成 /api/v1/claims/，必须显式登记尾斜杠路径。
  @GetMapping({"", "/"})
  public ApiResponse<PageResult<ClaimHeadProjection>> page(
      @Valid @ModelAttribute ClaimPageReq qry) {
    List<ClaimStatus> statuses = claimCaseAssembler.statusesOf(qry);
    return ApiResponse.success(
        claimCaseApplicationService.page(
            statuses, qry.sourceChannel(), qry.pageOrDefault(), qry.sizeOrDefault()));
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
  public ApiResponse<Void> allocate(@Parameter(description = "案件ID") @PathVariable long id) {
    claimCaseApplicationService.allocate(id, CurrentOperator.requireId());
    return ApiResponse.success();
  }

  @Operation(summary = "开始初审", description = "进入初审（10→11 人工 / 13 自动化）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/pre-exam/start")
  public ApiResponse<Void> startPreExam(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "是否自动化路径")
          @RequestHeader(value = "X-Automated", defaultValue = "false")
          boolean automated) {
    claimCaseApplicationService.startPreExam(id, automated, CurrentOperator.requireId());
    return ApiResponse.success();
  }

  @Operation(summary = "决策图完成", description = "初审完成并回填理算核定金额（11/13→12）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/pre-exam/complete")
  public ApiResponse<Void> completePreExam(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "核定金额") @RequestHeader(value = "X-Approved-Amount")
          java.math.BigDecimal approvedAmount) {
    claimCaseApplicationService.completePreExam(id, approvedAmount, CurrentOperator.requireId());
    return ApiResponse.success();
  }

  @Operation(summary = "转人工核赔", description = "系统建议/强制转人工（12→40）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/escalate")
  public ApiResponse<Void> escalate(@Parameter(description = "案件ID") @PathVariable long id) {
    claimCaseApplicationService.escalate(id, CurrentOperator.requireId());
    return ApiResponse.success();
  }

  @Operation(summary = "领取核赔任务", description = "核赔员领取（40→41）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/adjust/claim")
  public ApiResponse<Void> claimAdjustTask(@Parameter(description = "案件ID") @PathVariable long id) {
    // 领取人即当前登录核赔员（第一审核人），后续双人复核据此判「复核人 ≠ 第一审核人」
    claimCaseApplicationService.claimAdjustTask(id, CurrentOperator.requireId());
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
        CurrentOperator.requireId(),
        request.comment(),
        request.rejectType(),
        request.approvedAmount());
    return ApiResponse.success();
  }

  @Operation(summary = "发起双人复核", description = "42→44（≥1 万；复核人须非第一审核人）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/second-review")
  public ApiResponse<Void> submitForSecondReview(
      @Parameter(description = "案件ID") @PathVariable long id) {
    claimCaseApplicationService.submitForSecondReview(id, CurrentOperator.requireId());
    return ApiResponse.success();
  }

  @Operation(summary = "复核结论", description = "44→50 通过 / 45 拒赔")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/second-review/complete")
  public ApiResponse<Void> completeSecondReview(
      @Parameter(description = "案件ID") @PathVariable long id,
      @Parameter(description = "是否通过") @RequestHeader(value = "X-Approved", defaultValue = "true")
          boolean approved) {
    claimCaseApplicationService.completeSecondReview(id, approved, CurrentOperator.requireId());
    return ApiResponse.success();
  }

  @Operation(
      summary = "自动通过",
      description = "12→50（R-FAST-01 快赔路径）+ 建结算单软占额度；结案与建单同事务，避免「已结案未出金」的静默欠账")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "已结案并建结算单（返回资金阶段与单号）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_ADJUST_NOT_FOUND: 尚未理算，无出金依据"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "SC_CLAIM_QUOTA_INSUFFICIENT: 可用额度不足")
  })
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{id}/auto-approve")
  public ApiResponse<AutoApprovalOutcome> autoApprove(
      @Parameter(description = "案件ID") @PathVariable long id) {
    return ApiResponse.success(
        autoApprovalApplicationService.approveAndSettle(id, CurrentOperator.requireId()));
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
          HangUpType type) {
    String operator = CurrentOperator.requireId();
    if ("RESUME".equalsIgnoreCase(action)) {
      claimCaseApplicationService.resume(id, operator);
    } else {
      claimCaseApplicationService.hangUp(id, type, operator);
    }
    return ApiResponse.success();
  }

  /**
   * 撤回同意（PIPL 第 15 条；DSAR 册 {@code POST /consent/revoke}，G10 合规前置）。
   *
   * <p><b>为什么挂在赔案下而不是全局 {@code /consent/revoke}</b>：本系统的个人信息处理是按<b>赔案</b>组织的
   * （额度、决策、材料、审计都挂在案件上），撤回的影响面天然是"这个主体的这一批处理"。全局端点在没有主体主键
   * 的模型里无法定位到具体赔案，只能靠额外索引再猜。上游同意服务若需全局撤回，按其自身流水反查本模块案件后 逐案调用即可。
   *
   * <p><b>幂等</b>：重复撤回返回 200 + {@code data=false}（本来就已撤回），不报错、不 bump version。
   *
   * <p><b>权限</b>：沿用 {@code claim:claims:write}。DSAR 独立权限码（查阅/删除/撤回分权，避免同一岗位既能查又能删） 属 G10
   * 剩余范围，拆码时在此改一行即可——<b>不</b>因为"以后要拆"就先造一个没人绑定的权限码（配了不生效）。
   */
  @Operation(
      summary = "撤回同意（PIPL 15 条）",
      description =
          "置撤回标记后，分配/初审/核赔/复核/自动通过等处理类用例一律 422 拒绝；" + "关单、挂起等收尾动作不受影响。幂等：重复撤回返回 data=false。")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "已撤回（data=true）/ 本来就已撤回（data=false）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_NOT_FOUND")
  })
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/{id}/consent/revoke")
  public ApiResponse<Boolean> revokeConsent(
      @Parameter(description = "案件ID") @PathVariable long id) {
    return ApiResponse.success(claimCaseApplicationService.revokeConsent(id));
  }

  @SuppressWarnings("unchecked")
  private static ApiResponse<ClaimCaseResp> toReplayedResponse(
      IdempotencyService.ReplayedResponse snapshot) {
    return (ApiResponse<ClaimCaseResp>) snapshot.body();
  }
}
