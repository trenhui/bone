package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.request.CreateDataQualityRuleReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateDataQualityRuleReq;
import com.bone.masterdata.application.command.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.UpdateDataQualityRuleCommand;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.quality.valueobject.RuleSeverity;
import org.springframework.stereotype.Component;

@Component
public class DataQualityWebConverter {

  public CreateDataQualityRuleCommand toCommand(CreateDataQualityRuleReq req) {
    return CreateDataQualityRuleCommand.builder()
        .masterDataEntityId(req.getMasterDataEntityId())
        .name(req.getName())
        .type(req.getType())
        .expression(req.getExpression())
        .severity(parseSeverity(req.getSeverity()))
        .description(req.getDescription())
        .build();
  }

  public UpdateDataQualityRuleCommand toCommand(Long id, UpdateDataQualityRuleReq req) {
    return UpdateDataQualityRuleCommand.builder()
        .id(id)
        .name(req.getName())
        .type(req.getType())
        .expression(req.getExpression())
        .severity(parseSeverity(req.getSeverity()))
        .description(req.getDescription())
        .build();
  }

  /**
   * 解析严重级别。
   *
   * <p>历史上这里直接 {@code RuleSeverity.valueOf}，非法取值（如前端旧选项 ERROR/WARNING/INFO）会抛出
   * IllegalArgumentException 并被兜底成 400「No enum constant ...」——用户看不懂枚举全名。改为显式 400 + 可选值列表。
   */
  private static RuleSeverity parseSeverity(String severity) {
    if (severity == null || severity.isBlank()) {
      return null;
    }
    for (RuleSeverity candidate : RuleSeverity.values()) {
      if (candidate.name().equalsIgnoreCase(severity.trim())) {
        return candidate;
      }
    }
    throw MasterDataErrors.of(
        MasterDataErrorCodes.RULE_SEVERITY_INVALID,
        severity + "（可选: LOW / MEDIUM / HIGH / CRITICAL）");
  }
}
