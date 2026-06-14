package com.bone.masterdata.application.query.handler;

import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQuery;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataQualityRuleListQueryHandler {

  @Transactional(readOnly = true)
  public List<DataQualityRuleDTO> handle(DataQualityRuleListQuery qry) {
    List<DataQualityRule> rules =
        QueryBuilder.from(DataQualityRule.class)
            .where(DataQualityRule::getMasterDataEntityId)
            .eq(qry.getMasterDataEntityId())
            .list();

    return rules.stream()
        .filter(rule -> qry.getType() == null || rule.getType().equals(qry.getType()))
        .filter(
            rule ->
                qry.getSeverity() == null || rule.getSeverity().name().equals(qry.getSeverity()))
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  private DataQualityRuleDTO toDto(DataQualityRule rule) {
    DataQualityRuleDTO dto = new DataQualityRuleDTO();
    dto.setId(rule.getId());
    dto.setMasterDataEntityId(rule.getMasterDataEntityId());
    dto.setName(rule.getName().value());
    dto.setType(rule.getType());
    dto.setExpression(rule.getExpression());
    dto.setSeverity(rule.getSeverity());
    dto.setDescription(rule.getDescription());
    dto.setCreatedAt(rule.getCreatedAt());
    dto.setUpdatedAt(rule.getUpdatedAt());
    return dto;
  }
}
