package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.ReviewTaskStatusMapper;
import com.bone.claim.adapter.web.ReviewTaskStatusMapper.TaskStatus;
import com.bone.claim.adapter.web.assembler.ReviewDecisionAssembler;
import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.DecisionExecutionApplicationService;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 核赔工作台「任务中心」契约门面（delivery/05 §一 / §2.2；落地版 §10.6 #6 路径分歧的方案①）。
 *
 * <p><b>门面而非第二套业务</b>：三个端点全部路由到既有案件中心能力（{@link ClaimCaseApplicationService} / {@link
 * DecisionExecutionApplicationService}），本类不含状态判断、不碰仓储、不复制校验。选方案①（代码补门面、契约不回改）的理由： 改契约会波及
 * integration-out 下游与已签的 05，而门面的代价只是一层转发。
 *
 * <p><b>任务 ID 即案件 ID</b>：任务维在案件模型里没有独立实体（见 {@link ReviewTaskStatusMapper} 的映射说明）， 因此 {@code {id}}
 * 一律按案件 ID 解释——不另造一套任务主键，避免「任务 id 与案件 id 双轨」后工作台跳错对象。
 *
 * <p><b>职责分离（SoD）</b>：请求体里的 {@code reviewerId}/{@code secondReviewerId} <b>一律忽略</b>，审核人取 {@link
 * CurrentOperator}。若允许客户端指定审核人，「复核人 ≠ 第一审核人」的双人复核校验就变成自我豁免。
 */
@Tag(name = "智能理赔-工作台任务中心", description = "待办列表 / 审核决策 / 决策追溯（对外契约门面）")
@RestController
@RequestMapping("/api/v1/review/tasks")
@RequiredArgsConstructor
public class ReviewTaskController {

  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final DecisionExecutionApplicationService decisionExecutionApplicationService;

  @Operation(
      summary = "待办列表",
      description = "任务维 status（WAITING/ASSIGNED/PROCESSING/DONE）→ 案件状态集；不传取默认待办（40+41）")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<PageResult<ClaimHeadProjection>> tasks(
      @Parameter(description = "任务维状态；非法值由 Spring 直接 400") @RequestParam(required = false)
          TaskStatus status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    List<ClaimStatus> statuses = ReviewTaskStatusMapper.toClaimStatuses(status);
    return ApiResponse.success(claimCaseApplicationService.page(statuses, null, page, size));
  }

  @Operation(summary = "审核决策", description = "路由到案件中心人工核赔决策（41→42/45/21）；审核人取认证主体")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "决策生效"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "SC_CLAIM_REJECT_REASON_REQUIRED: 拒赔五要素或受控原因码缺失"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "SC_CLAIM_STATUS_CONFLICT")
  })
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{claimId}/decision")
  public ApiResponse<Void> decide(
      @Parameter(description = "案件ID（= 任务ID）") @PathVariable long claimId,
      @Valid @RequestBody ReviewDecisionReq request) {
    claimCaseApplicationService.decide(
        claimId,
        request.decision(),
        CurrentOperator.requireId(),
        ReviewDecisionAssembler.composeComment(request),
        ReviewDecisionAssembler.requireRejectType(request),
        request.approvedAmount());
    return ApiResponse.success();
  }

  @Operation(summary = "决策追溯", description = "取该案最近一次决策执行的全链轨迹（无执行记录 404）")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "轨迹节点序列"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_DECISION_NOT_FOUND")
  })
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{claimId}/trace")
  public ApiResponse<List<DecisionTraceNode>> trace(
      @Parameter(description = "案件ID（= 任务ID）") @PathVariable long claimId) {
    String traceId = decisionExecutionApplicationService.latestTraceId(claimId);
    if (traceId == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_DECISION_NOT_FOUND, claimId);
    }
    return ApiResponse.success(decisionExecutionApplicationService.replay(traceId));
  }
}
