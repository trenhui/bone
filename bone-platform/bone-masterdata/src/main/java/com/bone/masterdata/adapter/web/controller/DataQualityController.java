package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.DataQualityWebConverter;
import com.bone.masterdata.adapter.web.dto.req.CreateDataQualityRuleReq;
import com.bone.masterdata.adapter.web.dto.req.PerformDataQualityCheckReq;
import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.command.handler.CreateDataQualityRuleHandler;
import com.bone.masterdata.application.command.handler.PerformDataQualityCheckHandler;
import com.bone.masterdata.application.query.dto.DataQualityReportDTO;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.dto.QualityCheckDTO;
import com.bone.masterdata.application.query.dto.QualityReportDTO;
import com.bone.masterdata.application.query.handler.DataQualityRuleListQueryHandler;
import com.bone.masterdata.application.query.handler.GetQualityReportQueryHandler;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQuery;
import com.bone.masterdata.domain.quality.QualityCheck;
import com.bone.masterdata.domain.quality.QualityReport;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/quality")
@RequiredArgsConstructor
public class DataQualityController {
  private final CreateDataQualityRuleHandler createRuleHandler;
  private final DataQualityRuleListQueryHandler ruleListQueryHandler;
  private final PerformDataQualityCheckHandler performCheckHandler;
  private final GetQualityReportQueryHandler getQualityReportQueryHandler;
  private final DataQualityWebConverter converter;

  @PostMapping("/rules")
  public ApiResponse<Long> createRule(@RequestBody CreateDataQualityRuleReq req) {
    CreateDataQualityRuleCommand cmd = converter.toCommand(req);
    Long id = createRuleHandler.handle(cmd);
    return ApiResponse.success(id);
  }

  @GetMapping("/rules")
  public ApiResponse<List<DataQualityRuleDTO>> listRules(DataQualityRuleListQuery qry) {
    List<DataQualityRuleDTO> rules = ruleListQueryHandler.handle(qry);
    return ApiResponse.success(rules);
  }

  @PostMapping("/checks")
  public ApiResponse<Long> performCheck(@Valid @RequestBody PerformDataQualityCheckReq req) {
    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(req.getMasterDataEntityId());
    Long reportId = performCheckHandler.handle(cmd);
    return ApiResponse.success(reportId);
  }

  @GetMapping("/checks")
  public ApiResponse<List<QualityCheckDTO>> listChecks(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId) {
    List<QualityCheck> checks;
    if (masterDataEntityId != null) {
      checks =
          QueryBuilder.from(QualityCheck.class)
              .where(QualityCheck::getMasterDataEntityId)
              .eq(masterDataEntityId)
              .list();
    } else {
      checks = QueryBuilder.from(QualityCheck.class).list();
    }
    List<QualityCheckDTO> dtos = checks.stream().map(this::toCheckDto).collect(Collectors.toList());
    return ApiResponse.success(dtos);
  }

  @GetMapping("/reports")
  public ApiResponse<List<DataQualityReportDTO>> listReports(
      @RequestParam(value = "qualityCheckId", required = false) Long qualityCheckId) {
    List<QualityReport> reports;
    if (qualityCheckId != null) {
      reports =
          QueryBuilder.from(QualityReport.class)
              .where(QualityReport::getQualityCheckId)
              .eq(qualityCheckId)
              .list();
    } else {
      reports = QueryBuilder.from(QualityReport.class).list();
    }
    List<DataQualityReportDTO> dtos =
        reports.stream().map(this::toReportDto).collect(Collectors.toList());
    return ApiResponse.success(dtos);
  }

  @GetMapping("/reports/{id}")
  public ApiResponse<QualityReportDTO> getReport(@PathVariable Long id) {
    return ApiResponse.success(getQualityReportQueryHandler.handle(id));
  }

  private QualityCheckDTO toCheckDto(QualityCheck check) {
    return QualityCheckDTO.builder()
        .id(check.getId())
        .masterDataEntityId(check.getMasterDataEntityId())
        .status(check.getStatus())
        .totalRecords(check.getTotalRecords())
        .passedRecords(check.getPassedRecords())
        .failedRecords(check.getFailedRecords())
        .startedAt(
            check.getStartedAt() != null
                ? java.util.Date.from(
                    check.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                : null)
        .endedAt(
            check.getEndedAt() != null
                ? java.util.Date.from(
                    check.getEndedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                : null)
        .build();
  }

  private DataQualityReportDTO toReportDto(QualityReport report) {
    DataQualityReportDTO dto = new DataQualityReportDTO();
    dto.setId(report.getId());
    dto.setQualityCheckId(report.getQualityCheckId());
    dto.setReportData(report.getReportData());
    dto.setIssueCount(report.getIssueCount());
    dto.setCreatedAt(report.getCreatedAt());
    return dto;
  }
}
