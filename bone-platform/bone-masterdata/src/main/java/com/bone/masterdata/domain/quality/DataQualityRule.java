package com.bone.masterdata.domain.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.masterdata.domain.quality.event.DataQualityRuleCreatedEvent;
import com.bone.masterdata.domain.quality.vo.RuleName;
import com.bone.masterdata.domain.quality.vo.RuleSeverity;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("md_quality_rule")
public class DataQualityRule extends AggregateRoot<Long> {
    private Long id;
    private Long masterDataEntityId;
    private RuleName name;
    private String type;
    private String expression;
    private RuleSeverity severity;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static DataQualityRule create(
            Long id,
            Long masterDataEntityId,
            RuleName name,
            String type,
            String expression,
            RuleSeverity severity,
            String description
    ) {
        DataQualityRule rule = new DataQualityRule();
        rule.id = id;
        rule.masterDataEntityId = masterDataEntityId;
        rule.name = name;
        rule.type = type;
        rule.expression = expression;
        rule.severity = severity;
        rule.description = description;
        rule.createTime = LocalDateTime.now();
        rule.updateTime = LocalDateTime.now();
        rule.addDomainEvent(new DataQualityRuleCreatedEvent(rule));
        return rule;
    }

    public void update(
            RuleName name,
            String type,
            String expression,
            RuleSeverity severity,
            String description
    ) {
        this.name = name;
        this.type = type;
        this.expression = expression;
        this.severity = severity;
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }
}
