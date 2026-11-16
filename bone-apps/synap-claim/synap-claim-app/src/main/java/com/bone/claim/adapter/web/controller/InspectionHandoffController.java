package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.FeatureTenantToggleService;
import com.bone.claim.application.InspectionDispositionService;
import com.bone.claim.application.InspectionDispositionService.DispositionView;
import com.bone.claim.application.TaskTransferService;
import com.bone.claim.application.TaskTransferService.TransferView;
import com.bone.claim.application.UserAgentService;
import com.bone.claim.application.UserAgentService.AgentView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.inspection.InspectionConfig;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抽检处置与交接面（批 4）：处置留痕/打回重办、代理区间、任务转交。
 *
 * <p>挂在已登记网关白名单的 {@code /api/v1/review/**} 前缀下。所有操作人 ID 取已验签的 {@link CurrentOperator}， 不接受请求体声明；雪花
 * ID 路径按 {@code String} 接收（前端 JS 精度安全）。
 */
@Tag(name = "智能理赔-抽检处置与交接", description = "处置留痕 / 打回重办 / 代理区间 / 任务转交")
@RestController
@RequestMapping("/api/v1/review/inspection-handoff")
@RequiredArgsConstructor
public class InspectionHandoffController {

  private final InspectionDispositionService inspectionDispositionService;
  private final UserAgentService userAgentService;
  private final TaskTransferService taskTransferService;
  private final FeatureTenantToggleService featureTenantToggleService;
  private final TenantPort tenantPort;

  /** 抽检比例上屏视图：当前生效比例 + 未生效配置说明（配了不生效要可见，不能页面上显示默认值骗人）。 */
  public record SamplingConfigView(
      int samplingRatePercent, java.util.Map<String, String> rejected) {}

  /**
   * 生效代理视图。
   *
   * <p>必须包成对象而非返回裸 String：{@code ApiResponse.success(String)} 有专门重载会把值当 message、data 置 null， 前端取
   * data 拿不到代理人（联调实测踩中）。
   */
  public record AgentEffectiveView(String agentId) {}

  /** 处置请求（决策三值 + 打回原因）。 */
  public record DisposeReq(String decision, String backReason) {}

  /** 代理登记请求。 */
  public record RegisterAgentReq(
      String principalId,
      String agentId,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date validFrom,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date validTo,
      String reason) {}

  /** 转交请求。 */
  public record TransferReq(String toAssigneeId, String reason) {}

  /** 当前租户生效的抽检比例（4.1：页面值与配置读回值同源，不再硬编码 5%）。 */
  @GetMapping("/sampling-config")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  public ApiResponse<SamplingConfigView> samplingConfig() {
    InspectionConfig cfg =
        featureTenantToggleService.inspectionConfig(tenantPort.currentTenantId());
    return ApiResponse.success(new SamplingConfigView(cfg.samplingRatePercent(), cfg.rejected()));
  }

  @PostMapping("/claims/{claimId}/disposition")
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  public ApiResponse<DispositionView> dispose(
      @PathVariable("claimId") String claimId, @RequestBody DisposeReq request) {
    return ApiResponse.success(
        inspectionDispositionService.dispose(
            parseId(claimId, "案件ID"),
            request.decision(),
            request.backReason(),
            CurrentOperator.requireId()));
  }

  @PostMapping("/agents")
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  public ApiResponse<AgentView> registerAgent(@RequestBody RegisterAgentReq request) {
    return ApiResponse.success(
        userAgentService.register(
            request.principalId(),
            request.agentId(),
            request.validFrom(),
            request.validTo(),
            request.reason()));
  }

  @PostMapping("/agents/{principalId}/deactivate")
  @PreAuthorize("hasAuthority('claim:inspection:write')")
  public ApiResponse<Void> deactivateAgent(@PathVariable("principalId") String principalId) {
    userAgentService.deactivate(principalId);
    return ApiResponse.success(null);
  }

  @GetMapping("/agents/effective")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  public ApiResponse<AgentEffectiveView> effectiveAgent(
      @RequestParam("principalId") String principalId,
      @RequestParam(value = "at", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Date at) {
    String agentId = userAgentService.effectiveAgent(principalId, at == null ? new Date() : at);
    return ApiResponse.success(new AgentEffectiveView(agentId));
  }

  @PostMapping("/tasks/{taskId}/transfer")
  @PreAuthorize("hasAuthority('claim:review:write')")
  public ApiResponse<TransferView> transferTask(
      @PathVariable("taskId") String taskId, @RequestBody TransferReq request) {
    return ApiResponse.success(
        taskTransferService.transfer(
            parseId(taskId, "任务ID"),
            request.toAssigneeId(),
            request.reason(),
            CurrentOperator.requireId()));
  }

  /** 雪花 ID 一律按 String 接收，非法值给 404 语义而非 500/400。 */
  private static long parseId(String raw, String label) {
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      throw com.bone.claim.common.ClaimErrors.of(
          com.bone.claim.common.ClaimErrorCodes.SC_CLAIM_TASK_NOT_FOUND, label + "=" + raw);
    }
  }
}
