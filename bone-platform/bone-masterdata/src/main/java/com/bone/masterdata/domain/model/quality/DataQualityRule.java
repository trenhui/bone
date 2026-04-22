package com.bone.masterdata.domain.model.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.quality.event.DataQualityRuleCreatedEvent;
import com.bone.masterdata.domain.model.quality.vo.DataQualityRuleId;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DataQualityRule extends AggregateRoot<DataQualityRuleId> {
    private MasterDataEntityId masterDataEntityId;
    private RuleName name;
    private String type;
    private String expression;
    private RuleSeverity severity;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static DataQualityRule create(
            MasterDataEntityId masterDataEntityId,
            RuleName name,
            String type,
            String expression,
            RuleSeverity severity,
            String description
    ) {
        DataQualityRule rule = new DataQualityRule();
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

    // 仅供 SDK 回填 ID 使用
    void setId(DataQualityRuleId id) {
        super.setId(id);
    }
}