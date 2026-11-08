package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.CsvExport;
import com.bone.claim.adapter.web.dto.request.ClaimConfigRequests;
import com.bone.claim.adapter.web.dto.request.PolicyPageQry;
import com.bone.claim.application.ClaimConfigApplicationService;
import com.bone.claim.application.PolicyDetailApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保单主数据配置端点。
 *
 * <p><b>为什么单独一个 Controller</b>：{@code ClaimConfigController} 的类级路径是 {@code
 * /api/v1/claim-config/dicts}，它是前端字典 codegen 的契约，不能改前缀。按「一资源一 Controller」新增， 与 liabilities /
 * coverage-overrides / quota-accounts 同族。
 *
 * <p><b>契约来源</b>：前端 {@code claimConfigApi.ts} 与 {@code scripts/e2e/claim-config-e2e.mjs}
 * 已定死路径与状态码（创建 201、缺字段 400、重复/版本冲突 409），本类只做请求体到应用命令的转换， 事务、租户、领域事件全部留在 {@link
 * ClaimConfigApplicationService}。
 */
@Tag(name = "智能理赔-保单配置", description = "保单主数据的查询、新建、编辑与启用停用")
@RestController
@RequestMapping("/api/v1/claim-config/policies")
@RequiredArgsConstructor
public class PolicyConfigController {

  private final ClaimConfigApplicationService claimConfigApplicationService;
  private final PolicyDetailApplicationService policyDetailApplicationService;

  @Operation(summary = "保单列表（分页）", description = "keyword 匹配保单号/保司保单号/名称；configStatus 过滤规则开关")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping({"", "/"})
  public ApiResponse<PageResult<ClaimConfigApplicationService.PolicyView>> list(
      @Valid @ModelAttribute PolicyPageQry qry) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        claimConfigApplicationService.listPolicies(
            qry.getKeyword(), qry.getConfigStatus(), qry.getPage(), qry.getSize()));
  }

  @Operation(summary = "保单详情")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/{policyNo}")
  public ApiResponse<ClaimConfigApplicationService.PolicyView> get(
      @Parameter(description = "保单号") @PathVariable String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.getPolicy(policyNo));
  }

  @Operation(summary = "保单聚合详情", description = "概要 + 主体映射健康度 + 计划→责任树")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/{policyNo}/detail")
  public ApiResponse<PolicyDetailApplicationService.PolicyDetail> detail(
      @Parameter(description = "保单号") @PathVariable String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), policyDetailApplicationService.detail(policyNo));
  }

  @Operation(summary = "新建保单", description = "草稿态（INACTIVE），需另行启用")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "COMMON_VALIDATION_FAILED：必填字段缺失")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "SC_CLAIM_POLICY_DUPLICATED / PLAN_DUPLICATED / COVERAGE_DUPLICATED：业务键重复")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping({"", "/"})
  public ApiResponse<ClaimConfigApplicationService.PolicyView> create(
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
                request.insuredSnapshot(),
                request.docClaimApply(),
                request.docClaimNotice(),
                request.insurerPolicyNo())));
  }

  @Operation(summary = "编辑保单", description = "不改保单号与规则开关；insurerPolicyNo 不传=沿用原值，空串=显式清空")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/{policyNo}")
  public ApiResponse<ClaimConfigApplicationService.PolicyView> update(
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
                request.docClaimApply(),
                request.docClaimNotice(),
                request.insurerPolicyNo(),
                request.version())));
  }

  @Operation(summary = "启用保单责任", description = "INACTIVE/PAUSE → ACTIVE")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{policyNo}/activate")
  public ApiResponse<ClaimConfigApplicationService.PolicyView> activate(
      @Parameter(description = "保单号") @PathVariable String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.activatePolicy(policyNo));
  }

  @Operation(summary = "停用保单责任", description = "ACTIVE → PAUSE")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{policyNo}/pause")
  public ApiResponse<ClaimConfigApplicationService.PolicyView> pause(
      @Parameter(description = "保单号") @PathVariable String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.pausePolicy(policyNo));
  }

  @Operation(summary = "导出保单列表（CSV）", description = "与列表同筛选条件的全量导出；UTF-8 BOM，Excel 直开不乱码")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String configStatus) {
    List<ClaimConfigApplicationService.PolicyView> records =
        claimConfigApplicationService.exportPolicies(keyword, configStatus);
    List<String[]> rows =
        records.stream()
            .map(
                r ->
                    new String[] {
                      text(r.policyNo()), text(r.policyName()), text(r.insurerName()),
                      text(r.branchName()), text(r.insureName()), text(r.bizIdentityCode()),
                      text(r.productType()), text(r.policyType()), text(r.effectiveStart()),
                      text(r.effectiveEnd()), String.valueOf(r.waitingPeriod()),
                          text(r.policyVersion()),
                      text(r.insuredSnapshot()), text(r.insurerPolicyNo()), text(r.configStatus()),
                      text(r.docClaimApply()), text(r.docClaimNotice()), String.valueOf(r.stale()),
                      text(r.createdAt()), text(r.updatedAt()), text(r.version())
                    })
            .toList();
    return CsvExport.response(
        "policies",
        new String[] {
          "保单号", "保单名称", "保险公司", "保险分公司", "投保公司", "业务主体码", "产品类型", "保单类型", "生效起", "生效止", "等待期(天)",
          "保单版本", "被保人清单", "保司侧保单号", "配置状态", "理赔申请书开关", "理赔通知单开关", "是否陈旧", "创建时间", "更新时间", "乐观锁版本"
        },
        rows);
  }

  /** null 输出空串，避免导出文件里出现 Java 的 {@code "null"} 字面量。 */
  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
