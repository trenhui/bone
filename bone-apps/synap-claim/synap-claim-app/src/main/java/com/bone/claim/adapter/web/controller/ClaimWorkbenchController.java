package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.request.AiFeedbackEventReq;
import com.bone.claim.adapter.web.dto.response.AiConfigGovernanceResp;
import com.bone.claim.adapter.web.dto.response.AiFeedbackEventResp;
import com.bone.claim.adapter.web.dto.response.AiFeedbackSummaryResp;
import com.bone.claim.application.AiConfigGovernanceService;
import com.bone.claim.application.AiFeedbackCommand;
import com.bone.claim.application.ClaimFeedbackApplicationService;
import com.bone.claim.application.ClaimSearchApplicationService;
import com.bone.claim.application.ClaimTaskApplicationService;
import com.bone.claim.application.ClaimTaskApplicationService.MyTaskRow;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.ClaimSearchHit;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
  private final ClaimTaskApplicationService claimTaskApplicationService;
  private final AiConfigGovernanceService aiConfigGovernanceService;
  private final TenantPort tenantPort;

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

  @Operation(
      summary = "AI 配置治理快照",
      description =
          "当前生效分诊阈值 + 写 API 开关态；治理页据此渲染只读或可编辑（双闸门的服务端数据源）。" + "开关未配置时 writeEnabled=false。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai/config/governance")
  public ApiResponse<AiConfigGovernanceResp> configGovernance() {
    long tenantId = tenantPort.currentTenantId();
    return ApiResponse.success(
        new AiConfigGovernanceResp(
            aiConfigGovernanceService.currentLimits(tenantId),
            aiConfigGovernanceService.isWriteEnabled(tenantId)));
  }

  @Operation(
      summary = "受控写入 AI 分诊阈值",
      description = "逐字段比较当前生效值，只允许相等或更严格；放宽 / 未知键 / 写开关关闭一律拒绝。" + "审计写 remark + 日志，零新增 DDL。")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PutMapping("/ai/config/triage-limits")
  public ApiResponse<Void> updateTriageLimits(@RequestBody Map<String, Object> requested) {
    long tenantId = tenantPort.currentTenantId();
    aiConfigGovernanceService.updateTriageLimits(tenantId, CurrentOperator.requireId(), requested);
    return ApiResponse.success(null);
  }

  @Operation(
      summary = "我的待办（任务维分页）",
      description =
          "按 assignee=当前登录人 + 可选 status 过滤；返回字段最小化（不含租户/版本/规则ID）；"
              + "total 与列表同源 COUNT(*)，前端只传 page/size。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/tasks/my")
  public ApiResponse<PageResult<MyTaskRow>> myTasks(
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.success(
        claimTaskApplicationService.myTasks(CurrentOperator.requireId(), status, page, size));
  }

  @Operation(
      summary = "自领任务",
      description =
          "乐观锁 + 生成列唯一键保证并发恰 1 成功；冲突返回 409 SC_CLAIM_TASK_CONFLICT（不重试、不静默改派）。"
              + "领取人取认证主体，请求体不接受客户端声明的 operatorId。")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/tasks/{taskId}/claim")
  public ApiResponse<MyTaskRow> claimTask(@PathVariable("taskId") String taskId) {
    return ApiResponse.success(
        claimTaskApplicationService.claim(parseTaskId(taskId), CurrentOperator.requireId()));
  }

  @Operation(summary = "某案任务列表", description = "含终态，按创建时间正序；案件 360 视图用。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{claimId}/tasks")
  public ApiResponse<java.util.List<MyTaskRow>> claimTasks(
      @PathVariable("claimId") String claimId) {
    return ApiResponse.success(claimTaskApplicationService.tasksOfClaim(parseTaskId(claimId)));
  }

  /**
   * 路径里的雪花 ID 一律按 String 接收再转 long。
   *
   * <p><b>为什么不直接 {@code @PathVariable long}</b>：前端 JS 的 number 只有 53 位精度，63 位雪花 ID 必须
   * 以字符串传输；这里按字符串接再显式解析，非法值给 404 语义（「任务不存在」）而不是 500 或 400 细节泄露。
   */
  private static long parseTaskId(String raw) {
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_NOT_FOUND, raw);
    }
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
