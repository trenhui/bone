package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.CsvExport;
import com.bone.claim.adapter.web.dto.request.ClaimConfigRequests;
import com.bone.claim.application.ClaimConfigApplicationService;
import com.bone.claim.application.ClaimConfigApplicationService.CoverageView;
import com.bone.claim.application.ClaimConfigApplicationService.PlanView;
import com.bone.claim.application.ClaimConfigApplicationService.PolicyView;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保单 / 计划 / 险种配置接口（运营面）。
 *
 * <p><b>命名空间</b>：{@code /api/v1/claim-config/**}，与理赔主干 {@code /api/v1/claims/**} 平级但独立。 不挂在 {@code
 * /claims} 下是因为保单/计划是<b>配置资源</b>而非赔案子资源——挂在下面会让 "取某保单"与"取某赔案"的路径语义混在一处，且前端要在同一个 axios 前缀下表达两套资源。
 *
 * <p><b>权限码</b>：读 {@code claim:policy:read} / 写 {@code claim:policy:write}。 与作业面（{@code
 * claim:claims:read} / {@code claim:claims:write}）分开， 因为两者的风险面不同：作业面是"处理具体赔案"，配置面是"改动影响所有未来赔案的规则"——
 * 后者需要更严格的授权（参考实现里"启用保单责任"这类开关就是运营专属动作）。
 *
 * <p><b>操作人身份</b>：写操作的操作人取自已验签 token（{@code CurrentOperator}）， 不接受客户端在请求体里声明是谁——否则审计留痕可被伪造。
 */
@Tag(name = "智能理赔-保单配置", description = "保单、计划、险种的配置与发布")
@RestController
@RequestMapping("/api/v1/claim-config")
@RequiredArgsConstructor
public class ClaimConfigController {

  private final ClaimConfigApplicationService claimConfigApplicationService;

  // ===================== 保单 =====================

  @Operation(summary = "保单列表", description = "分页查保单；支持保单号/保单名称模糊匹配与规则开关过滤")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/policies")
  public ApiResponse<PageResult<PolicyView>> policies(
      @Parameter(description = "保单号/名称关键字") String keyword,
      @Parameter(description = "规则开关：INACTIVE/ACTIVE/PAUSE") String configStatus,
      @Parameter(description = "页码（缺省 1）") Integer page,
      @Parameter(description = "每页条数（缺省 20）") Integer size) {
    int p = page == null || page < 1 ? 1 : page;
    int s = size == null || size < 1 ? 20 : Math.min(size, 100);
    return ApiResponse.success(
        HttpStatus.OK.value(),
        claimConfigApplicationService.listPolicies(keyword, configStatus, p, s));
  }

  @Operation(summary = "保单详情")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/policies/{policyNo}")
  public ApiResponse<PolicyView> policy(
      @Parameter(description = "保单号") @PathVariable String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.getPolicy(policyNo));
  }

  @Operation(
      summary = "导出保单（CSV）",
      description = "按与列表一致的关键字/规则开关条件导出全量（不分页）；UTF-8 BOM，Excel 直开不乱码")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping(value = "/policies/export", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<byte[]> exportPolicies(
      @Parameter(description = "保单号/名称关键字") String keyword,
      @Parameter(description = "规则开关：INACTIVE/ACTIVE/PAUSE") String configStatus) {
    List<PolicyView> records = claimConfigApplicationService.exportPolicies(keyword, configStatus);
    List<String[]> rows =
        records.stream()
            .map(
                r ->
                    new String[] {
                      r.policyNo(),
                      r.policyName(),
                      r.insurerName(),
                      r.branchName(),
                      r.insureName(),
                      r.bizIdentityCode(),
                      r.productType(),
                      r.policyType(),
                      String.valueOf(r.effectiveStart()),
                      String.valueOf(r.effectiveEnd()),
                      String.valueOf(r.waitingPeriod()),
                      r.policyVersion(),
                      r.configStatus(),
                      String.valueOf(r.updatedAt())
                    })
            .toList();
    return CsvExport.response(
        "policies",
        new String[] {
          "保单号", "保单名称", "保险公司", "保险分公司", "投保公司", "业务主体码", "产品类型", "保单类型", "生效起", "生效止", "等待期(天)",
          "保单版本", "规则开关", "更新时间"
        },
        rows);
  }

  @Operation(summary = "新建保单", description = "新建后规则开关为「未启用」，需另行启用才接受报案")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/policies")
  public ApiResponse<PolicyView> createPolicy(
      @Valid @RequestBody ClaimConfigRequests.CreatePolicyReq request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        claimConfigApplicationService.createPolicy(
            new ClaimConfigApplicationService.CreatePolicy(
                request.policyNo(),
                request.policyName(),
                request.insurerName(),
                request.branchName(),
                request.insureName(),
                request.bizIdentityCode(),
                request.productType(),
                request.policyType(),
                request.effectiveStart(),
                request.effectiveEnd(),
                request.waitingPeriod() == null ? 0 : request.waitingPeriod(),
                request.policyVersion(),
                request.insuredSnapshot())));
  }

  @Operation(summary = "启用保单责任", description = "规则开关置为启用中；此后保单方可接受新报案")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/policies/{policyNo}/activate")
  public ApiResponse<PolicyView> activatePolicy(
      @Parameter(description = "保单号") @PathVariable String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.activatePolicy(policyNo));
  }

  @Operation(summary = "停用保单责任", description = "规则开关置为停用中；已受理赔案不受影响")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/policies/{policyNo}/pause")
  public ApiResponse<PolicyView> pausePolicy(
      @Parameter(description = "保单号") @PathVariable String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.pausePolicy(policyNo));
  }

  @Operation(summary = "编辑保单", description = "修正可改字段（名称/承保期间/等待期/被保人清单等）；启用中的保单不允许改承保期间，需先停用")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/policies/{policyNo}")
  public ApiResponse<PolicyView> updatePolicy(
      @Parameter(description = "保单号") @PathVariable String policyNo,
      @Valid @RequestBody ClaimConfigRequests.UpdatePolicyReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        claimConfigApplicationService.updatePolicy(
            policyNo,
            new ClaimConfigApplicationService.UpdatePolicy(
                request.policyName(),
                request.insurerName(),
                request.branchName(),
                request.insureName(),
                request.bizIdentityCode(),
                request.productType(),
                request.policyType(),
                request.effectiveStart(),
                request.effectiveEnd(),
                request.waitingPeriod() == null ? 0 : request.waitingPeriod(),
                request.policyVersion(),
                request.insuredSnapshot(),
                request.version())));
  }

  // ===================== 计划 =====================

  @Operation(summary = "计划列表", description = "某保单下的全部计划")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/plans")
  public ApiResponse<List<PlanView>> plans(
      @Parameter(description = "保单号") @ModelAttribute("policyNo") String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.listPlans(policyNo));
  }

  @Operation(summary = "导出计划（CSV）", description = "导出某保单下的全部计划（不分页）；UTF-8 BOM，Excel 直开不乱码")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping(value = "/plans/export", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<byte[]> exportPlans(
      @Parameter(description = "保单号") @ModelAttribute("policyNo") String policyNo) {
    List<PlanView> records = claimConfigApplicationService.listPlans(policyNo);
    List<String[]> rows =
        records.stream()
            .map(
                r ->
                    new String[] {
                      r.planId(),
                      r.policyNo(),
                      r.planCode(),
                      r.planName(),
                      money(r.planLimit()),
                      String.valueOf(r.waitingPeriod()),
                      r.versionNo(),
                      r.status(),
                      String.valueOf(r.updatedAt())
                    })
            .toList();
    return CsvExport.response(
        "plans",
        new String[] {"计划ID", "保单号", "计划编码", "计划名称", "计划额度", "等待期(天)", "计划版本", "状态", "更新时间"},
        rows);
  }

  /** 金额统一普通计数法（BigDecimal 直接 toString 可能是 {@code 1E+2} 科学计数，Excel 当文本）。 */
  private static String money(java.math.BigDecimal value) {
    return value == null ? "0.00" : value.toPlainString();
  }

  @Operation(summary = "新建计划")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/plans")
  public ApiResponse<PlanView> createPlan(
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

  @Operation(summary = "发布计划", description = "草稿 → 已发布；其下生效责任自此参与理算")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/plans/{planId}/publish")
  public ApiResponse<PlanView> publishPlan(
      @Parameter(description = "计划ID") @PathVariable long planId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.publishPlan(planId));
  }

  @Operation(summary = "下线计划")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/plans/{planId}/retire")
  public ApiResponse<PlanView> retirePlan(
      @Parameter(description = "计划ID") @PathVariable long planId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.retirePlan(planId));
  }

  @Operation(summary = "编辑计划", description = "修正可改字段（名称/额度/等待期/版本）；不改计划编码与状态")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/plans/{planId}")
  public ApiResponse<PlanView> updatePlan(
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

  // ===================== 险种 =====================

  @Operation(summary = "险种列表", description = "某计划下的全部险种")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/coverages")
  public ApiResponse<List<CoverageView>> coverages(
      @Parameter(description = "计划ID") @ModelAttribute("planId") Long planId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.listCoverages(planId));
  }

  @Operation(summary = "新建险种")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/coverages")
  public ApiResponse<CoverageView> createCoverage(
      @Valid @RequestBody ClaimConfigRequests.CreateCoverageReq request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        claimConfigApplicationService.createCoverage(
            new ClaimConfigApplicationService.CreateCoverage(
                request.planId(),
                request.coverageCode(),
                request.coverageName(),
                request.coverageLimit(),
                request.liabilityType())));
  }

  @Operation(summary = "编辑险种", description = "修正可改字段（名称/额度/责任形式）；不改险种编码与状态")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/coverages/{coverageId}")
  public ApiResponse<CoverageView> updateCoverage(
      @Parameter(description = "险种ID") @PathVariable long coverageId,
      @Valid @RequestBody ClaimConfigRequests.UpdateCoverageReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        claimConfigApplicationService.updateCoverage(
            coverageId,
            new ClaimConfigApplicationService.UpdateCoverage(
                request.coverageName(),
                request.coverageLimit(),
                request.liabilityType(),
                request.version())));
  }

  @Operation(summary = "发布险种", description = "草稿 → 已发布；与计划同语义的二级发布")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/coverages/{coverageId}/publish")
  public ApiResponse<CoverageView> publishCoverage(
      @Parameter(description = "险种ID") @PathVariable long coverageId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.publishCoverage(coverageId));
  }

  @Operation(summary = "下线险种")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/coverages/{coverageId}/retire")
  public ApiResponse<CoverageView> retireCoverage(
      @Parameter(description = "险种ID") @PathVariable long coverageId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.retireCoverage(coverageId));
  }
}
