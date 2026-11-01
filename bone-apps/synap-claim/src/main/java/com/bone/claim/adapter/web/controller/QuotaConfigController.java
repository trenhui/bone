package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.CsvExport;
import com.bone.claim.adapter.web.dto.request.ClaimConfigRequests;
import com.bone.claim.application.QuotaConfigApplicationService;
import com.bone.claim.application.QuotaConfigApplicationService.QuotaAccountView;
import com.bone.claim.application.QuotaConfigApplicationService.QuotaChangeView;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.security.auth.CurrentOperator;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 额度配置接口（运营面）：开户 / 调额 / 减人 / 恢复 / 保全记录。
 *
 * <p><b>与 {@code GET /api/v1/claims/{claimId}/quota} 的分工</b>：后者是<b>理赔作业面</b>的只读视图
 * （"这件赔案占了多少额度"），本类是<b>运营配置面</b>的读写（"这个被保人有多少额度、保额改了多少"）。
 * 两者读的是同一张表，但口径与用途不同，混在一个端点里会让作业页多出改额度的能力（越权）。
 *
 * <p><b>操作人</b>：取自已验签 token（{@link CurrentOperator#requireId()}），不接受客户端声明—— 额度变更是资金相关动作，留痕必须可信。
 */
@Tag(name = "智能理赔-额度配置", description = "个人额度开户、调额、减人与保全记录")
@RestController
@RequestMapping("/api/v1/claim-config/quota-accounts")
@RequiredArgsConstructor
public class QuotaConfigController {

  private final QuotaConfigApplicationService quotaConfigApplicationService;

  @Operation(summary = "额度账户列表", description = "分页查该保单下的额度账户；支持被保人假名化ID关键字、额度维度、减人状态筛选")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping
  public ApiResponse<PageResult<QuotaAccountView>> accounts(
      @Parameter(description = "保单号") @ModelAttribute("policyNo") String policyNo,
      @Parameter(description = "被保人假名化ID关键字（模糊匹配）") String keyword,
      @Parameter(description = "额度维度：PERSONAL/LIABILITY_CAP/LIABILITY_PERSONAL") String quotaType,
      @Parameter(description = "减人状态：true=已减人 false=正常，缺省不过滤") Boolean removed,
      @Parameter(description = "页码（缺省 1）") Integer page,
      @Parameter(description = "每页条数（缺省 20，上限 100）") Integer size) {
    int p = page == null || page < 1 ? 1 : page;
    int s = size == null || size < 1 ? 20 : Math.min(size, 100);
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.listByPolicy(policyNo, keyword, quotaType, removed, p, s));
  }

  @Operation(summary = "导出额度账户（CSV）", description = "按与列表一致的筛选条件导出全量（不分页）；UTF-8 BOM，Excel 直开不乱码")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<byte[]> export(
      @Parameter(description = "保单号") @ModelAttribute("policyNo") String policyNo,
      @Parameter(description = "被保人假名化ID关键字（模糊匹配）") String keyword,
      @Parameter(description = "额度维度") String quotaType,
      @Parameter(description = "减人状态：true=已减人 false=正常，缺省不过滤") Boolean removed) {
    List<QuotaAccountView> records =
        quotaConfigApplicationService.exportByPolicy(policyNo, keyword, quotaType, removed);
    List<String[]> rows =
        records.stream()
            .map(
                r ->
                    new String[] {
                      r.quotaAccountId(),
                      r.insuredIdHash(),
                      r.policyNo(),
                      r.planId(),
                      String.valueOf(r.quotaYear()),
                      r.quotaType(),
                      r.liabilityUuid(),
                      money(r.initial()),
                      money(r.claimed()),
                      money(r.frozen()),
                      money(r.available()),
                      money(r.deductibleUsed()),
                      money(r.capUsed()),
                      r.removed() ? "REMOVED" : "ACTIVE",
                      String.valueOf(r.updatedAt())
                    })
            .toList();
    return CsvExport.response(
        "quota-accounts",
        new String[] {
          "额度账户ID", "被保人假名ID", "保单号", "计划ID", "额度年度", "额度维度", "责任UUID", "初始额度", "已扣减", "已冻结",
          "可用额度", "年免赔已用", "封顶已用", "状态", "更新时间"
        },
        rows);
  }

  /** 金额统一两位小数（BigDecimal 直接 toString 可能是 {@code 1E+2} 之类科学计数，Excel 会当文本）。 */
  private static String money(java.math.BigDecimal value) {
    return value == null ? "0.00" : value.toPlainString();
  }

  @Operation(summary = "某被保人的额度账户", description = "按保单号 + 被保人假名化ID查")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping("/by-insured")
  public ApiResponse<List<QuotaAccountView>> accountsByInsured(
      @Parameter(description = "保单号") @ModelAttribute("policyNo") String policyNo,
      @Parameter(description = "被保人假名化ID") @ModelAttribute("insuredIdHash") String insuredIdHash) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.listByPolicyAndInsured(policyNo, insuredIdHash));
  }

  /** 公账汇总：责任额度池（LIABILITY_CAP）按责任 UUID 聚合。 */
  @Operation(summary = "公账额度池汇总", description = "按责任 UUID 聚合责任额度账户：账户数、减人数、初始/已扣减/冻结/可用合计")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping("/public-summary")
  public ApiResponse<List<QuotaConfigApplicationService.PublicQuotaSummaryView>> publicSummary(
      @ModelAttribute("policyNo") String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), quotaConfigApplicationService.summarizePublic(policyNo));
  }

  @Operation(summary = "额度保全记录", description = "某额度账户的历次变更（谁、何时、从多少改到多少）")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping("/{quotaAccountId}/changes")
  public ApiResponse<List<QuotaChangeView>> changes(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), quotaConfigApplicationService.listChanges(quotaAccountId));
  }

  @Operation(summary = "开户（初始化个人额度）", description = "同键重复调用返回既有账户（幂等）")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<QuotaAccountView> open(
      @Valid @RequestBody ClaimConfigRequests.OpenQuotaReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        quotaConfigApplicationService.openAccount(
            new QuotaConfigApplicationService.OpenQuota(
                request.insuredIdHash(),
                request.policyNo(),
                request.planId(),
                request.quotaYear() == null ? 0 : request.quotaYear(),
                request.quotaType() == null || request.quotaType().isBlank()
                    ? "PERSONAL"
                    : request.quotaType(),
                request.liabilityUuid(),
                request.quotaInitial(),
                operatorId,
                operatorId,
                request.remark())));
  }

  @Operation(summary = "调额", description = "改初始额度；下调不得低于已占用额度（已赔+已冻结），否则守恒被破坏")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @PostMapping("/{quotaAccountId}/adjust")
  public ApiResponse<QuotaAccountView> adjust(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId,
      @Valid @RequestBody ClaimConfigRequests.AdjustQuotaReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.adjustInitial(
            new QuotaConfigApplicationService.AdjustQuota(
                quotaAccountId,
                request.newInitial(),
                operatorId,
                operatorId,
                request.remark(),
                request.version())));
  }

  @Operation(summary = "减人", description = "该被保人不再参与额度累计（不删除账户与历史）")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @PostMapping("/{quotaAccountId}/remove")
  public ApiResponse<QuotaAccountView> remove(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId,
      @Valid @RequestBody ClaimConfigRequests.InsuredStateReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.removeInsured(
            new QuotaConfigApplicationService.AdjustQuota(
                quotaAccountId,
                null,
                operatorId,
                operatorId,
                request.remark(),
                request.version())));
  }

  @Operation(summary = "恢复", description = "撤销减人，重新参与额度累计")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @PostMapping("/{quotaAccountId}/restore")
  public ApiResponse<QuotaAccountView> restore(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId,
      @Valid @RequestBody ClaimConfigRequests.InsuredStateReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.restoreInsured(
            new QuotaConfigApplicationService.AdjustQuota(
                quotaAccountId,
                null,
                operatorId,
                operatorId,
                request.remark(),
                request.version())));
  }
}
