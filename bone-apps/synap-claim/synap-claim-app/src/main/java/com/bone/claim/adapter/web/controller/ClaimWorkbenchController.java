package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.request.AiFeedbackEventReq;
import com.bone.claim.adapter.web.dto.response.AiFeedbackEventResp;
import com.bone.claim.adapter.web.dto.response.AiFeedbackSummaryResp;
import com.bone.claim.application.AiFeedbackCommand;
import com.bone.claim.application.ClaimFeedbackApplicationService;
import com.bone.claim.application.ClaimSearchApplicationService;
import com.bone.claim.domain.model.claim.projection.ClaimSearchHit;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

// 工作台联调契约：跨键检索 + AI 曝光/裁决回流。
// 全部挂在网关已白名单的 /api/v1/claims/** 下，不改网关、不新开 /api/ai/v1/**。
@Tag(name = "智能理赔-工作台联调", description = "跨键检索 / AI 建议回流（只读 + append-only）")
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimWorkbenchController {

  private final ClaimSearchApplicationService claimSearchApplicationService;
  private final ClaimFeedbackApplicationService claimFeedbackApplicationService;

  @Operation(
      summary = "案件跨键检索",
      description =
          "报案号/保单号/发票号/批次号/被保人假名化ID + 干系人遮蔽姓名/手机；" + "租户隔离由 SDK @TenantScope 注入，结果不回 PII 原值。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/search")
  public ApiResponse<List<ClaimSearchHit>> search(
      @RequestParam("keyword") String keyword,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {
    return ApiResponse.success(claimSearchApplicationService.search(keyword, limit));
  }

  @Operation(
      summary = "登记 AI 建议曝光 / 人工裁决",
      description =
          "eventId 幂等；仅 AI_ASSISTED 可裁决；REJECT/MODIFY 强制受控原因码；"
              + "未知字段（金额/决策结论）一律 400；写 append-only 表，不改案件状态与资金。")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/{claimId}/ai/feedback")
  public ApiResponse<AiFeedbackEventResp> record(
      @PathVariable long claimId, @RequestBody AiFeedbackEventReq request) {
    return ApiResponse.success(claimFeedbackApplicationService.record(claimId, toCommand(request)));
  }

  @Operation(summary = "查询该案的 AI 回流事件", description = "最多 50 条，按服务端接收时间倒序。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{claimId}/ai/feedback")
  public ApiResponse<List<AiFeedbackEventResp>> list(@PathVariable long claimId) {
    return ApiResponse.success(claimFeedbackApplicationService.list(claimId));
  }

  @Operation(summary = "租户级 AI 采纳率概览", description = "30 天滚动窗口；曝光为分母，裁决为分子。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai/feedback/summary")
  public ApiResponse<AiFeedbackSummaryResp> summary() {
    return ApiResponse.success(claimFeedbackApplicationService.summary());
  }

  private static AiFeedbackCommand toCommand(AiFeedbackEventReq r) {
    return new AiFeedbackCommand(
        r.getEventId(),
        r.getEventType(),
        r.getTraceId(),
        r.getInvolvement(),
        r.getScene(),
        r.getDisposition(),
        r.getReasonCode(),
        r.getComment(),
        r.getModifiedFields(),
        r.getUnknownFields());
  }
}
