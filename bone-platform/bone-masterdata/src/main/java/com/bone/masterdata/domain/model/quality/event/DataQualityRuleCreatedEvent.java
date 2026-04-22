package com.bone.masterdata.domain.model.quality.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.vo.DataQualityRuleId;
import com.bone.masterdata.domain.model.quality.vo.RuleName;

public record DataQualityRuleCreatedEvent(DataQualityRuleId ruleId, MasterDataEntityId entityId, RuleName ruleName) implements DomainEvent {
    public DataQualityRuleCreatedEvent(DataQualityRule rule) {
        this(rule.getId(), rule.getMasterDataEntityId(), rule.getName());
    }
}