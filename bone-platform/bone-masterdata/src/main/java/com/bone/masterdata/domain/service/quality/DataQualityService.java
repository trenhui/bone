package com.bone.masterdata.domain.service.quality;

import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.QualityReport;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import lombok.RequiredArgsConstructor;

/** 数据质量领域服务：承载跨用例的规则/报告构造逻辑。 */
@RequiredArgsConstructor
public class DataQualityService {

  public DataQualityRule createRule(
      Long id,
      Long masterDataEntityId,
      RuleName name,
      String type,
      String expression,
      RuleSeverity severity,
      String description) {
    return DataQualityRule.create(
        id, masterDataEntityId, name, type, expression, severity, description);
  }

  public QualityReport generateQualityReport(
      Long reportId, Long qualityCheckId, String reportData, Integer issueCount) {
    return QualityReport.create(reportId, qualityCheckId, reportData, issueCount);
  }
}
