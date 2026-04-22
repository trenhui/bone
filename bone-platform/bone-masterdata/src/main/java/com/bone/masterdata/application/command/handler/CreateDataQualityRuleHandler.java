package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCmd;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateDataQualityRuleHandler {
    private final DataQualityRuleRepository ruleRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(CreateDataQualityRuleCmd cmd) {
        // 验证主数据实体存在
        MasterDataEntityId entityId = MasterDataEntityId.of(cmd.getMasterDataEntityId());
        if (entityRepository.findById(entityId) == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        RuleName ruleName = RuleName.of(cmd.getName());
        RuleSeverity severity = cmd.getSeverity();

        DataQualityRule rule = DataQualityRule.create(
            entityId,
            ruleName,
            cmd.getType(),
            cmd.getExpression(),
            severity,
            cmd.getDescription()
        );

        return ruleRepository.save(rule).getValue();
    }
}