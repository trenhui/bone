package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiCalibrationReportApplicationService;
import com.bone.claim.application.AiCalibrationReportApplicationService.AiCalibrationReport;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校准报表只读端点（G4-1 数据飞轮下游）。
 *
 * <p>与 {@link AiCapabilityReadinessController} 同一治理形态：无请求体、无路径参数、不调用能力
 * 业务方法、不改开关——查一次不会触发任何阈值变更。verdict 为受控枚举
 * （NO_EVIDENCE/UNTRUSTED_EVIDENCE/INSUFFICIENT_SAMPLES/SLICE_INCOMPLETE/NO_SAFE_THRESHOLD/
 * THRESHOLD_PROPOSED），样本不足时 ECE/Brier 为 null（不是 0），绝不编造数值。
 */
@Tag(name = "智能理赔-AI治理", description = "置信度校准报表（只读，供人工阈值决策参考）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiCalibrationReportController {

  private final AiCalibrationReportApplicationService aiCalibrationReportService;

  @Operation(
      summary = "AI 置信度校准报表（只读）",
      description =
          "基于独立盲标成对样本计算 ECE/Brier 与阈值建议。仅为建议，不自动生效；"
              + "样本不足时 verdict=INSUFFICIENT_SAMPLES 且不产数值性结论。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-calibration/report")
  public ApiResponse<AiCalibrationReport> report(
      @RequestParam(required = false) Integer minSamples,
      @RequestParam(required = false) Integer minSamplesPerSlice) {
    return ApiResponse.success(aiCalibrationReportService.report(minSamples, minSamplesPerSlice));
  }
}
