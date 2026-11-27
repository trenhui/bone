package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.MrmGovernanceApplicationService;
import com.bone.claim.application.MrmGovernanceApplicationService.BiasMonitoringReport;
import com.bone.claim.application.MrmGovernanceApplicationService.GovernanceReport;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MRM 模型风险治理只读端点（NIST AI RMF GOVERN / SR 11-7 三道防线的运行期可查面）。
 *
 * <p><b>只读、无副作用</b>：无请求体、不调用任何能力业务方法、不改开关/配置、不落库、不碰资金路径与监管签署状态。查一次不会触发任何阈值变更。
 *
 * <p><b>fail-closed 边界</b>：空模型卡清单返回 {@code NO_MODEL_CARDS}（不把 {@code inventory().clean()} 读成治理健康）；无
 * PSI 观测返回 {@code NO_DATA}；金标/盲标样本为 0 返回 {@code NO_EVIDENCE}；分组样本量低于阈值返回 {@code
 * INSUFFICIENT_SAMPLES} 且偏差数值/结论为 null； 真实样本取不到的维度（如机构等级）返回 {@code DIMENSION_UNAVAILABLE}
 * 并说明需要样本携带对应字段。HTTP 仍为 200，由信封内的受控状态表达不可用。
 *
 * <p>对外最小样本量不可被查询参数下调到治理下限以下（默认 30），并按上限保护（500）。
 */
@Tag(name = "智能理赔-AI治理", description = "MRM 治理状态与偏差监测（只读，fail-closed）")
@RestController
@RequestMapping("/api/v1/claim-ops/mrm")
@RequiredArgsConstructor
public class MrmGovernanceController {

  private final MrmGovernanceApplicationService service;

  @Operation(
      summary = "MRM 治理状态（只读）",
      description =
          "模型卡清单 + inventory + PSI 漂移四态 + champion-challenger 证据状态。"
              + "空清单 NO_MODEL_CARDS / 无观测 NO_DATA / 无样本 NO_EVIDENCE，不给优劣结论。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/governance")
  public ApiResponse<GovernanceReport> governance(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return ApiResponse.success(service.governance(asOf == null ? LocalDate.now() : asOf));
  }

  @Operation(
      summary = "按维度偏差监测（只读）",
      description =
          "按真实样本维度分组（CHANNEL / DECISION_RESULT），逐组返回样本量。"
              + "样本量低于阈值返回 INSUFFICIENT_SAMPLES 且不输出偏差数值或结论；无真实字段的维度返回 DIMENSION_UNAVAILABLE。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/bias-monitoring")
  public ApiResponse<BiasMonitoringReport> biasMonitoring(
      @RequestParam(required = false) String dimension,
      @RequestParam(required = false) Integer minSamplesPerSlice) {
    return ApiResponse.success(
        service.biasMonitoring(dimension, guardedMinSamples(minSamplesPerSlice)));
  }

  /** 对外入口的治理下限：只允许上调到 30 及以上，且不超过 500 上限保护。 */
  static int guardedMinSamples(Integer requested) {
    int effective = MrmGovernanceApplicationService.effectiveMinSamplesPerSlice(requested);
    return Math.max(MrmGovernanceApplicationService.DEFAULT_MIN_SAMPLES_PER_SLICE, effective);
  }
}
