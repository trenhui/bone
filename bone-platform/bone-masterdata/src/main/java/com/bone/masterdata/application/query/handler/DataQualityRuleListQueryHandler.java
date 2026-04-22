package com.bone.masterdata.application.query.handler;

import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQry;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataQualityRuleListQueryHandler {
    private final DataQualityRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public List<DataQualityRuleDTO> handle(DataQualityRuleListQry qry) {
        List<DataQualityRule> rules = ruleRepository.findByMasterDataEntityId(MasterDataEntityId.of(qry.getMasterDataEntityId()));

        return rules.stream()
                .filter(rule -> qry.getType() == null || rule.getType().equals(qry.getType()))
                .filter(rule -> qry.getSeverity() == null || rule.getSeverity().name().equals(qry.getSeverity()))
                .map(rule -> {
                    DataQualityRuleDTO dto = new DataQualityRuleDTO();
                    dto.setId(rule.getId().getValue());
                    dto.setMasterDataEntityId(rule.getMasterDataEntityId().getValue());
                    dto.setName(rule.getName().value());
                    dto.setType(rule.getType());
                    dto.setExpression(rule.getExpression());
                    dto.setSeverity(rule.getSeverity());
                    dto.setDescription(rule.getDescription());
                    dto.setCreateTime(rule.getCreateTime());
                    dto.setUpdateTime(rule.getUpdateTime());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}