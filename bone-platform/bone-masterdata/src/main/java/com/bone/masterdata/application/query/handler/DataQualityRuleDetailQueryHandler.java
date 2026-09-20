package com.bone.masterdata.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.qry.DataQualityRuleDetailQuery;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataQualityRuleDetailQueryHandler {

  private final DataQualityRuleRepository dataQualityRuleRepository;

  @Transactional(readOnly = true)
  public DataQualityRuleDTO handle(DataQualityRuleDetailQuery qry) {
    DataQualityRule rule = dataQualityRuleRepository.findById(qry.id());
    if (rule == null) {
      throw NotFoundException.of("数据质量规则不存在");
    }
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
