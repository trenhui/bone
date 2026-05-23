package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCommand;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateDataQualityRule",
    description = "创建数据质量规则",
    inputSchema = "{\"masterDataEntityId\": \"long\", \"name\": \"string\", \"type\": \"string\", \"expression\": \"string\", \"severity\": \"string\"}",
    outputSchema = "{\"ruleId\": \"long\"}",
    idempotent = false,
    cost = 2,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class CreateDataQualityRuleHandler {
    private final DataQualityRuleRepository ruleRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(CreateDataQualityRuleCommand cmd) {
        if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
            throw NotFoundException.of("主数据实体不存在");
        }

        Long ruleId = DistributedIdGenerator.generateLongId();
        RuleName ruleName = RuleName.of(cmd.getName());
        RuleSeverity severity = cmd.getSeverity();

        DataQualityRule rule = DataQualityRule.create(
                ruleId,
                cmd.getMasterDataEntityId(),
                ruleName,
                cmd.getType(),
                cmd.getExpression(),
                severity,
                cmd.getDescription()
        );

        return ruleRepository.save(rule);
    }
}
