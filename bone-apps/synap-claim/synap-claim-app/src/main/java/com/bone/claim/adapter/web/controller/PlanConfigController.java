package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.CsvExport;
import com.bone.claim.adapter.web.dto.request.ClaimConfigRequests;
import com.bone.claim.application.ClaimConfigApplicationService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 计划配置端点；契约路径由前端 {@code claimConfigApi.ts} 与配置面 E2E 定死。 */
@Tag(name = "智能理赔-计划配置", description = "某保单下计划的查询、新建、编辑与发布下线")
@RestController
@RequestMapping("/api/v1/claim-config/plans")
@RequiredArgsConstructor
public class PlanConfigController {

  private final ClaimConfigApplicationService claimConfigApplicationService;

  @Operation(summary = "某保单下的计划列表")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping({"", "/"})
  public ApiResponse<List<ClaimConfigApplicationService.PlanView>> list(
      @RequestParam String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.listPlans(policyNo));
  }

  @Operation(summary = "新建计划", description = "草稿态；同保单下计划编码唯一，重复返回 409")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "COMMON_VALIDATION_FAILED：必填字段缺失")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "SC_CLAIM_POLICY_DUPLICATED / PLAN_DUPLICATED / COVERAGE_DUPLICATED：业务键重复")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping({"", "/"})
  public ApiResponse<ClaimConfigApplicationService.PlanView> create(
      @Valid @RequestBody ClaimConfigRequests.CreatePlanReq request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        claimConfigApplicationService.createPlan(
            new ClaimConfigApplicationService.CreatePlan(
                request.policyNo(),
                request.planCode(),
                request.planName(),
                request.planLimit(),
                request.waitingPeriod() == null ? 0 : request.waitingPeriod(),
                request.versionNo())));
  }

  @Operation(summary = "编辑计划", description = "不改计划编码与状态；version 用于并发冲突检测")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/{planId}")
  public ApiResponse<ClaimConfigApplicationService.PlanView> update(
      @Parameter(description = "计划ID") @PathVariable long planId,
      @Valid @RequestBody ClaimConfigRequests.UpdatePlanReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        claimConfigApplicationService.updatePlan(
            planId,
            new ClaimConfigApplicationService.UpdatePlan(
                request.planName(),
                request.planLimit(),
                request.waitingPeriod() == null ? 0 : request.waitingPeriod(),
                request.versionNo(),
                request.version())));
  }

  @Operation(summary = "发布计划", description = "DRAFT → ACTIVE；激活后其下生效责任才参与理算")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{planId}/publish")
  public ApiResponse<ClaimConfigApplicationService.PlanView> publish(
      @Parameter(description = "计划ID") @PathVariable long planId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.publishPlan(planId));
  }

  @Operation(summary = "下线计划")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{planId}/retire")
  public ApiResponse<ClaimConfigApplicationService.PlanView> retire(
      @Parameter(description = "计划ID") @PathVariable long planId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.retirePlan(planId));
  }

  @Operation(summary = "导出某保单下的计划列表（CSV）", description = "复用 listPlans 的全量结果；UTF-8 BOM")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<byte[]> export(@RequestParam String policyNo) {
    List<ClaimConfigApplicationService.PlanView> records =
        claimConfigApplicationService.listPlans(policyNo);
    List<String[]> rows =
        records.stream()
            .map(
                r ->
                    new String[] {
                      text(r.planId()),
                      text(r.policyNo()),
                      text(r.planCode()),
                      text(r.planName()),
                      money(r.planLimit()),
                      String.valueOf(r.waitingPeriod()),
                      text(r.versionNo()),
                      text(r.status()),
                      text(r.createdAt()),
                      text(r.updatedAt()),
                      text(r.version())
                    })
            .toList();
    return CsvExport.response(
        "plans",
        new String[] {
          "计划ID", "保单号", "计划编码", "计划名称", "计划额度", "等待期(天)", "计划版本", "状态", "创建时间", "更新时间", "乐观锁版本"
        },
        rows);
  }

  /** null 输出空串，避免导出文件里出现 Java 的 {@code "null"} 字面量。 */
  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /** BigDecimal 直接 toString 可能是 {@code 1E+2}，Excel 会当文本；统一 toPlainString。 */
  private static String money(java.math.BigDecimal value) {
    return value == null ? "0.00" : value.toPlainString();
  }
}
