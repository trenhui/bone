package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.query.dto.QualityResultDTO;
import com.bone.masterdata.domain.quality.QualityCheck;
import com.bone.masterdata.domain.quality.QualityReport;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 质量结果控制器：按记录/实体返回质量检查结果（非实时计算，基于现有质量数据映射）。 */
@Tag(name = "质量结果", description = "质量检查结果查询接口")
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/quality-results")
@RequiredArgsConstructor
public class QualityResultController {

  @Operation(summary = "按记录/实体查询质量结果")
  @GetMapping
  public ApiResponse<List<QualityResultDTO>> list(
      @RequestParam(value = "recordId", required = false) Long recordId,
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
    List<QualityResultDTO> results = new ArrayList<>();
    for (QualityCheck check : checks) {
      List<QualityReport> reports =
          QueryBuilder.from(QualityReport.class)
              .where(QualityReport::getQualityCheckId)
              .eq(check.getId())
              .list();
      long failed = check.getFailedRecords() != null ? check.getFailedRecords() : 0L;
      for (QualityReport report : reports) {
        Long ruleId = resolveRuleId(report);
        results.add(
            QualityResultDTO.builder()
                .id(report.getId())
                .masterDataRecordId(recordId)
                .dataQualityRuleId(ruleId)
                .passed(failed == 0L)
                .message(buildMessage(failed, report))
                .timestamp(
                    report.getCreatedAt() != null ? report.getCreatedAt() : LocalDateTime.now())
                .build());
      }
      if (reports.isEmpty()) {
        results.add(
            QualityResultDTO.builder()
                .id(check.getId())
                .masterDataRecordId(recordId)
                .dataQualityRuleId(check.getMasterDataEntityId())
                .passed(failed == 0L)
                .message(failed == 0L ? "质量检查通过" : "质量检查存在 " + failed + " 条未通过记录")
                .timestamp(check.getEndedAt() != null ? check.getEndedAt() : LocalDateTime.now())
                .build());
      }
    }
    return ApiResponse.success(results);
  }

  private Long resolveRuleId(QualityReport report) {
    try {
      return report.getQualityCheckId();
    } catch (Exception e) {
      return null;
    }
  }

  private String buildMessage(long failed, QualityReport report) {
    Integer issues = report.getIssueCount();
    if (issues != null && issues > 0) {
      return "发现 " + issues + " 个质量问题";
    }
    return failed > 0 ? "部分记录未通过校验" : "通过";
  }
}
