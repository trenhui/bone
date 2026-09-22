package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.request.CreateDataQualityRuleReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateDataQualityRuleReq;
import com.bone.masterdata.application.command.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.UpdateDataQualityRuleCommand;
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
        .severity(req.getSeverity())
        .description(req.getDescription())
        .build();
  }

  private static RuleSeverity parseSeverity(String severity) {
    return severity != null && !severity.isBlank() ? RuleSeverity.valueOf(severity) : null;
  }
}
