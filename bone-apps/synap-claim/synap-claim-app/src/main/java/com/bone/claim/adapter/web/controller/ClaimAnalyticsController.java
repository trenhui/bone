package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.ClaimAnalyticsApplicationService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 只读运营分析出口（G0 度量先行）。 */
@Tag(name = "理赔运营分析", description = "决策终态分布、AI 降级率、影子自动化率")
@RestController
@RequestMapping("/api/v1/claims/analytics")
@RequiredArgsConstructor
public class ClaimAnalyticsController {

  private final ClaimAnalyticsApplicationService claimAnalyticsApplicationService;

  @Operation(
      summary = "决策运营指标汇总",
      description =
          "按决策收敛时间窗聚合：终态分布、FALLBACK 降级数、平均决策耗时、影子自动化率。"
              + "影子自动化率不等于实际出金率（自动放款开关默认关闭、E1/E2 未交付）。时间窗最多 31 天。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/summary")
  public ApiResponse<ClaimAnalyticsApplicationService.AnalyticsSummary> summary(
      @Parameter(description = "窗口起点（含），ISO-8601")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @Parameter(description = "窗口终点（不含），ISO-8601")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    return ApiResponse.success(claimAnalyticsApplicationService.summary(from, to));
  }

  @Operation(
      summary = "当前租户运营指标汇总（租户隔离）",
      description =
          "按当前认证租户过滤后聚合：终态分布、FALLBACK 降级数、影子自动化率、平均决策耗时。"
              + "租户身份取自认证主体（TenantPort），不接受前端传入 tenantId，无法越权查他人租户。"
              + "只含可按租户拆分的可复算指标；scopeNote 必随结果返回，影子自动化率不等于实际出金率。时间窗最多 31 天。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/by-tenant")
  public ApiResponse<ClaimAnalyticsApplicationService.TenantAnalyticsSummary> byTenant(
      @Parameter(description = "窗口起点（含），ISO-8601")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @Parameter(description = "窗口终点（不含），ISO-8601")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    return ApiResponse.success(claimAnalyticsApplicationService.summaryByTenant(from, to));
  }
}
