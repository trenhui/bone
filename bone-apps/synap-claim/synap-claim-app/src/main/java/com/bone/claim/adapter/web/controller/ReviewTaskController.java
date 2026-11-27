package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.ReviewTaskStatusMapper;
import com.bone.claim.adapter.web.ReviewTaskStatusMapper.TaskStatus;
import com.bone.claim.adapter.web.assembler.DecisionTraceAssembler;
import com.bone.claim.adapter.web.assembler.ReviewDecisionAssembler;
import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq;
import com.bone.claim.adapter.web.dto.response.DecisionExecutionResp;
import com.bone.claim.adapter.web.dto.response.DecisionTraceResp;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.DecisionExecutionApplicationService;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.core.exception.BizException;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
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

  private static final int MAX_BATCH_DECISION = 20;
  private static final String BATCH_DECISION_PATH = "/api/v1/review/tasks/batch-decision";

  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final DecisionExecutionApplicationService decisionExecutionApplicationService;
  private final IdempotencyService idempotencyService;

  /**
   * 批量决策请求：{@code idempotencyKey} 是整批的客户端幂等键，逐条派生稳定子键（见 {@link
   * #stableClaimActionKey}），重放同一批不会重复决策任何一案。
   */
  public record BatchDecisionReq(
      @NotEmpty(message = "items 不能为空") List<BatchDecisionItem> items,
      @NotBlank(message = "idempotencyKey 不能为空") String idempotencyKey) {}

  public record BatchDecisionItem(
      long claimId,
      @NotNull(message = "decision 不能为空") Decision decision,
      String rejectType,
      BigDecimal approvedAmount) {}

  /** 部分成功响应：succeeded 是成功的案件 ID，failed 逐条带稳定业务码，不中断整批。 */
  public record BatchDecisionResp(List<Long> succeeded, List<BatchDecisionFailure> failed) {}

  public record BatchDecisionFailure(long claimId, String code, String message) {}

  private final ClaimIdempotencyProperties idempotencyProperties;

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

  @Operation(
      summary = "审核决策",
      description =
          "路由到案件中心人工核赔决策（41→42/45/21）；审核人取认证主体。"
              + "同 Idempotency-Key 重复请求重放首次结果，防「决策已生效但响应丢失」的重试二次改判；不传则不启用幂等")
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
      @Parameter(description = "幂等键（UUID v4）；不传则不启用幂等，重试同动作必须复用同键")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false)
          String idempotencyKey,
      @Valid @RequestBody ReviewDecisionReq request,
      HttpServletRequest httpRequest) {
    DecisionPayload payload = new DecisionPayload(claimId, request);
    Optional<IdempotencyService.ReplayedResponse> replayed =
        idempotencyService.replay(
            idempotencyKey,
            MDC.get("userId"),
            httpRequest.getMethod(),
            httpRequest.getRequestURI(),
            payload);
    if (replayed.isPresent()) {
      return replayedBody(replayed.get());
    }
    // 拒赔证据位置锚点：写入期校验，杜绝不可定位的自由文本进合规文书（批量端点契约不带此字段，不接此类校验）。
    ReviewDecisionAssembler.requireEvidenceRefs(request);
    claimCaseApplicationService.decide(
        claimId,
        request.decision(),
        CurrentOperator.requireId(),
        ReviewDecisionAssembler.composeComment(request),
        ReviewDecisionAssembler.requireRejectType(request),
        request.approvedAmount());
    ApiResponse<Void> body = ApiResponse.success();
    idempotencyService.remember(
        idempotencyKey,
        MDC.get("userId"),
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        payload,
        new IdempotencyService.ReplayedResponse(HttpStatus.OK.value(), null, body),
        idempotencyProperties.getTtl());
    return body;
  }

  /**
   * 批量审核决策（显式部分成功）。
   *
   * <p><b>为什么不复用单条端点的事务边界</b>：逐条调用 {@link ClaimCaseApplicationService#decide}（自身
   * {@code @Transactional}），每条独立提交——一条状态冲突只进 {@code failed}，不把前面已成功的决策回滚成「整批失败」。 对调用方而言，幂等重放
   * 必须得到同一结果，故每条用「批次键 + 案件 ID + 动作」派生的稳定子键走幂等快照：成功才记快照，失败留空让客户端改状态后可重试。
   */
  @Operation(
      summary = "批量审核决策",
      description =
          "逐条独立事务；部分成功语义——成功的进 succeeded，状态冲突等失败项进 failed（带稳定业务码），不中断整批。"
              + "单批上限 20 单，超出 409；同 idempotencyKey 重放不重复决策。")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "部分成功结果（succeeded / failed）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "SC_CLAIM_TASK_CONFLICT: 超过 20 单/批")
  })
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/batch-decision")
  public ApiResponse<BatchDecisionResp> batchDecision(
      @Valid @RequestBody BatchDecisionReq request) {
    List<BatchDecisionItem> items = request.items();
    if (items == null || items.size() > MAX_BATCH_DECISION) {
      throw ClaimErrors.of(
          ClaimErrorCodes.SC_CLAIM_TASK_CONFLICT,
          "批量决策上限 " + MAX_BATCH_DECISION + " 单，当前 " + (items == null ? 0 : items.size()) + " 单");
    }

    String operatorId = CurrentOperator.requireId();
    List<Long> succeeded = new ArrayList<>();
    List<BatchDecisionFailure> failed = new ArrayList<>();
    for (BatchDecisionItem item : items) {
      String scopeKey =
          idempotencyService.buildScopeKey(
              stableClaimActionKey(request.idempotencyKey(), item.claimId(), "review-decide"),
              operatorId,
              "POST",
              BATCH_DECISION_PATH);
      Optional<IdempotencyService.ReplayedResponse> replayed =
          idempotencyService.replay(scopeKey, item);
      if (replayed.isPresent()) {
        succeeded.add(item.claimId());
        continue;
      }
      try {
        ReviewDecisionReq single =
            new ReviewDecisionReq(
                item.decision(),
                null,
                null,
                null,
                null,
                null,
                item.rejectType(),
                item.approvedAmount());
        claimCaseApplicationService.decide(
            item.claimId(),
            item.decision(),
            operatorId,
            ReviewDecisionAssembler.composeComment(single),
            ReviewDecisionAssembler.requireRejectType(single),
            item.approvedAmount());
        idempotencyService.remember(
            scopeKey,
            item,
            new IdempotencyService.ReplayedResponse(
                200, null, ApiResponse.success(item.claimId())));
        succeeded.add(item.claimId());
      } catch (RuntimeException e) {
        failed.add(new BatchDecisionFailure(item.claimId(), errorCodeOf(e), e.getMessage()));
      }
    }
    return ApiResponse.success(new BatchDecisionResp(List.copyOf(succeeded), List.copyOf(failed)));
  }

  /** 每案幂等子键 = 批次键:案件ID:动作；同批次重放派生同一组键，不同案件互不冲突。 */
  public static String stableClaimActionKey(
      String batchIdempotencyKey, long claimId, String action) {
    return batchIdempotencyKey + ":" + claimId + ":" + action;
  }

  /** 有稳定业务码就用它，域层状态冲突（无码）统一落 CLAIM_STATUS_CONFLICT，前端可分支提示。 */
  private static String errorCodeOf(RuntimeException e) {
    if (e instanceof BizException biz && biz.getErrorCode() != null) {
      return biz.getErrorCode();
    }
    return ClaimErrorCodes.CLAIM_STATUS_CONFLICT;
  }

  @Operation(
      summary = "决策执行摘要",
      description =
          "取该案最近一次决策执行的只读摘要（含自动通过结论的消费结果）；无执行记录 404。"
              + "与 /trace 分工：轨迹回答「怎么判出来的」，本接口回答「结论有没有被消费、为什么没有」。")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "执行摘要"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_DECISION_NOT_FOUND")
  })
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{claimId}/execution")
  public ApiResponse<DecisionExecutionResp> execution(
      @Parameter(description = "案件ID（= 任务ID）") @PathVariable long claimId) {
    DecisionExecution execution = decisionExecutionApplicationService.findLatest(claimId);
    if (execution == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_DECISION_NOT_FOUND, claimId);
    }
    return ApiResponse.success(DecisionExecutionResp.of(execution));
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
  public ApiResponse<List<DecisionTraceResp>> trace(
      @Parameter(description = "案件ID（= 任务ID）") @PathVariable long claimId) {
    String traceId = decisionExecutionApplicationService.latestTraceId(claimId);
    if (traceId == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_DECISION_NOT_FOUND, claimId);
    }
    return ApiResponse.success(
        DecisionTraceAssembler.toResp(decisionExecutionApplicationService.replay(traceId)));
  }

  /** 决策幂等载荷：案件号 + 请求体参与指纹，防同键换决策内容仍能重放。 */
  private record DecisionPayload(long claimId, ReviewDecisionReq request) {}

  @SuppressWarnings("unchecked")
  private static <T> ApiResponse<T> replayedBody(IdempotencyService.ReplayedResponse snapshot) {
    return (ApiResponse<T>) snapshot.body();
  }
}
