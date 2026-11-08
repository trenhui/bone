package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.InspectionApplicationService;
import com.bone.claim.application.InspectionApplicationService.InspectionView;
import com.bone.claim.domain.model.inspection.InspectionAction;
import com.bone.claim.domain.model.inspection.InspectionDiffType;
import com.bone.claim.domain.model.inspection.InspectionDimension;
import com.bone.claim.domain.model.inspection.InspectionResult;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A9 后督抽检接口（§10.1 #13 发起抽检 / #14 抽检结论）。
 *
 * <p><b>命名空间</b>：挂在 {@code /api/v1/review/**}（管理后台/工作台命名空间），与 §10.1 的 #13/#14 契约路径一致； 案件侧动作仍在
 * {@code /api/v1/claims/**}，两侧由不同适配器承接（见 {@code ReviewTaskController} 的收敛说明）。
 *
 * <p><b>操作人</b>：质检人取 {@link CurrentOperator}（已验签 token），不接受请求体声明——后督结论要追责到人。
 */
@Tag(name = "智能理赔-后督抽检", description = "发起抽检 / 领取 / 结论 / 查询")
@RestController
@RequestMapping("/api/v1/review/inspections")
@RequiredArgsConstructor
public class InspectionController {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final InspectionApplicationService inspectionApplicationService;

  /** #13 发起抽检请求（{@code dimension} 缺省 AMOUNT）。 */
  public record OpenInspectionReq(
      @Parameter(description = "案件ID") Long claimId,
      @Parameter(description = "抽检维度 AMOUNT/DOCUMENT/MEDICAL/PROCESS")
          InspectionDimension dimension) {}

  /** #14 抽检结论请求。 */
  public record ConcludeInspectionReq(
      @Parameter(description = "结论 PASS/DIFF") InspectionResult result,
      @Parameter(description = "差异类型 ADJUST_MISMATCH/RULE_MISS/AI_MISJUDGE/QUOTA_ABNORMAL/NONE")
          InspectionDiffType diffType,
      @Parameter(description = "差异明细（受控字段 → 本方值/复核值）") Map<String, String> diffDetail,
      @Parameter(description = "处置动作 RECALCULATE/REVIEW/REFUND/CLOSE/NONE") InspectionAction action,
      @Parameter(description = "处置目标状态（§6.2 枚举名或质检段 30/31/32，可空）") String targetStatus) {}

  @Operation(summary = "发起抽检（#13）", description = "案件须已终态；同案重复发起幂等返回原抽检单")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "抽检单已创建（或幂等返回既有单）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_NOT_FOUND"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "SC_CLAIM_STATUS_CONFLICT: 案件未终态")
  })
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<InspectionView> open(@Valid @RequestBody OpenInspectionReq request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        inspectionApplicationService.openManually(
            request.claimId(), request.dimension(), CurrentOperator.requireId()));
  }

  @Operation(summary = "领取抽检任务", description = "PENDING → IN_PROGRESS（同一质检人重复领取幂等）")
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  @PostMapping("/{inspectionNo}/claim")
  public ApiResponse<InspectionView> claim(
      @Parameter(description = "抽检单号") @PathVariable String inspectionNo) {
    return ApiResponse.success(
        inspectionApplicationService.claim(inspectionNo, CurrentOperator.requireId()));
  }

  @Operation(summary = "抽检结论（#14）", description = "结论即终态，不可改写；DIFF 必带受控差异类型/处置动作/明细")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "结论已登记"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "SC_CLAIM_INSPECTION_NOT_FOUND"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "SC_CLAIM_INSPECTION_CONFLICT: 已结论")
  })
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  @PostMapping("/{inspectionNo}/conclusion")
  public ApiResponse<InspectionView> conclude(
      @Parameter(description = "抽检单号") @PathVariable String inspectionNo,
      @Valid @RequestBody ConcludeInspectionReq request) {
    return ApiResponse.success(
        inspectionApplicationService.conclude(
            inspectionNo,
            request.result(),
            request.diffType(),
            toJson(request.diffDetail()),
            request.action(),
            request.targetStatus(),
            CurrentOperator.requireId()));
  }

  @Operation(summary = "查询抽检单", description = "按单号查询")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{inspectionNo}")
  public ApiResponse<InspectionView> detail(
      @Parameter(description = "抽检单号") @PathVariable String inspectionNo) {
    return ApiResponse.success(inspectionApplicationService.detail(inspectionNo));
  }

  @Operation(summary = "查询案件最新抽检单", description = "无抽检单时返回 data=null（非 404）")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<InspectionView> latest(
      @Parameter(description = "案件ID") @RequestParam("claimId") long claimId) {
    return ApiResponse.success(inspectionApplicationService.latest(claimId));
  }

  /** 差异明细 → JSON 文本（列类型为 JSON；空明细返回 null，由领域层判定 DIFF 必须带明细）。 */
  private static String toJson(Map<String, String> detail) {
    if (detail == null || detail.isEmpty()) {
      return null;
    }
    try {
      return JSON.writeValueAsString(detail);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("差异明细无法序列化为 JSON", e);
    }
  }
}
