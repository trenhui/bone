package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.BlindLabelApplicationService;
import com.bone.claim.application.BlindLabelApplicationService.BlindLabelView;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 独立盲标工作台（G4-1 数据飞轮的防反馈环闸门）。
 *
 * <p>生命周期：{@code POST}（派发，载荷过 AI 痕迹禁区扫描）→ {@code POST /{id}/submit}（一次性提交盲标结论）→ {@code POST
 * /{id}/unblind}（解盲配对，AI 建议快照此时才落库）。解盲前任何视图都不含 AI 侧字段。
 *
 * <p><b>权限</b>复用 {@code claim:inspection:write/read}：盲标与后督抽检同属质检域，且同一人不能既抽检又盲标的
 * 互斥由应用服务拒绝（409），不需要新权限码（新造权限码不落 IAM 权限表会让端点对所有人 403）。
 */
@Tag(name = "智能理赔-AI治理", description = "独立盲标：防反馈环的金标准样本产出（派发 / 提交 / 解盲）")
@Validated
@RestController
@RequestMapping("/api/v1/claim-ops/blind-labels")
@RequiredArgsConstructor
public class BlindLabelController {

  private final BlindLabelApplicationService blindLabelApplicationService;

  @Operation(
      summary = "派发盲标任务",
      description =
          "为指定案件创建独立盲标任务。载荷（labelPayloadJson）必须是 JSON 对象且不得含任何 AI 痕迹键"
              + "（aiSuggestion/confidence/ruleHits/decisionResult 等，命中返回 422）；"
              + "同一案件存在未解盲任务时 409；标注人与该案后督抽检质检人相同时 409（防既有印象污染）。")
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  @PostMapping
  public ApiResponse<BlindLabelView> dispatch(@RequestBody @Validated DispatchRequest request) {
    return ApiResponse.success(
        blindLabelApplicationService.dispatch(
            request.claimId(), request.assigneeId(), request.labelPayloadJson()));
  }

  @Operation(summary = "提交盲标结论（一次性，提交后不可改写）", description = "仅 PENDING 态可提交；重复提交 409。")
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  @PostMapping("/{id}/submit")
  public ApiResponse<BlindLabelView> submit(
      @PathVariable("id") long id, @RequestBody @Validated SubmitRequest request) {
    return ApiResponse.success(blindLabelApplicationService.submit(id, request.labelResultJson()));
  }

  @Operation(
      summary = "解盲配对",
      description =
          "LABELED → UNBLINDED。解盲时才从决策轨迹读取 AI 建议快照写入库内；"
              + "confidence_source=MODEL 才算 AI 真实参与（PAIRED），否则 AI_ABSENT。发布解盲事件供评测流水线消费。")
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  @PostMapping("/{id}/unblind")
  public ApiResponse<BlindLabelView> unblind(@PathVariable("id") long id) {
    return ApiResponse.success(blindLabelApplicationService.unblind(id));
  }

  @Operation(summary = "盲标任务详情", description = "解盲前视图不含 AI 侧字段（库内在解盲前本就没有 AI 数据）。")
  @PreAuthorize("hasAuthority('claim:inspection:read')")
  @GetMapping("/{id}")
  public ApiResponse<BlindLabelView> get(@PathVariable("id") long id) {
    return ApiResponse.success(blindLabelApplicationService.get(id));
  }

  @Operation(summary = "按标注人查最近任务（上限 50）")
  @PreAuthorize("hasAuthority('claim:inspection:read')")
  @GetMapping
  public ApiResponse<List<BlindLabelView>> list(@RequestParam("assigneeId") String assigneeId) {
    return ApiResponse.success(blindLabelApplicationService.listByAssignee(assigneeId));
  }

  /** 派发请求。载荷由调用方组装（材料引用清单），禁区键扫描在应用层。 */
  public record DispatchRequest(
      @NotNull Long claimId, @NotBlank String assigneeId, @NotBlank String labelPayloadJson) {}

  /** 盲标结论提交请求。 */
  public record SubmitRequest(@NotBlank String labelResultJson) {}
}
