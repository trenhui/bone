package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.QualityApplicationService;
import com.bone.masterdata.application.query.dto.QualityResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 质量结果控制器：按记录/实体返回质量检查结果。
 *
 * <p>返回粒度由入参决定（见 {@code QualityResultDTO#level}）：
 *
 * <ul>
 *   <li>传 {@code recordId} → 逐条明细（DETAIL），数据来自 {@code mdm_qcheck_detail}，recordId 真正参与筛选；
 *   <li>不传 {@code recordId} → 检查任务汇总（SUMMARY），只给整体结论。
 * </ul>
 *
 * <p>此前 recordId 只被原样回填到 DTO 而不参与筛选，"按记录查质量结果"语义不成立；根因是 {@code mdm_qcheck_detail} 只有 DDL、没有实体与写入链路。
 */
@Tag(name = "质量结果", description = "质量检查结果查询接口")
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/quality-results")
@RequiredArgsConstructor
public class QualityResultController {

  private final QualityApplicationService qualityService;

  @Operation(
      summary = "按记录/实体查询质量结果",
      description =
          "传 recordId 返回逐规则×记录的明细（level=DETAIL）；不传则返回检查任务汇总（level=SUMMARY）。两者同时传时按实体限定检查范围。")
  @GetMapping
  public ApiResponse<List<QualityResultDTO>> list(
      @RequestParam(value = "recordId", required = false) Long recordId,
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId) {
    return ApiResponse.success(qualityService.listQualityResults(recordId, masterDataEntityId));
  }
}
