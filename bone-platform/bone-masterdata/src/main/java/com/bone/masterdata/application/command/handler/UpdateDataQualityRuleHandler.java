package com.bone.masterdata.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.masterdata.application.command.cmd.UpdateDataQualityRuleCmd;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.quality.vo.RuleName;
import com.bone.masterdata.domain.quality.vo.RuleSeverity;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateDataQualityRule",
    description = "更新数据质量规则",
    inputSchema = "{\"id\": \"long\", \"name\": \"string\", \"type\": \"string\", \"expression\": \"string\", \"severity\": \"string\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class UpdateDataQualityRuleHandler {
    private final DataQualityRuleRepository dataQualityRuleRepository;

    @Transactional
    public void handle(UpdateDataQualityRuleCmd cmd) {
        DataQualityRule rule = dataQualityRuleRepository.findById(cmd.getId());
        if (rule == null) {
            throw NotFoundException.of("数据质量规则不存在");
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
