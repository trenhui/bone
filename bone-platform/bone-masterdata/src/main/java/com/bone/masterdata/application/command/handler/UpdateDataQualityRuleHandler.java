package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.UpdateDataQualityRuleCmd;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.vo.DataQualityRuleId;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateDataQualityRuleHandler {
    private final DataQualityRuleRepository dataQualityRuleRepository;

    @Transactional
    public void handle(UpdateDataQualityRuleCmd cmd) {
        DataQualityRule rule = dataQualityRuleRepository.findById(DataQualityRuleId.of(cmd.getId()));
        if (rule == null) {
            throw new NotFoundException("数据质量规则不存在");
        }

        rule.update(
            RuleName.of(cmd.getName()),
            cmd.getRuleType(),
            cmd.getRuleConfig(),
            RuleSeverity.valueOf(cmd.getSeverity()),
            cmd.getDescription()
        );
        dataQualityRuleRepository.update(rule);
    }
}
