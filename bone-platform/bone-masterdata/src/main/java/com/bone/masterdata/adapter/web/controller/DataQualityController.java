package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.DataQualityWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateDataQualityRuleReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateDataQualityRuleReq;
import com.bone.masterdata.application.QualityApplicationService;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.dto.QualityCheckDTO;
import com.bone.masterdata.application.query.dto.QualityReportDTO;
import com.bone.masterdata.application.query.qry.DataQualityRuleDetailQuery;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQuery;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/quality")
@RequiredArgsConstructor
public class DataQualityController {

  private final QualityApplicationService qualityService;
  private final DataQualityWebConverter converter;

  @PostMapping("/rules")
  public ApiResponse<Long> createRule(@RequestBody CreateDataQualityRuleReq req) {
    Long id = qualityService.createRule(converter.toCommand(req));
    return ApiResponse.success(id);
  }

  @GetMapping("/rules")
  public ApiResponse<List<DataQualityRuleDTO>> listRules(DataQualityRuleListQuery qry) {
    return ApiResponse.success(qualityService.ruleList(qry));
  }

  @GetMapping("/rules/{id}")
  public ApiResponse<DataQualityRuleDTO> getRule(@PathVariable Long id) {
    return ApiResponse.success(qualityService.ruleDetail(new DataQualityRuleDetailQuery(id)));
  }

  @PutMapping("/rules/{id}")
  public ApiResponse<Void> updateRule(
      @PathVariable Long id, @Valid @RequestBody UpdateDataQualityRuleReq req) {
    qualityService.updateRule(converter.toCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/rules/{id}")
  public ApiResponse<Void> deleteRule(@PathVariable Long id) {
    qualityService.deleteRule(id);
    return ApiResponse.success();
  }

  @PostMapping("/check")
  public ApiResponse<Long> check(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId) {
    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(masterDataEntityId);
    return ApiResponse.success(qualityService.performCheck(cmd));
  }

  @GetMapping("/checks")
  public ApiResponse<List<QualityCheckDTO>> listChecks(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId) {
    return ApiResponse.success(qualityService.listChecks(masterDataEntityId));
  }

  @GetMapping("/reports")
  public ApiResponse<List<QualityReportDTO>> listReports(
      @RequestParam(value = "qualityCheckId", required = false) Long qualityCheckId) {
    return ApiResponse.success(qualityService.listReports(qualityCheckId));
  }

  @GetMapping("/reports/{id}")
  public ApiResponse<QualityReportDTO> getReport(@PathVariable Long id) {
    return ApiResponse.success(qualityService.report(id));
  }
}
