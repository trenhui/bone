package com.bone.masterdata.domain.service.quality;

import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.quality.QualityCheck;
import com.bone.masterdata.domain.quality.QualityReport;
import com.bone.masterdata.domain.record.MasterDataRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 数据质量领域服务 处理数据质量规则相关的业务逻辑 */
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

  /**
   * 执行数据质量检查。
   *
   * <p>规则与记录的读取属于读侧查询，由应用层（允许使用 Criteria/QueryBuilder）完成并传入；领域服务只负责基于传入数据执行检查计算。
   */
  public QualityCheck performQualityCheck(
      Long checkId,
      Long masterDataEntityId,
      List<DataQualityRule> rules,
      List<MasterDataRecord> records) {
    QualityCheck check = QualityCheck.create(checkId, masterDataEntityId);

    // 执行质量检查逻辑（简化实现）
    int totalRecords = records.size();
    int passedRecords = 0;
    int failedRecords = 0;

    // 模拟质量检查结果
    for (var record : records) {
      // 这里应该根据规则执行实际的质量检查
      // 简化处理，假设一半通过，一半失败
      if (Math.random() > 0.5) {
        passedRecords++;
      } else {
        failedRecords++;
      }
    }

    check.complete(totalRecords, passedRecords, failedRecords);
    return check;
  }

  public QualityReport generateQualityReport(
      Long reportId, Long qualityCheckId, String reportData, Integer issueCount) {
    return QualityReport.create(reportId, qualityCheckId, reportData, issueCount);
  }
}
