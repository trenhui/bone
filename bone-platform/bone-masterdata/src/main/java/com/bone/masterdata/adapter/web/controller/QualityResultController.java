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

/** 质量结果控制器：按记录/实体返回质量检查结果（非实时计算，基于现有质量数据映射）。 */
@Tag(name = "质量结果", description = "质量检查结果查询接口")
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/quality-results")
@RequiredArgsConstructor
public class QualityResultController {

  private final QualityApplicationService qualityService;

  @Operation(summary = "按记录/实体查询质量结果")
  @GetMapping
  public ApiResponse<List<QualityResultDTO>> list(
      @RequestParam(value = "recordId", required = false) Long recordId,
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId) {
    return ApiResponse.success(qualityService.listQualityResults(recordId, masterDataEntityId));
  }
}
